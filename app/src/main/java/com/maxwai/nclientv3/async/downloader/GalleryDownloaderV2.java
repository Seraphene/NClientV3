package com.maxwai.nclientv3.async.downloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.ApiResponse;
import com.maxwai.nclientv3.settings.AuthStore;
import com.maxwai.nclientv3.files.GalleryFolder;
import com.maxwai.nclientv3.utility.ArchiveTicket;
import com.maxwai.nclientv3.utility.GalleryArchiveExtractor;
import com.maxwai.nclientv3.utility.ImageHttpClient;
import com.maxwai.nclientv3.utility.ImageUrlResolver;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.local.LocalGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.RequestBody;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

public class GalleryDownloaderV2 {
    public static final String DUPLICATE_EXTENSION = ".DUP";
    public static final Pattern ID_FILE = Pattern.compile("^\\.\\d{1,6}$");
    private final Context context;
    private final int id;
    private final CopyOnWriteArraySet<DownloadObserver> observers = new CopyOnWriteArraySet<>();
    private final List<PageContainer> urls = new ArrayList<>();
    private volatile Status status = Status.NOT_STARTED;
    private volatile Call activeCall;
    private volatile String lastError;
    private String title;
    private Uri thumbnail;
    private int start = -1, end = -1;
    private Gallery gallery;
    private File folder;
    private boolean initialized = false;

    public GalleryDownloaderV2(Context context, @Nullable String title, @Nullable Uri thumbnail, int id) {
        this.context = context;
        this.id = id;
        this.thumbnail = thumbnail;
        this.title = Gallery.getPathTitle(title, context.getString(R.string.download_gallery));
    }

    public GalleryDownloaderV2(Context context, Gallery gallery, int start, int end) {
        this(context, gallery.getTitle(), gallery.getCover(), gallery.getId());
        this.start = start;
        this.end = end;
        setGallery(gallery);
    }

    private static File findFolder(File downloadfolder, String pathTitle, int id) {
        File folder = new File(downloadfolder, pathTitle);
        if (usableFolder(folder, id)) return folder;
        int i = 1;
        do {
            folder = new File(downloadfolder, pathTitle + DUPLICATE_EXTENSION + (i++));
        } while (!usableFolder(folder, id));
        return folder;
    }

    private static boolean usableFolder(File file, int id) {
        if (!file.exists()) return true;//folder not exists
        if (new File(file, "." + id).exists()) return true;//same id
        File[] files = file.listFiles((dir, name) -> ID_FILE.matcher(name).matches());
        if (files != null && files.length > 0) return false;//has id but not equal
        LocalGallery localGallery = new LocalGallery(file);//read id from metadata
        return localGallery.getId() == id;
    }

    public boolean hasData() {
        return gallery != null;
    }

    public void removeObserver(DownloadObserver observer) {
        observers.remove(observer);
    }

    public File getFolder() {
        return folder;
    }

    public Gallery getGallery() {
        return gallery;
    }

    private void setGallery(Gallery gallery) {
        this.gallery = gallery;
        title = gallery.getPathTitle();
        thumbnail = gallery.getThumbnail();
        Queries.DownloadTable.addGallery(this);
        if (start == -1) start = 0;
        if (end == -1) end = gallery.getPageCount() - 1;
    }

    private int getTotalPage() {
        return Math.max(1, end - start + 1);
    }

    public int getPercentage() {
        if (status == Status.FINISHED) return 100;
        if (gallery == null || !initialized) return 0;
        return ((getTotalPage() - urls.size()) * 100) / getTotalPage();
    }

    private void onStart() {
        setStatus(Status.DOWNLOADING);
        for (DownloadObserver observer : observers) observer.triggerStartDownload(this);
    }

    private void onEnd() {
        setStatus(Status.FINISHED);
        for (DownloadObserver observer : observers) observer.triggerEndDownload(this);
        LogUtility.d("Delete 75: " + id);
        Queries.DownloadTable.removeGallery(id);
    }

    private void onUpdate() {
        int total = getTotalPage();
        int reach = total - urls.size();
        for (DownloadObserver observer : observers)
            observer.triggerUpdateProgress(this, reach, total);
    }

    private void onCancel() {
        for (DownloadObserver observer : observers) observer.triggerCancelDownload(this);
    }

    private void onPause() {
        for (DownloadObserver observer : observers) observer.triggerPauseDownload(this);
    }

    public LocalGallery localGallery() {
        if (status != Status.FINISHED || folder == null) return null;
        return new LocalGallery(folder);
    }

    public String getTitle() {
        return title;
    }

    public void addObserver(DownloadObserver observer) {
        if (observer == null) return;
        observers.add(observer);
    }

    public int getId() {
        return id;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    @NonNull
    public String getPathTitle() {
        return title;
    }

    @NonNull
    public String getTruePathTitle() {
        return title;
    }

    /**
     * @return true if the download has been completed, false otherwise
     */
    public boolean downloadGalleryData() {
        if (this.gallery != null) return true;
        InspectorV3 inspector = InspectorV3.galleryInspector(context, id, null);
        try {
            if (!inspector.createDocument()) return false;
            inspector.parseDocument();
            if (inspector.getGalleries() == null || inspector.getGalleries().isEmpty())
                return false;
            Gallery g = (Gallery) inspector.getGalleries().get(0);
            if (g.isValid())
                setGallery(g);
            return g.isValid();
        } catch (Exception e) {
            lastError = e.getMessage();
            LogUtility.e("Error while downloading", e);
            return false;
        }
    }

    void pauseAfterMetadataFailure() {
        if (status == Status.CANCELED) return;
        if (lastError == null) lastError = context.getString(R.string.failed);
        setStatus(Status.PAUSED);
        onPause();
    }

    public Uri getThumbnail() {
        return thumbnail;
    }

    public boolean canBeFetched() {
        return status == Status.NOT_STARTED;
    }

    public Status getStatus() {
        return status;
    }

    public String getLastError() { return lastError; }

    public void setStatus(Status status) {
        if (this.status == status) return;
        this.status = status;
        Call running = activeCall;
        if ((status == Status.PAUSED || status == Status.CANCELED) && running != null) running.cancel();
        if (status == Status.CANCELED) {
            LogUtility.d("Delete 95: " + id);
            onCancel();
            Global.recursiveDelete(folder);
            Queries.DownloadTable.removeGallery(id);
        }
    }

    public void download() {
        if (status == Status.CANCELED || status == Status.PAUSED) return;
        lastError = null;
        onStart();
        try {
            initDownload();
            if (urls.isEmpty()) { onEnd(); return; }
            if (!AuthStore.hasValidApiKey(context)) throw new IOException(context.getString(R.string.archive_key_required));
            Request request = new Request.Builder()
                .url(Utility.getApiBaseUrl() + "galleries/" + id + "/download?format=zip")
                .post(RequestBody.create(new byte[0], null)).build();
            ArchiveTicket ticket;
            activeCall = Global.getClient(context).newCall(request);
            if (status != Status.DOWNLOADING) { activeCall.cancel(); activeCall = null; return; }
            try (Response response = activeCall.execute()) {
                if (response.code() == 503) throw new IOException(context.getString(R.string.archive_disabled));
                ticket = ArchiveTicket.parse(ApiResponse.read(response), System.currentTimeMillis() / 1000);
            } finally { activeCall = null; }
            if (status != Status.DOWNLOADING) return;
            Set<Integer> wanted = new HashSet<>();
            Map<String, Integer> names = new HashMap<>();
            for (PageContainer page : urls) {
                wanted.add(page.page);
                String path = java.net.URI.create(ImageUrlResolver.filePath(page.url.toString())).getPath();
                names.put(path.substring(path.lastIndexOf('/') + 1), page.page);
            }
            if (ticket.expiresAt <= System.currentTimeMillis() / 1000) throw new IOException("Archive link expired. Retry the download.");
            // Archive URLs are supplied by the API; credentials stay on the API request.
            activeCall = ImageHttpClient.get().newBuilder().readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.MINUTES).build()
                .newCall(new Request.Builder().url(ticket.url).build());
            if (status != Status.DOWNLOADING) { activeCall.cancel(); return; }
            try (Response response = activeCall.execute()) {
                if (!response.isSuccessful()) throw new IOException("Archive request failed (HTTP " + response.code() + ")");
                if (response.body() == null) throw new IOException("Empty archive response");
                GalleryArchiveExtractor.extract(response.body().byteStream(), folder, wanted, names,
                    () -> status != Status.DOWNLOADING, (page, file) -> {
                        if (isCorrupted(file)) {
                            java.nio.file.Files.deleteIfExists(file.toPath());
                            throw new IOException("Archive contains an unreadable page");
                        }
                        urls.removeIf(item -> item.page == page);
                        onUpdate();
                    });
            } finally { activeCall = null; }
            if (status == Status.DOWNLOADING) onEnd();
        } catch (IOException | IllegalArgumentException error) {
            if (status == Status.CANCELED) return;
            if (status != Status.PAUSED) {
                lastError = error.getMessage();
                setStatus(Status.PAUSED);
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context,
                    context.getString(R.string.archive_paused, lastError), Toast.LENGTH_LONG).show());
            }
            LogUtility.e("Archive download paused", error);
        } finally {
            activeCall = null;
            if (status == Status.PAUSED) onPause();
        }
    }

    private boolean isCorrupted(File file) {
        String path = file.getAbsolutePath();
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return Global.isJPEGCorrupted(path);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 256;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        boolean x = bitmap == null;
        if (!x) bitmap.recycle();
        return x;
    }

    public synchronized void initDownload() {
        if (initialized) return;
        createFolder();
        createPages();
        checkPages();
        initialized = true;
    }

    private void checkPages() {
        GalleryFolder existing = new GalleryFolder(folder);
        for (int i = 0; i < urls.size(); i++) {
            if (urls.get(i) == null) {
                urls.remove(i--);
                continue;
            }
            File filePath = existing.getPage(urls.get(i).page);
            if (filePath != null && filePath.isFile() && !isCorrupted(filePath))
                urls.remove(i--);
        }
    }

    private void createPages() {
        for (int i = start; i <= end && i < gallery.getPageCount(); i++)
            urls.add(new PageContainer(i + 1, gallery.getHighPage(i)));
    }

    private void createFolder() {
        folder = findFolder(Global.DOWNLOADFOLDER, title, id);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalArgumentException("Unable to create download folder");
        }
        try {
            writeNoMedia();
            createIdFile();
        } catch (IOException e) {
            LogUtility.e("Error creating base files", e);
        }
    }

    private void createIdFile() throws IOException {
        File idFile = new File(folder, "." + id);
        //noinspection ResultOfMethodCallIgnored
        idFile.createNewFile();
    }

    private void writeNoMedia() throws IOException {
        File nomedia = new File(folder, ".nomedia");
        LogUtility.d("NOMEDIA: " + nomedia + " for id " + id);
        try (FileWriter writer = new FileWriter(nomedia)) {
            gallery.jsonWrite(writer);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GalleryDownloaderV2 that = (GalleryDownloaderV2) o;

        if (id != that.id) return false;
        return Objects.equals(folder, that.folder);
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + (folder != null ? folder.hashCode() : 0);
        return result;
    }

    public enum Status {NOT_STARTED, DOWNLOADING, PAUSED, FINISHED, CANCELED}

    public static class PageContainer {
        public final int page;
        public final Uri url;

        public PageContainer(int page, Uri url) {
            this.page = page;
            this.url = url;
        }


    }
}
