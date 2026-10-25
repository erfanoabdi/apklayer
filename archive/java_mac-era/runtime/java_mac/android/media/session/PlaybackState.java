// android.media.session.PlaybackState -- the current playback state of a
// MediaSession (state, position, speed, actions, queue id). AOSP frameworks/base
// media/java/android/media/session/PlaybackState.java. Immutable, built via
// Builder; a pure data holder, fully serviceable on Mac.apk.
package android.media.session;

public final class PlaybackState implements android.os.Parcelable {
    public static final int STATE_NONE = 0;
    public static final int STATE_STOPPED = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_FAST_FORWARDING = 4;
    public static final int STATE_REWINDING = 5;
    public static final int STATE_BUFFERING = 6;
    public static final int STATE_ERROR = 7;
    public static final int STATE_CONNECTING = 8;
    public static final int STATE_SKIPPING_TO_PREVIOUS = 9;
    public static final int STATE_SKIPPING_TO_NEXT = 10;
    public static final int STATE_SKIPPING_TO_QUEUE_ITEM = 11;
    public static final int STATE_PLAYBACK_SUPPRESSED = 12;

    /**
     * API 31. AOSP: true when the state is one in which playback is considered
     * ACTIVE -- FAST_FORWARDING, REWINDING, SKIPPING_TO_PREVIOUS,
     * SKIPPING_TO_NEXT, SKIPPING_TO_QUEUE_ITEM, BUFFERING, CONNECTING or
     * PLAYING (PlaybackState.java, isActive). Computed from getState() rather
     * than stored, so it can never disagree with the state the app set.
     */
    public boolean isActive() {
        switch (getState()) {
            case STATE_FAST_FORWARDING:
            case STATE_REWINDING:
            case STATE_SKIPPING_TO_PREVIOUS:
            case STATE_SKIPPING_TO_NEXT:
            case STATE_SKIPPING_TO_QUEUE_ITEM:
            case STATE_BUFFERING:
            case STATE_CONNECTING:
            case STATE_PLAYING:
                return true;
            default:
                return false;
        }
    }

    public static final long ACTION_STOP = 1 << 0;
    public static final long ACTION_PAUSE = 1 << 1;
    public static final long ACTION_PLAY = 1 << 2;
    public static final long ACTION_REWIND = 1 << 3;
    public static final long ACTION_SKIP_TO_PREVIOUS = 1 << 4;
    public static final long ACTION_SKIP_TO_NEXT = 1 << 5;
    public static final long ACTION_FAST_FORWARD = 1 << 6;
    public static final long ACTION_SET_RATING = 1 << 7;
    public static final long ACTION_SEEK_TO = 1 << 8;
    public static final long ACTION_PLAY_PAUSE = 1 << 9;
    public static final long ACTION_PLAY_FROM_MEDIA_ID = 1 << 10;
    public static final long ACTION_PLAY_FROM_SEARCH = 1 << 11;
    public static final long ACTION_SKIP_TO_QUEUE_ITEM = 1 << 12;
    public static final long ACTION_PLAY_FROM_URI = 1 << 13;
    public static final long ACTION_PREPARE = 1 << 14;
    public static final long ACTION_PREPARE_FROM_MEDIA_ID = 1 << 15;
    public static final long ACTION_PREPARE_FROM_SEARCH = 1 << 16;
    public static final long ACTION_PREPARE_FROM_URI = 1 << 17;
    public static final long ACTION_SET_PLAYBACK_SPEED = 1 << 22;

    public static final long PLAYBACK_POSITION_UNKNOWN = -1;

    private final int mState;
    private final long mPosition;
    private final long mBufferedPosition;
    private final float mSpeed;
    private final long mActions;
    private final CharSequence mErrorMessage;
    private final long mUpdateTime;
    private final long mActiveItemId;
    private final java.util.List<CustomAction> mCustomActions;
    private final android.os.Bundle mExtras;

    private PlaybackState(int state, long position, long bufferedPosition, float speed,
                          long actions, CharSequence errorMessage, long updateTime,
                          long activeItemId, java.util.List<CustomAction> customActions,
                          android.os.Bundle extras) {
        mState = state;
        mPosition = position;
        mBufferedPosition = bufferedPosition;
        mSpeed = speed;
        mActions = actions;
        mErrorMessage = errorMessage;
        mUpdateTime = updateTime;
        mActiveItemId = activeItemId;
        mCustomActions = customActions;
        mExtras = extras;
    }

    public int getState() { return mState; }
    public long getPosition() { return mPosition; }
    public long getBufferedPosition() { return mBufferedPosition; }
    public float getPlaybackSpeed() { return mSpeed; }
    public long getActions() { return mActions; }
    public CharSequence getErrorMessage() { return mErrorMessage; }
    public long getLastPositionUpdateTime() { return mUpdateTime; }
    public long getActiveQueueItemId() { return mActiveItemId; }
    public java.util.List<CustomAction> getCustomActions() { return mCustomActions; }
    public android.os.Bundle getExtras() { return mExtras; }

    public static final long MEDIA_SESSION_QUEUE_ITEM_UNKNOWN_ID = -1;

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mState);
        dest.writeLong(mPosition);
        dest.writeLong(mBufferedPosition);
        dest.writeFloat(mSpeed);
        dest.writeLong(mActions);
    }
    public static final android.os.Parcelable.Creator<PlaybackState> CREATOR =
        new android.os.Parcelable.Creator<PlaybackState>() {
            @Override public PlaybackState createFromParcel(android.os.Parcel in) {
                return new Builder()
                    .setState(in.readInt(), in.readLong(), in.readFloat())
                    .build();
            }
            @Override public PlaybackState[] newArray(int size) {
                return new PlaybackState[size];
            }
        };

    public static final class CustomAction implements android.os.Parcelable {
        private final String mAction;
        private final CharSequence mName;
        private final int mIcon;
        private final android.os.Bundle mExtras;
        private CustomAction(String action, CharSequence name, int icon, android.os.Bundle extras) {
            mAction = action; mName = name; mIcon = icon; mExtras = extras;
        }
        public String getAction() { return mAction; }
        public CharSequence getName() { return mName; }
        public int getIcon() { return mIcon; }
        public android.os.Bundle getExtras() { return mExtras; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeString(mAction);
            dest.writeInt(mIcon);
        }
        public static final android.os.Parcelable.Creator<CustomAction> CREATOR =
            new android.os.Parcelable.Creator<CustomAction>() {
                @Override public CustomAction createFromParcel(android.os.Parcel in) {
                    return new CustomAction(in.readString(), null, in.readInt(), null);
                }
                @Override public CustomAction[] newArray(int size) {
                    return new CustomAction[size];
                }
            };
        public static final class Builder {
            private final String action;
            private final CharSequence name;
            private final int icon;
            private android.os.Bundle extras;
            public Builder(String action, CharSequence name, int icon) {
                this.action = action; this.name = name; this.icon = icon;
            }
            public Builder setExtras(android.os.Bundle extras) { this.extras = extras; return this; }
            public CustomAction build() { return new CustomAction(action, name, icon, extras); }
        }
    }

    public static final class Builder {
        private int state;
        private long position;
        private long bufferedPosition;
        private float speed;
        private long actions;
        private CharSequence errorMessage;
        private long updateTime;
        private long activeItemId = MEDIA_SESSION_QUEUE_ITEM_UNKNOWN_ID;
        private final java.util.List<CustomAction> customActions = new java.util.ArrayList<CustomAction>();
        private android.os.Bundle extras;

        public Builder() {}
        public Builder(PlaybackState from) {
            if (from != null) {
                state = from.mState; position = from.mPosition;
                bufferedPosition = from.mBufferedPosition; speed = from.mSpeed;
                actions = from.mActions; errorMessage = from.mErrorMessage;
                updateTime = from.mUpdateTime; activeItemId = from.mActiveItemId;
                extras = from.mExtras;
                if (from.mCustomActions != null) customActions.addAll(from.mCustomActions);
            }
        }
        public Builder setState(int state, long position, float playbackSpeed) {
            // AOSP (frameworks/base media/java/android/media/session/
            // PlaybackState.java, Builder.setState(int,long,float)):
            // "return setState(state, position, playbackSpeed,
            // SystemClock.elapsedRealtime());" -- it delegates to the 4-arg
            // overload rather than duplicating logic here. Doing the fields
            // directly instead (as before this fix) left updateTime at its
            // 0 default for this overload, which is the one almost every
            // caller uses (the 4-arg form exists mainly for replaying a
            // historical state). Any UI that interpolates the live position
            // as position + speed*(elapsedRealtime() - updateTime) then
            // computed elapsedRealtime() itself (time since boot) as the
            // elapsed term, producing a wildly wrong seek-bar position.
            return setState(state, position, playbackSpeed, android.os.SystemClock.elapsedRealtime());
        }
        // AOSP android14-release PlaybackState.java:814-821 -- the 4-arg overload
        // is the one that ASSIGNS; it never calls back into the 3-arg form:
        //     mState = state; mPosition = position;
        //     mUpdateTime = updateTime; mSpeed = playbackSpeed; return this;
        // ⚠ It used to call setState(state, position, playbackSpeed) while the
        // 3-arg form (above) called this one, so EVERY setState overflowed the
        // stack (e9030a3f, 2026-07-30 -> 2026-09-29). Every MediaSession app died
        // in it: NewPipe's PlayerService.onCreate threw StackOverflowError at
        // PlaybackState$Builder.setState:202/205 (lanes19 media-playback);
        // debug/mediasession_gate.sh keeps it fixed.
        public Builder setState(int state, long position, float playbackSpeed, long updateTime) {
            this.state = state;
            this.position = position;
            this.updateTime = updateTime;
            this.speed = playbackSpeed;
            return this;
        }
        public Builder setActions(long actions) { this.actions = actions; return this; }
        public Builder setBufferedPosition(long bufferPosition) { this.bufferedPosition = bufferPosition; return this; }
        public Builder setActiveQueueItemId(long id) { this.activeItemId = id; return this; }
        public Builder setErrorMessage(CharSequence error) { this.errorMessage = error; return this; }
        public Builder setExtras(android.os.Bundle extras) { this.extras = extras; return this; }
        public Builder addCustomAction(String action, String name, int icon) {
            return addCustomAction(new CustomAction.Builder(action, name, icon).build());
        }
        public Builder addCustomAction(CustomAction customAction) {
            if (customAction != null) customActions.add(customAction);
            return this;
        }
        public PlaybackState build() {
            return new PlaybackState(state, position, bufferedPosition, speed, actions,
                errorMessage, updateTime, activeItemId, customActions, extras);
        }
    }
}
