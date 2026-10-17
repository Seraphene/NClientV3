package com.maxwai.nclientv3.utility;

import java.net.URI;

/** Resolves API media paths without discarding a server-supplied CDN host. */
public final class ImageUrlResolver {
    private ImageUrlResolver() { }

    public static String resolve(String path, String host, boolean thumbnail) {
        if (path == null || path.trim().isEmpty())
            throw new IllegalArgumentException("Missing image path");
        String value = path.trim();
        if (value.startsWith("//")) value = "https:" + value;
        URI uri = URI.create(value);
        if (uri.isAbsolute()) {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null)
                throw new IllegalArgumentException("Unsupported image URL");
            return uri.toString();
        }
        String relative = value.startsWith("/") ? value.substring(1) : value;
        return "https://" + (thumbnail ? "t1." : "i1.") + host + "/" + relative;
    }
}
