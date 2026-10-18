package com.maxwai.nclientv3.utility;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveTicketTest {
    @Test public void documentedResponseKeepsSignedUrlExact() throws Exception {
        String url = "https://archives.example/download/opaque.zip?sig=a%2Fb+z";
        ArchiveTicket ticket = ArchiveTicket.parse("{\"url\":\"" + url + "\",\"expires_at\":2000}", 1000);
        assertEquals(url, ticket.url); assertEquals(2000, ticket.expiresAt);
    }
    @Test public void expiredMissingOrUnsafeTicketsAreRejected() {
        for (String json : new String[]{"{}", "{\"url\":\"https://files.example/x.zip\",\"expires_at\":1000}",
            "{\"url\":\"http://files.example/x.zip\",\"expires_at\":2000}",
            "{\"url\":\"https://user:secret@files.example/x.zip\",\"expires_at\":2000}"}) {
            try { ArchiveTicket.parse(json, 1000); fail(json); } catch (java.io.IOException expected) { }
        }
    }
}
