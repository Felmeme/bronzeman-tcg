package com.bronzemantcg.interop;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BetaCardLookupClientTest
{
	private static final String ENDPOINT = "https://example.test/api/v1/players";

	@Test
	public void usesCurrentPlayerEndpoint()
	{
		assertEquals("https://osrs-tcg.net/api/v1/players",
			BetaCardLookupClient.PLAYERS_URL);
	}

	@Test
	public void encodesPlayerAsOnePathSegmentAndBypassesHttpCache() throws Exception
	{
		byte[] body = "{\"displayName\":\"A/B Name\",\"revision\":1,\"cardNames\":[]}"
			.getBytes(StandardCharsets.UTF_8);
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			assertEquals("/api/v1/players/A%2FB%20Name/beta-names",
				chain.request().url().encodedPath());
			assertTrue(chain.request().cacheControl().noCache());
			assertTrue(chain.request().cacheControl().noStore());
			return response(chain.request(), 200, body);
		}).build();

		BetaCardLookupClient.LookupResponse result = fetch(
			new BetaCardLookupClient(http, ENDPOINT, 1024), "A/B Name");

		assertEquals(200, result.getStatusCode());
		assertArrayEquals(body, result.getBody());
	}

	@Test
	public void doublesLiteralUnderscoresForPlayerApiRouting() throws Exception
	{
		byte[] body = "{\"displayName\":\"TCG_Bruh\",\"revision\":1,\"cardNames\":[]}"
			.getBytes(StandardCharsets.UTF_8);
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			assertEquals("/api/v1/players/TCG__Bruh/beta-names",
				chain.request().url().encodedPath());
			return response(chain.request(), 200, body);
		}).build();

		BetaCardLookupClient.LookupResponse result = fetch(
			new BetaCardLookupClient(http, ENDPOINT, 1024), "TCG_Bruh");

		assertEquals(200, result.getStatusCode());
		assertArrayEquals(body, result.getBody());
	}

	@Test
	public void rejectsResponsesAboveTheConfiguredLimit()
	{
		BetaCardLookupClient client = new BetaCardLookupClient(
			clientReturning(200, "12345".getBytes(StandardCharsets.UTF_8)), ENDPOINT, 4);

		ExecutionException exception = assertThrows(ExecutionException.class,
			() -> fetch(client, "Player"));
		assertTrue(exception.getCause().getMessage().contains("size limit"));
	}

	private static BetaCardLookupClient.LookupResponse fetch(BetaCardLookupClient client,
		String name) throws InterruptedException, ExecutionException, TimeoutException
	{
		CompletableFuture<BetaCardLookupClient.LookupResponse> future = new CompletableFuture<>();
		client.fetch(name, new BetaCardLookupClient.Listener()
		{
			@Override
			public void onResponse(BetaCardLookupClient.LookupResponse response)
			{
				future.complete(response);
			}

			@Override
			public void onFailure(Throwable cause)
			{
				future.completeExceptionally(cause);
			}
		});
		return future.get(2, TimeUnit.SECONDS);
	}

	private static OkHttpClient clientReturning(int code, byte[] body)
	{
		return new OkHttpClient.Builder()
			.addInterceptor(chain -> response(chain.request(), code, body)).build();
	}

	private static Response response(okhttp3.Request request, int code, byte[] body)
	{
		return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
			.code(code).message(code == 200 ? "OK" : "Error")
			.body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
	}
}
