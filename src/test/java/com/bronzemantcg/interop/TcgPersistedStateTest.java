package com.bronzemantcg.interop;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TcgPersistedStateTest
{
	private static final byte[] XOR_SALT = {
		(byte) 0x52, (byte) 0x4c, (byte) 0x54, (byte) 0x43, (byte) 0x47,
		(byte) 0x7c, (byte) 0x6f, (byte) 0x73, (byte) 0x72, (byte) 0x73,
		(byte) 0x2d, (byte) 0x74, (byte) 0x63, (byte) 0x67, (byte) 0x21,
	};

	@Test
	public void decodesLegacyV2AndTreatsPreV1InstancesAsBeta() throws Exception
	{
		String json = "{\"schemaVersion\":3,\"cardInstances\":["
			+ "{\"cardName\":\"Water rune pack\",\"foil\":false},"
			+ "{\"cardName\":\"Manta ray\",\"foil\":true}]}";
		String stored = encode(json, true);

		assertEquals(json, TcgStateDecoder.decode(stored));
		TcgCollectionReader.PersistedState parsed =
			TcgCollectionReader.parsePersistedState(stored, new Gson());
		assertTrue(parsed.isCollectionPresent());
		assertEquals(Set.of("water rune pack", "manta ray"), parsed.getOwnedNames());
		assertTrue(parsed.getBetaCollection().isAvailable());
		assertEquals(Set.of("water rune pack", "manta ray"),
			parsed.getBetaCollection().getOwnedNamesLowerCase());
	}

	@Test
	public void respectsExplicitLegacyInstanceBetaMetadata() throws Exception
	{
		String stored = encode("{\"cardInstances\":["
			+ "{\"cardName\":\"Water rune\",\"beta\":true},"
			+ "{\"cardName\":\"Manta ray\",\"beta\":false}]}", true);

		TcgCollectionReader.PersistedState parsed =
			TcgCollectionReader.parsePersistedState(stored, new Gson());
		assertEquals(Set.of("water rune", "manta ray"), parsed.getOwnedNames());
		assertEquals(Set.of("water rune"),
			parsed.getBetaCollection().getOwnedNamesLowerCase());
	}

	@Test
	public void decodesV3CardEntriesAndExtractsOnlyBetaVariants() throws Exception
	{
		String json = "{\"schemaVersion\":6,\"cardEntries\":["
			+ "{\"cardName\":\"Water rune\",\"variants\":["
			+ "{\"beta\":true},{\"foil\":true}]},"
			+ "{\"cardName\":\"Manta ray\",\"variants\":[{}]},"
			+ "{\"cardName\":\"Ignored\",\"variants\":[{\"beta\":true,\"quantity\":0}]}]}";
		String stored = encode(json, false);

		assertEquals(json, TcgStateDecoder.decode(stored));
		TcgCollectionReader.PersistedState parsed =
			TcgCollectionReader.parsePersistedState(stored, new Gson());
		assertEquals(Set.of("water rune", "manta ray"), parsed.getOwnedNames());
		assertEquals(Set.of("water rune"),
			parsed.getBetaCollection().getOwnedNamesLowerCase());
	}

	@Test
	public void preservesACompleteEmptyCollectionAndRejectsBadData() throws Exception
	{
		TcgCollectionReader.PersistedState empty = TcgCollectionReader.parsePersistedState(
			encode("{\"cardEntries\":[]}", false), new Gson());
		assertTrue(empty.isCollectionPresent());
		assertTrue(empty.getOwnedNames().isEmpty());
		assertTrue(empty.getBetaCollection().isAvailable());
		assertTrue(empty.getBetaCollection().getOwnedNamesLowerCase().isEmpty());

		assertEquals("", TcgStateDecoder.decode("not-osrs-tcg-state"));
		TcgCollectionReader.PersistedState malformed =
			TcgCollectionReader.parsePersistedState("RLTCG_v3:not-base64", new Gson());
		assertFalse(malformed.isCollectionPresent());
		assertFalse(malformed.getBetaCollection().isAvailable());
	}

	@Test
	public void rejectsDecodedStateBeyondTheBoundedSize() throws Exception
	{
		String oversized = "x".repeat(16 * 1024 * 1024 + 1);
		assertEquals("", TcgStateDecoder.decode(encode(oversized, false)));
	}

	private static String encode(String json, boolean legacyV2) throws Exception
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(output))
		{
			gzip.write(json.getBytes(StandardCharsets.UTF_8));
		}
		byte[] compressed = output.toByteArray();
		if (legacyV2)
		{
			for (int i = 0; i < compressed.length; i++)
			{
				compressed[i] ^= XOR_SALT[i % XOR_SALT.length];
			}
		}
		return (legacyV2 ? TcgStateDecoder.STORAGE_PREFIX_V2
			: TcgStateDecoder.STORAGE_PREFIX_V3)
			+ Base64.getEncoder().encodeToString(compressed);
	}
}
