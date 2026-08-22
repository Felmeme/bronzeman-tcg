package com.bronzemantcg.collection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Stable, beta-only identity order used by the compact migration snapshot. */
@Slf4j
@Singleton
public final class BetaCollectionIdentityCatalog
{
	private static final String RESOURCE = "/beta_collection_identities.txt";

	private final List<String> names;
	private final Set<String> nameSet;
	private final String fingerprint;

	@Inject
	public BetaCollectionIdentityCatalog()
	{
		this(loadResource());
	}

	BetaCollectionIdentityCatalog(List<String> sourceNames)
	{
		List<String> normalized = new ArrayList<>();
		if (sourceNames != null)
		{
			for (String name : sourceNames)
			{
				if (name != null && !name.trim().isEmpty())
				{
					normalized.add(name.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		Collections.sort(normalized);
		Set<String> unique = new LinkedHashSet<>(normalized);
		names = Collections.unmodifiableList(new ArrayList<>(unique));
		nameSet = Collections.unmodifiableSet(unique);
		fingerprint = fingerprint(names);
	}

	public List<String> getNames()
	{
		return names;
	}

	public Set<String> getNameSet()
	{
		return nameSet;
	}

	public String getFingerprint()
	{
		return fingerprint;
	}

	private static List<String> loadResource()
	{
		InputStream stream = BetaCollectionIdentityCatalog.class.getResourceAsStream(RESOURCE);
		if (stream == null)
		{
			log.warn("{} is missing; beta collection snapshots will remain unavailable", RESOURCE);
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(stream, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (!line.trim().isEmpty())
				{
					result.add(line);
				}
			}
		}
		catch (IOException ex)
		{
			log.warn("Could not load {}", RESOURCE, ex);
			return Collections.emptyList();
		}
		return result;
	}

	private static String fingerprint(List<String> values)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String value : values)
			{
				digest.update(value.getBytes(StandardCharsets.UTF_8));
				digest.update((byte) '\n');
			}
			StringBuilder result = new StringBuilder("sha256:");
			for (byte value : digest.digest())
			{
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}
}
