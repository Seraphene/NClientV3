package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveHttpClientTest {
    @Test public void authorizationIsLimitedToOfficialApiOnEveryHop() throws Exception {
        AtomicInteger reads=new AtomicInteger();
        for(String url:new String[]{"https://nhentai.net/api/v2/archive/file?signature=fixture",
            "https://nhentai.net/download/file", "https://files.nhentai.net/api/v2/file",
            "https://external.example/api/v2/file", "https://nhentai.net:444/api/v2/file"}) {
            Request input=new Request.Builder().url(url).header("Authorization","unexpected")
                .header("Cookie","cf_clearance=fixture").header("User-Agent","wrong").build();
            Request result=ArchiveHttpClient.prepare(input,"Verified NClient/test",()->{reads.incrementAndGet();return "Key fixture-key";});
            assertEquals("Verified NClient/test",result.header("User-Agent"));
            if(url.startsWith("https://nhentai.net/api/v2/")) assertEquals("Key fixture-key",result.header("Authorization"));
            else assertNull(result.header("Authorization"));
            if(!SiteConnectionPolicy.isSite(url)) assertNull(result.header("Cookie"));
        }
        assertEquals(1,reads.get());
        try { ArchiveHttpClient.prepare(new Request.Builder().url("http://nhentai.net/api/v2/file").build(),"NClient/test",()->"Key fixture"); fail(); }
        catch(IOException expected) { }
    }

    @Test public void onlySiteVerificationCookiesAreShared() {
        List<Cookie> saved=new ArrayList<>();
        List<Cookie> source=Arrays.asList(
            new Cookie.Builder().name("cf_clearance").value("verified").hostOnlyDomain("nhentai.net").secure().build(),
            new Cookie.Builder().name("sessionid").value("account-secret").hostOnlyDomain("nhentai.net").secure().build(),
            new Cookie.Builder().name("__cf_bm").value("expired").hostOnlyDomain("nhentai.net").secure().expiresAt(1).build());
        CookieJar scoped=ArchiveHttpClient.verificationCookies(new CookieJar() {
            @Override public List<Cookie> loadForRequest(HttpUrl url) { return source; }
            @Override public void saveFromResponse(HttpUrl url,List<Cookie> cookies) { saved.addAll(cookies); }
        });
        HttpUrl site=HttpUrl.get("https://nhentai.net/download/archive");
        assertEquals(1,scoped.loadForRequest(site).size());
        assertEquals("cf_clearance",scoped.loadForRequest(site).get(0).name());
        for(String url:new String[]{"https://external.example/file", "https://cdn.nhentai.net/file", "http://nhentai.net/file"}) {
            assertTrue(scoped.loadForRequest(HttpUrl.get(url)).isEmpty());
            scoped.saveFromResponse(HttpUrl.get(url),source); assertTrue(saved.isEmpty());
        }
        scoped.saveFromResponse(site,source); assertEquals(1,saved.size());
    }

    @Test public void realHttpsRedirectPreservesIdentityAndSignatureWithoutLeakingSecrets() throws Exception {
        HeldCertificate certificate=new HeldCertificate.Builder().addSubjectAlternativeName("localhost").build();
        HandshakeCertificates serverTls=new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        HandshakeCertificates clientTls=new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
        try(MockWebServer first=new MockWebServer(); MockWebServer second=new MockWebServer()) {
            first.useHttps(serverTls.sslSocketFactory(),false); second.useHttps(serverTls.sslSocketFactory(),false);
            first.start(); second.start();
            String signed=second.url("/archive.zip?signature=a%2Fb+z&expires=2000").toString();
            first.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",signed));
            second.enqueue(new MockResponse().setHeader("Content-Type","application/zip").setBody(new okio.Buffer().write(new byte[]{'P','K',3,4})));
            OkHttpClient client=ArchiveHttpClient.create("Verified NClient/test",CookieJar.NO_COOKIES,()->"Key must-stay-private")
                .newBuilder().sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager())
                .addInterceptor(chain->chain.proceed(chain.request().newBuilder()
                    .header("Authorization","Key must-stay-private").header("Cookie","private=secret").build())).build();
            ArchiveTicket ticket=ArchiveTicket.parse("{\"url\":\""+first.url("/redirect")+"\",\"expires_at\":2000}",1000);
            try(Response response=client.newCall(ArchiveRequests.archive(ticket,"Verified NClient/test",1000)).execute()) {
                ArchiveRequests.check(response,ArchiveRequests.Stage.FILE);
                assertEquals(4,response.body().bytes().length);
            }
            for(MockWebServer server:Arrays.asList(first,second)) {
                RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS); assertNotNull(request);
                assertEquals("Verified NClient/test",request.getHeader("User-Agent"));
                assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"));
                if(server==second) assertEquals("/archive.zip?signature=a%2Fb+z&expires=2000",request.getPath());
            }
        }
    }

    @Test public void redirectCannotDowngradeHttpsToHttp() throws Exception {
        HeldCertificate certificate=new HeldCertificate.Builder().addSubjectAlternativeName("localhost").build();
        HandshakeCertificates serverTls=new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        HandshakeCertificates clientTls=new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
        try(MockWebServer secure=new MockWebServer(); MockWebServer insecure=new MockWebServer()) {
            secure.useHttps(serverTls.sslSocketFactory(),false); secure.start(); insecure.start();
            secure.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",insecure.url("/file")));
            OkHttpClient client=ArchiveHttpClient.create("NClient/test",CookieJar.NO_COOKIES,()->null).newBuilder()
                .sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager()).build();
            try(Response response=client.newCall(new Request.Builder().url(secure.url("/file")).build()).execute()) {
                assertEquals(302,response.code());
            }
            assertEquals(0,insecure.getRequestCount());
        }
    }
}
