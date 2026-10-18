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

There are 21 regression tests per flavor, covering media URL resolution and
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
with package com.maxwai.nclientv3.debug and version code 420703. They install
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

## Timeout handling update (420703 / 4.2.7-debug3)

The phone reported `Image request timed out`. APK manifest and library bytecode
inspection confirmed that media used Glide’s default OkHttp client (10-second
read deadline), independently of the API client. The app now explicitly registers
ReaderGlideModule; the generated module excludes the default integration.

- API: 15-second connect, 30-second read, 45-second overall call limit.
- Images: 15-second connect, 45-second read, 75-second overall call limit.
- Glide media dispatch: at most three asynchronous requests to one host, six overall.
  Saved-page downloads use the same dedicated media transport.
- Known site media requests include the site Referer and the app User-Agent.
  External image hosts receive neither site-specific header; API authorization
  and login cookies are not included in the dedicated media client.
- Selected pages start before nearby pages. At most two neighboring pages on
  each side start requests, including when an older saved view-cache preference
  retains more fragments. Running requests are not canceled/restarted merely
  because the same page is selected again. The default retained-page limit is two.
- TLS and certificate validation retain OkHttp defaults. No alternate DNS,
  speculative CDN hostname or credentials have been introduced.

The reader uses a scrollable state card with visible Retry, Open in browser and
Connection details actions; a slow-server notice appears after eight seconds.
Reader chrome has improved contrast and the immersive page badge now draws above
the pager. List spacing is consistent and browse item-change animations are
removed to avoid flicker while metadata refreshes.

Regression coverage includes a real local HTTP response delayed for eleven
seconds (beyond the old deadline), a short-timeout control that must fail, scoped
headers, finite request budgets, and existing API error/URL diagnostics tests.
Live API/gallery checks, including a read-only check outside the sandbox, still
received a cloud-proxy CONNECT 403 before reaching nhentai.net. This is unrelated
to the timeout observed on the phone. Current website schema/CDN behavior and
successful reading on the phone remain unverified; compare the exact failed
image URL with the browser before claiming that underlying issue is resolved.
