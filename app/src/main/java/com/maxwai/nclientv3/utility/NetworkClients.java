package com.maxwai.nclientv3.utility;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/** API and full-size media need different, finite network budgets. */
public final class NetworkClients {
    private NetworkClients() { }

    public static OkHttpClient.Builder apiBuilder() {
        return new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS);
    }

    public static OkHttpClient images(Supplier<String> siteHost, String userAgent) {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(6);
        dispatcher.setMaxRequestsPerHost(3);
        return new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                Request request = chain.request();
                // No API authorization or login cookies are attached to media requests.
                String host = siteHost.get();
                if (host != null && (request.url().host().equals(host)
                    || request.url().host().endsWith("." + host))) {
                    request = request.newBuilder()
                        .header("Referer", "https://" + host + "/")
                        .header("User-Agent", userAgent).build();
                }
                return chain.proceed(request);
            }).build();
    }
}
