package com.maxwai.nclientv3.utility;
import org.junit.Test;
import static org.junit.Assert.*;

public class CdnServersTest {
    @Test public void documentedFieldsSelectTheRightServerPoolAndKeepPathExact() throws Exception {
        CdnServers servers = CdnServers.parse("{\"image_servers\":[\"https://new-a.example\",\"https://new-b.example/\"],\"thumb_servers\":[\"https://thumbs.example\"]}");
        String path = "/opaque/a%2Fb.webp?sig=xx%2By+z";
        String page = ImageUrlResolver.resolve(path, false);
        assertEquals("https://new-a.example" + path, servers.resolve(page).get(0));
        assertEquals("https://new-b.example" + path, servers.resolve(page).get(1));
        assertEquals("https://thumbs.example" + path, servers.resolve(ImageUrlResolver.resolve(path, true)).get(0));
        assertEquals("https://new-b.example/", servers.baseFor(page, "https://new-b.example" + path));
        assertEquals("https://new-b.example" + path, servers.resolve(page, "https://new-b.example/").get(0));
    }
    @Test public void serverChangesRequireNoCodeChangeOrFilenameConstruction() throws Exception {
        String model = ImageUrlResolver.resolve("not-numbered/odd-name.avif", false);
        CdnServers first = CdnServers.parse("{\"image_servers\":[\"https://first.example\"],\"thumb_servers\":[\"https://t.example\"]}");
        CdnServers later = CdnServers.parse("{\"image_servers\":[\"https://later.example\"],\"thumb_servers\":[\"https://t.example\"]}");
        assertEquals("https://first.example/not-numbered/odd-name.avif", first.resolve(model).get(0));
        assertEquals("https://later.example/not-numbered/odd-name.avif", later.resolve(model).get(0));
    }
    @Test public void malformedAndEmptyListsAreRejected() {
        for (String json : new String[]{"{}", "{\"image_servers\":[],\"thumb_servers\":[]}", "{\"image_servers\":[\"http://unsafe.example\"],\"thumb_servers\":[\"https://t.example\"]}"}) {
            try { CdnServers.parse(json); fail(json); } catch (java.io.IOException expected) { }
        }
    }
}
