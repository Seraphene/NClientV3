package com.maxwai.nclientv3.api.local;

import com.maxwai.nclientv3.LocalActivity;
import com.maxwai.nclientv3.components.ThreadAsyncTask;
import com.maxwai.nclientv3.utility.LogUtility;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Scan files off the UI thread; publish only if this scan is still current. */
public class FakeInspector extends ThreadAsyncTask<LocalActivity, Void, List<LocalGallery>> {
    private final LocalActivity activity;
    private final File folder;
    private final int generation;

    public FakeInspector(LocalActivity activity, File parent) {
        super(activity);
        this.activity = activity;
        this.folder = new File(parent, "Download");
        this.generation = activity.beginDownloadScan();
    }

    @Override protected List<LocalGallery> doInBackground(LocalActivity unused) {
        List<LocalGallery> galleries = new ArrayList<>();
        try {
            if (!folder.exists()) return galleries;
            File[] files = folder.listFiles();
            if (files == null) return null;
            for (File file : files) if (file.isDirectory()) {
                try {
                    LocalGallery gallery = new LocalGallery(file, false);
                    if (gallery.isValid()) galleries.add(gallery);
                } catch (RuntimeException error) { LogUtility.e("Unable to read downloaded gallery", error); }
            }
            return galleries;
        } catch (SecurityException error) { LogUtility.e("Unable to scan downloads", error); return null; }
    }

    @Override protected void onPostExecute(List<LocalGallery> galleries) {
        activity.completeDownloadScan(generation, galleries);
    }
}
