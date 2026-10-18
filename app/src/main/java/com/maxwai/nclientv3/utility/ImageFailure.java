package com.maxwai.nclientv3.utility;

import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.engine.GlideException;
import java.util.Collections;
import java.util.List;

/** Non-sensitive diagnostics: no response bodies, cookies or query parameters. */
public final class ImageFailure {
    private ImageFailure() { }

    public static String describe(GlideException failure) {
        List<Throwable> causes = failure == null ? Collections.emptyList() : failure.getRootCauses();
        for (Throwable cause : causes) {
            if (cause instanceof CdnRepository.Unavailable) return "Image-server discovery unavailable";
            if (cause instanceof HttpException) {
                int status = ((HttpException) cause).getStatusCode();
                if (status > 0) return "HTTP " + status;
            }
        }
        for (Throwable cause : causes) {
            if (cause instanceof java.net.UnknownHostException) return "DNS lookup failed";
            if (cause instanceof javax.net.ssl.SSLException) return "TLS connection failed";
            if (cause instanceof java.io.InterruptedIOException) return "Image request timed out";
            if (cause instanceof java.net.ConnectException) return "Image server connection failed";
        }
        return causes.isEmpty() ? "Image could not be decoded or downloaded" : causes.get(0).getClass().getSimpleName();
    }
}
