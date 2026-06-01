# PopupPlayer final UI application notes

GitHub direct write returned 403 in ChatGPT, so apply these file replacements and make these code edits.

## Replace files
Copy the included files into the project root:

- `app/src/main/res/layout/item_folder.xml`
- `app/src/main/res/layout/item_file_entry.xml`
- `app/src/main/res/drawable/bg_folder_item.xml`
- `app/src/main/res/drawable/bg_media_thumb.xml`
- `app/src/main/res/drawable/ic_folder_default.xml`
- `app/src/main/res/drawable/category_icon_bg.xml`
- `app/src/main/res/drawable/category_icon_bg_primary.xml`

## MainActivity.kt edits

In `applyLayoutMode()`, list mode must keep RecyclerView top padding, not reset it to 0.

Change this block:

```kotlin
rvFolders.setPadding(0, 0, 0, 0)
rvFolders.clipToPadding = true
toggleBtn.setImageResource(R.drawable.ic_layout_grid)
```

to:

```kotlin
val topPx = (14 * resources.displayMetrics.density).toInt()
rvFolders.setPadding(0, topPx, 0, 0)
rvFolders.clipToPadding = false
toggleBtn.setImageResource(R.drawable.ic_layout_grid)
```

Also in grid mode, preserve a top padding:

```kotlin
rvFolders.setPadding(outerPx, topPx, outerPx, 0)
```

where `topPx` is defined before the `if (isGridLayout)` branch.

## FileListAdapter.kt edits

In `ListDirVH` bind block, currently directory entries use the same thumbnail background as media. Update it so directory rows show the tall folder icon without the media thumbnail background.

Replace:

```kotlin
holder.ivThumb.setImageResource(R.drawable.ic_folder_default)
holder.ivThumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
holder.ivThumb.setBackgroundColor(android.graphics.Color.WHITE)
```

with:

```kotlin
holder.ivThumb.setImageResource(R.drawable.ic_folder_default)
holder.ivThumb.scaleType = ImageView.ScaleType.FIT_CENTER
holder.ivThumb.setBackgroundColor(android.graphics.Color.TRANSPARENT)
```

For non-directory media rows, keep Glide thumbnails. They will now render inside a 64dp x 46dp rounded 9:16 thumbnail container via `item_file_entry.xml`.

## Toolbar/Appbar final details

If not already applied:
- toolbar icon frame should be 50dp, not 58dp
- toolbar icon padding should make regular icons about 60%
- settings icon can remain slightly larger visually if needed
- appbar/toolbar divider should be visible with `#DDE3EA`
- path row/filter row divider should be visible with `#DDE3EA`

## Build
Remove duplicate PNG resources if vector XMLs with the same names exist:

```bash
rm -f app/src/main/res/drawable/filter_toggle_normal.png
rm -f app/src/main/res/drawable/filter_toggle_only_media.png
./gradlew clean assembleDevDebug
```
