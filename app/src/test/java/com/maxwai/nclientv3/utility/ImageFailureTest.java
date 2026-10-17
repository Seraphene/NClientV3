package com.maxwai.nclientv3.utility;

import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.engine.GlideException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImageFailureTest {
    @Test public void httpStatusIsVisibleWithoutLeakingResponseDetails() {
        assertEquals("HTTP 403", ImageFailure.describe(new GlideException("request", new HttpException("private response", 403))));
    }
    @Test public void dnsFailureIsIdentified() {
        assertEquals("DNS lookup failed", ImageFailure.describe(new GlideException("request", new java.net.UnknownHostException("private hostname"))));
    }
    @Test public void timeoutIsIdentified() {
        assertEquals("Image request timed out", ImageFailure.describe(new GlideException("request", new java.net.SocketTimeoutException())));
    }
    @Test public void missingCauseDoesNotCrash() {
        assertEquals("Image could not be decoded or downloaded", ImageFailure.describe(null));
    }
}
