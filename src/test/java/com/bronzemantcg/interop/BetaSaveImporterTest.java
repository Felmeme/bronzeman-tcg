package com.bronzemantcg.interop;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BetaSaveImporterTest
{
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();
	private final BetaSaveImporter importer = new BetaSaveImporter(new Gson());

	@Test
	public void legacyNamesAndUnknownCardsSurviveImport() throws Exception
	{
		assertEquals(Set.of("water rune", "fish chunks"), importer.parse(encode(
			"{\"cardInstances\":[{\"cardName\":\"Water rune\"},"
				+ "{\"cardName\":\"Fish chunks\"},{\"cardName\":\"Water rune\"}]}", true)));
	}

	@Test
	public void v2AndV3EntriesRespectBetaProvenanceAndQuantities() throws Exception
	{
		String json = "{\"schemaVersion\":6,\"cardEntries\":["
			+ "{\"cardName\":\"Water rune\",\"variants\":[{\"beta\":true,\"quantity\":5},{}]},"
			+ "{\"cardName\":\"Fish chunks\",\"variants\":[{\"beta\":true}]},"
			+ "{\"cardName\":\"v1 only\",\"variants\":[{\"beta\":false}]},"
			+ "{\"cardName\":\"No copies\",\"variants\":[{\"beta\":true,\"quantity\":0}]}]}";
		assertEquals(Set.of("water rune", "fish chunks"), importer.parse(encode(json, true)));
		assertEquals(Set.of("water rune", "fish chunks"), importer.parse(encode(json, false)));
	}

	@Test
	public void extensionlessFileIsReadWithoutModifyingIt() throws Exception
	{
		Path file = temporary.newFile("abcdef012345").toPath();
		String stored = encode("{\"cardInstances\":[{\"cardName\":\"Fish chunks\"}]}", true);
		Files.writeString(file, stored);
		assertEquals(Set.of("fish chunks"), importer.read(file));
		assertEquals(stored, Files.readString(file));
		assertThrows(IOException.class, () -> importer.read(temporary.getRoot().toPath()));
	}

	@Test
	public void explicitInstanceFlagsExcludeNewCardsEvenInsideV2() throws Exception
	{
		assertEquals(Set.of("old"), importer.parse(encode("{\"cardInstances\":["
			+ "{\"cardName\":\"Old\",\"beta\":true},{\"cardName\":\"New\",\"beta\":false}]}", true)));
	}

	@Test
	public void emptyInvalidAndAmbiguousDataCannotReplaceASnapshot() throws Exception
	{
		String[] invalid = {
			"{}", "null", "[]", "{\"cardEntries\":[]}",
			"{\"cardEntries\":[{\"cardName\":\"Only v1\",\"variants\":[{}]}]}",
			"{\"cardEntries\":[{\"cardName\":\"Broken\"}]}",
			"{\"cardInstances\":[null]}",
			"{\"cardInstances\":[{\"cardName\":55,\"beta\":true}]}",
			"{\"cardInstances\":[{\"cardName\":\"Unknown provenance\"}]}",
			"{\"cardInstances\":[{\"cardName\":\"A\",\"beta\":\"true\"}]}",
			"{\"cardInstances\":[{\"cardName\":\"A\",\"beta\":false,\"beta\":true}]}",
			"{\"cardEntries\":[{\"cardName\":\"A\",\"variants\":[{\"beta\":true,\"quantity\":1.5}]}]}",
			"{\"cardInstances\":[{\"cardName\":\"A\",\"beta\":true}]} {}",
			"{'cardInstances': [{'cardName': 'A', 'beta': true}]}"
		};
		for (String json : invalid)
		{
			String stored = encode(json, false);
			assertThrows(json, IOException.class, () -> importer.parse(stored));
		}
		assertThrows(IOException.class, () -> importer.parse("RLTCG_v3:broken"));
	}

	@Test
	public void rejectsExcessiveNestingAndDecompressedSize() throws Exception
	{
		String nested = encode("[".repeat(1000) + "0" + "]".repeat(1000), false);
		assertThrows(IOException.class, () -> importer.parse(nested));
		String oversized = encode("x".repeat(16 * 1024 * 1024 + 1), false);
		assertThrows(IOException.class, () -> importer.parse(oversized));
	}

	private static String encode(String json, boolean legacy) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(output))
		{
			gzip.write(json.getBytes(StandardCharsets.UTF_8));
		}
		byte[] bytes = output.toByteArray();
		if (legacy)
		{
			byte[] salt = "RLTCG|osrs-tcg!".getBytes(StandardCharsets.UTF_8);
			for (int i = 0; i < bytes.length; i++)
			{
				bytes[i] ^= salt[i % salt.length];
			}
		}
		return (legacy ? "RLTCG_v2:" : "RLTCG_v3:") + Base64.getEncoder().encodeToString(bytes);
	}
}
