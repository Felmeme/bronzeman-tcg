package com.bronzemantcg.ownership;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.interop.BetaCardLookupClient;
import com.google.gson.Gson;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BetaCardCacheServiceTest
{
	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	private final AtomicBoolean consent = new AtomicBoolean(true);
	private final AtomicBoolean disableBetaVariants = new AtomicBoolean(false);
	private final TestProfileAccess profiles = new TestProfileAccess("profile-one");
	private ScheduledExecutorService executor;
	private BetaCardCacheStore store;
	private FakeLookupClient lookup;
	private BetaCardCacheService service;

	@Before
	public void setUp() throws Exception
	{
		Gson gson = new Gson();
		executor = Executors.newSingleThreadScheduledExecutor();
		store = new BetaCardCacheStore(gson, temporaryFolder.newFolder().toPath());
		lookup = new FakeLookupClient();
		service = new BetaCardCacheService(null, null, config(), profiles, lookup, store,
			new BundledCardIdentityCatalog(gson), gson, executor, () -> 1234L);
	}

	@After
	public void tearDown() throws Exception
	{
		service.shutDown();
		executor.shutdownNow();
		executor.awaitTermination(2, TimeUnit.SECONDS);
	}

	@Test
	public void startupLoadsOnlyTheCurrentProfilesLocalCache() throws Exception
	{
		store.save("profile-one", "Player One", 7, 999L,
			List.of("Water rune pack"));

		service.startUp();

		assertEquals(0, lookup.requests);
		assertEquals(BetaCardCacheService.Status.CACHED, service.getState().getStatus());
		assertEquals(Set.of("water rune pack"), service.getState().getBetaNamesLowerCase());
		profiles.profile = "profile-two";
		service.onProfileChanged();
		assertEquals(BetaCardCacheService.Status.NO_CACHE, service.getState().getStatus());
		assertTrue(service.getBetaCardUnlocks().getParentNamesLowerCase().isEmpty());
	}

	@Test
	public void cachedBetaNamesProjectToReviewedParentsWithoutPluginMessage() throws Exception
	{
		store.save("profile-one", "Player One", 7, 999L,
			List.of("Water rune pack", "Teak logs", "Crawling hand",
				"Future beta card"));
		service.startUp();

		BetaCardUnlockSource.View unlocks = service.getBetaCardUnlocks();
		assertEquals(Set.of("water rune", "teak logs"),
			unlocks.getParentNamesLowerCase(CardEntityKind.ITEM));
		assertTrue(unlocks.getParentNamesLowerCase(CardEntityKind.NPC).isEmpty());
		assertFalse(unlocks.getParentNamesLowerCase().contains("crawling hand"));
		assertFalse(unlocks.getParentNamesLowerCase().contains("future beta card"));
	}

	@Test
	public void disablingBetaVariantsSuppressesGameplayUnlocksWithoutDeletingCache()
		throws Exception
	{
		store.save("profile-one", "Player One", 7, 999L,
			List.of("Water rune pack", "Teak logs", "Aberrant spectre"));
		service.startUp();

		assertEquals(Set.of("water rune", "teak logs"),
			service.getBetaCardUnlocks().getParentNamesLowerCase(CardEntityKind.ITEM));
		assertEquals(Set.of("aberrant spectre"),
			service.getBetaCardUnlocks().getParentNamesLowerCase(CardEntityKind.NPC));
		disableBetaVariants.set(true);
		assertTrue(service.getBetaCardUnlocks().getParentNamesLowerCase().isEmpty());
		assertEquals(BetaCardCacheService.Status.CACHED, service.getState().getStatus());
		assertEquals(Set.of("water rune pack", "teak logs", "aberrant spectre"),
			service.getState().getBetaNamesLowerCase());

		disableBetaVariants.set(false);
		assertEquals(Set.of("water rune", "teak logs"),
			service.getBetaCardUnlocks().getParentNamesLowerCase(CardEntityKind.ITEM));
		assertEquals(Set.of("aberrant spectre"),
			service.getBetaCardUnlocks().getParentNamesLowerCase(CardEntityKind.NPC));
	}

	@Test
	public void explicitRefreshAcceptsAnAuthoritativeEmptyCollection() throws Exception
	{
		service.startUp();

		service.beginRefresh("profile-one", "Felmeme");
		lookup.respond(200,
			"{\"displayName\":\"Felmeme\",\"revision\":56,\"cardNames\":[]}");
		waitForStatus(BetaCardCacheService.Status.CACHED);

		assertEquals(0, service.getState().getCachedCount());
		assertEquals(56, service.getState().getRevision());
		assertTrue(store.load("profile-one").getCardNames().isEmpty());
		assertTrue(service.getBetaCardUnlocks().getParentNamesLowerCase().isEmpty());
	}

	@Test
	public void failedRefreshKeepsThePreviousValidatedClassification() throws Exception
	{
		store.save("profile-one", "Player One", 7, 999L,
			List.of("Water rune pack"));
		service.startUp();

		service.beginRefresh("profile-one", "Player One");
		lookup.respond(200,
			"{\"displayName\":\"Someone Else\",\"revision\":8,\"cardNames\":[]}");
		waitForStatus(BetaCardCacheService.Status.FAILED);

		assertEquals(Set.of("water rune pack"), service.getState().getBetaNamesLowerCase());
		assertEquals(List.of("Water rune pack"), store.load("profile-one").getCardNames());
		assertEquals(Set.of("water rune"),
			service.getBetaCardUnlocks().getParentNamesLowerCase(CardEntityKind.ITEM));
	}

	@Test
	public void underscorePlayerAcceptsMatchingResponse() throws Exception
	{
		service.startUp();

		service.beginRefresh("profile-one", "TCG_Bruh");
		lookup.respond(200,
			"{\"displayName\":\"TCG_Bruh\",\"revision\":57,\"cardNames\":[]}");
		waitForStatus(BetaCardCacheService.Status.CACHED);

		assertEquals("TCG_Bruh", service.getState().getDisplayName());
		assertEquals(57, store.load("profile-one").getRevision());
	}

	@Test
	public void underscorePlayerRejectsSpaceNameResponseAndKeepsPreviousCache()
		throws Exception
	{
		store.save("profile-one", "TCG_Bruh", 7, 999L,
			List.of("Water rune pack"));
		service.startUp();

		service.beginRefresh("profile-one", "TCG_Bruh");
		lookup.respond(200,
			"{\"displayName\":\"TCG Bruh\",\"revision\":58,\"cardNames\":[]}");
		waitForStatus(BetaCardCacheService.Status.FAILED);

		assertEquals(Set.of("water rune pack"), service.getState().getBetaNamesLowerCase());
		assertEquals(7, store.load("profile-one").getRevision());
	}

	@Test
	public void responseWithoutRevisionIsRejected() throws Exception
	{
		store.save("profile-one", "Player One", 7, 999L,
			List.of("Water rune pack"));
		service.startUp();

		service.beginRefresh("profile-one", "Player One");
		lookup.respond(200,
			"{\"displayName\":\"Player One\",\"cardNames\":[]}");
		waitForStatus(BetaCardCacheService.Status.FAILED);

		assertEquals(Set.of("water rune pack"), service.getState().getBetaNamesLowerCase());
		assertEquals(7, store.load("profile-one").getRevision());
	}

	@Test
	public void synchronousLookupFailureDoesNotRemainRefreshing()
	{
		service.startUp();
		lookup.failSynchronously = true;

		service.beginRefresh("profile-one", "Player One");

		assertEquals(BetaCardCacheService.Status.FAILED, service.getState().getStatus());
		assertTrue(service.getState().getMessage().contains("Could not start"));
	}

	@Test
	public void withdrawingConsentCancelsOnlyTheRequestAndKeepsCache() throws Exception
	{
		store.save("profile-one", "Player One", 7, 999L,
			List.of("Water rune pack"));
		service.startUp();
		service.beginRefresh("profile-one", "Player One");
		BetaCardLookupClient.FetchHandle handle = lookup.lastHandle;

		consent.set(false);
		service.onLookupConsentChanged();

		assertTrue(handle.isCancelled());
		assertEquals(BetaCardCacheService.Status.FAILED, service.getState().getStatus());
		assertFalse(service.getState().getBetaNamesLowerCase().isEmpty());
		assertEquals(Set.of("water rune"),
			service.getBetaCardUnlocks().getParentNamesLowerCase(CardEntityKind.ITEM));
	}

	@Test
	public void clearRemovesRawNamesAndProjectedUnlocks() throws Exception
	{
		store.save("profile-one", "Player One", 7, 999L,
			List.of("Water rune pack"));
		service.startUp();

		service.clear();

		assertEquals(BetaCardCacheService.Status.NO_CACHE, service.getState().getStatus());
		assertTrue(service.getState().getBetaNamesLowerCase().isEmpty());
		assertTrue(service.getBetaCardUnlocks().getParentNamesLowerCase().isEmpty());
		assertNull(store.load("profile-one"));
	}

	private void waitForStatus(BetaCardCacheService.Status expected) throws Exception
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (service.getState().getStatus() != expected && System.nanoTime() < deadline)
		{
			Thread.sleep(10L);
		}
		assertEquals(expected, service.getState().getStatus());
	}

	private BronzemanTcgConfig config()
	{
		return (BronzemanTcgConfig) Proxy.newProxyInstance(
			BronzemanTcgConfig.class.getClassLoader(),
			new Class<?>[]{BronzemanTcgConfig.class}, (proxy, method, arguments) ->
			{
				if (method.getName().equals("allowBetaCardLookup"))
				{
					return consent.get();
				}
				if (method.getName().equals("disableBetaVariants"))
				{
					return disableBetaVariants.get();
				}
				if (method.getName().equals("toString"))
				{
					return "test config";
				}
				throw new UnsupportedOperationException(method.getName());
			});
	}

	private static final class TestProfileAccess implements BetaCardCacheService.ProfileAccess
	{
		private String profile;

		private TestProfileAccess(String profile)
		{
			this.profile = profile;
		}

		@Override
		public String currentProfileKey()
		{
			return profile;
		}
	}

	private static final class FakeLookupClient extends BetaCardLookupClient
	{
		private Listener listener;
		private int requests;
		private FetchHandle lastHandle;
		private boolean failSynchronously;

		private FakeLookupClient()
		{
			super(new OkHttpClient(), "https://example.test/api/v1/players", 1024 * 1024);
		}

		@Override
		public FetchHandle fetch(String displayName, Listener listener)
		{
			requests++;
			if (failSynchronously)
			{
				throw new IllegalStateException("test failure");
			}
			this.listener = listener;
			lastHandle = new FetchHandle();
			return lastHandle;
		}

		private void respond(int status, String json)
		{
			listener.onResponse(new LookupResponse(status,
				json.getBytes(StandardCharsets.UTF_8)));
		}
	}
}
