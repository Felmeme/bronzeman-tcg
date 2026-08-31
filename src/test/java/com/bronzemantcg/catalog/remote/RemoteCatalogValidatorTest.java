package com.bronzemantcg.catalog.remote;

import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardIdentity;
import com.bronzemantcg.ownership.ImmutableCardIdentityCatalog;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RemoteCatalogValidatorTest
{
	@Test
	public void rejectsStructurallyValidPartialSnapshot()
	{
		RemoteCatalogValidator validator = new RemoteCatalogValidator(2, 2, Map.of());
		CatalogValidationException exception = assertThrows(CatalogValidationException.class,
			() -> validator.validate(snapshot(identity(CardEntityKind.ITEM, "Only item", 1),
				identity(CardEntityKind.NPC, "Only NPC", 2))));
		assertTrue(exception.getMessage().contains("expected at least 2"));
	}

	@Test
	public void rejectsMissingSentinelAfterCoveragePasses()
	{
		Map<CardEntityKind, Set<String>> sentinels = new EnumMap<>(CardEntityKind.class);
		sentinels.put(CardEntityKind.ITEM, Set.of("Required item"));
		RemoteCatalogValidator validator = new RemoteCatalogValidator(1, 1, sentinels);
		CatalogValidationException exception = assertThrows(CatalogValidationException.class,
			() -> validator.validate(snapshot(identity(CardEntityKind.ITEM, "Other item", 1),
				identity(CardEntityKind.NPC, "NPC", 2))));
		assertTrue(exception.getMessage().contains("Required item"));
	}

	@Test
	public void acceptsCompleteCoverageAndSentinels() throws Exception
	{
		Map<CardEntityKind, Set<String>> sentinels = new EnumMap<>(CardEntityKind.class);
		sentinels.put(CardEntityKind.ITEM, Set.of("Required item"));
		sentinels.put(CardEntityKind.NPC, Set.of("Required NPC"));
		RemoteCatalogValidator validator = new RemoteCatalogValidator(1, 1, sentinels);
		validator.validate(snapshot(identity(CardEntityKind.ITEM, "Required item", 1),
			identity(CardEntityKind.NPC, "Required NPC", 2)));
	}

	private static CardIdentity identity(CardEntityKind kind, String name, int id)
	{
		return new CardIdentity(kind, name, Set.of(id));
	}

	private static OsrsTcgCatalogSnapshot snapshot(CardIdentity... identities)
	{
		List<ImmutableCardIdentityCatalog.Entry> entries = new ArrayList<>();
		for (CardIdentity identity : identities)
		{
			entries.add(new ImmutableCardIdentityCatalog.Entry(identity,
				Set.of(identity.getCardName())));
		}
		return new OsrsTcgCatalogSnapshot(entries);
	}
}
