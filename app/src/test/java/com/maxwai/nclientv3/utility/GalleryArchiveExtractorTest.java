package com.maxwai.nclientv3.utility;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import static org.junit.Assert.*;

public class GalleryArchiveExtractorTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private ByteArrayInputStream zip(String... entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry)); zip.write(("image-" + entry).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
    @Test public void extractsOnlySelectedPagesWithArchiveExtensionsAndReportsProgress() throws Exception {
        File folder = temporary.newFolder(); Set<Integer> saved = new HashSet<>();
        GalleryArchiveExtractor.extract(zip("688486/1.webp", "688486/002.png", "688486/3.jpg"), folder,
            new HashSet<>(Arrays.asList(1, 2)), Collections.emptyMap(), () -> false, (page, file) -> saved.add(page));
        assertEquals(new HashSet<>(Arrays.asList(1, 2)), saved);
        assertTrue(new File(folder, "001.webp").isFile()); assertTrue(new File(folder, "002.png").isFile());
        assertEquals(2, Objects.requireNonNull(folder.list()).length);
        assertEquals("image-688486/1.webp", new String(Files.readAllBytes(new File(folder, "001.webp").toPath()), StandardCharsets.UTF_8));
    }
    @Test public void opaqueApiBasenamesCanMatchArchiveEntries() throws Exception {
        File folder = temporary.newFolder();
        GalleryArchiveExtractor.extract(zip("folder/opaque-name.jpeg"), folder, Collections.singleton(3),
            Collections.singletonMap("opaque-name.jpeg", 3), () -> false, (page, file) -> assertEquals(3, page));
        assertTrue(new File(folder, "003.jpeg").isFile());
    }
    @Test public void unsafeArchiveEntriesCannotEscapeTheGalleryFolder() throws Exception {
        for (String entry : new String[]{"../1.jpg", "/1.jpg", "folder/../../1.jpg", "folder\\1.jpg", "C:/1.jpg"}) {
            File folder = temporary.newFolder();
            try { GalleryArchiveExtractor.extract(zip(entry), folder, Collections.singleton(1), Collections.emptyMap(),
                () -> false, (page, file) -> fail()); fail(entry); } catch (IOException expected) { }
            assertEquals(0, Objects.requireNonNull(folder.list()).length);
        }
    }
    @Test public void cancellationLeavesNoPartialPage() throws Exception {
        File folder = temporary.newFolder(); AtomicInteger checks = new AtomicInteger();
        try { GalleryArchiveExtractor.extract(zip("1.webp"), folder, Collections.singleton(1), Collections.emptyMap(),
            () -> checks.incrementAndGet() >= 2, (page, file) -> fail()); fail(); }
        catch (InterruptedIOException expected) { }
        assertEquals(0, Objects.requireNonNull(folder.list()).length);
    }
    @Test public void missingPagesAndHtmlResponsesDoNotCompleteTheDownload() throws Exception {
        for (InputStream stream : new InputStream[]{zip("2.webp"), new ByteArrayInputStream("<html>error</html>".getBytes(StandardCharsets.UTF_8))}) {
            try { GalleryArchiveExtractor.extract(stream, temporary.newFolder(), Collections.singleton(1), Collections.emptyMap(),
                () -> false, (page, file) -> fail()); fail(); } catch (IOException expected) { }
        }
    }
    @Test public void brokenZipCrcDoesNotPublishAPartialPage() throws Exception {
        File folder = temporary.newFolder();
        byte[] bytes = zip("1.webp").readAllBytes();
        // Damage the compressed data, leaving the ZIP header in place.
        bytes[38] ^= 0x40;
        try { GalleryArchiveExtractor.extract(new ByteArrayInputStream(bytes), folder, Collections.singleton(1), Collections.emptyMap(),
            () -> false, (page, file) -> fail()); fail(); } catch (IOException expected) { }
        assertEquals(0, Objects.requireNonNull(folder.list()).length);
    }
}
