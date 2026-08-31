package com.bronzemantcg.catalog.remote;

import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Fetches, validates and capability-gates the public OSRS TCG identity catalogue. */
@Slf4j
@Singleton
public final class RemoteCatalogService
{
	private final OsrsTcgCatalogClient client;
	private final OsrsTcgCatalogParser parser;
	private final RemoteCatalogValidator validator;
	private final BundledCardIdentityCatalog bundledCatalog;
	private final ActiveCardIdentityCatalog activeCatalog;
	private final AtomicLong generation = new AtomicLong();
	private final AtomicReference<OsrsTcgCatalogClient.FetchHandle> activeFetch =
		new AtomicReference<>();

	private boolean running;
	private boolean v1Capable;
	private boolean fetchStarted;
	private OsrsTcgCatalogSnapshot pendingSnapshot;
	private String pendingVersion;
	private volatile Listener listener = Listener.NONE;

	@Inject
	public RemoteCatalogService(OsrsTcgCatalogClient client,
		OsrsTcgCatalogParser parser, RemoteCatalogValidator validator,
		BundledCardIdentityCatalog bundledCatalog,
		ActiveCardIdentityCatalog activeCatalog)
	{
		this.client = client;
		this.parser = parser;
		this.validator = validator;
		this.bundledCatalog = bundledCatalog;
		this.activeCatalog = activeCatalog;
	}

	public void setListener(Listener listener)
	{
		this.listener = listener == null ? Listener.NONE : listener;
	}

	public void startUp()
	{
		generation.incrementAndGet();
		cancelActiveFetch();
		synchronized (this)
		{
			running = true;
			v1Capable = false;
			fetchStarted = false;
			pendingSnapshot = null;
			pendingVersion = null;
			activeCatalog.useBundled();
		}
	}

	private void startFetch(long activeGeneration)
	{
		OsrsTcgCatalogClient.FetchHandle handle = client.fetch(
			new OsrsTcgCatalogClient.Listener()
			{
				@Override
				public void onSuccess(OsrsTcgCatalogClient.CatalogResponse response)
				{
					handleSuccess(activeGeneration, response);
				}

				@Override
				public void onFailure(String reason, Throwable cause)
				{
					if (activeGeneration == generation.get())
					{
						log.debug("Remote OSRS TCG catalogue unavailable: {}", reason, cause);
					}
				}
			});
		activeFetch.set(handle);
		if (activeGeneration != generation.get())
		{
			handle.cancel();
		}
	}

	public void setV1Capable(boolean capable)
	{
		long changedRevision;
		long fetchGeneration = -1L;
		synchronized (this)
		{
			long before = activeCatalog.getRevision();
			v1Capable = running && capable;
			if (!v1Capable)
			{
				activeCatalog.useBundled();
			}
			else
			{
				activatePendingIfReady();
				if (pendingSnapshot == null && !fetchStarted)
				{
					fetchStarted = true;
					fetchGeneration = generation.get();
				}
			}
			changedRevision = changedRevision(before);
		}
		notifyChanged(changedRevision);
		if (fetchGeneration >= 0)
		{
			startFetch(fetchGeneration);
		}
	}

	public void shutDown()
	{
		generation.incrementAndGet();
		cancelActiveFetch();
		synchronized (this)
		{
			running = false;
			v1Capable = false;
			fetchStarted = false;
			pendingSnapshot = null;
			pendingVersion = null;
			activeCatalog.useBundled();
		}
	}

	private void cancelActiveFetch()
	{
		OsrsTcgCatalogClient.FetchHandle handle = activeFetch.getAndSet(null);
		if (handle != null)
		{
			handle.cancel();
		}
	}

	private void handleSuccess(long activeGeneration,
		OsrsTcgCatalogClient.CatalogResponse response)
	{
		if (activeGeneration != generation.get())
		{
			return;
		}
		try
		{
			OsrsTcgCatalogSnapshot remote;
			try (InputStreamReader reader = new InputStreamReader(
				new ByteArrayInputStream(response.getBody()), StandardCharsets.UTF_8))
			{
				remote = parser.parse(reader);
			}
			validator.validate(remote);
			remote = remote.withLegacyAliases(bundledCatalog.getEntries());
			long changedRevision;
			synchronized (this)
			{
				if (!running || activeGeneration != generation.get())
				{
					return;
				}
				pendingSnapshot = remote;
				pendingVersion = response.getVersion();
				long before = activeCatalog.getRevision();
				activatePendingIfReady();
				changedRevision = changedRevision(before);
			}
			notifyChanged(changedRevision);
			log.info("Validated OSRS TCG catalogue (version={}, cached={}, active={})",
				response.getVersion(), response.isServedFromCache(), activeCatalog.isRemoteActive());
		}
		catch (CatalogValidationException | IOException | RuntimeException exception)
		{
			if (activeGeneration == generation.get())
			{
				log.warn("Rejected remote OSRS TCG catalogue; Beta fallback remains active.",
					exception);
			}
		}
	}

	private void activatePendingIfReady()
	{
		if (running && v1Capable && pendingSnapshot != null)
		{
			activeCatalog.activate(pendingSnapshot, pendingSnapshot.getEntries(), pendingVersion);
		}
	}

	private long changedRevision(long before)
	{
		long after = activeCatalog.getRevision();
		return after == before ? -1L : after;
	}

	private void notifyChanged(long revision)
	{
		if (revision < 0)
		{
			return;
		}
		try
		{
			listener.onActiveCatalogChanged(revision, activeCatalog.isV1CatalogAvailable());
		}
		catch (RuntimeException ex)
		{
			log.warn("Remote catalogue listener failed", ex);
		}
	}

	public interface Listener
	{
		Listener NONE = (revision, v1CatalogAvailable) -> { };

		void onActiveCatalogChanged(long revision, boolean v1CatalogAvailable);
	}
}
