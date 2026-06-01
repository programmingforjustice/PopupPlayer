# AGENTS.md

This file gives future coding agents the repository-specific context needed to work safely in PopupPlayer.

## Project identity

- Repository: `programmingforjustice/PopupPlayer`
- Default branch at the time this guide was written: `feature/main-activity-replacement`
- Android app package / namespace: `nl.blauw.pipplayer`
- Gradle root project name: `PipPlayer`
- App display name resource: `PipPlayer`

## Stack and build system

This is a native Android project using Kotlin/Java-style Gradle scripts.

- Root Gradle file: `build.gradle`
- Settings file: `settings.gradle`
- App module: `app/`
- Android Gradle Plugin: `8.2.2`
- Kotlin version declared at root: `2.0.20`
- KSP plugin in app module: `2.0.21-1.0.28`
- Compile SDK / target SDK: `35`
- Min SDK: `26`
- Java source/target compatibility: `JavaVersion.VERSION_21`
- Product flavors:
  - `dev`: `applicationId "nl.blauw.pipplayer.dev"`, `versionNameSuffix "-dev"`
  - `prod`: `applicationId "nl.blauw.pipplayer"`
- ABI filters: `armeabi-v7a`, `arm64-v8a`

Common commands from repository root:

```bash
./gradlew assembleDevDebug
./gradlew assembleProdDebug
./gradlew assembleProdRelease
./gradlew clean
```

When changing Room entities or KSP-related code, prefer a clean build:

```bash
./gradlew clean assembleDevDebug
```

## Important security note

`app/build.gradle` currently contains a release signing configuration with inline keystore path and passwords. Do not copy, expand, or further expose these values in new documentation, logs, issues, PR descriptions, or generated code. Future cleanup should move signing credentials into `gradle.properties`, environment variables, CI secrets, or an untracked local properties file.

## Repository layout

Expected high-level layout:

```text
.
├── build.gradle
├── settings.gradle
├── gradle.properties
├── README.md
├── AGENTS.md
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/nl/blauw/pipplayer/
        └── res/
            ├── drawable*/
            ├── layout/
            ├── menu/
            ├── mipmap*/
            └── values/
```

Use Kotlin under `app/src/main/java/nl/blauw/pipplayer/`. The package is consistently `nl.blauw.pipplayer`.

## App purpose and main flows

PopupPlayer is a local media browser and floating popup player.

Primary capabilities:

- Scan local video/image media through `MediaStore`.
- Browse root media folders and child directories.
- Switch between folder mode and all-media mode.
- Search, sort, and filter media by all/video/image.
- Toggle list/grid layouts.
- Multi-select media files.
- Start popup playback as a foreground service.
- Play playlists with next/previous, shuffle, repeat-one, and repeat-all behavior.
- Show videos/images in floating overlay windows using `SYSTEM_ALERT_WINDOW`.
- Enter fullscreen playback and restore popup playback afterward.
- Maintain playlists and play history using Room.
- Move files to Android recycle bin or delete via `MediaStore` delete/trash requests.

## Android manifest and permissions

Main manifest: `app/src/main/AndroidManifest.xml`.

Declared runtime-sensitive permissions include:

- `READ_MEDIA_IMAGES`
- `READ_MEDIA_VIDEO`
- `READ_MEDIA_AUDIO`
- `INTERNET`
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`

Registered components include:

- Launcher activity: `.MainActivity`
- Other activities: `.PlayerListActivity`, `.NewMainActivity`, `.PlaylistActivity`, `.PlaylistDetailActivity`, `.MediaPickerActivity`, `.RecycleBinActivity`, `.HistoryActivity`, `.FullscreenPlayerActivity`, `.FullscreenImageActivity`
- Services: `.PlayerService`, `.FloatingButtonService`

When changing overlay playback, foreground services, fullscreen behavior, or media permissions, verify the manifest and Android 13+ media permission behavior together.

## Key source files and responsibilities

### `MainActivity.kt`

This is the central local media browser screen.

Responsibilities:

- Requests overlay permission before storage/media permissions.
- Binds `activity_main.xml` toolbar, category row, filter bar, RecyclerView, search bar, multiselect bar, and bottom nav.
- Uses `FolderAdapter` for root folder browsing.
- Uses `FileListAdapter` for directory/all-media browsing.
- Tracks folder navigation with `ArrayDeque<Pair<File, Long>>`.
- Preserves RecyclerView scroll state by path.
- Implements sort order cycling:
  - name ascending / descending
  - date newest / oldest
  - size largest / smallest
- Supports filters: all, video, image.
- Supports list/grid mode through `GridLayoutManager` and `GridSpacingDecoration`.
- Scans root folders and directories through Flow-based `MediaStore` queries on `Dispatchers.IO`.
- Emits partial results first, then final sorted results to reduce UI blocking.
- Starts `PlayerService` with `ACTION_START_PIP_PLAYLIST` for selected media.
- Uses `MediaStore.createDeleteRequest` and `MediaStore.createTrashRequest` for deletion/recycle-bin actions on Android R+.
- Uses SAF `OpenDocumentTree` for copy/move to selected folders.

Be careful when editing this file: it is large and mixes UI binding, navigation state, MediaStore scanning, file operations, and toolbar/filter logic. Prefer extracting small helpers instead of adding more large blocks.

### `FolderAdapter.kt`

Root folder list adapter.

- Defines `FolderItem`.
- Uses `ListAdapter` + `DiffUtil`.
- Maintains `masterList` and applies name search before `submitList`.
- Currently shows a default folder icon; thumbnail loading code is present but commented.

### `FileListAdapter.kt`

Directory/all-media adapter.

- Defines `EntryType` and `FileEntry`.
- Supports list and grid item view types.
- Supports headers in grid mode.
- Supports multi-select state and callbacks.
- Uses Glide for thumbnails.
- Handles video/image/GIF display differences.
- Opens bottom sheet menu for favorite, playlist, share, rename actions.

When changing item layouts, update both list and grid ViewHolder assumptions. Keep `onViewRecycled` Glide cleanup.

### `PlayerService.kt`

Foreground service entry point for popup playback.

- Initializes `PopupPlayerManager`.
- Creates notification channel and starts foreground notification.
- Dispatches commands through `commandMap`:
  - `ACTION_START_PIP`
  - `ACTION_START_PIP_PLAYLIST`
  - `ACTION_SAVE_CURRENT_PLAYLIST`
  - `ACTION_RESTORE_CURRENT_PLAYLIST`
- Records play history through `HistoryRepository`.
- Calls `PopupPlayerManager.clear()` in `onDestroy`.

When adding commands, add a constant, map entry, and intent caller together.

### `PopupPlayerManager.kt`

Singleton coordinator for active popup players.

- Holds active `playerList` and z-order-like `orderList`.
- Creates players via `DefaultPopupPlayerFactory`.
- Starts playlists and applies navigation callbacks.
- Restores from fullscreen through `FullscreenBridge` state.
- Saves/restores playlist popup state to `play_list.txt` using JSON serialization.
- Handles z-order escalation and cleanup.

Be careful with lifecycle cleanup: stale players can leave overlay windows attached.

### `PopupPlayer.kt`

Core popup player abstractions and shared base implementation.

- Defines `PopupPlayer` interface.
- Defines `BasePopupPlayer`, including overlay `WindowManager.LayoutParams` and ghost/touch-through mode.
- Contains image popup implementation and legacy/alternate video popup logic.
- Ghost mode makes popup not touchable and shows a separate exit/opacity overlay.

When changing ghost mode, coordinate alpha, flags, overlay button, and cleanup in `removeGhostExitOverlay()`.

### `BasicVideoPopupPlayer.kt`

Primary video popup implementation used by `VideoPopupPlayerFactory`.

- Wraps ExoPlayer `Player` and custom `PlayerViewWrapper`.
- Handles popup sizing after video size is known.
- Wires close, mute, fullscreen, z-order, drag, prev/next, touch-through, shuffle, repeat-one, repeat-all controls.
- Enters fullscreen through `FullscreenPlayerActivity` and restores popup state through `FullscreenBridge`.
- Uses `PlayerWrapper` callbacks for video size, playback state, and playback-ended behavior.

### `PlayerFactory.kt`

ExoPlayer construction.

- Builds ExoPlayer with `DefaultRenderersFactory` decoder fallback and FFmpeg extension preference.
- Uses custom `DefaultLoadControl` buffering values.
- Sets movie/media audio attributes without requesting audio focus.
- Creates `ProgressiveMediaSource` from a file path or URI.

### `PopupPlayerFactory.kt`

Chooses image/video popup player implementation.

- `DefaultPopupPlayerFactory` delegates to video then image factories.
- Video support currently checks common file extensions and MIME subtypes, notably `.mp4` and `.mkv`.
- Image support checks `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.bmp`.
- Restores players from JSON layout/player state.

### Room data layer

Relevant files include:

- `AppDatabase.kt`
- `Playlist.kt`
- `PlaylistItem.kt`
- `PlaylistDao.kt`
- `PlayHistory.kt`
- `PlayHistoryDao.kt`
- `HistoryRepository.kt`
- Playlist-related activities/bottom sheets/repositories.

`AppDatabase` uses database name `pip_player.db`, version `2`, and `fallbackToDestructiveMigration()`. For production-safe schema changes, add real migrations instead of relying on destructive migration.

## Resources and UI conventions

Primary inspected resources:

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/colors.xml`
- Layouts referenced by adapters and activities, including item cards, popup controls, bottom sheets, multiselect bar, search bar, delete dialog, fullscreen screens, playlist screens, recycle/history screens.
- Drawables referenced by runtime code, including filter toggle icons, sort icons, layout toggle icons, folder/file icons, popup controls, bottom nav selectors, category backgrounds, and file/folder item backgrounds.

Current main screen visual language:

- Light background: `#F7F9FC`
- White toolbar/surfaces
- Neutral text: `#171717`, `#3E4449`, `#65717C`, `#7F8891`, `#8B949E`
- Primary/action blue in main layout: `#3F7FD8`
- Theme resources still include Material seed purple values (`#6750A4`) and other generated Material colors.

When updating UI, avoid scattering new hardcoded colors. Prefer consolidating recurring colors into `res/values/colors.xml` and using drawable selectors for state.

## Design/UX notes from the current code

- `activity_main.xml` uses a `CoordinatorLayout`, top app bar, horizontal category toolbar, breadcrumb row, filter row, empty state, `SwipeRefreshLayout`, `RecyclerView`, bottom navigation, multiselect overlay, and search overlay.
- The top toolbar icon resources are reset at runtime by `MainActivity.updateSortButton()`, `MainActivity.updateFilterToggleButton()`, and `MainActivity.applyLayoutMode()`. If changing those icons, update the drawable resources with the exact names referenced in code.
- README notes that previous UI fixes replaced runtime-referenced drawable names directly because the code sets them programmatically.
- Keep touch targets around 44dp or larger for toolbar/action buttons.
- When adding screens, prefer consistency with existing card/list/grid styling rather than introducing unrelated visual systems.

## Media and storage rules

- Use `MediaStore` for querying external media.
- Avoid long-running file scans on the main thread.
- Keep scanning, sorting, file existence checks, copy/move I/O, and Room writes off the main thread.
- On Android R+, use `MediaStore.createDeleteRequest` and `MediaStore.createTrashRequest` for user-mediated deletion/trash flows.
- Use SAF (`OpenDocumentTree`) for user-selected copy/move destinations.
- Avoid broad storage permissions unless there is a deliberate product decision.

## Overlay/window rules

- Overlay windows require `SYSTEM_ALERT_WINDOW` and `TYPE_APPLICATION_OVERLAY` on Android O+.
- Always remove overlay views with `WindowManager.removeViewImmediate` or equivalent cleanup before releasing players.
- Keep `WindowManager.LayoutParams` state consistent when moving between popup, ghost mode, and fullscreen.
- Ghost mode changes `alpha` and `FLAG_NOT_TOUCHABLE`; always restore these when disabling ghost mode.

## ExoPlayer rules

- Player creation flows through `DefaultPlayerFactory` and `PlayerWrapper`.
- Video UI creation flows through `DefaultPlayerViewFactory` and `PlayerViewWrapper`.
- Do not create bare ExoPlayer instances directly in activities unless there is a strong reason.
- Release ExoPlayer when popup players are disposed.
- Fullscreen transitions should preserve current position and playlist navigation state.

## Playlist/history rules

- Play history stores JSON arrays of media paths.
- `isNavigation` marks multi-item/navigation playback sessions.
- Playlist item insertion uses `OnConflictStrategy.IGNORE`; check duplicate behavior before changing UI messages.
- Room Flow is used for live UI updates. Prefer collecting Flows in lifecycle-aware scopes.

## Coding style

- Existing code uses Kotlin with many Korean comments. Korean comments are acceptable and already common in this project.
- Keep package `nl.blauw.pipplayer`.
- Prefer small private helper methods for new logic.
- Keep UI operations on the main thread and heavy media/file work on `Dispatchers.IO`.
- Use `lifecycleScope` in activities and a service-owned `CoroutineScope` in services.
- Use `ListAdapter`/`DiffUtil` for RecyclerView lists.
- Clean up Glide requests in recycled ViewHolders.
- Preserve existing resource names when runtime code references them.

## Testing and verification checklist

Before finishing a meaningful change, run or at least reason through:

```bash
./gradlew assembleDevDebug
```

Manual verification areas:

- First launch permission flow: overlay permission, then media permissions.
- Root folder list loads without long blocking.
- Directory navigation, breadcrumb, and back behavior.
- Search, sort, filter, list/grid toggle.
- Multi-select actions: play, playlist, share, delete/trash, copy/move.
- Popup video playback, mute, drag/resize/touch controls.
- Popup image playback, GIF/image handling.
- Ghost/touch-through mode and exit overlay.
- Fullscreen enter/exit and return to popup.
- Playlist next/previous/shuffle/repeat modes.
- History and playlist persistence after app/service restart.

## Known risks / cleanup targets

- `MainActivity.kt` is too large and mixes several responsibilities. Future refactors should extract media scanning, file operations, and UI state helpers.
- Signing credentials are inline in Gradle and should be moved out of source control.
- `fallbackToDestructiveMigration()` can destroy local Room data on schema changes; replace with explicit migrations before production use.
- Some UI strings are hardcoded in layouts/Kotlin and mixed across Korean, English, and Dutch. Future localization should move user-facing strings to `strings.xml`.
- Some share flows use `Uri.fromFile(...)`; Android file sharing usually needs a `FileProvider` content URI to avoid `FileUriExposedException` on modern Android.
- `MediaStore.Files.FileColumns.DATA` is used in queries; be careful with scoped-storage compatibility on newer Android versions.
- Several runtime resources are set programmatically; resource renames can silently break toolbar/menu icons.

## Instructions for future agents

1. Read this file first.
2. Inspect the exact files you will modify before editing.
3. Do not make broad formatting-only rewrites of large files.
4. Keep changes small and easy to review.
5. Do not introduce new secrets or expose existing signing credentials.
6. Preserve current package names, resource IDs, and runtime-referenced drawable names unless updating every caller.
7. Prefer improving structure by extraction over expanding already-large activity/player classes.
8. After code changes, report which Gradle command was run. If no build was run, say so clearly.
