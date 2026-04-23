#!/bin/bash

# 첫 번째 인자를 가져옴
MODE=$1

# 인자가 비었는지 확인
if [ -z "$MODE" ]; then
  echo "사용법: $0 [dev|prod]"
  exit 1
fi

#git pull

# 분기 처리
case "$MODE" in
  dev)
    ./gradlew clean assembleDevRelease --rerun-tasks
    ;;
  prod)
    ./gradlew clean assembleProdRelease --rerun-tasks
    ;;
  *)
    echo "오류: dev 또는 prod만 입력하세요."
    exit 1
    ;;
esac