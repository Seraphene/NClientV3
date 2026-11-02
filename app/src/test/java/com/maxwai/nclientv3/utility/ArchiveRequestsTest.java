package com.maxwai.nclientv3.utility;

import java.io.IOException;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveRequestsTest {
    @Test public void ticketUsesDocumentedPostAndFormatQuery() {
        Request request=ArchiveRequests.ticket(42);
        assertEquals("POST",request.method()); assertEquals("https://nhentai.net/api/v2/galleries/42/download?format=zip",request.url().toString());
        assertNotNull(request.body()); assertNull(request.header("Authorization"));
    }
    @Test public void archiveUrlIsUnchangedAndExpiredLinksAreNotRequested() throws Exception {
        String url="https://archives.example/opaque?signature=a%2Fb+z";
        ArchiveTicket ticket=ArchiveTicket.parse("{\"url\":\""+url+"\",\"expires_at\":2000}",1000);
        assertEquals(url,ArchiveRequests.archive(ticket,"NClient/test",1000).url().toString());
        try { ArchiveRequests.archive(ticket,"NClient/test",2000); fail(); }
        catch(IOException expected) { assertTrue(expected.getMessage().contains("expired")); }
    }
    private String failure(int status,ArchiveRequests.Stage stage,String body,String challenge) throws Exception {
        try(Response response=TestChain.response(ArchiveRequests.ticket(42),status,body).newBuilder()
            .header("cf-mitigated",challenge).build()) {
            try { ArchiveRequests.check(response,stage); fail(); return ""; }
            catch(IOException expected) { return expected.getMessage(); }
        }
    }
    @Test public void forbiddenDiagnosticsDistinguishStagesAndDoNotExposeResponseSecrets() throws Exception {
        String secret="{\"error\":\"secret-key https://files.example/?token=secret\"}";
        String link=failure(403,ArchiveRequests.Stage.LINK,secret,"");
        String file=failure(403,ArchiveRequests.Stage.FILE,secret,"");
        assertTrue(link.contains("Archive-link API")); assertTrue(link.contains("permissions"));
        assertTrue(file.contains("API issued a link")); assertFalse(file.contains("key is invalid"));
        assertFalse(link.contains("secret")); assertFalse(file.contains("secret"));
        assertTrue(failure(403,ArchiveRequests.Stage.FILE,"{}","challenge").contains("verification"));
        assertTrue(failure(403,ArchiveRequests.Stage.FILE,"{\"error\":\"expired_token\"}","").contains("expired"));
    }
    @Test public void statusHintsCoverKeyRejectionRateLimitsAndDisabledDownloads() throws Exception {
        assertTrue(failure(401,ArchiveRequests.Stage.LINK,"{}","").contains("key was rejected"));
        assertTrue(failure(429,ArchiveRequests.Stage.FILE,"{}","").contains("Retry-After"));
        assertTrue(failure(503,ArchiveRequests.Stage.LINK,"{}","").contains("unavailable"));
    }
    @Test public void successfulHtmlOrNonZipResponseIsNotExtracted() throws Exception {
        for(String type:new String[]{"text/html","application/json","application/octet-stream"}) {
            try(Response response=TestChain.response(ArchiveRequests.ticket(42),200,"not a ZIP").newBuilder().header("Content-Type",type).build()) {
                try { ArchiveRequests.check(response,ArchiveRequests.Stage.FILE); fail(); }
                catch(IOException expected) { assertTrue(expected.getMessage().contains("ZIP")); }
            }
        }
    }
}
