package com.bronzemantcg.restriction;

import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import com.google.gson.Gson;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ExemptionListTest
{
	private ExemptionList exemptions;

	@Before
	public void setUp()
	{
		Gson gson = new Gson();
		exemptions = new ExemptionList(new ActiveCardIdentityCatalog(
			new BundledCardIdentityCatalog(gson)));
	}

	@Test
	public void exactEntriesRemainCaseDoseAndWhitespaceInsensitive()
	{
		ExemptionList.Snapshot snapshot = exemptions.resolve("  Prayer Potion(4), HAMMER ");

		assertTrue(snapshot.containsEntity("Prayer potion(1)"));
		assertTrue(snapshot.containsEntity("hammer"));
		assertFalse(snapshot.containsEntity("Rune hammer"));
	}

	@Test
	public void wildcardMatchesTheWholeNormalizedName()
	{
		ExemptionList.Snapshot snapshot = exemptions.resolve("Rune*, *potion*, * dragon leather");

		assertTrue(snapshot.containsEntity("Rune axe"));
		assertTrue(snapshot.containsEntity("Divine super attack potion"));
		assertTrue(snapshot.containsEntity("Green dragon leather"));
		assertFalse(snapshot.containsEntity("Adamant axe"));
		assertFalse(exemptions.resolve("Rune").containsEntity("Rune axe"));
	}

	@Test
	public void questionMarkIsLiteralAndLoneWildcardIsIgnored()
	{
		ExemptionList.Snapshot wildcardOnly = exemptions.resolve("*, **");
		ExemptionList.Snapshot literalQuestionMark = exemptions.resolve("Rune ?xe");

		assertTrue(wildcardOnly.getCardNamesLowerCase().isEmpty());
		assertFalse(literalQuestionMark.containsEntity("Rune axe"));
		assertTrue(literalQuestionMark.getCardNamesLowerCase().contains("rune ?xe"));
	}

	@Test
	public void entityMatchesResolveNpcCardVariants()
	{
		ExemptionList.Snapshot snapshot = exemptions.resolve("Soldier");

		assertTrue(snapshot.containsEntity("Soldier"));
		assertTrue(snapshot.getCardNamesLowerCase().contains("soldier (yanille)"));
	}

	@Test
	public void wildcardMatchesBecomeEffectiveCardNames()
	{
		ExemptionList.Snapshot snapshot = exemptions.resolve("Rune*");

		assertTrue(snapshot.getCardNamesLowerCase().contains("rune axe"));
		assertTrue(snapshot.getCardNamesLowerCase().contains("rune platebody"));
	}

	@Test
	public void snapshotIsStableUntilRawConfigChanges()
	{
		ExemptionList.Snapshot first = exemptions.resolve("Rune*");

		assertSame(first, exemptions.resolve("Rune*"));
		assertNotSame(first, exemptions.resolve("Dragon*"));
		assertFalse(exemptions.resolve("Dragon*").containsEntity("Rune axe"));
	}

	@Test
	public void sameWildcardRebuildsWhenRemoteCatalogueActivates()
	{
		Gson gson = new Gson();
		BundledCardIdentityCatalog bundled = new BundledCardIdentityCatalog(gson);
		ActiveCardIdentityCatalog active = new ActiveCardIdentityCatalog(bundled);
		ExemptionList revisionAware = new ExemptionList(active);
		ExemptionList.Snapshot bundledSnapshot = revisionAware.resolve("Remote*");
		assertFalse(bundledSnapshot.containsEntity("Remote widget"));

		CardIdentity identity = new CardIdentity(
			CardEntityKind.ITEM, "Remote parent", Set.of(9000));
		ImmutableCardIdentityCatalog remote = new ImmutableCardIdentityCatalog(List.of(
			new ImmutableCardIdentityCatalog.Entry(identity, List.of("Remote widget"))));
		active.activate(remote, remote.getEntries(), "version-1");
		ExemptionList.Snapshot remoteSnapshot = revisionAware.resolve("Remote*");

		assertNotSame(bundledSnapshot, remoteSnapshot);
		assertTrue(remoteSnapshot.containsEntity("Remote widget"));
		assertTrue(remoteSnapshot.getCardNamesLowerCase().contains("remote parent"));
	}
}
