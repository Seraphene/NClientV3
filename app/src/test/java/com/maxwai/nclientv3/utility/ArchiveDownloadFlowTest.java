package com.maxwai.nclientv3.utility;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.async.database.DatabaseHelper;
import com.maxwai.nclientv3.async.downloader.GalleryDownloaderV2;
import com.maxwai.nclientv3.settings.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Authenticated ticket issuance plus a real TLS archive transfer, with no live service/key. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=31,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ArchiveDownloadFlowTest {
    private static final String SESSION_AGENT="VerifiedBrowser NClient/test";
    private Context context;
    private DatabaseHelper database;
    private Gallery gallery;
    private OkHttpClient previous;
    private MockWebServer server;
    private HandshakeCertificates clientTls;
    private byte[] archive;
    private final AtomicInteger linkRequests=new AtomicInteger();
    private int linkStatus=200;

    @Before public void setup() throws Exception {
        context=RuntimeEnvironment.getApplication(); ReadingFixtures.initialize(context);
        database=new DatabaseHelper(context); database.getWritableDatabase();
        Global.DOWNLOADFOLDER=new File(context.getFilesDir(),"archive-test"); assertTrue(Global.DOWNLOADFOLDER.mkdirs());
        gallery=ReadingFixtures.gallery(context,42);
        AuthStore.saveApiKey(context,"test-only-key",true);
        context.getSharedPreferences("SiteSession",0).edit().putString("user_agent",SESSION_AGENT).commit();
        Bitmap bitmap=Bitmap.createBitmap(40,60,Bitmap.Config.ARGB_8888); bitmap.eraseColor(0xff557766);
        ByteArrayOutputStream image=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,image); bitmap.recycle();
        ByteArrayOutputStream zip=new ByteArrayOutputStream();
        try(ZipOutputStream output=new ZipOutputStream(zip)) {
            for(int page=1;page<=2;page++) { output.putNextEntry(new ZipEntry(page+".png")); output.write(image.toByteArray()); output.closeEntry(); }
        }
        archive=zip.toByteArray();
        HeldCertificate certificate=new HeldCertificate.Builder().addSubjectAlternativeName("localhost").build();
        HandshakeCertificates serverTls=new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        clientTls=new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
        server=new MockWebServer(); server.useHttps(serverTls.sslSocketFactory(),false); server.start();
        previous=Global.client;
        Global.client=new OkHttpClient.Builder().addInterceptor(new ApiAuthInterceptor(context,false))
            .addInterceptor(chain->{
                Request request=chain.request(); int issue=linkRequests.incrementAndGet();
                assertEquals("https://nhentai.net/api/v2/galleries/42/download?format=zip",request.url().toString());
                assertEquals("POST",request.method()); assertEquals(0,request.body().contentLength());
                assertEquals("Key test-only-key",request.header("Authorization"));
                assertEquals("application/json",request.header("Accept"));
                assertEquals(SESSION_AGENT,request.header("User-Agent"));
                try {
                    String body=new JSONObject().put("url",server.url("/archive.zip?signature=fixture%2F+value&issue="+issue).toString())
                        .put("expires_at",System.currentTimeMillis()/1000+300).toString();
                    return TestChain.response(request,linkStatus,body);
                } catch(org.json.JSONException error) { throw new IOException(error); }
            }).build();
    }
    @After public void close() throws Exception { server.shutdown(); Global.client=previous; AuthStore.clear(context); database.close(); }
    private GalleryDownloaderV2 downloader() {
        return new GalleryDownloaderV2(context,gallery,0,1) {
            @Override protected OkHttpClient archiveClient(String userAgent) {
                return super.archiveClient(userAgent).newBuilder()
                    .sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager()).build();
            }
        };
    }
    private MockResponse zipped() { return new MockResponse().setHeader("Content-Type","application/zip").setBody(new okio.Buffer().write(archive)); }

    @Test public void verifiedApiIdentityIsPreservedAndArchiveFinishesWithoutLeakingKey() throws Exception {
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                return SESSION_AGENT.equals(request.getHeader("User-Agent")) ? zipped() : new MockResponse().setResponseCode(403);
            }
        });
        // Control: the former image transport does not preserve the API session identity.
        OkHttpClient oldTransport=NetworkClients.images(()->"nhentai.net","NClient/test").newBuilder()
            .sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager()).build();
        try(Response response=oldTransport.newCall(new Request.Builder().url(server.url("/control.zip")).build()).execute()) {
            assertEquals(403,response.code());
        }
        GalleryDownloaderV2 downloader=downloader(); downloader.download();
        assertEquals(GalleryDownloaderV2.Status.FINISHED,downloader.getStatus()); assertEquals(100,downloader.getPercentage());
        assertEquals(2,downloader.localGallery().getPageCount()); assertEquals(1,linkRequests.get());
        server.takeRequest(); RecordedRequest file=server.takeRequest();
        assertEquals(SESSION_AGENT,file.getHeader("User-Agent")); assertNull(file.getHeader("Authorization")); assertNull(file.getHeader("Cookie"));
        assertEquals("/archive.zip?signature=fixture%2F+value&issue=1",file.getPath());
        assertTrue(AuthStore.hasValidApiKey(context));
    }
    @Test public void file403KeepsKeyValidAndExplicitResumeRequestsFreshLink() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403).setHeader("Content-Type","application/json").setBody("{\"error\":\"expired_token\"}"));
        GalleryDownloaderV2 downloader=downloader(); downloader.download();
        assertEquals(GalleryDownloaderV2.Status.PAUSED,downloader.getStatus()); assertEquals(0,downloader.getPercentage());
        assertTrue(downloader.getLastError().contains("Archive file request")); assertTrue(downloader.getLastError().contains("expired"));
        assertTrue(AuthStore.hasValidApiKey(context)); assertEquals(1,linkRequests.get()); assertEquals(1,server.getRequestCount());
        server.enqueue(zipped()); downloader.setStatus(GalleryDownloaderV2.Status.NOT_STARTED); downloader.download();
        assertEquals(GalleryDownloaderV2.Status.FINISHED,downloader.getStatus()); assertEquals(2,linkRequests.get());
        assertTrue(server.takeRequest().getPath().endsWith("issue=1")); assertTrue(server.takeRequest().getPath().endsWith("issue=2"));
    }
    @Test public void ticket403IsDistinctFromInvalidKeyAndNeverFetchesArchive() {
        linkStatus=403; GalleryDownloaderV2 denied=downloader(); denied.download();
        assertEquals(GalleryDownloaderV2.Status.PAUSED,denied.getStatus());
        assertTrue(denied.getLastError().contains("Archive-link API request")); assertTrue(AuthStore.hasValidApiKey(context));
        linkStatus=401; GalleryDownloaderV2 invalid=downloader(); invalid.download();
        assertEquals(GalleryDownloaderV2.Status.PAUSED,invalid.getStatus());
        assertTrue(invalid.getLastError().contains("key was rejected")); assertFalse(AuthStore.hasValidApiKey(context));
        assertEquals(0,server.getRequestCount());
    }
}
