# Startup and navigation cleanup — build 420711 / 4.2.7-debug11

This update fixes a reproduced crash when a downloaded story uses the single-column
card layout. Selection code cast the card's FrameLayout to ConstraintLayout;
inflating a populated Downloads screen threw ClassCastException. Remembering
Downloads as the starting library could expose the crash immediately after launch.
The selection overlay now attaches correctly to both card layouts, and clicks on
removed RecyclerView rows are ignored.

The **Read downloaded stories** shortcut is now an icon in the top-right toolbar.
In downloaded-reading mode it becomes **Browse online**. Its title is available
to accessibility services and as a long-press tooltip. Pagination occupies its
own fixed row above bottom navigation, so the collapsing header cannot move it
underneath the navigation bar. There is no extra full-width mode button at the bottom.

Downloads scans run off the UI thread and publish a complete list on the UI thread.
Older scans cannot overwrite newer results. Returning to Downloads refreshes the
list, and search text survives refresh and activity recreation. Old adapters detach
their download observers; progress and completion updates run on the UI thread.
Returning to Explore starts a fresh browsing root instead of reusing stale search
results from an earlier MainActivity.

The APK publishing task now copies artifacts into `app/build/outputs/installable/`
instead of renaming Android Gradle Plugin's managed outputs. This preserves the
paths in APK metadata across test and bundle tasks. GitHub Actions uploads only
the named APK copies and fails the upload step if none are present.

## Downloads and API keys

The upstream [NClientV3 downloader](https://github.com/maxwai/NClientV3/blob/main/app/src/main/java/com/maxwai/nclientv3/async/downloader/GalleryDownloaderV2.java)
was inspected on 2026-11-02. Its `download()` loop saves individual page URLs via
`savePage()`, without an archive API key. This describes that implementation; it
does not establish that its requests still work with today's service.

This fork uses `POST /api/v2/galleries/{id}/download?format=zip`. The documentation
provided for this task requires an API key for third-party archive downloads and
explicitly directs clients away from rebuilding archives by walking CDN pages.
Public reading does not require a key. Existing downloaded files and cached pages
can be read without one, but the cache is bounded and can be evicted; it is not a
replacement for a complete saved gallery. Server-side archive availability still
depends on the provider's download feature flag.

## Verification and installation

Both variants passed all 103 regression tests (206 total). Debug APK assembly and
bundle tasks passed with the corrected publishing task; the named APKs match the
files referenced by AGP's output metadata. Both APK signatures were verified and
match the previous repository-published debug builds.

Lint reports six errors for missing translations in the newly merged image-feed
strings, plus 84 warnings. These remain reported rather than being hidden by a
global lint suppression; they do not prevent these debug APKs from building.

The regression suite covers the real application's initialization and launcher to
Explore flow on simulated Android 9, 12, and 14. The test harness initializes
WorkManager in place of Android's startup ContentProvider; API responses are local
fixtures. Downloads tests render populated lists, restore searches, and reject stale
scan results. A native-rendered 320dp layout test checks the toolbar touch target and
pagination separation. Existing history, reader, API, archive, cache, and theme
tests remain in the suite.

No physical-phone test or authenticated live archive download is claimed. The
reported Actions-installed launch failure has not been matched to a phone stack
trace; the reproduced Downloads crash is fixed, but that is not proof of its being
the only cause on the user's device.

Use the APK linked in [the APK directory](../apks/README.md). It uses the same
signing certificate as earlier APKs published there. GitHub Actions generates a
debug signing key on its runner, which may differ from these APKs or another run;
Android can refuse an in-place update when certificates differ. An installation
signature mismatch is distinct from an app crashing after installation. Preserve
your data before uninstalling anything to resolve a signature mismatch.

## Review of the separately added image-feed screen

The merged `HanimeImagesActivity` has a manifest registration and a drawer action
in MainActivity. Its network integration is incomplete: the new request type has
no branch in `InspectorV3.createUrl()`, so it produces nhentai's API root instead
of the intended provider URL. The declared provider endpoint and channel list are
unused in URL construction. The screen's failure callback only logs errors, and
its pagination does not stop after an empty result. These are review findings,
not a claim that this separate feed was repaired or verified against a live API.

One-click prefetch of all story pages is technically possible, but performs the
same bulk page transfer as the older downloader. Calling it caching does not
change the supplied provider rule to use the archive endpoint for whole galleries.
No cache-all workaround was added; the ordinary bounded reading cache remains.
