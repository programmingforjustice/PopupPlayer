package nl.blauw.pipplayer;

import android.view.View;
import android.widget.ImageButton;
import com.google.android.exoplayer2.Player;

public class MuteToggleButtonListener implements View.OnClickListener {

    private boolean isMuted = false;
    private final Player player;

    public MuteToggleButtonListener(Player player) {
        this.player = player;

        // MuteToggleButtonListener 자신을 클릭 리스너로 설정
        // muteToggleButton.setOnClickListener(this);

        // 초기 버튼 이미지 설정
        //updateButtonImage();
    }

    @Override
    public void onClick(View v) {
        toggleMute((ImageButton)v);
    }

    private void toggleMute(final ImageButton muteToggleButton) {
        isMuted = !isMuted;
        player.setVolume(isMuted ? 0f : 1f); // Player 인터페이스를 통해 볼륨 조작
        updateButtonImage(muteToggleButton);
    }

    private void updateButtonImage(final ImageButton muteToggleButton) {
        muteToggleButton.setImageResource(isMuted ? R.drawable.ic_mute : R.drawable.ic_unmute);
    }
}