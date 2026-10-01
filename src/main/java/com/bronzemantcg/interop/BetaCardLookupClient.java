package com.bronzemantcg.interop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** One explicit, privacy-sensitive lookup against the OSRS TCG player API. */
@Singleton
public class BetaCardLookupClient
{
	static final String PLAYERS_URL = "https://osrs-tcg.net/api/v1/players";
	static final int MAX_RESPONSE_BYTES = 1024 * 1024;
	private static final CacheControl NO_STORAGE =
		new CacheControl.Builder().noCache().noStore().build();

	private final OkHttpClient httpClient;
	private final HttpUrl playersUrl;
	private final int maximumResponseBytes;

	@Inject
	public BetaCardLookupClient(OkHttpClient httpClient)
	{
		this(httpClient, PLAYERS_URL, MAX_RESPONSE_BYTES);
	}

	protected BetaCardLookupClient(OkHttpClient httpClient, String playersUrl,
		int maximumResponseBytes)
	{
		if (httpClient == null || playersUrl == null || maximumResponseBytes <= 0)
		{
			throw new IllegalArgumentException("HTTP client, endpoint and size limit are required");
		}
		this.httpClient = httpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build();
		this.playersUrl = HttpUrl.get(playersUrl);
		this.maximumResponseBytes = maximumResponseBytes;
	}

	public FetchHandle fetch(String displayName, Listener listener)
	{
		if (displayName == null || displayName.trim().isEmpty() || listener == null)
		{
			throw new IllegalArgumentException("display name and listener are required");
		}
		HttpUrl url = playersUrl.newBuilder()
			.addPathSegment(escapePlayerPathName(displayName))
			.addPathSegment("beta-names")
			.build();
		Request request = new Request.Builder().url(url).get().cacheControl(NO_STORAGE).build();
		FetchHandle handle = new FetchHandle();
		Call call = httpClient.newCall(request);
		handle.setCall(call);
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call failedCall, @Nonnull IOException exception)
			{
				if (!handle.isCancelled())
				{
					listener.onFailure(exception);
				}
			}

			@Override
			public void onResponse(@Nonnull Call completedCall, @Nonnull Response response)
			{
				try (Response closeable = response)
				{
					if (handle.isCancelled())
					{
						return;
					}
					ResponseBody body = response.body();
					byte[] bytes = body == null ? new byte[0] : readBounded(body);
					if (!handle.isCancelled())
					{
						listener.onResponse(new LookupResponse(response.code(), bytes));
					}
				}
				catch (IOException exception)
				{
					if (!handle.isCancelled())
					{
						listener.onFailure(exception);
					}
				}
			}
		});
		return handle;
	}

	private static String escapePlayerPathName(String displayName)
	{
		return displayName.trim().replace("_", "__");
	}

	private byte[] readBounded(ResponseBody body) throws IOException
	{
		long contentLength = body.contentLength();
		if (contentLength > maximumResponseBytes)
		{
			throw new IOException("player response exceeds size limit");
		}
		try (InputStream input = body.byteStream();
			ByteArrayOutputStream output = new ByteArrayOutputStream(
				contentLength > 0 ? (int) contentLength : 4096))
		{
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = input.read(buffer)) != -1)
			{
				total += read;
				if (total > maximumResponseBytes)
				{
					throw new IOException("player response exceeds size limit");
				}
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		}
	}

	public interface Listener
	{
		void onResponse(LookupResponse response);

		void onFailure(Throwable cause);
	}

	public static final class LookupResponse
	{
		private final int statusCode;
		private final byte[] body;

		public LookupResponse(int statusCode, byte[] body)
		{
			this.statusCode = statusCode;
			this.body = body.clone();
		}

		public int getStatusCode()
		{
			return statusCode;
		}

		public byte[] getBody()
		{
			return body.clone();
		}
	}

	public static final class FetchHandle
	{
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final AtomicReference<Call> call = new AtomicReference<>();

		public void cancel()
		{
			cancelled.set(true);
			Call active = call.getAndSet(null);
			if (active != null)
			{
				active.cancel();
			}
		}

		public boolean isCancelled()
		{
			return cancelled.get();
		}

		private void setCall(Call active)
		{
			call.set(active);
			if (cancelled.get() && call.compareAndSet(active, null))
			{
				active.cancel();
			}
		}
	}
}
