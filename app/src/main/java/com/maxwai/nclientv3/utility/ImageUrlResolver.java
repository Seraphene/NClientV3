package com.maxwai.nclientv3.utility;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Retains API paths until the media transport can resolve them using /api/v2/cdn. */
public final class ImageUrlResolver {
    public static final String INTERNAL_HOST = "media.nclient.invalid";
    private ImageUrlResolver() { }

    public static String resolve(String path, boolean thumbnail) {
        if (path == null || path.trim().isEmpty()) throw new IllegalArgumentException("Missing image path");
        if (isManaged(path)) return path;
        if (path.startsWith("//")) path = "https:" + path;
        URI uri = URI.create(path);
        if (uri.isAbsolute()) {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null)
                throw new IllegalArgumentException("Unsupported image URL");
            return path;
        }
        if (uri.getRawFragment() != null) throw new IllegalArgumentException("Image paths cannot contain fragments");
        String rawPath = uri.getRawPath();
        for (String segment : rawPath.split("/"))
            if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("Invalid image path");
        try {
            return "https://" + INTERNAL_HOST + "/" + (thumbnail ? "thumb" : "image") + "?path="
                + URLEncoder.encode(path, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
    }

    public static boolean isManaged(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && INTERNAL_HOST.equalsIgnoreCase(uri.getHost())
                && ("/image".equals(uri.getRawPath()) || "/thumb".equals(uri.getRawPath()));
        } catch (IllegalArgumentException error) { return false; }
    }

    public static boolean isThumbnail(String value) { return "/thumb".equals(URI.create(value).getRawPath()); }

    public static String rawPath(String value) {
        if (!isManaged(value)) return value;
        String query = URI.create(value).getRawQuery();
        if (query == null || !query.startsWith("path=") || query.contains("&"))
            throw new IllegalArgumentException("Missing stored media path");
        try { return URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8.name()); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Saved full URLs under the site domain are rebound to currently advertised servers. */
    public static String restoreStored(String value, String siteHost, boolean thumbnail) {
        if (isManaged(value)) return value;
        URI uri = URI.create(value);
        if ("file".equals(uri.getScheme()) || "content".equals(uri.getScheme())) return value;
        String host = uri.getHost();
        if (host != null && (host.equalsIgnoreCase(siteHost) || host.toLowerCase(java.util.Locale.ROOT)
            .endsWith("." + siteHost.toLowerCase(java.util.Locale.ROOT)))) {
            String path = uri.getRawPath();
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            return resolve(path, thumbnail);
        }
        return resolve(value, thumbnail);
    }

    public static String concatenate(String server, String path) {
        String base = server;
        if (path.startsWith("/") && base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + (!base.endsWith("/") && !path.startsWith("/") ? "/" : "") + path;
    }

    public static String filePath(String value) {
        return URI.create(rawPath(value)).getRawPath();
    }
}
