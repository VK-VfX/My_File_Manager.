# Changelog

All notable changes to **WhatFiles?** are documented here. Each version is also
published as a GitHub Release with the debug and release APKs attached.

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
