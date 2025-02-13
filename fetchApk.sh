#!/usr/bin/expect

# 명령어 경로 설정 (필요 시 수정)
set env(PATH) "/usr/local/bin:/usr/bin:/bin:$env(PATH)"

# 명령어 실행
set timeout -1
spawn gh cs cp -e remote:/workspaces/PopupPlayer/app/build/outputs/apk/release/app-release.apk /root/PopupPlayer

# 엔터 입력 자동화
expect {
    ">" { send "\r"; exp_continue }
    eof
}

spawn cp /root/PopupPlayer/app-release.apk /root/storage/shared/Apks/app-release-refactoring.apk
expect eof
