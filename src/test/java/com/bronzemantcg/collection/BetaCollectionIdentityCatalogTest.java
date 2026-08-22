package com.bronzemantcg.collection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BetaCollectionIdentityCatalogTest
{
	private static final String EXPECTED_FINGERPRINT =
		"sha256:9479946ff2180c7c6c78001121622ed0d67c19c0e3833dab930afc5ad305c5af";

	@Test
	public void productionCatalogIsStableSortedAndUnique()
	{
		BetaCollectionIdentityCatalog catalog = new BetaCollectionIdentityCatalog();
		List<String> names = catalog.getNames();
		List<String> sorted = new ArrayList<>(names);
		sorted.sort(String::compareTo);

		assertEquals(6361, names.size());
		assertEquals(names.size(), new HashSet<>(names).size());
		assertEquals(sorted, names);
		assertTrue(names.stream().allMatch(name -> name.equals(name.toLowerCase(Locale.ROOT))));
		assertEquals(EXPECTED_FINGERPRINT, catalog.getFingerprint());
	}

	@Test
	public void productionCatalogRetainsRepresentativeBetaVariants()
	{
		BetaCollectionIdentityCatalog catalog = new BetaCollectionIdentityCatalog();

		assertTrue(catalog.getNameSet().contains("water rune pack"));
		assertTrue(catalog.getNameSet().contains("strange creature"));
		assertTrue(catalog.getNameSet().contains("strange creature (shadows of custodia)"));
	}
}
