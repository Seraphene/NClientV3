package com.maxwai.nclientv3.utility;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImageUrlResolverTest {
    @Test public void relativePageUsesImageServer() {
        assertEquals("https://i1.nhentai.net/galleries/123/1.webp", ImageUrlResolver.resolve("galleries/123/1.webp", "nhentai.net", false));
    }
    @Test public void rootRelativeThumbnailHasOneSlash() {
        assertEquals("https://t1.nhentai.net/galleries/123/1t.jpg", ImageUrlResolver.resolve("/galleries/123/1t.jpg", "nhentai.net", true));
    }
    @Test public void absoluteCdnUrlIsPreserved() {
        String url = "https://cdn.example.org/galleries/123/1.webp?token=abc";
        assertEquals(url, ImageUrlResolver.resolve(url, "nhentai.net", false));
    }
    @Test public void protocolRelativeUrlUsesHttps() {
        assertEquals("https://cdn.example.org/1.webp", ImageUrlResolver.resolve("//cdn.example.org/1.webp", "nhentai.net", false));
    }
    @Test(expected=IllegalArgumentException.class) public void missingPathIsRejected() { ImageUrlResolver.resolve(" ", "nhentai.net", false); }
    @Test(expected=IllegalArgumentException.class) public void localFileUrlIsRejected() { ImageUrlResolver.resolve("file:///data/private", "nhentai.net", false); }
    @Test(expected=IllegalArgumentException.class) public void insecureAbsoluteUrlIsRejected() { ImageUrlResolver.resolve("http://cdn.example.org/1.jpg", "nhentai.net", false); }
}
