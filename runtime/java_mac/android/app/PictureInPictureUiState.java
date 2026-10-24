package android.app;
// android.app.PictureInPictureUiState (API 31+). Passed to
// Activity.onPictureInPictureUiStateChanged; we never enter PiP on macOS, so the
// state is always "not stashed / not transitioning" for anything WE construct.
// 2026-08-23 conformance sweep: added real fields + Parcelable so an app that
// stores one (e.g. via a saved-instance Bundle) or receives one built via the
// public single-arg constructor gets its actual value back instead of null.
// Source: frameworks/base/core/java/android/app/PictureInPictureUiState.java
public final class PictureInPictureUiState implements android.os.Parcelable {
    private final boolean mIsStashed;
    private final boolean mIsTransitioningToPip;

    public PictureInPictureUiState() { this(false, false); }
    public PictureInPictureUiState(boolean isStashed) { this(isStashed, false); }
    private PictureInPictureUiState(boolean isStashed, boolean isTransitioningToPip) {
        mIsStashed = isStashed;
        mIsTransitioningToPip = isTransitioningToPip;
    }

    public boolean isStashed()             { return mIsStashed; }
    public boolean isTransitioningToPip()  { return mIsTransitioningToPip; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeBoolean(mIsStashed);
        out.writeBoolean(mIsTransitioningToPip);
    }

    public static final android.os.Parcelable.Creator<PictureInPictureUiState> CREATOR =
            new android.os.Parcelable.Creator<PictureInPictureUiState>() {
        @Override public PictureInPictureUiState createFromParcel(android.os.Parcel in) {
            return new PictureInPictureUiState(in.readBoolean(), in.readBoolean());
        }
        @Override public PictureInPictureUiState[] newArray(int size) {
            return new PictureInPictureUiState[size];
        }
    };
}
