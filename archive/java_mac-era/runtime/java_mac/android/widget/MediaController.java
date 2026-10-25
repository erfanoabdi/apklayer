// android.widget.MediaController -- the transport-control overlay (play/pause,
// seekbar, time) that attaches to a VideoView. Was Absent. We keep the
// MediaPlayerControl wiring + show/hide/anchor state; the on-screen controls
// are not painted. AOSP:
//   frameworks/base/core/java/android/widget/MediaController.java
//   public class MediaController extends FrameLayout
package android.widget;

import android.content.Context;
import android.view.View;

public class MediaController extends FrameLayout {
    public interface MediaPlayerControl {
        void start();
        void pause();
        int  getDuration();
        int  getCurrentPosition();
        void seekTo(int pos);
        boolean isPlaying();
        int  getBufferPercentage();
        boolean canPause();
        boolean canSeekBackward();
        boolean canSeekForward();
        int  getAudioSessionId();
    }

    private MediaPlayerControl mPlayer;
    private View mAnchor;
    private boolean mShowing;

    public MediaController(Context context) { super(context); }
    public MediaController(Context context, boolean useFastForward) { super(context); }
    public MediaController(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMediaPlayer(MediaPlayerControl player) { mPlayer = player; }
    public void setAnchorView(View view) { mAnchor = view; }

    public void show() { mShowing = true; }
    public void show(int timeout) { mShowing = true; }
    public void hide() { mShowing = false; }
    public boolean isShowing() { return mShowing; }

    @Override public void setEnabled(boolean enabled) { super.setEnabled(enabled); }

    public void setPrevNextListeners(View.OnClickListener next, View.OnClickListener prev) {}
}
