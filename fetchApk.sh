#!/bin/expect

# 명령어 경로 설정 (필요 시 수정)
set env(PATH) "/usr/local/bin:/usr/bin:/bin:$env(PATH)"

# 현재 스크립트 파일 이름 (자기 자신)
set script_name [file tail $argv0]

# 인자 개수 확인
if { $argc != 1 } {
    puts "사용법: ./$script_name [dev|prod]"
    exit 1
}

# 첫 번째 인자 가져오기
set mode [lindex $argv 0]
set timeout -1

# 분기 처리
if { $mode == "dev" } {
    spawn gh cs cp -e remote:/workspaces/PopupPlayer/app/build/outputs/apk/dev/release/app-dev-release.apk /root/storage/shared/Apks
    #interact
} elseif { $mode == "prod" } {
    spawn gh cs cp -e remote:/workspaces/PopupPlayer/app/build/outputs/apk/prod/release/app-prod-release.apk /root/storage/shared/Apks
    #interact
} else {
    puts "오류: dev 또는 prod만 입력하세요."
    puts "사용법: ./$script_name [dev|prod]"
    exit 1
}

# 엔터 입력 자동화
expect {
    ">" { send "\r"; exp_continue }
    eof
}

#spawn cp /root/PopupPlayer/app-release.apk /root/storage/shared/Apks/app-release-refactoring.apk
#expect eof
