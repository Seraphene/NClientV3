# Authenticated archive downloads — build 420712 / 4.2.7-debug12

Downloads use the documented archive API and require a validated API key in
Settings. There is no sequential page-fetching or cache-all download alternative.

## Request flow

1. The app sends `POST https://nhentai.net/api/v2/galleries/{id}/download?format=zip`
   with `Authorization: Key <saved key>`. Format is a query parameter; the request
   body is empty, as permitted by the supplied API documentation.
2. It validates the returned HTTPS URL and `expires_at` timestamp.
3. It fetches the exact returned URL, preserving its signed query and the User-Agent
   actually used by the issuing API request.
4. Verification cookies are shared only with the official HTTPS site. The API key
   is attached only if a request or redirect itself targets the official HTTPS
   origin's `/api/v2/` path. External archive/CDN hosts never receive the key or
   site cookies. Website account/session cookies are not imported into this client.
5. The response must be a ZIP before extraction begins. Existing archive size,
   path safety, image-validation and cancellation protections still apply.

The previous implementation fetched archives through the image client. That client
could use a different User-Agent from the API's verified session and did not carry
site-verification cookies or API authorization for an archive URL hosted inside
the official API. The archive transport now handles those cases explicitly. These
were client inconsistencies; no claim is made that the live provider necessarily
binds every signed URL to User-Agent or requires verification on every archive host.

## Understanding failures

- **Archive-link API request failed (HTTP 403):** the API refused to issue a link.
  Check key permissions and the site's Connection check. A 403 alone does not prove
  that the key is invalid. A 401 from the API does mark it invalid.
- **Archive file request failed (HTTP 403):** the API issued a link, but fetching
  the file was refused. Known expiry responses and explicit Cloudflare challenge
  headers get more specific explanations. The key remains valid.
- **HTTP 429:** wait before resuming; the existing `Retry-After` backoff applies.
- **Link API HTTP 503:** archive downloads are unavailable on the server.

Resuming requests a fresh archive link; it does not reuse an expired signed URL.
There is no automatic retry loop for access denials. Errors do not expose raw
response bodies, signed URLs or API keys. HTTPS certificate validation stays on;
HTTPS-to-HTTP redirects are not followed.

After installing, validate the key under **Settings → API key** if needed. Complete
the existing **Connection** check if site verification is requested. In Downloads,
resume the paused entry; its error text now identifies which stage was refused.

## Verification limits

Both APK variants build and pass all 115 regression tests each (230 total).
APK signatures match the preceding repository-published debug builds. Checks of
the packaged code confirm the archive helpers are present and the canceled
sequential-saving implementation is absent. Lint still reports six pre-existing
missing-translation errors in the separate image-feed labels and 86 warnings;
no new lint errors were introduced by this archive update.

Local tests use a fake API key, generated non-explicit page images, real HTTPS
fixture servers and signed-link responses. They check key scope, verification
cookie scope, redirect credential stripping, User-Agent consistency, exact signed
queries, successful extraction, expiry, denials and fresh-link resume behavior.
One fixture intentionally rejects the old image-client identity with 403 and
accepts the corrected archive-client identity.

The cloud proxy rejects connections to nhentai before they reach the site, and the
user's private key is not available in this workspace. Consequently this is not a
live authenticated download verification or a physical-phone test. Provider-side
permissions, disabled downloads or verification requirements can still cause a
403; the improved diagnostics identify which request was refused without guessing
that every 403 means an invalid key.
