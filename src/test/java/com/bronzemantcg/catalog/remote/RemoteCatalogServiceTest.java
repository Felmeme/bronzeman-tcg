package com.bronzemantcg.catalog.remote;

import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
	public void startupWaitsForLiveV1CapabilityBeforeFetching() throws Exception
	{
		service.startUp();
		assertFalse(active.isRemoteActive());
		assertEquals(0, client.fetches);

		service.setV1Capable(true);
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
		service.setV1Capable(true);
		client.succeed("{}".getBytes(StandardCharsets.UTF_8), "invalid");
		assertFalse(active.isRemoteActive());

		service.startUp();
		service.setV1Capable(true);
		service.shutDown();
		client.succeed("{}".getBytes(StandardCharsets.UTF_8), "late");
		assertFalse(active.isRemoteActive());
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
		private Listener listener;
		private int fetches;

		private FakeClient()
		{
			super(new OkHttpClient());
		}

		@Override
		public FetchHandle fetch(Listener listener)
		{
			fetches++;
			this.listener = listener;
			return new FetchHandle();
		}

		private void succeed(byte[] body, String version)
		{
			listener.onSuccess(new CatalogResponse(body, version, false));
		}
	}
}
