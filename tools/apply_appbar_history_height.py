#!/usr/bin/env python3
from pathlib import Path
import re

path = Path("app/src/main/res/layout/activity_main.xml")
if not path.exists():
    raise SystemExit("activity_main.xml not found. Run this script from the project root.")

s = path.read_text(encoding="utf-8")

# Match HistoryActivity appbar height model:
# wrap_content + top 10dp + bottom 8dp + 44dp icons = about 62dp.
s = s.replace(
    'android:layout_height="76dp"\n'
    '            android:orientation="horizontal"\n'
    '            android:gravity="center_vertical"\n'
    '            android:background="#FFFFFF"\n'
    '            android:paddingStart="14dp"\n'
    '            android:paddingEnd="14dp">',
    'android:layout_height="wrap_content"\n'
    '            android:orientation="horizontal"\n'
    '            android:gravity="center_vertical"\n'
    '            android:background="#FFFFFF"\n'
    '            android:paddingStart="4dp"\n'
    '            android:paddingEnd="8dp"\n'
    '            android:paddingTop="10dp"\n'
    '            android:paddingBottom="8dp">',
    1
)

s = s.replace(
    'android:id="@+id/btnBack"\n'
    '                android:layout_width="46dp"\n'
    '                android:layout_height="46dp"\n'
    '                android:layout_marginEnd="8dp"\n'
    '                android:src="@drawable/ic_pp_back"\n'
    '                android:tint="#65717C"\n'
    '                android:padding="9dp"',
    'android:id="@+id/btnBack"\n'
    '                android:layout_width="44dp"\n'
    '                android:layout_height="44dp"\n'
    '                android:layout_marginEnd="8dp"\n'
    '                android:src="@drawable/ic_pp_back"\n'
    '                android:tint="#65717C"\n'
    '                android:padding="8dp"',
    1
)

s = s.replace(
    'android:id="@+id/tvTitle"\n'
    '                android:layout_width="0dp"\n'
    '                android:layout_height="match_parent"',
    'android:id="@+id/tvTitle"\n'
    '                android:layout_width="0dp"\n'
    '                android:layout_height="wrap_content"',
    1
)

path.write_text(s, encoding="utf-8")
print("Updated activity_main.xml appbar height to match HistoryActivity.")
