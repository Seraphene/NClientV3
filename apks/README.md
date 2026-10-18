# Installable development APKs

- **Android 9 and newer (including Android 12):** [Download APK](NClientV3-4.2.7-reader-fix-android9plus.apk)
- **Android 8 / 8.1:** [Download APK](NClientV3-4.2.7-reader-fix-android8.apk)

On GitHub, open the APK and choose **Download raw file**, then open the file on
your phone. Build **420705 / 4.2.7-debug5** updates **NClientV3 Debug**, retains its
settings and uses the same signing key as the previous debug builds. It installs
alongside the production app.

This build replaces the earlier fixed-host revision with the documented
`/api/v2/cdn` server discovery and exact API media paths. It adds bounded failover
among advertised servers, HTTP 429 backoff and official ZIP archive downloads.
The loading/retry controls and modernized UI from the earlier updates remain.

Public reading needs no API key according to the supplied docs. Gallery archive
downloads require a valid API key in Settings and must be enabled by the server.

Both Android variants pass 50 tests each (100 total); lint reports zero errors. APK signing identities
are checked against the previous builds. Verify file checksums with
`sha256sum -c SHA256SUMS`.

Live requests remain blocked by the cloud proxy. The API contract was reviewed
against the official documentation supplied by the user; reading on the phone
still needs confirmation. See [technical details](../docs/reader-fixes.md).
