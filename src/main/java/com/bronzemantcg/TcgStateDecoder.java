package com.bronzemantcg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Mirrors osrs-tcg's TcgStateStorageEncoding decode routine.
 * Reimplemented here (not imported) so this plugin has no compile-time
 * dependency on the osrs-tcg plugin. The encoding is a simple, fixed,
 * publicly-known transform (gzip -> XOR with a hardcoded salt -> base64
 * with a version prefix) - there is nothing secret being reversed here,
 * it's just osrs-tcg's own on-disk format.
 */
@Slf4j
public final class TcgStateDecoder
{
	private static final String STORAGE_PREFIX = "RLTCG_v2:";

	// Must match osrs-tcg's TcgStateStorageEncoding.XOR_SALT exactly.
	private static final byte[] XOR_SALT = {
		(byte) 0x52, (byte) 0x4c, (byte) 0x54, (byte) 0x43, (byte) 0x47,
		(byte) 0x7c, (byte) 0x6f, (byte) 0x73, (byte) 0x72, (byte) 0x73,
		(byte) 0x2d, (byte) 0x74, (byte) 0x63, (byte) 0x67, (byte) 0x21,
	};

	private TcgStateDecoder()
	{
	}

	/**
	 * @param stored the raw string from ConfigManager.getRSProfileConfiguration("osrstcg", "state")
	 * @return decoded plain JSON, or empty string if missing/corrupt/wrong format
	 */
	public static String decode(String stored)
	{
		String s = Objects.requireNonNullElse(stored, "");
		if (s.isEmpty() || !s.startsWith(STORAGE_PREFIX))
		{
			return "";
		}

		try
		{
			byte[] compressed = Base64.getDecoder().decode(s.substring(STORAGE_PREFIX.length()));
			xorWithSalt(compressed);
			return gzipDecompress(compressed);
		}
		catch (IllegalArgumentException | IOException ex)
		{
			log.debug("Failed to decode osrs-tcg state (format may have changed upstream)", ex);
			return "";
		}
	}

	private static String gzipDecompress(byte[] compressed) throws IOException
	{
		try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressed)))
		{
			return new String(gzis.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static void xorWithSalt(byte[] data)
	{
		for (int i = 0; i < data.length; i++)
		{
			data[i] ^= XOR_SALT[i % XOR_SALT.length];
		}
	}
}
