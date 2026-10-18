package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Field names match nhentai API 2.0.0+994d126's public CDN response. */
public final class CdnServers {
    private final List<String> images, thumbnails;
    private CdnServers(List<String> images, List<String> thumbnails) {
        this.images = images; this.thumbnails = thumbnails;
    }
    public static CdnServers parse(String json) throws IOException {
        try {
            JSONObject object = new JSONObject(json);
            return new CdnServers(read(object.getJSONArray("image_servers")), read(object.getJSONArray("thumb_servers")));
        } catch (JSONException | IllegalArgumentException error) {
            throw new IOException("Invalid image-server configuration", error);
        }
    }
    private static List<String> read(JSONArray array) throws JSONException, IOException {
        LinkedHashSet<String> servers = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.getString(i);
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw new IOException("Invalid HTTPS image server");
            servers.add(value);
        }
        if (servers.isEmpty()) throw new IOException("No image servers are available");
        return Collections.unmodifiableList(new ArrayList<>(servers));
    }
    public String baseFor(String managedUrl, String resolvedUrl) {
        for (String base : ImageUrlResolver.isThumbnail(managedUrl) ? thumbnails : images)
            if (ImageUrlResolver.concatenate(base, ImageUrlResolver.rawPath(managedUrl)).equals(resolvedUrl)) return base;
        return null;
    }
    public List<String> resolve(String managedUrl) { return resolve(managedUrl, null); }
    public List<String> resolve(String managedUrl, String preferredBase) {
        String path = ImageUrlResolver.rawPath(managedUrl);
        List<String> result = new ArrayList<>();
        List<String> bases = ImageUrlResolver.isThumbnail(managedUrl) ? thumbnails : images;
        if (bases.contains(preferredBase)) result.add(ImageUrlResolver.concatenate(preferredBase, path));
        for (String server : bases) if (!server.equals(preferredBase))
            result.add(ImageUrlResolver.concatenate(server, path));
        return result;
    }
}
