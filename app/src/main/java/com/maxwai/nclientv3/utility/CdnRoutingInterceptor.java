package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Only server bases returned by /cdn are tried; API paths are kept unchanged. */
public final class CdnRoutingInterceptor implements Interceptor {
    private final CdnRepository repository;
    private final Supplier<String> siteHost;
    private final String userAgent;
    public CdnRoutingInterceptor(CdnRepository repository, Supplier<String> siteHost, String userAgent) {
        this.repository = repository; this.siteHost = siteHost; this.userAgent = userAgent;
    }
    @Override public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        if (!ImageUrlResolver.isManaged(original.url().toString())) return chain.proceed(original);
        List<String> urls = repository.urlsFor(original.url().toString());
        Chain attempt = chain.withConnectTimeout(5, TimeUnit.SECONDS);
        for (int index = 0; index < urls.size(); index++) {
            if (chain.call().isCanceled()) throw new IOException("Canceled");
            long backoff = RateLimitStore.SHARED.remainingSeconds(siteHost.get());
            if (backoff > 0) return RateLimitInterceptor.paused(original, backoff);
            Request request = original.newBuilder().url(urls.get(index))
                .removeHeader("Authorization").removeHeader("Cookie")
                .header("User-Agent", userAgent).header("Referer", "https://" + siteHost.get() + "/").build();
            try {
                Response response = attempt.proceed(request);
                int status = response.code();
                if (response.isSuccessful()) repository.succeeded(original.url().toString(), urls.get(index));
                // Do not repeatedly send an API path the CDN says is invalid, or evade a ban.
                boolean retry = status == 502 || status == 503 || status == 504;
                if (!retry || index == urls.size() - 1) {
                    if (retry) repository.invalidate();
                    return response;
                }
                response.close();
            } catch (IOException error) {
                if (chain.call().isCanceled() || index == urls.size() - 1) { repository.invalidate(); throw error; }
            }
        }
        throw new IOException("No advertised image server responded");
    }
}
