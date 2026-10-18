# Installable development APKs

- **Android 9 and newer (including Android 12):** [Download the new UI APK](NClientV3-4.2.7-premium-ui-android9plus.apk)
- **Android 8 / 8.1:** [Download the new UI APK](NClientV3-4.2.7-premium-ui-android8.apk)

On GitHub, open the APK and choose **Download raw file**, then open the file on
your phone. Build **420706 / 4.2.7-debug6** updates **NClientV3 Debug**, retains its
settings, reading progress and API key, and uses the same signing key as the
previous debug build. It installs alongside the production app.

This build introduces a new warm dark UI with spacious layouts, bottom navigation,
separate cover captions, a gallery header with Read/Resume controls, floating
reader controls and redesigned settings. Under **Settings → Accent color**, choose
**Gold, Jade, Ocean, Amethyst, Rose or Copper**. Light and true-black themes remain
available. See [UI details and rendered previews](../docs/premium-ui.md).

The working reader changes from build 420705 remain: documented `/api/v2/cdn`
discovery, exact API media paths, bounded failover among advertised servers, HTTP
429 backoff and official ZIP archive downloads. The user confirmed reading works.
Public reading needs no API key according to the supplied docs. Gallery archive
downloads require a valid API key in Settings and must be enabled by the server.

Both Android variants pass 63 tests each (126 total); lint reports zero errors.
The new APK signing identities match the previous build. Verify all retained APK
checksums with `sha256sum -c SHA256SUMS` from this directory.

Earlier `reader-fix` APKs are retained for reference. They have a lower version
code, so Android will not install them over this update normally. See
[API implementation details](../docs/reader-fixes.md).
