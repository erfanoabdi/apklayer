// android.widget.VideoView -- a SurfaceView that plays a video via an
// internal MediaPlayer, implementing MediaController.MediaPlayerControl so a
// MediaController can drive it. Was Absent. We wire a real MediaPlayer to the
// surface and forward transport calls; actual frame presentation depends on
// the MediaPlayer/native backend. AOSP:
//   frameworks/base/core/java/android/widget/VideoView.java
//   public class VideoView extends SurfaceView
//       implements MediaController.MediaPlayerControl, ...
package android.widget;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.SurfaceView;

import java.util.Map;

public class VideoView extends SurfaceView
        implements MediaController.MediaPlayerControl {

    private Uri mUri;
    private MediaPlayer mMediaPlayer;
    private MediaController mMediaController;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private int mCurrentBufferPercentage;
    private int mSeekWhenPrepared;
    private boolean mPrepared;

    public VideoView(Context context) { super(context); }
    public VideoView(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public VideoView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public VideoView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setVideoPath(String path) {
        setVideoURI(Uri.parse(path));
    }
    public void setVideoURI(Uri uri) { setVideoURI(uri, null); }
    public void setVideoURI(Uri uri, Map<String, String> headers) {
        mUri = uri;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

    private void openVideo() {
        if (mUri == null) return;
        release(false);
        try {
            mMediaPlayer = new MediaPlayer();
            if (mOnPreparedListener != null) {
                mMediaPlayer.setOnPreparedListener(mOnPreparedListener);
            }
            mMediaPlayer.setOnCompletionListener(mOnCompletionListener);
            mMediaPlayer.setOnErrorListener(mOnErrorListener);
            // AOSP binds the player to the SurfaceHolder here; our MediaPlayer
            // presents via its own native path, so no setDisplay is needed.
            mMediaPlayer.setDataSource(getContext(), mUri);
            mMediaPlayer.prepareAsync();
            mPrepared = false;
        } catch (Throwable t) {
            if (mOnErrorListener != null) {
                mOnErrorListener.onError(mMediaPlayer, 1, 0);
            }
        }
    }

    public void setMediaController(MediaController controller) {
        if (mMediaController != null) mMediaController.hide();
        mMediaController = controller;
        if (controller != null) {
            controller.setMediaPlayer(this);
            controller.setAnchorView(this);
        }
    }

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) { mOnPreparedListener = l; }
    public void setOnCompletionListener(MediaPlayer.OnCompletionListener l) { mOnCompletionListener = l; }
    public void setOnErrorListener(MediaPlayer.OnErrorListener l) { mOnErrorListener = l; }

    private void release(boolean clearTargetState) {
        if (mMediaPlayer != null) {
            try { mMediaPlayer.release(); } catch (Throwable ignored) {}
            mMediaPlayer = null;
            mPrepared = false;
        }
    }
    public void stopPlayback() { release(true); }
    public void suspend() { release(false); }
    public void resume() { openVideo(); }

    @Override public void start() { if (mMediaPlayer != null) mMediaPlayer.start(); }
    @Override public void pause() { if (mMediaPlayer != null && mMediaPlayer.isPlaying()) mMediaPlayer.pause(); }
    @Override public int getDuration() { return mMediaPlayer != null ? mMediaPlayer.getDuration() : -1; }
    @Override public int getCurrentPosition() { return mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0; }
    @Override public void seekTo(int msec) {
        if (mMediaPlayer != null) mMediaPlayer.seekTo(msec);
        else mSeekWhenPrepared = msec;
    }
    @Override public boolean isPlaying() { return mMediaPlayer != null && mMediaPlayer.isPlaying(); }
    @Override public int getBufferPercentage() { return mCurrentBufferPercentage; }
    @Override public boolean canPause() { return true; }
    @Override public boolean canSeekBackward() { return true; }
    @Override public boolean canSeekForward() { return true; }
    @Override public int getAudioSessionId() { return 0; }

    public void setAudioFocusRequest(int focusGain) { }
    public void setOnInfoListener(android.media.MediaPlayer.OnInfoListener l) { }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.VideoView had 3
    // public members missing). AOSP:
    // frameworks/base/core/java/android/widget/VideoView.java (android16).
    // ==================================================================

    private android.media.AudioAttributes mAudioAttributes;
    private final java.util.ArrayList<Object[]> mPendingSubtitleTracks =
            new java.util.ArrayList<Object[]>();

    /** AOSP :251 -- verbatim; VideoView's onMeasure uses it to fit the video
     *  into the measure spec. */
    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        return getDefaultSize(desiredSize, measureSpec);
    }

    /** AOSP :320 -- verbatim, including the null check AOSP throws on. AOSP
     *  applies the attributes when it (re)opens the player; this port's
     *  openVideo does the same through MediaPlayer.setAudioAttributes. */
    public void setAudioAttributes(android.media.AudioAttributes attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("Illegal null AudioAttributes");
        }
        mAudioAttributes = attributes;
        if (mMediaPlayer != null) mMediaPlayer.setAudioAttributes(attributes);
    }

    /** AOSP :349 -- an external subtitle stream. Before the player exists AOSP
     *  queues the (stream, format) pair and replays it at prepare time; this
     *  tree's MediaPlayer has no addSubtitleSource, so the pair is queued and
     *  stays queued. That is AOSP's own pre-player state, and it means no
     *  subtitles are rendered -- a media gap, not an API one. */
    public void addSubtitleSource(java.io.InputStream is, android.media.MediaFormat format) {
        mPendingSubtitleTracks.add(new Object[] { is, format });
    }
}
