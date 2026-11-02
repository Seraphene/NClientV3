# Installable development APKs

- **Android 9 and newer (including Android 12):** [Download the startup and navigation fix](NClientV3-4.2.7-navigation-fix-android9plus.apk)
- **Android 8 / 8.1:** [Download the startup and navigation fix](NClientV3-4.2.7-navigation-fix-android8.apk)

On GitHub, open the APK and choose **Download raw file**, then open the file on
your phone. Build **420711 / 4.2.7-debug11** updates **NClientV3 Debug**, retains its
settings, reading progress and API key, and uses the same signing key as the
previous debug build. It installs alongside the production app.

This update fixes a crash rendering saved stories, makes Downloads refresh and
search consistent, and moves **Read downloaded stories / Browse online** to the
top-right toolbar. Pagination now stays above bottom navigation when the header
scrolls. See [fixes and verification](../docs/navigation-cleanup.md).

History search and progress filters remain available, along with the remembered
startup library. See [library tools](../docs/library-tools.md).

The previous update added a **Details** button to History and **About this gallery** to
the reader menu, opening the artist and tags without losing your reading position.
See [navigation details](../docs/history-details.md).

The previous update added a **History** tab with automatic page saving and one-tap resume,
a **512 MiB image cache** with offline reuse, and seven themes: **Warm, Midnight,
Forest, Amethyst, Paper, OLED Black and Glass**. The six accent colors remain
independently selectable. Choose a theme and accent in Settings.
See [feature details, rendered previews and validation](../docs/reading-update.md).

Connection recovery and the spacious UI from the previous builds remain in place.
See [connection details](../docs/connection-update.md) and [UI details](../docs/premium-ui.md).

The working reader changes from build 420705 remain: documented `/api/v2/cdn`
discovery, exact API media paths, bounded failover among advertised servers, HTTP
429 backoff and official ZIP archive downloads.
Public reading needs no API key according to the supplied docs. Gallery archive
downloads require a valid API key in Settings and must be enabled by the server.

See the navigation-cleanup notes for validation and test scope. Tests use Android
simulation; no physical-device test is claimed.
The new APK signing identities match the previous build. Verify all retained APK
checksums with `sha256sum -c SHA256SUMS` from this directory. APKs produced separately
by GitHub Actions can have a different debug certificate, preventing an in-place
update; this is separate from a launch crash. Do not uninstall your current app
without preserving its data.

Earlier `reader-fix`, `premium-ui` and `connection-update` APKs are retained for reference. They have a lower version
code, so Android will not install them over this update normally. See
[API implementation details](../docs/reader-fixes.md).
