# WhatFiles?

A Material 3 Android file manager: browse, view and edit files, analyze and
clean up storage, play videos and music, and set AMOLED wallpapers. Fully
offline — no accounts, no network permissions.

Package: `com.vfxsal.filemanager` · minSdk 26 · targetSdk 35 · Kotlin + Jetpack Compose.

## Features

- **Files** — browse storage, category shortcuts (images/videos/audio/docs/
  APKs/archives), copy/move/delete/rename/share, multi-select, search. Text
  files (code, config, markdown, logs, ...) open in a built-in editor instead
  of handing off to another app, with save and unsaved-changes protection.
- **Clean** — storage breakdown by category, junk file scanner, large-file
  finder, duplicate finder (SHA-256), all with confirm-before-delete.
- **Video** — folder-grouped gallery with thumbnails, full-screen Media3 player.
- **Music** — Songs/Albums/Artists library, background playback via a Media3
  `MediaSessionService` (survives backgrounding), now-playing screen.
- **Wallpapers** — a gallery of AMOLED (true-black) wallpapers generated
  on-device (no image assets, no network), set as Home/Lock/Both, or save to
  the gallery.

## Building

Open the project root in Android Studio (Koala/Ladybug or newer), or from the
command line:

```
./gradlew assembleDebug
```

This project was scaffolded and built out in an environment without access to
Google's Maven repository, so **it has not been compiled here** — do a build
in Android Studio first to catch any dependency-version or API-surface issues
before relying on it.

## Play Store note

The Files/Clean features request `MANAGE_EXTERNAL_STORAGE` for full
filesystem access. Google Play restricts this permission to apps whose core
function requires it (file managers qualify) and requires a policy
declaration at submission time. Not relevant if you're just sideloading this
for personal use.

## Project layout

```
app/src/main/java/com/vfxsal/filemanager/
  ui/            theme, navigation shell (bottom nav + NavHost)
  data/, util/   shared models (FileEntry, FileCategory) and utils
  service/       MusicPlaybackService (Media3 MediaSessionService)
  feature/
    files/       file browser, operations, and text editor
    clean/       storage analyzer & scanners
    video/       video gallery & player
    music/       music library & player
    wallpaper/   AMOLED wallpaper gallery & renderer
```
