package com.bronzemantcg.interop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
 * publicly-known transform. Legacy v2 uses gzip -> XOR -> base64, while v3
 * uses gzip -> base64. There is nothing secret being reversed here; these
 * are osrs-tcg's own persisted formats.
 */
@Slf4j
public final class TcgStateDecoder
{
	static final String STORAGE_PREFIX_V2 = "RLTCG_v2:";
	static final String STORAGE_PREFIX_V3 = "RLTCG_v3:";
	private static final int MAX_STORED_CHARS = 16 * 1024 * 1024;
	private static final int MAX_DECODED_BYTES = 16 * 1024 * 1024;

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
		if (s.length() > MAX_STORED_CHARS)
		{
			return "";
		}

		try
		{
			byte[] compressed;
			if (s.startsWith(STORAGE_PREFIX_V3))
			{
				compressed = Base64.getDecoder().decode(s.substring(STORAGE_PREFIX_V3.length()));
			}
			else if (s.startsWith(STORAGE_PREFIX_V2))
			{
				compressed = Base64.getDecoder().decode(s.substring(STORAGE_PREFIX_V2.length()));
				xorWithSalt(compressed);
			}
			else
			{
				return "";
			}
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
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = gzis.read(buffer)) >= 0)
			{
				total += read;
				if (total > MAX_DECODED_BYTES)
				{
					throw new IOException("decoded osrs-tcg state exceeds size limit");
				}
				output.write(buffer, 0, read);
			}
			return output.toString(StandardCharsets.UTF_8);
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
