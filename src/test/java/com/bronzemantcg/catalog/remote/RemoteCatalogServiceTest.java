package com.bronzemantcg.catalog.remote;

import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteCatalogServiceTest
{
	private FakeClient client;
	private ActiveCardIdentityCatalog active;
	private RemoteCatalogService service;
	private AtomicInteger notifications;

	@Before
	public void setUp()
	{
		Gson gson = new Gson();
		BundledCardIdentityCatalog bundled = new BundledCardIdentityCatalog(gson);
		active = new ActiveCardIdentityCatalog(bundled);
		client = new FakeClient();
		notifications = new AtomicInteger();
		service = new RemoteCatalogService(client, new OsrsTcgCatalogParser(gson),
			new RemoteCatalogValidator(1, 1, Map.of()), bundled, active);
		service.setListener((revision, available) -> notifications.incrementAndGet());
	}

	@Test
	public void startupRequiresExplicitEnablementAndLiveV1CapabilityBeforeFetching()
		throws Exception
	{
		service.startUp();
		assertFalse(active.isRemoteActive());
		assertEquals(0, client.fetches);

		service.setV1Capable(true);
		assertEquals(0, client.fetches);

		service.setEnabled(true);
		assertEquals(1, client.fetches);
		client.succeed(fixture(), "version-1");

		assertTrue(active.isRemoteActive());
		assertTrue(active.isV1CatalogAvailable());
		assertTrue(active.findById(CardEntityKind.ITEM, 12730).size() == 1);
		assertTrue(notifications.get() > 0);
	}

	@Test
	public void repeatedCapabilityMessagesStartOnlyOneFetch() throws Exception
	{
		service.startUp();
		service.setEnabled(true);
		service.setV1Capable(true);
		service.setV1Capable(true);
		service.setV1Capable(true);

		assertEquals(1, client.fetches);
		client.succeed(fixture(), "version-1");

		assertTrue(active.isRemoteActive());
		assertTrue(notifications.get() > 0);
	}

	@Test
	public void losingCapabilityReturnsToBundleButKeepsValidatedPendingRevision()
		throws Exception
	{
		service.startUp();
		service.setEnabled(true);
		service.setV1Capable(true);
		client.succeed(fixture(), "version-1");
		assertTrue(active.isRemoteActive());

		service.setV1Capable(false);
		assertFalse(active.isRemoteActive());
		assertFalse(active.isV1CatalogAvailable());

		service.setV1Capable(true);
		assertTrue(active.isRemoteActive());
		assertEquals(1, client.fetches);
		assertTrue(notifications.get() >= 3);
	}

	@Test
	public void responseWhileCapabilityIsLostRemainsPendingWithoutRefetching()
		throws Exception
	{
		service.startUp();
		service.setEnabled(true);
		service.setV1Capable(true);
		service.setV1Capable(false);
		client.succeed(fixture(), "version-1");

		assertFalse(active.isRemoteActive());
		service.setV1Capable(true);

		assertTrue(active.isRemoteActive());
		assertEquals(1, client.fetches);
	}

	@Test
	public void invalidResponseAndLateShutdownCallbackNeverActivate()
	{
		service.startUp();
		service.setEnabled(true);
		service.setV1Capable(true);
		client.succeed("{}".getBytes(StandardCharsets.UTF_8), "invalid");
		assertFalse(active.isRemoteActive());

		service.startUp();
		service.setEnabled(true);
		service.setV1Capable(true);
		service.shutDown();
		client.succeed("{}".getBytes(StandardCharsets.UTF_8), "late");
		assertFalse(active.isRemoteActive());
	}

	@Test
	public void disablingCancelsFetchRejectsLateResponseAndAllowsRefetch() throws Exception
	{
		service.startUp();
		service.setEnabled(true);
		service.setV1Capable(true);
		OsrsTcgCatalogClient.FetchHandle firstHandle = client.handles.get(0);

		service.setEnabled(false);

		assertTrue(firstHandle.isCancelled());
		assertFalse(active.isRemoteActive());
		client.succeed(0, fixture(), "cancelled-version");
		assertFalse(active.isRemoteActive());

		service.setEnabled(true);
		assertEquals(2, client.fetches);
		client.succeed(1, fixture(), "enabled-version");
		assertTrue(active.isRemoteActive());
	}

	private static byte[] fixture() throws IOException
	{
		try (InputStream stream = RemoteCatalogServiceTest.class
			.getResourceAsStream("/osrs-tcg-live-catalog-fixture.json"))
		{
			if (stream == null)
			{
				throw new IOException("fixture missing");
			}
			return stream.readAllBytes();
		}
	}

	private static final class FakeClient extends OsrsTcgCatalogClient
	{
		private final List<Listener> listeners = new ArrayList<>();
		private final List<FetchHandle> handles = new ArrayList<>();
		private int fetches;

		private FakeClient()
		{
			super(new OkHttpClient());
		}

		@Override
		public FetchHandle fetch(Listener listener)
		{
			fetches++;
			listeners.add(listener);
			FetchHandle handle = new FetchHandle();
			handles.add(handle);
			return handle;
		}

		private void succeed(byte[] body, String version)
		{
			succeed(listeners.size() - 1, body, version);
		}

		private void succeed(int index, byte[] body, String version)
		{
			listeners.get(index).onSuccess(new CatalogResponse(body, version, false));
		}
	}
}
