# Changelog

All notable changes to **WhatFiles?** are documented here. Each version is also
published as a GitHub Release with the debug and release APKs attached.

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
