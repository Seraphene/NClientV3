# Installable development APKs

- **Android 9 and newer:** [Download APK](NClientV3-4.2.7-reader-fix-android9plus.apk)
- **Android 8 / 8.1:** [Download APK](NClientV3-4.2.7-reader-fix-android8.apk)

On GitHub, open the APK and choose **Download raw file**. Transfer it to your
Android device, open it, and allow installation from that source if prompted.

These signed development builds install as **NClientV3 Debug** alongside the
production app, with separate login and settings. Source commit: `c6866cb`.
Version code: `420701`. Do not uninstall the production app to try these builds.

Both variants built successfully and passed 11 regression tests each. Live
website/API access was blocked in the build environment, so reading on a device
has not been confirmed. See [validation details](../docs/reader-fixes.md).

Verify downloaded files with `sha256sum -c SHA256SUMS`.
