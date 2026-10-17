# Reader reliability and UI update

This update removes the success-gated image queue: one failed request no longer
prevents the remaining gallery panels from loading. Glide manages concurrency
and cancels requests when views are reused. Gallery panels have loading and
retry states; the reader falls back from a broken downloaded file to its remote
URL. Rotation now applies after a page has loaded, and reader requests are
cleared when their view is destroyed.

API v2 remains the data source. Absolute HTTPS image URLs and protocol-relative
CDN URLs are preserved rather than prefixed with a guessed host. Existing
relative paths still use the original image/thumbnail hosts. Saved metadata now
retains complete URLs, while existing stored paths remain readable. HTTP errors,
HTML responses and empty bodies are rejected before JSON parsing; HTTP 403 no
longer marks a saved API key invalid. Credentials are never sent to image CDNs.

Cards, light/dark colors and reader controls have been refreshed. The gallery
Read/Resume action opens the saved page directly. New reader strings are
included for every existing app locale.

## Validation

With a JDK and Android SDK 36 configured, run:

```sh
./gradlew assembleDebug bundleDebug :app:testPost28DebugUnitTest :app:testPre28DebugUnitTest lintPost28Debug --max-workers=4
```

There are 15 regression tests per flavor, covering media URL resolution and
API transport failures. CI runs both debug test variants and builds both APK
flavors. Lint also reports existing dependency/accessibility warnings.

Live API documentation and gallery/image requests were denied by the cloud
network proxy before reaching nhentai.net. Therefore no claim is made that the
website's current response schema, CDN host, or authentication contract was
verified, or that reading was tested on a device. The existing API migration
commit documents a March 2026 move from HTML scraping to /api/v2/; this remains
repository evidence rather than independent confirmation of current behavior.

## Install

Use the post28 debug APK on Android 9 or later, or the pre28 debug APK on
Android 8/8.1. These are signed development builds named **NClientV3 Debug**,
with package com.maxwai.nclientv3.debug and version code 420702. They install
alongside the production app and require separate login/settings. Release
signing credentials are not included. Do not uninstall the production app to
try the debug build.

## Follow-up: gallery 688486

The first build did not resolve the user’s image-loading failure on Android 12.
The gallery works in their browser on the same connection. Version 420702 adds
non-sensitive image-failure diagnostics and a browser action using the exact
requested image URL. It does not claim a confirmed fix for that remaining
failure. Diagnostic tests cover HTTP status, DNS, timeouts and absent causes.
A similar upstream report is https://github.com/maxwai/NClientV3/issues/264;
its DNS workaround is external evidence, not a diagnosis of this user’s device.
