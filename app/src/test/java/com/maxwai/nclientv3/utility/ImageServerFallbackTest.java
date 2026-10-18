package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImageServerFallbackTest {
    private static final class FakeChain {
        final Request request;
        final Call call;
        final List<Request> attempts = new ArrayList<>();
        final Interceptor.Chain chain;
        int failures;
        int status = 200;
        FakeChain(String url) {
            request = new Request.Builder().url(url).header("Authorization", "test").header("Cookie", "test=1").build();
            call = new OkHttpClient().newCall(request);
            chain = (Interceptor.Chain) java.lang.reflect.Proxy.newProxyInstance(
                Interceptor.Chain.class.getClassLoader(), new Class<?>[]{Interceptor.Chain.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "request": return request;
                        case "call": return call;
                        case "connection": return null;
                        case "withConnectTimeout": return proxy;
                        case "proceed":
                            Request next = (Request) args[0];
                            attempts.add(next);
                            if (attempts.size() <= failures) throw new ConnectException("fixture");
                            return new Response.Builder().request(next).protocol(Protocol.HTTP_1_1)
                                .code(status).message("fixture").body(ResponseBody.create("image", MediaType.get("image/webp"))).build();
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        }
    }

    @Test public void unavailableHostFallsBackWithoutChangingTheGalleryPath() throws Exception {
        FakeChain chain = new FakeChain("https://i.nhentai.net/galleries/4247333/1.webp?x=a%2Fb");
        chain.failures = 3;
        try (Response response = new ImageServerFallback().intercept(chain.chain)) {
            assertEquals("i5.nhentai.net", response.request().url().host());
            assertEquals("/galleries/4247333/1.webp", response.request().url().encodedPath());
            assertEquals("x=a%2Fb", response.request().url().encodedQuery());
            assertNull(response.request().header("Authorization"));
            assertNull(response.request().header("Cookie"));
        }
    }
    @Test public void legacyHostCanFallBackToCanonicalHost() throws Exception {
        FakeChain chain = new FakeChain("https://i1.nhentai.net/galleries/4247333/1.webp");
        chain.failures = 1;
        try (Response response = new ImageServerFallback().intercept(chain.chain)) {
            assertEquals("i.nhentai.net", response.request().url().host());
        }
    }
    @Test public void forbiddenIsNotRetried() throws Exception {
        FakeChain chain = new FakeChain("https://i.nhentai.net/galleries/4247333/1.webp"); chain.status = 403;
        try (Response response = new ImageServerFallback().intercept(chain.chain)) {
            assertEquals(403, response.code()); assertEquals(1, chain.attempts.size());
        }
    }
    @Test public void authenticationFailureIsNotRetried() throws Exception {
        FakeChain chain = new FakeChain("https://i.nhentai.net/galleries/4247333/1.webp"); chain.status = 401;
        try (Response response = new ImageServerFallback().intercept(chain.chain)) {
            assertEquals(401, response.code()); assertEquals(1, chain.attempts.size());
        }
    }
    @Test public void allFailuresStopAfterTheKnownHostList() {
        FakeChain chain = new FakeChain("https://i.nhentai.net/galleries/4247333/1.webp"); chain.failures = 100;
        try { new ImageServerFallback().intercept(chain.chain); fail(); }
        catch (IOException expected) { assertEquals(5, chain.attempts.size()); }
    }
    @Test public void unrelatedHostIsNeverRewritten() {
        FakeChain chain = new FakeChain("https://example.org/galleries/4247333/1.webp"); chain.failures = 100;
        try { new ImageServerFallback().intercept(chain.chain); fail(); }
        catch (IOException expected) { assertEquals(1, chain.attempts.size()); }
    }
    @Test public void cancelledRequestDoesNotStartAnotherAttempt() {
        FakeChain chain = new FakeChain("https://i.nhentai.net/galleries/4247333/1.webp"); chain.call.cancel();
        try { new ImageServerFallback().intercept(chain.chain); fail(); }
        catch (IOException expected) { assertEquals(0, chain.attempts.size()); }
    }
}
