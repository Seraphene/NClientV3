package com.maxwai.nclientv3.async.downloader;

public class PageChecker extends Thread {
    @Override
    public void run() {
        for (GalleryDownloaderV2 g : DownloadQueue.getDownloaders())
            if (g.hasData()) {
                try { g.initDownload(); }
                catch (IllegalArgumentException error) {
                    com.maxwai.nclientv3.utility.LogUtility.e("Unable to check saved pages", error);
                    g.setStatus(GalleryDownloaderV2.Status.PAUSED);
                }
            }
    }
}
