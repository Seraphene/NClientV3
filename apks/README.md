# Installable development APKs

- **Android 9 and newer:** [Download APK](NClientV3-4.2.7-reader-fix-android9plus.apk)
- **Android 8 / 8.1:** [Download APK](NClientV3-4.2.7-reader-fix-android8.apk)

On GitHub, open the APK and choose **Download raw file**. Open the downloaded
file on your phone to install it. Build **420704 / 4.2.7-debug4** installs over
NClientV3 Debug and uses the same signing key as the previous builds. It keeps
that app’s settings. It installs alongside the production app; do not uninstall
the production app to try it.

## Image host correction

For gallery 688486, the user confirmed that
`https://i1.nhentai.net/galleries/4247333/1.webp` fails in the browser while
`https://i.nhentai.net/galleries/4247333/1.webp` loads. The image path and file
extension are identical.

This build uses `i.nhentai.net` and `t.nhentai.net` for relative API media paths,
repairs saved legacy i1/t1 URLs, and adds bounded connection failover among the
known public image hosts. Absolute URLs supplied by the API remain authoritative.
The previous reader UI, finite timeouts and request-prioritization changes remain.

Both APK variants pass 33 regression tests each. APK signatures and checksums
are verified. See [technical details](../docs/reader-fixes.md).

The official API documentation request is blocked by the cloud proxy before it
reaches nhentai. The working image URL is user-verified; the full current API
contract and this APK’s reading behavior on the phone are not yet verified.

Verify downloaded files with `sha256sum -c SHA256SUMS`.
