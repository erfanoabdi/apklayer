package android.media;
// android.media.AudioFocusRequest -- the modern (API 26+) audio-focus request
// object passed to AudioManager.requestAudioFocus(AudioFocusRequest). Mirrors
// AOSP frameworks/base media/java/android/media/AudioFocusRequest.java: an
// immutable bag of focus-gain type + attributes + listener + behavior flags,
// built via Builder. Mac.apk always grants focus and never delivers loss, so
// the stored fields are only introspection holders.
public final class AudioFocusRequest {
    private final int mFocusGain;
    private final AudioAttributes mAttributes;
    private final AudioManager.OnAudioFocusChangeListener mListener;
    private final android.os.Handler mHandler;
    private final boolean mPauseOnDuck;
    private final boolean mAcceptsDelayedFocus;

    private AudioFocusRequest(int focusGain, AudioAttributes attrs,
                             AudioManager.OnAudioFocusChangeListener listener,
                             android.os.Handler handler, boolean pauseOnDuck,
                             boolean acceptsDelayed) {
        mFocusGain = focusGain;
        mAttributes = attrs;
        mListener = listener;
        mHandler = handler;
        mPauseOnDuck = pauseOnDuck;
        mAcceptsDelayedFocus = acceptsDelayed;
    }

    public int getFocusGain() { return mFocusGain; }
    public AudioAttributes getAudioAttributes() { return mAttributes; }
    public boolean willPauseWhenDucked() { return mPauseOnDuck; }
    public boolean acceptsDelayedFocusGain() { return mAcceptsDelayedFocus; }
    public AudioManager.OnAudioFocusChangeListener getOnAudioFocusChangeListener() { return mListener; }

    public static final class Builder {
        private int focusGain;
        private AudioAttributes attrs;
        private AudioManager.OnAudioFocusChangeListener listener;
        private android.os.Handler handler;
        private boolean pauseOnDuck;
        private boolean acceptsDelayed;

        public Builder(int focusGain) { this.focusGain = focusGain; }
        public Builder(AudioFocusRequest requestToCopy) {
            if (requestToCopy != null) {
                focusGain = requestToCopy.mFocusGain;
                attrs = requestToCopy.mAttributes;
                listener = requestToCopy.mListener;
                handler = requestToCopy.mHandler;
                pauseOnDuck = requestToCopy.mPauseOnDuck;
                acceptsDelayed = requestToCopy.mAcceptsDelayedFocus;
            }
        }
        public Builder setFocusGain(int focusGain) { this.focusGain = focusGain; return this; }
        public Builder setAudioAttributes(AudioAttributes attributes) { this.attrs = attributes; return this; }
        public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener listener) {
            this.listener = listener; return this;
        }
        public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener listener,
                                                    android.os.Handler handler) {
            this.listener = listener; this.handler = handler; return this;
        }
        public Builder setWillPauseWhenDucked(boolean pauseOnDuck) { this.pauseOnDuck = pauseOnDuck; return this; }
        public Builder setAcceptsDelayedFocusGain(boolean acceptsDelayedFocusGain) {
            this.acceptsDelayed = acceptsDelayedFocusGain; return this;
        }
        public Builder setForceDucking(boolean forceDucking) { return this; }
        public Builder setLocksFocus(boolean focusLocked) { return this; }
        public AudioFocusRequest build() {
            return new AudioFocusRequest(focusGain, attrs, listener, handler,
                                         pauseOnDuck, acceptsDelayed);
        }
    }
}
