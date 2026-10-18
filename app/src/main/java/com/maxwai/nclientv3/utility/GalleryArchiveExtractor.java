package com.maxwai.nclientv3.utility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extracts requested pages from the supported archive, never issuing page requests. */
public final class GalleryArchiveExtractor {
    private static final Pattern IMAGE = Pattern.compile("(.+)\\.(jpg|jpeg|png|gif|webp)", Pattern.CASE_INSENSITIVE);
    private static final long MAX_PAGE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;
    private GalleryArchiveExtractor() { }
    public interface Saved { void accept(int page, File file) throws IOException; }

    /** names maps exact API basenames to one-based page numbers; numbered archive entries also work. */
    public static void extract(InputStream input, File folder, Set<Integer> requested,
                               Map<String, Integer> names, BooleanSupplier stopped, Saved saved) throws IOException {
        Set<Integer> pending = new HashSet<>(requested);
        Set<Integer> seen = new HashSet<>();
        long total = 0;
        byte[] buffer = new byte[32768];
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (stopped.getAsBoolean()) throw new InterruptedIOException("Download paused");
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\") || name.contains(":")) throw new IOException("Unsafe archive entry");
                for (String segment : name.split("/"))
                    if (segment.equals("..") || segment.equals(".")) throw new IOException("Unsafe archive entry");
                if (entry.isDirectory()) { zip.closeEntry(); continue; }
                String base = name.substring(name.lastIndexOf('/') + 1);
                Matcher matcher = IMAGE.matcher(base);
                Integer page = names.get(base);
                if (matcher.matches() && page == null && matcher.group(1).matches("[0-9]{1,9}"))
                    page = Integer.parseInt(matcher.group(1));
                File destination = null, temporary = null;
                boolean selected = matcher.matches() && page != null && pending.contains(page);
                if (selected) {
                    if (!seen.add(page)) throw new IOException("Duplicate archive page");
                    destination = new File(folder, String.format(Locale.US, "%03d.%s", page, matcher.group(2).toLowerCase(Locale.ROOT)));
                    temporary = File.createTempFile(".page-", ".part", folder);
                }
                long size = 0;
                try {
                    try (FileOutputStream output = temporary == null ? null : new FileOutputStream(temporary)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            if (stopped.getAsBoolean()) throw new InterruptedIOException("Download paused");
                            size += count; total += count;
                            if (size > MAX_PAGE_BYTES || total > MAX_TOTAL_BYTES) throw new IOException("Archive exceeds size limits");
                            if (output != null) output.write(buffer, 0, count);
                        }
                    }
                    zip.closeEntry(); // verify CRC before publishing a page
                    if (temporary != null) {
                        if (size == 0) throw new IOException("Archive contains an empty page");
                        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        saved.accept(page, destination);
                        pending.remove(page);
                    }
                } finally {
                    if (temporary != null) Files.deleteIfExists(temporary.toPath());
                }
            }
        }
        if (!pending.isEmpty()) throw new IOException("Archive is missing requested pages");
    }
}
