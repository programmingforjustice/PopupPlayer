# PopupPlayer Complete UI Patch

이 패치는 지금까지 시안에서 결정한 변경사항 전체를 적용합니다.

## 포함 내용
- 현재 앱바 디자인 적용
- 앱바 아이콘 thin line 스타일/vertical center 정렬
- 앱바와 툴바 사이 명시적 구분선
- 툴바 버튼 50dp 축소
- 일반 툴바 아이콘 60% 크기
- Settings 아이콘 74% 크기 + 약간 아래 위치 보정
- Cleaner → Trash Bin
- Local Net → Settings
- 경로 표시 줄과 폴더/파일 개수 줄 사이 구분선
- RecyclerView 상단 여백
- 목록 카드형 디자인
- 디렉토리 아이콘 배경 제거
- 세로로 더 늘린 예쁜 폴더 아이콘
- 미디어 파일 썸네일 라운드 9:16 스타일
- 런타임에서 다시 세팅되는 filter/sort/layout 아이콘 리소스까지 교체

## 적용 방법

프로젝트 루트에서 실행:

```bash
unzip popupplayer_complete_ui_patch.zip -d .
python3 tools/apply_popupplayer_complete_ui_patch.py
./gradlew clean assembleDevDebug
```

중복 리소스 에러가 나면 아래 PNG가 남아있는지 확인하세요.

```bash
rm -f app/src/main/res/drawable/filter_toggle_normal.png
rm -f app/src/main/res/drawable/filter_toggle_only_media.png
```
