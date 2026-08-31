package com.bronzemantcg;

import com.bronzemantcg.catalog.remote.CatalogValidationException;
import com.bronzemantcg.catalog.remote.OsrsTcgCatalogParser;
import com.bronzemantcg.catalog.remote.OsrsTcgCatalogSnapshot;
import com.bronzemantcg.catalog.remote.RemoteCatalogValidator;
import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.BundledCardIdentityCatalog;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the maintained public v1 capture for tests which validate v1-authored policy. */
public final class LiveV1CatalogTestSupport
{
	private static final Path LIVE_CAPTURE = Path.of("scripts", "catalog", "v1", "source",
		"osrs-tcg-live-catalog-2026-08-27.json");

	private LiveV1CatalogTestSupport()
	{
	}

	public static OsrsTcgCatalogSnapshot load()
	{
		try (Reader reader = Files.newBufferedReader(LIVE_CAPTURE, StandardCharsets.UTF_8))
		{
			OsrsTcgCatalogSnapshot snapshot = new OsrsTcgCatalogParser(new Gson()).parse(reader);
			new RemoteCatalogValidator().validate(snapshot);
			return snapshot.withLegacyAliases(
				new BundledCardIdentityCatalog(new Gson()).getEntries());
		}
		catch (IOException | CatalogValidationException ex)
		{
			throw new AssertionError("Could not load maintained v1 catalogue capture", ex);
		}
	}

	public static void activate(ActiveCardIdentityCatalog active)
	{
		OsrsTcgCatalogSnapshot snapshot = load();
		active.activate(snapshot, snapshot.getEntries(), "maintained-test-capture");
	}
}
