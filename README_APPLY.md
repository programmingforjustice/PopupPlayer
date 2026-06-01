# PopupPlayer appbar height patch

HistoryActivity의 앱바 높이 구조와 MainActivity 앱바 높이를 맞추는 패치입니다.

## 적용

프로젝트 루트에서:

```bash
unzip popupplayer_appbar_history_height_patch.zip -d .
python3 tools/apply_appbar_history_height.py
./gradlew clean assembleDevDebug
```

## 변경 내용

- MainActivity 앱바 `layout_height`: `76dp` → `wrap_content`
- 앱바 padding: `top=10dp`, `bottom=8dp`, `start=4dp`, `end=8dp`
- Back button: `46dp` → `44dp`
- Title height: `match_parent` → `wrap_content`

예상 앱바 높이: `44dp + 10dp + 8dp = 62dp`
