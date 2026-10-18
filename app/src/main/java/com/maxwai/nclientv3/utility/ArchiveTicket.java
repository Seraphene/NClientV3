package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.net.URI;
import org.json.JSONException;
import org.json.JSONObject;

/** Response from POST /api/v2/galleries/{id}/download?format=zip. */
public final class ArchiveTicket {
    public final String url;
    public final long expiresAt;
    private ArchiveTicket(String url, long expiresAt) { this.url = url; this.expiresAt = expiresAt; }
    public static ArchiveTicket parse(String json, long nowSeconds) throws IOException {
        try {
            JSONObject object = new JSONObject(json);
            String url = object.getString("url");
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null)
                throw new IOException("Invalid archive URL");
            long expiry = object.getLong("expires_at");
            if (expiry <= nowSeconds) throw new IOException("Archive link expired. Retry the download.");
            return new ArchiveTicket(url, expiry);
        } catch (JSONException | IllegalArgumentException error) {
            throw new IOException("Invalid archive response", error);
        }
    }
}
