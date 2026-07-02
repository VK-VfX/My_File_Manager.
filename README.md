# Nimbus Files

A Material 3 Android file manager: browse/manage files, analyze and clean up
storage, play videos and music, and connect to Google Drive / Google Photos.

Package: `com.vfxsal.filemanager` · minSdk 26 · targetSdk 35 · Kotlin + Jetpack Compose.

## Features

- **Files** — browse storage, category shortcuts (images/videos/audio/docs/
  APKs/archives), copy/move/delete/rename/share, multi-select, search.
- **Clean** — storage breakdown by category, junk file scanner, large-file
  finder, duplicate finder (SHA-256), all with confirm-before-delete.
- **Video** — folder-grouped gallery with thumbnails, full-screen Media3 player.
- **Music** — Songs/Albums/Artists library, background playback via a Media3
  `MediaSessionService` (survives backgrounding), now-playing screen.
- **Cloud** — Google Drive browsing/upload/download (Drive v3, `drive.file`
  scope), and Google Photos import via the system Photo Picker (no OAuth
  needed for Photos).

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

## One-time setup: Google Drive sign-in

Google Sign-In requires an OAuth Android client registered in
[Google Cloud Console](https://console.cloud.google.com/) for this app's
package name and signing certificate, or sign-in will fail with
`DEVELOPER_ERROR` (status code 10). To enable it:

1. In Cloud Console, create/select a project and enable the **Google Drive
   API**.
2. Under **APIs & Services → Credentials**, create an **OAuth client ID** of
   type **Android**, using:
   - Package name: `com.vfxsal.filemanager` (release) and/or
     `com.vfxsal.filemanager.debug` (debug builds — see `applicationIdSuffix`
     in `app/build.gradle.kts`)
   - SHA-1 of your signing certificate (`./gradlew signingReport` prints it
     for both the debug and release keystores)
3. No code changes are needed — the classic `GoogleSignInClient` flow used
   here resolves the client automatically from the package name + SHA-1
   registered in Cloud Console.

Google Photos import needs no setup: it uses the system Photo Picker, which
surfaces the user's Google Photos library directly.

## Play Store note

The Files/Clean features request `MANAGE_EXTERNAL_STORAGE` for full
filesystem access. Google Play restricts this permission to apps whose core
function requires it (file managers qualify) and requires a policy
declaration at submission time.

## Project layout

```
app/src/main/java/com/vfxsal/filemanager/
  ui/            theme, navigation shell (bottom nav + NavHost)
  data/, util/   shared models (FileEntry, FileCategory) and utils
  service/       MusicPlaybackService (Media3 MediaSessionService)
  feature/
    files/       file browser & operations
    clean/       storage analyzer & scanners
    video/       video gallery & player
    music/       music library & player
    cloud/       Google Drive + Photos
```
