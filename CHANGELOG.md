# Changelog

All notable changes to **WhatFiles?** are documented here. Each version is also
published as a GitHub Release with the debug and release APKs attached.

## v4.14.0

### Changed — A proper browser, and faster huge folders
- **Visible "Go" button** in the address bar, next to the keyboard's own Go/Enter action.
- **Tap-to-select-all** in the address bar (like Chrome) so retyping a URL doesn't require
  manually clearing it first.
- **Address bar autocomplete** — suggests matching history and bookmark entries as you type,
  shown right under the address bar.
- **Pull-to-refresh** on the page itself, alongside the existing reload button.
- **"Desktop site" toggle** (via the new overflow menu) for pages that serve broken mobile
  layouts.
- **Private tabs** — a new tab that skips history and, since the browser shares a single WebView
  across tabs, has its cookies and site storage wiped the moment it's closed rather than trying to
  isolate them while open.
- **Find in page** — search the current page's text, with match count and next/previous.
- **Magnet links and `.torrent` downloads** now hand off to an installed torrent app instead of
  silently doing nothing or erroring, the same way a desktop browser would. (A full embedded
  BitTorrent client was considered, but its native library's exact current Maven coordinates
  couldn't be verified from this environment and this project can't run a local Gradle build to
  catch a bad dependency before it ships - handoff to an existing client is the safe, real choice
  here.)
- **Faster folders with thousands of files** — directory listing now streams in batches instead of
  blocking on stat-ing the entire folder before showing anything, so huge folders (e.g. a camera
  roll or Downloads with thousands of items) show their first screenful almost immediately instead
  of sitting on a blank loading state for the whole scan.

## v4.13.0

### Changed — Permanent delete, a revamped progress UI, and a real Chromium upgrade
- **Choose "Move to Recycle Bin" or "Delete permanently"** — every delete confirmation in the app
  (Files, category screens, image viewer, and all four Clean tools: Junk, Large Files, Duplicates,
  Similar Photos) now asks which you want, instead of always soft-deleting. This also fixes the
  dialogs' copy, which previously and incorrectly claimed deletion was immediate and unrecoverable
  even though it always went to the recycle bin.
- **Clean dashboard totals now stay accurate** — the dashboard only refreshed its numbers on
  first load or after a permission grant, so deleting junk/large/duplicate files from a sub-screen
  and returning didn't update the totals shown. It now refreshes every time you come back to it.
- **Revamped progress bar** — the shared batch-operation overlay (shown for multi-file deletes,
  emptying the recycle bin, etc.) now uses a gradient progress track and pairs with the new
  spinner instead of the plain default Material bar.
- **New loading spinner** — replaced the wavy 5-lobe "flower" animation with a rotating circle
  made of two arrow-tipped arcs, used everywhere a loading indicator appears across the app.
- **Browser: real Chromium-engine upgrades** — Android's WebView is Chromium under the hood, but
  several modern engine features weren't turned on:
  - Popups and `window.open()`/`target="_blank"` links (common in OAuth logins and "open in new
    tab" links) previously did nothing; they now open in a new tab in this browser's own tab
    strip.
  - Mixed-content compatibility mode, so more real-world HTTP/HTTPS-mixed pages load correctly.
  - Third-party cookies enabled, needed by many login and embedded-content flows.
  - Explicit Safe Browsing.
  - Algorithmic dark-mode rendering that follows the app's theme.
  - The History & bookmarks screen now shows the actual installed Chromium version powering the
    browser.

## v4.12.0

### Changed — Much faster deletes, plus a Clean refresh button
- **Deleting is dramatically faster, especially multi-select and big files** — every delete
  (Files, category screens, and the Clean tools) turned out to be doing far more work than it
  needed to:
  - Trash was stored in the app's internal storage, a different filesystem from the shared
    storage almost every file lives on. That meant every "delete" was silently falling back to a
    full byte-for-byte copy of the file plus a delete of the original, instead of an instant
    rename. Trash now lives in app-private storage on the *same* volume as the rest of shared
    storage, so moving a file there is a real, instant move for the common case.
  - Multi-select delete rewrote the entire trash manifest from scratch after *every single file*
    - selecting 50 files meant 50 full read+rewrite passes over the manifest, independent of file
    size. It's now read and written once per batch.
- **Refresh button in Clean** — the dashboard only supported pull-to-refresh before, which isn't
  discoverable on a screen with no visible top bar. There's now a refresh icon next to the "Clean"
  title.
- Minor Compose recomposition cleanup in the Clean dashboard's storage breakdown (avoids
  re-sorting the category list on every recomposition, not just when it actually changes).

## v4.11.0

### Changed — HLS downloads now produce a real, viewable mp4
- **Converts to `.mp4`** — downloaded streams are now repackaged into a standalone `.mp4` (via
  Android's built-in `MediaExtractor`/`MediaMuxer`, no re-encoding) instead of being left as a raw
  `.ts` blob this app's own Video tab didn't recognize and most players handled poorly. If a
  stream's codec genuinely can't be remuxed, the raw `.ts` is kept as a fallback instead of losing
  the download.
- **Handles fMP4/CMAF streams** — many modern HLS streams share one initialization segment
  (`#EXT-X-MAP`) across all fragments instead of each being a standalone MPEG-TS chunk; the
  downloader previously ignored this entirely, producing fragments with no codec info that
  couldn't play. It's now fetched and prepended before merging.
- **Downloads survive leaving the browser** — stream downloads previously ran on a ViewModel
  scoped to the browser screen, so closing the browser silently cancelled them mid-download. They
  now run in a process-wide manager, with live progress and cancel shown in Downloads (a new
  "streams" section there) instead of a blocking dialog stuck on the browser screen.

## v4.10.0

### Changed — Browser can now actually download HLS streams
- **Real `.m3u8` downloads** — the browser detected HLS streams before, but tapping "Download" on
  one just saved the raw manifest text file, not a video. It now fetches every segment, decrypts
  AES-128-encrypted ones, and merges them into a single playable `.ts` file in Downloads - with a
  progress dialog (segments downloaded / total) and the ability to cancel mid-download.
- Master playlists (the kind that list several resolutions) are handled automatically by picking
  the highest-bandwidth variant.
- Segments download with bounded concurrency and per-segment retries; streams using an encryption
  scheme we can't decrypt (anything other than AES-128) now fail with a clear error instead of
  silently producing a broken file.

## v4.9.0

### Changed — Browser overhaul
- **Multiple tabs** — the browser now supports real multi-tab browsing: a tab strip above the
  address bar to switch between open tabs, a "+" button to open new ones, and per-tab close
  buttons. Each tab keeps its own navigation history, so switching back to one picks up right
  where you left it.
- **Bigger, clearer controls** — the address bar now gets a full-width row of its own instead of
  being squeezed alongside six icons, and navigation (back/forward/reload/history/media/downloads)
  moved to a full-size bottom bar within thumb reach. Tabs and touch targets are sized for a
  thumb, not a mouse pointer.
- **History and bookmarks are reachable again** — previously they only lived on a blank start
  page the browser no longer shows on open, which made them effectively dead. They're now one tap
  away from the bottom bar at any time, with per-page bookmarking and a clear-history action.
- **Better media detection** — `<video>`/`<audio>` elements and their Open Graph/Twitter player
  tags are now recognized as video/audio directly from the markup, not just by guessing from the
  URL's file extension - this catches mp4s and other media served from signed, extension-less CDN
  URLs that were previously missed.
- **File size and format shown before downloading** — the "media on this page" list now shows
  each file's format up front and probes its size with a lightweight HEAD request, so you know
  what you're about to download.

## v4.8.0

### Changed
- **Streamlined bottom navigation** — with six tabs, long labels like "Wallpapers" no longer
  wrap to two lines on devices with wider default fonts (e.g. Pixel); labels are now forced to a
  single line so the bar looks consistent everywhere.
- **Faster data scanning** — the storage index is now warmed in the background at app startup,
  and the scan skips large, mostly-inaccessible trees (`Android/data`, `Android/obb`) and hidden
  caches, so the first screen loads noticeably quicker on big libraries.
- **In-app changelog** — the updater now shows the full, scrollable "What's new" notes for the
  available version, and releases are published with the curated changelog as their notes.
- **Smarter browser media capture** — the in-app browser now also detects HLS/DASH streams
  (`.m3u8`/`.mpd`), more video/audio formats, and pulls candidate media from `<meta>` player tags
  and media links on the page, not just `<video>`/`<audio>` elements and network requests.
  (HLS/DASH are flagged as "Stream" since their segments still need assembling; live and
  DRM-protected content can't be captured.)

## v4.7.0

### Added — Advanced Productivity Tools
A brand-new **Tools** tab in the bottom navigation collecting three power features:

- **Dual-Pane Manager** — two independent directory panes side by side. Long-press a file and
  drag it onto the other pane to copy it, or use the per-pane action bar to copy, move, delete,
  compress or extract the current selection.
- **Archive Manager** — compress files and folders into **ZIP or 7z** and extract archives
  (zip/7z/jar/apk) directly in the app. 7z is powered by Apache Commons Compress + XZ; ZIP still
  uses the built-in java.util.zip path. Both compress and extract are zip-slip protected.
- **Quick Viewer** — a lightweight previewer that opens any file (via the Storage Access
  Framework, no permission needed) and reads text, shows images, and plays audio/video inline,
  handing off to another app only when a format can't be previewed.

### Changed
- Unit tests now cover the new `ArchiveOps` ZIP and 7z round-trips.

## v4.6.0

### Added
- **In-app browser bookmarks & history** — a start page shows saved bookmarks and
  recent history as tappable rows; a star button in the address bar toggles the
  bookmark for the current page.
- **Download retry** — failed downloads now show a retry button that re-enqueues the
  original URL.
- **Alan Wake–inspired AMOLED wallpapers** — new light-beam, pine-forest, neon-sign and
  dark-vortex styles on pure-black backgrounds.

### Changed
- **Aurora palette** — refreshed the brand to an electric azure primary with a warm rose
  accent, keeping the signature pure-black AMOLED dark surfaces.
- **Sharper wallpapers** — applied and saved wallpapers now render up to 4K (short side
  ≥ 1440 px), and procedural detail scales with resolution so higher-res panels get more
  stars, particles and lines rather than upscaled blur.
- **Faster media categories** — Images/Videos/Audio now list via MediaStore instead of a
  full storage walk, so those screens open quickly on large libraries.

## v4.5.0
- First batch of Alan Wake 2–inspired AMOLED wallpaper designs.

## v4.4.0
- Browser media detection: sniff network requests and scan the DOM so videos and files on
  a page can be downloaded.

## v4.3.0
- Fixed the vault force-closing on open (missing biometric permission).
- Faster app startup via a stale-while-revalidate storage index.
- New in-app web browser and system-DownloadManager-backed download manager.

## v4.2.0
- Tag integrity across rename/move/delete, storage index cache, paste progress, vault
  biometric + PIN hardening, signed minified release build, and unit tests in CI.

## v4.1.0
- Labeled action bars, a global delete-progress overlay, and a redesigned Clean dashboard.

## v4.0.0
- Complete visual redesign: violet/mint brand palette, new shape system and type scale.

Earlier versions (v2.0–v3.5) added the recycle bin, global search, zip support, storage
visualizer, app manager, secure vault, timeline, OTA updates, image viewer, settings with
theme selector, and the AMOLED wallpaper gallery.
