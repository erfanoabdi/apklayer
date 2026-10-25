package android.app;
/** The game's current state (loading + mode + optional label/quality).
 *  Source: frameworks/base/core/java/android/app/GameState.java */
public final class GameState implements android.os.Parcelable {
    public static final int MODE_UNKNOWN = 0, MODE_NONE = 1, MODE_GAMEPLAY_INTERRUPTIBLE = 2,
        MODE_GAMEPLAY_UNINTERRUPTIBLE = 3, MODE_CONTENT = 4;
    private final boolean mIsLoading; private final int mMode, mLabel, mQuality;
    public GameState(boolean isLoading, int mode) { this(isLoading, mode, -1, -1); }
    public GameState(boolean isLoading, int mode, int label, int quality) {
        mIsLoading = isLoading; mMode = mode; mLabel = label; mQuality = quality;
    }
    public boolean isLoading() { return mIsLoading; }
    public int getMode() { return mMode; }
    public int getLabel() { return mLabel; }
    public int getQuality() { return mQuality; }

    // AOSP field order: isLoading, mode, label, quality (GameState.java L119-165).
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel parcel, int flags) {
        parcel.writeBoolean(mIsLoading);
        parcel.writeInt(mMode);
        parcel.writeInt(mLabel);
        parcel.writeInt(mQuality);
    }

    public static final android.os.Parcelable.Creator<GameState> CREATOR =
            new android.os.Parcelable.Creator<GameState>() {
        @Override public GameState createFromParcel(android.os.Parcel in) {
            boolean isLoading = in.readBoolean();
            int mode = in.readInt();
            int label = in.readInt();
            int quality = in.readInt();
            return new GameState(isLoading, mode, label, quality);
        }
        @Override public GameState[] newArray(int size) { return new GameState[size]; }
    };
}
