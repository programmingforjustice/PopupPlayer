#!/usr/bin/env python3
from pathlib import Path
import re

root = Path(".")
main = root / "app/src/main/java/nl/blauw/pipplayer/MainActivity.kt"
adapter = root / "app/src/main/java/nl/blauw/pipplayer/FileListAdapter.kt"

if main.exists():
    s = main.read_text(encoding="utf-8")

    if "private lateinit var layoutBreadcrumbDivider" not in s:
        s = s.replace(
            "private lateinit var layoutBreadcrumb: View\n",
            "private lateinit var layoutBreadcrumb: View\n    private lateinit var layoutBreadcrumbDivider: View\n"
        )

    if "layoutBreadcrumbDivider = findViewById(R.id.layoutBreadcrumbDivider)" not in s:
        s = s.replace(
            "layoutBreadcrumb = findViewById(R.id.layoutBreadcrumb)\n",
            "layoutBreadcrumb = findViewById(R.id.layoutBreadcrumb)\n        layoutBreadcrumbDivider = findViewById(R.id.layoutBreadcrumbDivider)\n"
        )

    s = re.sub(
        r"(layoutBreadcrumb\.visibility\s*=\s*View\.GONE)(?!\n\s*layoutBreadcrumbDivider)",
        r"\1\n        layoutBreadcrumbDivider.visibility = View.GONE",
        s,
        count=1
    )
    s = re.sub(
        r"(layoutBreadcrumb\.visibility\s*=\s*View\.VISIBLE)(?!\n\s*layoutBreadcrumbDivider)",
        r"\1\n        layoutBreadcrumbDivider.visibility = View.VISIBLE",
        s,
        count=1
    )

    pat = re.compile(r"private fun applyLayoutMode\(\) \{.*?\n    \}\n\n    private fun updateSortButton", re.S)
    repl = '''private fun applyLayoutMode() {
        val toggleBtn = findViewById<ImageButton>(R.id.btnLayoutToggle)
        while (rvFolders.itemDecorationCount > 0) {
            rvFolders.removeItemDecorationAt(0)
        }

        val topPx = (14 * resources.displayMetrics.density).toInt()

        if (isGridLayout) {
            val spanCount = 2
            val gridLM = androidx.recyclerview.widget.GridLayoutManager(this, spanCount)
            gridLM.spanSizeLookup = fileListAdapter.getSpanSizeLookup(spanCount)
            rvFolders.layoutManager = gridLM
            fileListAdapter.isGrid = true

            val outerPx = (12 * resources.displayMetrics.density).toInt()
            rvFolders.setPadding(outerPx, topPx, outerPx, 0)
            rvFolders.clipToPadding = false
            rvFolders.addItemDecoration(GridSpacingDecoration(spanCount, spacingDp = 10))
            toggleBtn.setImageResource(R.drawable.ic_layout_list)
        } else {
            rvFolders.layoutManager = LinearLayoutManager(this)
            fileListAdapter.isGrid = false
            rvFolders.setPadding(0, topPx, 0, 0)
            rvFolders.clipToPadding = false
            toggleBtn.setImageResource(R.drawable.ic_layout_grid)
        }
    }

    private fun updateSortButton'''
    s = pat.sub(repl, s, count=1)

    main.write_text(s, encoding="utf-8")
    print("Patched MainActivity.kt")
else:
    print("MainActivity.kt not found")

if adapter.exists():
    s = adapter.read_text(encoding="utf-8")
    s = s.replace(
        "holder.ivThumb.setImageResource(R.drawable.ic_folder_default)\n                holder.ivThumb.scaleType    = ImageView.ScaleType.CENTER_INSIDE\n                //holder.ivThumb.setBackgroundColor(0xFFE0E4EA.toInt())\n                holder.ivThumb.setBackgroundColor(android.graphics.Color.WHITE)",
        "holder.ivThumb.setImageResource(R.drawable.ic_folder_default)\n                holder.ivThumb.scaleType    = ImageView.ScaleType.FIT_CENTER\n                holder.ivThumb.setBackgroundColor(android.graphics.Color.TRANSPARENT)"
    )
    s = s.replace(
        "holder.ivThumb.setImageResource(R.drawable.ic_folder_default)\n                holder.ivThumb.scaleType    = ImageView.ScaleType.CENTER_INSIDE",
        "holder.ivThumb.setImageResource(R.drawable.ic_folder_default)\n                holder.ivThumb.scaleType    = ImageView.ScaleType.FIT_CENTER"
    )
    s = s.replace(
        "holder.ivThumb.setBackgroundColor(android.graphics.Color.WHITE)",
        "holder.ivThumb.setBackgroundColor(android.graphics.Color.TRANSPARENT)"
    )
    adapter.write_text(s, encoding="utf-8")
    print("Patched FileListAdapter.kt")
else:
    print("FileListAdapter.kt not found")

for p in [
    root / "app/src/main/res/drawable/filter_toggle_normal.png",
    root / "app/src/main/res/drawable/filter_toggle_only_media.png",
]:
    if p.exists():
        p.unlink()
        print(f"Removed duplicate {p}")
