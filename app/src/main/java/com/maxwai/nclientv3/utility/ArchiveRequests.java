package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.util.Locale;
import okhttp3.*;
import org.json.JSONObject;

/** The two archive stages have different authorization and different failure meanings. */
public final class ArchiveRequests {
    public enum Stage { LINK, FILE }
    private ArchiveRequests() { }

    public static Request ticket(int galleryId) {
        if (galleryId < 1) throw new IllegalArgumentException("Invalid gallery ID");
        // Keys are valid only at the official API origin, not arbitrary mirror/CDN hosts.
        return new Request.Builder().url("https://nhentai.net/api/v2/galleries/" + galleryId + "/download?format=zip")
            .post(RequestBody.create(new byte[0], null)).build();
    }

    public static Request archive(ArchiveTicket ticket, String userAgent, long nowSeconds) throws IOException {
        if (ticket.expiresAt <= nowSeconds) throw new IOException("Archive link expired. Resume to request a fresh link.");
        return new Request.Builder().url(ticket.url).header("User-Agent", userAgent)
            .header("Accept", "application/zip, application/octet-stream;q=0.9, */*;q=0.8")
            .header("Referer", "https://nhentai.net/").build();
    }

    public static void check(Response response, Stage stage) throws IOException {
        int status = response.code();
        String prefix = stage == Stage.LINK ? "Archive-link API request" : "Archive file request";
        if (status != 200) {
            String reason = prefix + " failed (HTTP " + status + "). ";
            if (status == 401 && stage == Stage.LINK)
                reason += "The API key was rejected. Validate or replace it in Settings.";
            else if (status == 403) {
                if ("challenge".equalsIgnoreCase(response.header("cf-mitigated")))
                    reason += "The server requested site verification. Open Connection, complete the check, then resume.";
                else if (stage == Stage.LINK)
                    reason += "The site refused download authorization. Check your key's permissions and site connection; this does not prove the key is invalid.";
                else if (expired(response))
                    reason += "The signed link has expired. Resume to request a fresh link.";
                else
                    reason += "The API issued a link, but the archive server denied it. Check the site connection, then resume to request a fresh link.";
            } else if (status == 429)
                reason += "The server is limiting requests. Wait before resuming; Retry-After backoff is respected.";
            else if (status == 503 && stage == Stage.LINK)
                reason += "Archive downloads are currently unavailable on the server.";
            else reason += "Resume later to request a fresh archive link.";
            throw new IOException(reason);
        }
        if (stage == Stage.FILE) {
            String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
            if (type.contains("text/html") || type.contains("application/json"))
                throw new IOException("Archive file request returned a web page or API message instead of a ZIP. Check the site connection and resume.");
            byte[] start = response.peekBody(4).bytes();
            if (start.length != 4 || start[0] != 'P' || start[1] != 'K'
                || !((start[2] == 3 && start[3] == 4) || (start[2] == 5 && start[3] == 6)))
                throw new IOException("Archive file request did not return a ZIP file. Check the site connection and resume.");
        }
    }

    private static boolean expired(Response response) {
        try {
            JSONObject json = new JSONObject(response.peekBody(4096).string());
            String code = json.optString("error", json.optString("code", ""));
            return code.equals("expired_token") || code.equals("token_expired")
                || code.equals("expired_download_url") || code.equals("url_expired");
        } catch (Exception ignored) { return false; }
    }
}
