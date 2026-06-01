# PopupPlayer UI fix patch v2

이 패치는 적용 후 스크린샷에서 확인된 누락 사항을 보정합니다.

수정 포인트:
1. 앱바 첫 번째/두 번째/네 번째 아이콘이 런타임에서 기존 리소스로 덮어써지는 문제 대응
   - `filter_toggle_normal.xml`
   - `filter_toggle_only_media.xml`
   - `ic_sort_*.xml`
   - `ic_layout_grid.xml`
   - `ic_layout_list.xml`
   를 thin line vector로 직접 교체했습니다.

2. 두 번째 줄 툴바 아이콘 크기 보정
   - 58dp 원형/라운드 프레임
   - 6dp padding
   - vector intrinsic size 46dp
   - 즉, 프레임 기준 약 80% 크기로 보이도록 조정했습니다.

3. RecyclerView 아이템 디자인 적용
   - `item_folder.xml` 루트 폴더 목록 카드형 디자인 적용
   - `item_file_entry.xml` 탐색 내부 파일/폴더 목록 카드형 디자인 적용
   - `bg_folder_item.xml`, `bg_folder_icon.xml` 추가

적용:
프로젝트 루트에서 압축을 풀어 덮어씌운 뒤 빌드하세요.

주의:
MainActivity.kt에서 `applyLayoutMode()`와 `updateSortButton()`이 아이콘을 런타임에 다시 설정하므로, 해당 함수들이 참조하는 기존 drawable 이름 자체를 이번 패치에서 교체했습니다.
