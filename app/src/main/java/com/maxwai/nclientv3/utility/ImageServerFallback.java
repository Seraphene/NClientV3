package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Fail over public gallery GETs among known media hosts, within the call deadline. */
public final class ImageServerFallback implements Interceptor {
    // Same media host family used by NhasixApp's nhentai image URL builder.
    private static final List<String> HOSTS = Arrays.asList("i.nhentai.net", "i2.nhentai.net",
        "i3.nhentai.net", "i5.nhentai.net", "i7.nhentai.net");

    @Override public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        HttpUrl url = original.url();
        if (!original.method().equals("GET") || !url.isHttps()
            || !url.encodedPath().startsWith("/galleries/")
            || (!HOSTS.contains(url.host()) && !url.host().equals("i1.nhentai.net"))) {
            return chain.proceed(original);
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(url.host());
        for (String host : HOSTS) if (!host.equals(url.host())) candidates.add(host);
        Chain attempt = chain.withConnectTimeout(5, TimeUnit.SECONDS);
        for (int index = 0; index < candidates.size(); index++) {
            if (chain.call().isCanceled()) throw new IOException("Canceled");
            String host = candidates.get(index);
            Request.Builder request = original.newBuilder().url(url.newBuilder().host(host).build());
            if (!host.equals(url.host())) request.removeHeader("Authorization").removeHeader("Cookie");
            try {
                Response response = attempt.proceed(request.build());
                int status = response.code();
                boolean retry = status == 404 || status == 502 || status == 503 || status == 504;
                if (!retry || index == candidates.size() - 1) return response;
                response.close();
            } catch (IOException error) {
                if (chain.call().isCanceled() || index == candidates.size() - 1) throw error;
            }
        }
        throw new IOException("No image server responded");
    }
}
