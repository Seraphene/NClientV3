# Installable development APKs

- **Android 9 and newer:** [Download APK](NClientV3-4.2.7-reader-fix-android9plus.apk)
- **Android 8 / 8.1:** [Download APK](NClientV3-4.2.7-reader-fix-android8.apk)

On GitHub, open the APK and choose **Download raw file**. Transfer it to your
Android device, open it, and allow installation from that source if prompted.

These signed development builds install as **NClientV3 Debug** alongside the
production app, with separate login and settings. The source and APKs are committed together in this directory’s update.
Version code: `420702` (`4.2.7-debug2`). Do not uninstall the production app to try these builds.

Both variants built successfully and passed 15 regression tests each. Live
website/API access was blocked in the build environment, so reading on a device
has not been confirmed. See [validation details](../docs/reader-fixes.md).

Verify downloaded files with `sha256sum -c SHA256SUMS`.

## Diagnostic update

This update identifies HTTP errors, DNS failures, TLS failures and timeouts on
failed pages. Tap the reader error text for details and **Open in browser** to
compare the exact image URL. Long-press the dialog text to copy it. URLs shown
in the dialog omit query parameters and fragments.

The image-loading failure reported on Android 12 for gallery 688486 remains
under investigation; this APK adds diagnostics and is not a confirmed fix for
that failure. The gallery works in the user’s browser on the same connection.
