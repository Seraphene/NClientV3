package com.maxwai.nclientv3.utility;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImageUrlResolverTest {
    @Test public void relativePageUsesImageServer() {
        assertEquals("https://i.nhentai.net/galleries/123/1.webp", ImageUrlResolver.resolve("galleries/123/1.webp", "nhentai.net", false));
    }
    @Test public void rootRelativeThumbnailHasOneSlash() {
        assertEquals("https://t.nhentai.net/galleries/123/1t.jpg", ImageUrlResolver.resolve("/galleries/123/1t.jpg", "nhentai.net", true));
    }
    @Test public void absoluteCdnUrlIsPreserved() {
        String url = "https://cdn.example.org/galleries/123/1.webp?token=abc";
        assertEquals(url, ImageUrlResolver.resolve(url, "nhentai.net", false));
    }
    @Test public void protocolRelativeUrlUsesHttps() {
        assertEquals("https://cdn.example.org/1.webp", ImageUrlResolver.resolve("//cdn.example.org/1.webp", "nhentai.net", false));
    }
    @Test public void reportedGalleryUsesCanonicalImageHost() {
        assertEquals("https://i.nhentai.net/galleries/4247333/1.webp", ImageUrlResolver.resolve("galleries/4247333/1.webp", "nhentai.net", false));
    }
    @Test public void savedLegacyPageUrlIsRepairedWithoutChangingPathOrQuery() {
        assertEquals("https://i.nhentai.net/galleries/4247333/1.webp?x=a%2Fb", ImageUrlResolver.restoreStored("https://i1.nhentai.net/galleries/4247333/1.webp?x=a%2Fb", "nhentai.net", false));
    }
    @Test public void savedLegacyThumbnailUrlIsRepaired() {
        assertEquals("https://t.nhentai.net/galleries/4247333/thumb.webp", ImageUrlResolver.restoreStored("https://t1.nhentai.net/galleries/4247333/thumb.webp", "nhentai.net", true));
    }
    @Test public void savedUnrelatedCdnIsPreserved() {
        String url = "https://i5.nhentai.net/galleries/4247333/1.webp";
        assertEquals(url, ImageUrlResolver.restoreStored(url, "nhentai.net", false));
    }
    @Test public void absoluteUrlSuppliedByApiRemainsAuthoritative() {
        String url = "https://i1.nhentai.net/galleries/4247333/1.webp";
        assertEquals(url, ImageUrlResolver.resolve(url, "nhentai.net", false));
    }
    @Test(expected=IllegalArgumentException.class) public void missingPathIsRejected() { ImageUrlResolver.resolve(" ", "nhentai.net", false); }
    @Test(expected=IllegalArgumentException.class) public void localFileUrlIsRejected() { ImageUrlResolver.resolve("file:///data/private", "nhentai.net", false); }
    @Test(expected=IllegalArgumentException.class) public void insecureAbsoluteUrlIsRejected() { ImageUrlResolver.resolve("http://cdn.example.org/1.jpg", "nhentai.net", false); }
}
