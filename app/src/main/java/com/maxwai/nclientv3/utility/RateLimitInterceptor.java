package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.util.function.Function;
import okhttp3.*;

public final class RateLimitInterceptor implements Interceptor {
    private final Function<Request, String> scope;
    private final RateLimitStore store;
    public RateLimitInterceptor(Function<Request, String> scope) { this(scope, RateLimitStore.SHARED); }
    public RateLimitInterceptor(Function<Request, String> scope, RateLimitStore store) { this.scope = scope; this.store = store; }
    public static Response paused(Request request, long seconds) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(429).message("Retry later").header("Retry-After", Long.toString(seconds))
            .body(ResponseBody.create("{\"error\":\"rate_limited\"}", MediaType.get("application/json"))).build();
    }
    @Override public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String key = scope.apply(request);
        long remaining = store.remainingSeconds(key);
        if (remaining > 0) return paused(request, remaining);
        Response response = chain.proceed(request);
        if (response.code() == 429) store.record(key, response.header("Retry-After"));
        return response;
    }
}
