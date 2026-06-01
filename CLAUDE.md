# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release builds (uses signing config in app/build.gradle)
./build.sh dev    # assembleDevRelease — dev flavor, applicationId nl.blauw.pipplayer.dev
./build.sh prod   # assembleProdRelease — prod flavor, applicationId nl.blauw.pipplayer

# Install to connected device
./gradlew installDevDebug
./gradlew installProdDebug

# Clean
./gradlew clean
```

There are no automated tests in this project.

## Architecture Overview

PopupPlayer is an Android app (minSdk 26, targetSdk 35, Kotlin) that plays video and image files in floating overlay windows on top of other apps.

### Entry Point & Activities

- **`MainActivity`** — the launcher activity. File browser with two views: root folder list (MediaStore bucket-based) and directory file explorer. Uses `FolderAdapter` for root buckets and `FileListAdapter` for directory contents. Supports sort, filter (all/video/image), grid/list toggle, multiselect, and delete/trash operations via MediaStore API.
- **`NewMainActivity`** — legacy alternative main with a NavigationDrawer. Not the launcher; kept for reference.
- **`PlaylistActivity` / `PlaylistDetailActivity`** — playlist management UI backed by Room.
- **`RecycleBinActivity`** — shows MediaStore trashed items (Android 11+ only).
- **`MediaPickerActivity`** — media picker for adding items to playlists.

### Popup Player System

The core feature: floating overlay windows drawn via `WindowManager` using `TYPE_APPLICATION_OVERLAY`.

**Class hierarchy** (`PopupPlayer.kt`):
- `PopupPlayer` interface — defines `show()`, `play()`, `dispose()`, `exportCurrentFrame()`, etc.
- `BasePopupPlayer` — holds `WindowManager`, `layoutParams`, and the root view reference.
- `VideoPopupPlayer` — ExoPlayer-based player; switches to `ImageView` of the current frame when playback pauses, then back to player on tap.
- `ImagePopupPlayer` — displays static images or GIFs with tap-to-toggle controls.
- `AdaptivePopupPlayer` — wraps a `BasicVideoPopupPlayer`; automatically swaps to `ImagePopupPlayer` on pause and back on tap. This is the type created by default.
- `BasicVideoPopupPlayer` — simpler ExoPlayer popup used internally by `AdaptivePopupPlayer`.

**`PopupPlayerManager`** (singleton) — owns all active `PopupPlayer` instances. Maintains two lists: `playerList` (all players) and `orderList` (z-order for overlay stacking). Handles save/restore of the playlist to `play_list.txt` as JSON.

**`PlayerService`** (foreground service) — receives intents to start players or save/restore playlists. Calls `PopupPlayerManager.create(url).show().play()`. Must be started as a foreground service due to overlay requirements.

**`FloatingButtonService`** — separate foreground service that adds a draggable FAB overlay (currently a stub).

### Media Scanning

`MainActivity` uses two `Flow`-based scanning functions running on `Dispatchers.IO`:
- `scanMediaFoldersFlow()` — single MediaStore query aggregating all media into folder buckets with thumbnail path and video count.
- `scanDirectoryFlow()` — emits at most twice (partial at 50 files, then final) to minimize DiffUtil calls on the main thread. Uses `BUCKET_ID` for fast queries when entering a folder from the root, falls back to `DATA LIKE` for subdirectories without a bucket ID.

### Playlist / Database

Room database (`pip_player.db`) with entities `Playlist` and `PlaylistItem`. `PlaylistRepository` provides coroutine-based access. `AppDatabase` uses `fallbackToDestructiveMigration` — schema changes during development will drop and recreate the database.

### Key Dependencies

- **ExoPlayer 2.18** (`com.google.android.exoplayer:exoplayer`) + Jellyfin FFmpeg extension for extended codec support.
- **Glide 4.14** + `zjupure:webpdecoder` for animated WebP support.
- **Room 2.6.1** with KSP for annotation processing.
- **ViewBinding** enabled; use `ActivityXxxBinding.inflate()` pattern.

### Permissions Required at Runtime

1. `SYSTEM_ALERT_WINDOW` — requested via Settings intent before anything else loads.
2. `READ_MEDIA_VIDEO` + `READ_MEDIA_IMAGES` (Android 13+) or `READ_EXTERNAL_STORAGE` (older).

### Product Flavors

| Flavor | applicationId | Suffix |
|--------|--------------|--------|
| `dev`  | `nl.blauw.pipplayer.dev` | `-dev` |
| `prod` | `nl.blauw.pipplayer` | — |

The signing keystore (`release-key.jks`) is checked into `app/`. Both flavors share the same signing config for release builds.
