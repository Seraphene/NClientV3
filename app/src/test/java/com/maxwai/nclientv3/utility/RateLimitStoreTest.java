package com.maxwai.nclientv3.utility;

import org.junit.Test;
import java.util.concurrent.atomic.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import okhttp3.*;
import static org.junit.Assert.*;

public class RateLimitStoreTest {
    @Test public void retryAfterSecondsStopsRequestsUntilDeadline() throws Exception {
        AtomicLong now = new AtomicLong(1000); AtomicInteger calls = new AtomicInteger();
        RateLimitStore store = new RateLimitStore(now::get);
        RateLimitInterceptor limiter = new RateLimitInterceptor(request -> request.url().host(), store);
        Request request = new Request.Builder().url("https://site.example/api/v2/galleries").build();
        Interceptor.Chain chain = TestChain.create(request, new OkHttpClient().newCall(request), candidate -> {
            int count = calls.incrementAndGet();
            return TestChain.response(candidate, count == 1 ? 429 : 200, "{}").newBuilder().header("Retry-After", "10").build();
        });
        try (Response response = limiter.intercept(chain)) { assertEquals(429, response.code()); }
        try (Response response = limiter.intercept(chain)) { assertEquals(429, response.code()); }
        assertEquals(1, calls.get());
        now.addAndGet(10001);
        try (Response response = limiter.intercept(chain)) { assertEquals(200, response.code()); }
        assertEquals(2, calls.get());
    }
    @Test public void datesDefaultsAndScopesRespectTheLongestBackoff() {
        AtomicLong now = new AtomicLong(Instant.parse("2026-10-18T00:00:00Z").toEpochMilli());
        RateLimitStore store = new RateLimitStore(now::get);
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now.get() + 60000).atZone(ZoneOffset.UTC));
        store.record("site", date);
        assertEquals(60, store.remainingSeconds("site"));
        store.record("site", "5"); assertEquals(60, store.remainingSeconds("site"));
        assertEquals(0, store.remainingSeconds("other"));
        store.record("other", "invalid"); assertEquals(30, store.remainingSeconds("other"));
        now.addAndGet(60001); assertEquals(0, store.remainingSeconds("site"));
    }
}
