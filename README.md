# PopupPlayer UI XML patch

이 패치는 현재 적용된 메인화면 구조를 유지하면서 HTML 시안의 아이콘/툴바 스타일을 Android XML 리소스로 옮긴 버전입니다.

적용 파일:
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/drawable/category_icon_bg.xml`
- `app/src/main/res/drawable/category_icon_bg_primary.xml`
- `app/src/main/res/drawable/bg_filter_group.xml`
- `app/src/main/res/drawable/bg_filter_selected.xml`
- `app/src/main/res/drawable/ic_pp_*.xml`

주요 변경:
- 앱바 아이콘을 통일된 thin-line vector drawable로 교체
- 앱바 아이콘 vertical center 정렬
- 두 번째 줄 툴바 아이콘을 통일된 line style로 교체
- `Cleaner` -> `Trash Bin`
- `Local Net` -> `Settings`
- Trash Bin / Settings 아이콘 크기 보정
- 앱바/툴바 아이콘 stroke를 기존 대비 약 절반 수준으로 얇게 조정
