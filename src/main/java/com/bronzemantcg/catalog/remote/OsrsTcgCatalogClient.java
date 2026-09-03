package com.bronzemantcg.catalog.remote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Downloads the public OSRS TCG catalogue using RuneLite's shared HTTP client and cache. */
@Singleton
public class OsrsTcgCatalogClient
{
	static final String LIVE_CATALOG_URL = "https://api.osrs-tcg.net/api/v1/catalog/cards/live";
	static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

	private final OkHttpClient httpClient;
	private final String endpoint;
	private final int maximumResponseBytes;

	@Inject
	public OsrsTcgCatalogClient(OkHttpClient httpClient)
	{
		this(httpClient, LIVE_CATALOG_URL, MAX_RESPONSE_BYTES);
	}

	OsrsTcgCatalogClient(OkHttpClient httpClient, String endpoint, int maximumResponseBytes)
	{
		if (httpClient == null)
		{
			throw new IllegalArgumentException("httpClient is required");
		}
		if (endpoint == null || endpoint.trim().isEmpty())
		{
			throw new IllegalArgumentException("endpoint is required");
		}
		if (maximumResponseBytes <= 0)
		{
			throw new IllegalArgumentException("maximumResponseBytes must be positive");
		}
		this.httpClient = httpClient;
		this.endpoint = endpoint;
		this.maximumResponseBytes = maximumResponseBytes;
	}

	public FetchHandle fetch(Listener listener)
	{
		if (listener == null)
		{
			throw new IllegalArgumentException("listener is required");
		}
		FetchHandle handle = new FetchHandle();
		enqueue(handle, listener, false, null);
		return handle;
	}

	private void enqueue(FetchHandle handle, Listener listener, boolean cacheOnly,
		String precedingFailure)
	{
		if (handle.isCancelled())
		{
			return;
		}
		Request.Builder builder = new Request.Builder().url(endpoint).get();
		if (cacheOnly)
		{
			builder.cacheControl(CacheControl.FORCE_CACHE);
		}
		Call call = httpClient.newCall(builder.build());
		handle.setActiveCall(call);
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call failedCall, @Nonnull IOException exception)
			{
				if (handle.isCancelled())
				{
					return;
				}
				String failure = failureMessage(precedingFailure, exception.getMessage());
				if (!cacheOnly)
				{
					enqueue(handle, listener, true, failure);
					return;
				}
				listener.onFailure(failure, exception);
			}

			@Override
			public void onResponse(@Nonnull Call completedCall, @Nonnull Response response)
			{
				try (Response closeableResponse = response)
				{
					if (handle.isCancelled())
					{
						return;
					}
					if (!response.isSuccessful())
					{
						handleResponseFailure(handle, listener, cacheOnly, precedingFailure,
							"HTTP " + response.code());
						return;
					}

					ResponseBody body = response.body();
					if (body == null)
					{
						handleResponseFailure(handle, listener, cacheOnly, precedingFailure,
							"catalogue response has no body");
						return;
					}
					byte[] bytes;
					try
					{
						bytes = readBounded(body);
					}
					catch (IOException exception)
					{
						handleResponseFailure(handle, listener, cacheOnly, precedingFailure,
							exception.getMessage());
						return;
					}
					if (!handle.isCancelled())
					{
						listener.onSuccess(new CatalogResponse(bytes,
							catalogVersion(response), cacheOnly || response.cacheResponse() != null));
					}
				}
			}
		});
	}

	private void handleResponseFailure(FetchHandle handle, Listener listener,
		boolean cacheOnly, String precedingFailure, String currentFailure)
	{
		if (handle.isCancelled())
		{
			return;
		}
		String failure = failureMessage(precedingFailure, currentFailure);
		if (!cacheOnly)
		{
			enqueue(handle, listener, true, failure);
			return;
		}
		listener.onFailure(failure, null);
	}

	private byte[] readBounded(ResponseBody body) throws IOException
	{
		long contentLength = body.contentLength();
		if (contentLength > maximumResponseBytes)
		{
			throw new IOException("catalogue response exceeds " + maximumResponseBytes + " bytes");
		}
		try (InputStream input = body.byteStream();
			ByteArrayOutputStream output = new ByteArrayOutputStream(
				contentLength > 0 ? (int) contentLength : 8192))
		{
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = input.read(buffer)) != -1)
			{
				total += read;
				if (total > maximumResponseBytes)
				{
					throw new IOException("catalogue response exceeds "
						+ maximumResponseBytes + " bytes");
				}
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		}
	}

	private static String catalogVersion(Response response)
	{
		String version = response.header("X-Catalog-Version");
		if (version == null || version.trim().isEmpty())
		{
			version = response.header("ETag");
		}
		return version == null ? "unknown" : version.trim();
	}

	private static String failureMessage(String precedingFailure, String currentFailure)
	{
		String current = currentFailure == null || currentFailure.trim().isEmpty()
			? "catalogue request failed" : currentFailure.trim();
		return precedingFailure == null || precedingFailure.isEmpty()
			? current : precedingFailure + "; cache fallback: " + current;
	}

	public interface Listener
	{
		void onSuccess(CatalogResponse response);

		void onFailure(String reason, Throwable cause);
	}

	public static final class FetchHandle
	{
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final AtomicReference<Call> activeCall = new AtomicReference<>();

		public void cancel()
		{
			cancelled.set(true);
			Call call = activeCall.getAndSet(null);
			if (call != null)
			{
				call.cancel();
			}
		}

		public boolean isCancelled()
		{
			return cancelled.get();
		}

		private void setActiveCall(Call call)
		{
			Call previous = activeCall.getAndSet(call);
			if (previous != null && previous != call)
			{
				previous.cancel();
			}
			if (cancelled.get() && activeCall.compareAndSet(call, null))
			{
				call.cancel();
			}
		}
	}

	public static final class CatalogResponse
	{
		private final byte[] body;
		private final String version;
		private final boolean servedFromCache;

		CatalogResponse(byte[] body, String version, boolean servedFromCache)
		{
			this.body = body.clone();
			this.version = version;
			this.servedFromCache = servedFromCache;
		}

		public byte[] getBody()
		{
			return body.clone();
		}

		public String getVersion()
		{
			return version;
		}

		public boolean isServedFromCache()
		{
			return servedFromCache;
		}
	}
}
