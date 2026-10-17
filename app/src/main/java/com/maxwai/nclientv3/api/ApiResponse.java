package com.maxwai.nclientv3.api;

import java.io.IOException;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Checks transport errors before handing data to a JSON parser. */
public final class ApiResponse {
    private ApiResponse() { }

    public static final class Failure extends IOException {
        public Failure(String message) { super(message); }
    }

    public static String read(Response response) throws IOException {
        if (!response.isSuccessful()) {
            String hint;
            switch (response.code()) {
                case 401: hint = "Check your API key in Settings."; break;
                case 403: hint = "The server denied access. Check website access in your browser."; break;
                case 404: hint = "The gallery or API endpoint was not found."; break;
                case 429: hint = "Too many requests. Wait before retrying."; break;
                default: hint = "Try again later.";
            }
            throw new Failure("API request failed (HTTP " + response.code() + "). " + hint);
        }
        ResponseBody body = response.body();
        if (body == null) throw new Failure("API returned an empty response");
        String type = response.header("Content-Type", "");
        if (type.toLowerCase(java.util.Locale.ROOT).contains("text/html"))
            throw new Failure("The server returned a web page instead of API data. Check website access in your browser.");
        String text = body.string();
        if (text.trim().isEmpty()) throw new Failure("API returned an empty response");
        return text;
    }
}
