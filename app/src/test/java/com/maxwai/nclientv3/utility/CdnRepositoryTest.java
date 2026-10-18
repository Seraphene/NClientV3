package com.maxwai.nclientv3.utility;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import static org.junit.Assert.*;

public class CdnRepositoryTest {
    static final String CONFIG = "{\"image_servers\":[\"https://first.example\",\"https://second.example\"],\"thumb_servers\":[\"https://thumb.example\"]}";
    @Test public void discoveryIsPublicCachedAndRefreshesAfterExpiry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicLong now = new AtomicLong(1000);
        AtomicReference<String> config = new AtomicReference<>(CONFIG);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.incrementAndGet();
            assertNull(chain.request().header("Authorization"));
            assertNull(chain.request().header("Cookie"));
            assertEquals("NClient/test (https://project.example)", chain.request().header("User-Agent"));
            assertEquals("/api/v2/cdn", chain.request().url().encodedPath());
            return TestChain.response(chain.request(), 200, config.get());
        }).build();
        CdnRepository repository = new CdnRepository(client, () -> "https://site.example/api/v2/cdn", "NClient/test (https://project.example)", now::get);
        String model = ImageUrlResolver.resolve("/opaque.webp", false);
        assertEquals("https://first.example/opaque.webp", repository.urlsFor(model).get(0));
        repository.succeeded(model, "https://second.example/opaque.webp");
        assertEquals("https://second.example/opaque.webp", repository.urlsFor(model).get(0));
        assertEquals(1, requests.get());
        config.set(CONFIG.replace("first.example", "replacement.example").replace("second.example", "replacement2.example"));
        now.addAndGet(600001);
        assertEquals("https://replacement.example/opaque.webp", repository.urlsFor(model).get(0));
        assertEquals(2, requests.get());
    }
    @Test public void simultaneousImageLoadsShareOneDiscovery() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.incrementAndGet(); entered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("fixture timed out"); }
            catch (InterruptedException error) { throw new IOException(error); }
            return TestChain.response(chain.request(), 200, CONFIG);
        }).build();
        CdnRepository repository = new CdnRepository(client, () -> "https://site.example/api/v2/cdn", "NClient/test");
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Future<?> a = executor.submit(() -> { try { repository.get(); } catch (IOException e) { throw new RuntimeException(e); } });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            Future<?> b = executor.submit(() -> { try { repository.get(); } catch (IOException e) { throw new RuntimeException(e); } });
            release.countDown(); a.get(3, TimeUnit.SECONDS); b.get(3, TimeUnit.SECONDS);
            assertEquals(1, requests.get());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test public void failedDiscoveryHasCooldownAndDoesNotInventServers() throws Exception {
        AtomicInteger requests = new AtomicInteger(); AtomicLong now = new AtomicLong(1000);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.incrementAndGet(); throw new IOException("network unavailable");
        }).build();
        CdnRepository repository = new CdnRepository(client, () -> "https://site.example/api/v2/cdn", "NClient/test", now::get);
        for (int i = 0; i < 3; i++) {
            try { repository.get(); fail(); } catch (CdnRepository.Unavailable expected) { }
        }
        assertEquals(1, requests.get());
        assertTrue(repository.cachedUrls(ImageUrlResolver.resolve("/page.webp", false)).isEmpty());
        now.addAndGet(15001);
        try { repository.get(); fail(); } catch (CdnRepository.Unavailable expected) { }
        assertEquals(2, requests.get());
    }
    @Test public void switchingSiteDiscardsTheOldServerPool() throws Exception {
        AtomicReference<String> endpoint = new AtomicReference<>("https://one.example/api/v2/cdn");
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> TestChain.response(chain.request(), 200,
            CONFIG.replace("first.example", chain.request().url().host()))).build();
        CdnRepository repository = new CdnRepository(client, endpoint::get, "NClient/test");
        String model = ImageUrlResolver.resolve("/p.webp", false);
        assertEquals("https://one.example/p.webp", repository.urlsFor(model).get(0));
        endpoint.set("https://two.example/api/v2/cdn");
        assertEquals("https://two.example/p.webp", repository.urlsFor(model).get(0));
    }
    @Test public void cachedDiagnosticsDoNotBlockDuringARefresh() throws Exception {
        AtomicLong now = new AtomicLong(1000); AtomicInteger calls = new AtomicInteger();
        CountDownLatch refreshing = new CountDownLatch(1), release = new CountDownLatch(1);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            if (calls.incrementAndGet() > 1) {
                refreshing.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("fixture timed out"); }
                catch (InterruptedException error) { throw new IOException(error); }
            }
            return TestChain.response(chain.request(), 200, CONFIG);
        }).build();
        CdnRepository repository = new CdnRepository(client, () -> "https://site.example/api/v2/cdn", "NClient/test", now::get);
        repository.get(); now.addAndGet(600001);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> fetch = executor.submit(() -> { try { repository.get(); } catch (IOException error) { throw new RuntimeException(error); } });
            assertTrue(refreshing.await(3, TimeUnit.SECONDS));
            Future<java.util.List<String>> diagnostic = executor.submit(() -> repository.cachedUrls(ImageUrlResolver.resolve("/p.webp", false)));
            assertEquals(2, diagnostic.get(500, TimeUnit.MILLISECONDS).size());
            release.countDown(); fetch.get(3, TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

}
