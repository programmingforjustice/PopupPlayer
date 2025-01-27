package nl.blauw.pipplayer

import com.google.android.exoplayer2.Player

/**
 * ExoPlayer에 isPlayerReleased 확장 프로퍼티를 추가합니다.
 */
val Player.isPlayerReleased: Boolean
    get() = try {
        this..setVolume(this.getVolume()) // 임의의 메서드 호출
        false // 예외가 발생하지 않으면 release되지 않음
    } catch (e: IllegalStateException) {
        true // 예외 발생 시 release된 상태
    }