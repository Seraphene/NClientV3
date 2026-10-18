package com.maxwai.nclientv3.utility;

import org.junit.Test;
import java.io.IOException;
import java.util.*;
import okhttp3.*;
import static org.junit.Assert.*;

public class CdnRoutingInterceptorTest {
    private CdnRepository repository() {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> TestChain.response(chain.request(), 200, CdnRepositoryTest.CONFIG)).build();
        return new CdnRepository(client, () -> "https://site.example/api/v2/cdn", "NClient/test");
    }
    @Test public void connectionFailureUsesOnlyAdvertisedServersAndRemembersSuccess() throws Exception {
        String path = "/opaque/a%2Fb.webp?sig=xx%2By+z";
        Request request = new Request.Builder().url(ImageUrlResolver.resolve(path, false))
            .header("Authorization", "Key fixture").header("Cookie", "fixture=secret").build();
        List<Request> attempts = new ArrayList<>();
        CdnRoutingInterceptor router = new CdnRoutingInterceptor(repository(), () -> "site.example", "NClient/test");
        TestChain.Transport transport = candidate -> {
            attempts.add(candidate);
            if (candidate.url().host().equals("first.example")) throw new java.net.ConnectException("unreachable");
            return TestChain.response(candidate, 200, "image");
        };
        try (Response response = router.intercept(TestChain.create(request, new OkHttpClient().newCall(request), transport))) {
            assertEquals(200, response.code());
        }
        assertEquals(2, attempts.size());
        for (Request candidate : attempts) {
            assertEquals(path, candidate.url().encodedPath() + "?" + candidate.url().encodedQuery());
            assertNull(candidate.header("Authorization")); assertNull(candidate.header("Cookie"));
            assertEquals("https://site.example/", candidate.header("Referer"));
            assertEquals("NClient/test", candidate.header("User-Agent"));
        }
        attempts.clear();
        try (Response ignored = router.intercept(TestChain.create(request, new OkHttpClient().newCall(request), transport))) { }
        assertEquals(1, attempts.size()); assertEquals("second.example", attempts.get(0).url().host());
    }
    @Test public void invalidPathsAccessDenialsAndRateLimitsAreNotRetriedOnOtherServers() throws Exception {
        for (int code : new int[]{400, 401, 403, 404, 429}) {
            Request request = new Request.Builder().url(ImageUrlResolver.resolve("/page.webp", false)).build();
            List<Request> attempts = new ArrayList<>();
            CdnRoutingInterceptor router = new CdnRoutingInterceptor(repository(), () -> "site.example", "NClient/test");
            try (Response response = router.intercept(TestChain.create(request, new OkHttpClient().newCall(request), candidate -> {
                attempts.add(candidate); return TestChain.response(candidate, code, "error");
            }))) { assertEquals(code, response.code()); }
            assertEquals(1, attempts.size());
        }
    }
    @Test public void gatewayFailureCanUseNextAdvertisedServer() throws Exception {
        Request request = new Request.Builder().url(ImageUrlResolver.resolve("/page.webp", true)).build();
        // Thumbnail pool has exactly one server; no image server is guessed as a fallback.
        List<Request> attempts = new ArrayList<>();
        CdnRoutingInterceptor router = new CdnRoutingInterceptor(repository(), () -> "site.example", "NClient/test");
        try (Response response = router.intercept(TestChain.create(request, new OkHttpClient().newCall(request), candidate -> {
            attempts.add(candidate); return TestChain.response(candidate, 503, "error");
        }))) { assertEquals(503, response.code()); }
        assertEquals(1, attempts.size()); assertEquals("thumb.example", attempts.get(0).url().host());
        Request image = new Request.Builder().url(ImageUrlResolver.resolve("/page.webp", false)).build();
        attempts.clear();
        try (Response response = router.intercept(TestChain.create(image, new OkHttpClient().newCall(image), candidate -> {
            attempts.add(candidate); return TestChain.response(candidate, attempts.size() == 1 ? 502 : 200, "image");
        }))) { assertEquals(200, response.code()); }
        assertEquals(2, attempts.size());
    }
    @Test public void cancellationAndExhaustedServersEndTheRequest() throws Exception {
        Request request = new Request.Builder().url(ImageUrlResolver.resolve("/p.webp", false)).build();
        Call call = new OkHttpClient().newCall(request); call.cancel();
        CdnRoutingInterceptor router = new CdnRoutingInterceptor(repository(), () -> "site.example", "NClient/test");
        try { router.intercept(TestChain.create(request, call, candidate -> { fail(); return null; })); fail(); }
        catch (IOException expected) { }
        List<Request> attempts = new ArrayList<>();
        try { router.intercept(TestChain.create(request, new OkHttpClient().newCall(request), candidate -> {
            attempts.add(candidate); throw new java.net.ConnectException("unreachable");
        })); fail(); } catch (IOException expected) { }
        assertEquals(2, attempts.size());
    }
    @Test public void absoluteApiUrlPassesThroughWithoutDiscoveryOrHostRewrite() throws Exception {
        Request request = new Request.Builder().url("https://explicit.example/authoritative.webp").build();
        CdnRepository repository = new CdnRepository(new OkHttpClient.Builder().addInterceptor(chain -> { fail(); return null; }).build(),
            () -> "https://site.example/api/v2/cdn", "NClient/test");
        CdnRoutingInterceptor router = new CdnRoutingInterceptor(repository, () -> "site.example", "NClient/test");
        try (Response response = router.intercept(TestChain.create(request, new OkHttpClient().newCall(request), candidate -> {
            assertEquals(request.url(), candidate.url()); return TestChain.response(candidate, 200, "image");
        }))) { assertEquals(200, response.code()); }
    }
}
