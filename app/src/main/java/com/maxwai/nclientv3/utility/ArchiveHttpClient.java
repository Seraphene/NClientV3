package com.maxwai.nclientv3.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import okhttp3.*;

/** Preserve the issuing session; API keys remain scoped to the official HTTPS API. */
public final class ArchiveHttpClient {
    private ArchiveHttpClient() { }

    public static OkHttpClient create(String userAgent, CookieJar siteCookies, Supplier<String> authorization) {
        return new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .retryOnConnectionFailure(false).followSslRedirects(false)
            .cookieJar(verificationCookies(siteCookies))
            .addInterceptor(new RateLimitInterceptor(request -> request.url().host()))
            .addNetworkInterceptor(chain -> {
                // Applied to every redirect hop, after OkHttp adds cookies.
                return chain.proceed(prepare(chain.request(), userAgent, authorization));
            }).build();
    }

    static Request prepare(Request request, String userAgent, Supplier<String> authorization) throws java.io.IOException {
        if (!request.url().isHttps()) throw new java.io.IOException("Archive transfer requires HTTPS");
        boolean site = SiteConnectionPolicy.isSite(request.url().toString());
        Request.Builder safe = request.newBuilder().removeHeader("Authorization").header("User-Agent", userAgent);
        if (!site) safe.removeHeader("Cookie");
        if (site && request.url().encodedPath().startsWith("/api/v2/")) {
            String key = authorization.get();
            if (key != null) safe.header("Authorization", key);
        }
        return safe.build();
    }

    static CookieJar verificationCookies(CookieJar source) {
        return new CookieJar() {
            @Override public List<Cookie> loadForRequest(HttpUrl url) {
                if (!SiteConnectionPolicy.isSite(url.toString())) return Collections.emptyList();
                return allowed(url, source.loadForRequest(url));
            }
            @Override public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                if (SiteConnectionPolicy.isSite(url.toString())) source.saveFromResponse(url, allowed(url, cookies));
            }
        };
    }

    private static List<Cookie> allowed(HttpUrl url, List<Cookie> cookies) {
        List<Cookie> result = new ArrayList<>();
        for (Cookie cookie : cookies)
            if (SiteConnectionPolicy.isVerificationCookie(cookie.name()) && cookie.matches(url)
                && cookie.expiresAt() > System.currentTimeMillis()) result.add(cookie);
        return result;
    }
}
