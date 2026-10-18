# Reader reliability, API compatibility and UI update

## API contract

This revision follows the official nhentai API documentation supplied by the user
for **2.0.0+994d126**, including the response from
[GET /api/v2/cdn](https://nhentai.net/api/v2/docs#/cdn/get_cdn_config_api_v2_cdn_get).

- Public gallery listing uses `result`, `num_pages`, `per_page` and `total`.
  List cards read `english_title`, `japanese_title`, `thumbnail` and `tag_ids`.
- Gallery details use `title`, `cover`, `thumbnail`, `pages` and full `tags`.
  The existing `include=related,favorite` parameter is documented. Cover and
  thumbnail objects contain `path`, `width` and `height`.
- Relative media paths are retained verbatim. A public `/api/v2/cdn` request
  obtains `image_servers` and `thumb_servers`. Reader pages use the image pool;
  covers and thumbnails use the thumbnail pool. There are no hardcoded CDN
  subdomains, guessed filename extensions, suffixes or page numbering in
  network requests. Explicit absolute HTTPS media URLs remain authoritative.
- CDN discovery is shared across concurrent image requests and cached for ten
  minutes. A failed discovery has a 15-second cooldown. Successful media servers
  are preferred for subsequent requests. Switching the configured site clears
  the old server selection.
- Connection/response-header failures and HTTP 502/503/504 can try the next
  advertised server, within a finite call deadline. HTTP 400/401/403/404/429 do
  not trigger server rotation. A partially downloaded response body is not
  automatically restarted. No unadvertised host is used as a fallback.
- API and media HTTP 429 responses create a shared service cooldown using
  `Retry-After` (seconds or HTTP date), or 30 seconds when it is absent/invalid.
  Requests during that interval fail locally without contacting another server.
- Third-party authentication remains `Authorization: Key …`; the app does not
  use first-party login or API-key-management endpoints. API requests identify
  `NClient/version` and the project URL. Public CDN discovery and media requests
  use a separate transport without API authorization or login cookies.

The earlier fixed-host build **420704** is superseded. The user's browser test
showed a reachability difference between `i1.nhentai.net` and `i.nhentai.net` for
one image; the official documentation establishes that the advertised pool,
which currently includes i1–i4, must determine server selection.

## Reader and saved metadata

The success-gated image queue has been removed: one failed image no longer
prevents later panels from loading. Glide handles concurrency, cancels reused
views, and uses the app's dedicated media client rather than its default
10-second transport. API requests have 15/30/45-second connect/read/call limits;
media has 15/45/75-second limits, with five-second connect attempts when rotating
advertised servers. TLS and certificate checks retain their defaults.

Selected pages start before their neighbors. Only two neighboring pages on each
side start prefetching, and an in-flight request is not restarted when its page
is selected again. Broken local pages fall back to their remote API path.

Saved site media URLs are rebound to the discovered pool without altering their
path/query. Older filename-only metadata is refreshed through the gallery API
rather than reconstructing media routes. New saved metadata retains opaque API
paths through internal transport models; these internal addresses never become
browser navigation targets. “Open in browser” resolves an actual advertised URL
in the background. Connection details hide query parameters.

## Downloads

Bulk downloads now request an archive with
`POST /api/v2/galleries/{id}/download?format=zip`, with an empty body. This requires
a valid API key. The documented `url` and `expires_at` response is validated and
the short-lived HTTPS URL is fetched before expiry. The key is not forwarded to
the archive server. HTTP 503 explains that downloads are disabled; HTTP 429
honors backoff. There is no page-by-page CDN download loop.

Archive transfers have a separate ten-minute call limit and a 60-second read
limit. Pause/cancel stops the active request. Selected page ranges are extracted
locally from the archive; this still transfers the complete archive. Existing
valid pages are retained on resume. ZIP entries are validated, page writes use
temporary files, CRC failures do not publish partial files, and missing or
unreadable pages pause the download. Paths cannot escape the gallery folder.
Archive entries are matched to exact API basenames or numbered local pages;
unknown archive layouts produce an explicit failure instead of guessing network
URLs. Failure text is available in the download list and notification.

## UI

Browse/gallery cards, light and dark colors, list spacing and reader controls
have been refreshed. Browse change animations are disabled to prevent flicker.
Read/Resume opens the saved page directly. The reader has a scrollable state
card, an eight-second slow-server notice, and visible Retry, Open in browser and
Connection details actions. Reader chrome has better contrast and the immersive
page badge draws above the pager. Download cards have 48dp controls, separate
error text with a details dialog, and readable icons over image previews. Rate-limit and archive messages are included
for all thirteen existing app locales.

## Validation and limits

Run with JDK 17+ and Android SDK 36:

```sh
./gradlew assembleDebug :app:testPost28DebugUnitTest :app:testPre28DebugUnitTest lintPost28Debug --max-workers=4
```

Both APK variants pass **50 tests each (100 total)**, with no failures or skipped
tests. Android lint reports **zero errors** (63 warnings). Regression coverage
includes opaque paths/query encoding, changed CDN pools,
shared/cached discovery, advertised-server failover, credential stripping,
cancellation, status codes that must not retry, rate-limit deadlines, archive
expiry, ZIP extraction and interruption, API error responses and reader failure
diagnostics. Real loopback HTTP tests include an eleven-second image response
that exceeds the old ten-second read deadline and a short-timeout control that
must fail.

Live requests from this cloud environment were rejected by its proxy before
reaching nhentai. Contract review uses the official documentation/JSON supplied
by the user; automated transport and archive tests use controlled fixtures.
Successful reading on the Android 12 phone and authenticated live archive
retrieval are still device checks, not claims made by these tests.

## APKs

Build **420705 / 4.2.7-debug5**, package `com.maxwai.nclientv3.debug`, installs as
**NClientV3 Debug**. The APKs use the same signing key as the earlier debug
builds, so they update that app and retain its settings. They install alongside
the production app. On Android 12, use the Android 9+ APK in [apks](../apks/).
