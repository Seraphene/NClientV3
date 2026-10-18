package com.maxwai.nclientv3.utility;

import com.maxwai.nclientv3.BuildConfig;
import okhttp3.OkHttpClient;

/** Shared media transport for Glide and saved-page downloads. */
public final class ImageHttpClient {
    private static final OkHttpClient CLIENT = NetworkClients.images(
        Utility::getHost, "NClient/" + BuildConfig.VERSION_NAME);
    private ImageHttpClient() { }
    public static OkHttpClient get() { return CLIENT; }
}
