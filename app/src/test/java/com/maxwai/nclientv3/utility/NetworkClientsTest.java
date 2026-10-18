package com.maxwai.nclientv3.utility;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import static org.junit.Assert.*;

public class NetworkClientsTest {
    /** A real loopback HTTP exchange; no external website or mock Android APIs. */
    private static final class SlowServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        final AtomicReference<String> headers = new AtomicReference<>("");
        final Thread thread;
        SlowServer() throws Exception { this(250); }
        SlowServer(long delayMillis) throws Exception {
            thread = new Thread(() -> {
                try (Socket connection = socket.accept()) {
                    connection.setSoTimeout(3000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                    StringBuilder request = new StringBuilder();
                    String line;
                    while ((line = input.readLine()) != null && !line.isEmpty()) request.append(line).append('\n');
                    headers.set(request.toString());
                    Thread.sleep(delayMillis);
                    OutputStream output = connection.getOutputStream();
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nimage".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                } catch (Exception ignored) {
                    // A short-timeout control client intentionally closes its connection.
                }
            }, "slow-image-fixture");
            thread.setDaemon(true);
            thread.start();
        }
        String url() { return "http://127.0.0.1:" + socket.getLocalPort() + "/page.webp"; }
        @Override public void close() throws Exception { socket.close(); thread.interrupt(); thread.join(4000); }
    }

    @Test public void delayedMediaResponseCompletesAndUsesSiteHeaders() throws Exception {
        try (SlowServer server = new SlowServer()) {
            OkHttpClient client = NetworkClients.images(() -> "127.0.0.1", "NClient/test");
            try (Response response = client.newCall(new Request.Builder().url(server.url()).build()).execute()) {
                assertEquals(200, response.code());
                assertEquals("image", response.body().string());
            }
            assertTrue(server.headers.get().contains("Referer: https://127.0.0.1/"));
            assertTrue(server.headers.get().contains("User-Agent: NClient/test"));
            assertFalse(server.headers.get().contains("Authorization:"));
            assertFalse(server.headers.get().contains("Cookie:"));
        }
    }

    @Test public void mediaCanCompleteBeyondTheLegacyTenSecondReadDeadline() throws Exception {
        try (SlowServer server = new SlowServer(11_000)) {
            OkHttpClient client = NetworkClients.images(() -> "127.0.0.1", "NClient/test");
            try (Response response = client.newCall(new Request.Builder().url(server.url()).build()).execute()) {
                assertEquals("image", response.body().string());
            }
        }
    }

    @Test public void unrelatedImageHostDoesNotReceiveSiteHeaders() throws Exception {
        try (SlowServer server = new SlowServer()) {
            OkHttpClient client = NetworkClients.images(() -> "nhentai.net", "NClient/test");
            try (Response response = client.newCall(new Request.Builder().url(server.url()).build()).execute()) {
                assertEquals("image", response.body().string());
            }
            assertFalse(server.headers.get().contains("Referer:"));
            assertFalse(server.headers.get().contains("NClient/test"));
        }
    }

    @Test public void shortReadDeadlineReallyTimesOutOnSlowMedia() throws Exception {
        try (SlowServer server = new SlowServer()) {
            OkHttpClient client = NetworkClients.images(() -> "127.0.0.1", "NClient/test").newBuilder()
                .readTimeout(50, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build();
            try (Response response = client.newCall(new Request.Builder().url(server.url()).build()).execute()) {
                response.body().string();
                fail("The delayed response must exceed the control client's read deadline");
            } catch (java.net.SocketTimeoutException expected) { }
        }
    }

    @Test public void apiAndMediaRequestsHaveFiniteDifferentBudgets() {
        OkHttpClient api = NetworkClients.apiBuilder().build();
        OkHttpClient media = NetworkClients.images(() -> "nhentai.net", "NClient/test");
        assertTrue(media.readTimeoutMillis() > api.readTimeoutMillis());
        assertTrue(media.callTimeoutMillis() > media.readTimeoutMillis());
        assertTrue(api.callTimeoutMillis() > api.readTimeoutMillis());
        assertTrue(media.dispatcher().getMaxRequestsPerHost() <= 3);
    }
}
