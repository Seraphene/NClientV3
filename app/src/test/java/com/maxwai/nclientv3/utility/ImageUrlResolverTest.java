package com.maxwai.nclientv3.utility;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImageUrlResolverTest {
    @Test public void relativePageRemainsAnOpaqueApiPath() {
        String path = "galleries/4247333/1.webp?token=a%2Fb+%20&v=1";
        String model = ImageUrlResolver.resolve(path, false);
        assertTrue(ImageUrlResolver.isManaged(model));
        assertFalse(ImageUrlResolver.isThumbnail(model));
        assertEquals(path, ImageUrlResolver.rawPath(model));
    }
    @Test public void thumbnailUsesDistinctServerCategory() {
        String model = ImageUrlResolver.resolve("/opaque/unchanged%2Bname.webp", true);
        assertTrue(ImageUrlResolver.isThumbnail(model));
        assertEquals("/opaque/unchanged%2Bname.webp", ImageUrlResolver.rawPath(model));
    }
    @Test public void absoluteCdnUrlIsPreserved() {
        String url = "https://cdn.example.org/galleries/123/1.webp?token=abc";
        assertEquals(url, ImageUrlResolver.resolve(url, false));
    }
    @Test public void protocolRelativeUrlUsesHttps() {
        assertEquals("https://cdn.example.org/1.webp", ImageUrlResolver.resolve("//cdn.example.org/1.webp", false));
    }
    @Test public void savedSiteUrlIsReboundWithoutChangingPathOrQuery() {
        String model = ImageUrlResolver.restoreStored("https://i1.nhentai.net/galleries/4247333/1.webp?x=a%2Fb", "nhentai.net", false);
        assertTrue(ImageUrlResolver.isManaged(model));
        assertEquals("/galleries/4247333/1.webp?x=a%2Fb", ImageUrlResolver.rawPath(model));
    }
    @Test public void savedExternalCdnAndLocalFilesArePreserved() {
        for (String url : new String[]{"https://cdn.example.org/1.webp", "file:///data/pages/001.jpg", "content://media/123"})
            assertEquals(url, ImageUrlResolver.restoreStored(url, "nhentai.net", false));
    }
    @Test public void savedLogicalPathDoesNotGetEncodedTwice() {
        String model = ImageUrlResolver.resolve("/opaque/a%2Fb.webp", false);
        assertEquals(model, ImageUrlResolver.restoreStored(model, "nhentai.net", false));
        assertEquals("/opaque/a%2Fb.webp", ImageUrlResolver.filePath(model));
    }
    @Test(expected=IllegalArgumentException.class) public void missingPathIsRejected() { ImageUrlResolver.resolve(" ", false); }
    @Test(expected=IllegalArgumentException.class) public void localFileFromApiIsRejected() { ImageUrlResolver.resolve("file:///data/private", false); }
    @Test(expected=IllegalArgumentException.class) public void insecureAbsoluteUrlIsRejected() { ImageUrlResolver.resolve("http://cdn.example.org/1.jpg", false); }
    @Test(expected=IllegalArgumentException.class) public void traversalIsRejected() { ImageUrlResolver.resolve("../image.jpg", false); }
    @Test(expected=IllegalArgumentException.class) public void fragmentsAreRejected() { ImageUrlResolver.resolve("/image.jpg#other", false); }
}
