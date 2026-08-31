package com.bronzemantcg.catalog.remote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class OsrsTcgCatalogClientTest
{
	private static final String ENDPOINT = "https://example.test/catalog";

	@Test
	public void downloadsBodyAndCapturesVersionAsynchronously() throws Exception
	{
		String json = "{\"items\":[],\"npcs\":[]}";
		OkHttpClient httpClient = clientReturning(200, json, "version-7");
		OsrsTcgCatalogClient client = new OsrsTcgCatalogClient(httpClient, ENDPOINT, 1024);

		OsrsTcgCatalogClient.CatalogResponse response = fetch(client);

		assertArrayEquals(json.getBytes(StandardCharsets.UTF_8), response.getBody());
		assertEquals("version-7", response.getVersion());
		assertFalse(response.isServedFromCache());
	}

	@Test
	public void fallsBackToRuneliteCacheAfterNetworkFailure() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		OkHttpClient httpClient = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				requests.incrementAndGet();
				if (!chain.request().cacheControl().onlyIfCached())
				{
					throw new IOException("offline");
				}
				return response(chain.request(), 200, "cached", null);
			})
			.build();
		OsrsTcgCatalogClient client = new OsrsTcgCatalogClient(httpClient, ENDPOINT, 1024);

		OsrsTcgCatalogClient.CatalogResponse response = fetch(client);

		assertEquals(2, requests.get());
		assertTrue(response.isServedFromCache());
		assertArrayEquals("cached".getBytes(StandardCharsets.UTF_8), response.getBody());
	}

	@Test
	public void reportsFailureWhenNetworkAndCacheAreUnavailable()
	{
		OkHttpClient httpClient = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				if (chain.request().cacheControl().onlyIfCached())
				{
					return response(chain.request(), 504, "", null);
				}
				throw new IOException("offline");
			})
			.build();
		OsrsTcgCatalogClient client = new OsrsTcgCatalogClient(httpClient, ENDPOINT, 1024);

		ExecutionException exception = assertThrows(ExecutionException.class,
			() -> fetch(client));
		assertTrue(exception.getCause().getMessage().contains("offline"));
		assertTrue(exception.getCause().getMessage().contains("HTTP 504"));
	}

	@Test
	public void rejectsResponsesAboveTheConfiguredLimit()
	{
		OkHttpClient httpClient = clientReturning(200, "12345", null);
		OsrsTcgCatalogClient client = new OsrsTcgCatalogClient(httpClient, ENDPOINT, 4);

		ExecutionException exception = assertThrows(ExecutionException.class,
			() -> fetch(client));
		assertTrue(exception.getCause().getMessage().contains("exceeds 4 bytes"));
	}

	@Test
	public void cancellationSuppressesLateCallbacks() throws Exception
	{
		CompletableFuture<Void> entered = new CompletableFuture<>();
		CompletableFuture<Void> release = new CompletableFuture<>();
		OkHttpClient httpClient = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				entered.complete(null);
				try
				{
					release.get(2, TimeUnit.SECONDS);
				}
				catch (InterruptedException exception)
				{
					Thread.currentThread().interrupt();
					throw new IOException("interrupted", exception);
				}
				catch (ExecutionException | TimeoutException exception)
				{
					throw new IOException("test gate failed", exception);
				}
				return response(chain.request(), 200, "late", null);
			})
			.build();
		OsrsTcgCatalogClient client = new OsrsTcgCatalogClient(httpClient, ENDPOINT, 1024);
		CompletableFuture<OsrsTcgCatalogClient.CatalogResponse> result = new CompletableFuture<>();
		OsrsTcgCatalogClient.FetchHandle handle = client.fetch(listener(result));
		entered.get(2, TimeUnit.SECONDS);

		handle.cancel();
		release.complete(null);

		assertTrue(handle.isCancelled());
		assertThrows(TimeoutException.class, () -> result.get(250, TimeUnit.MILLISECONDS));
	}

	private static OsrsTcgCatalogClient.CatalogResponse fetch(OsrsTcgCatalogClient client)
		throws InterruptedException, ExecutionException, TimeoutException
	{
		CompletableFuture<OsrsTcgCatalogClient.CatalogResponse> future = new CompletableFuture<>();
		client.fetch(listener(future));
		return future.get(2, TimeUnit.SECONDS);
	}

	private static OsrsTcgCatalogClient.Listener listener(
		CompletableFuture<OsrsTcgCatalogClient.CatalogResponse> future)
	{
		return new OsrsTcgCatalogClient.Listener()
		{
			@Override
			public void onSuccess(OsrsTcgCatalogClient.CatalogResponse response)
			{
				future.complete(response);
			}

			@Override
			public void onFailure(String reason, Throwable cause)
			{
				future.completeExceptionally(new IOException(reason, cause));
			}
		};
	}

	private static OkHttpClient clientReturning(int code, String body, String version)
	{
		return new OkHttpClient.Builder()
			.addInterceptor(chain -> response(chain.request(), code, body, version))
			.build();
	}

	private static Response response(okhttp3.Request request, int code,
		String body, String version)
	{
		Response.Builder builder = new Response.Builder()
			.request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(code)
			.message(code == 200 ? "OK" : "Unavailable")
			.body(ResponseBody.create(MediaType.parse("application/json"), body));
		if (version != null)
		{
			builder.header("X-Catalog-Version", version);
		}
		return builder.build();
	}
}
