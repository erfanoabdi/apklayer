// android.app.PictureInPictureParams -- configuration for entering
// picture-in-picture mode (aspect ratio, custom actions, source rect).
// AOSP API-26+. PiP is not supported on the macOS single-window host,
// but video apps build these params + call enterPictureInPictureMode;
// the type + Builder must resolve so the build() call does not throw.
//
// AOSP ref: frameworks/base/core/java/android/app/PictureInPictureParams.java.
package android.app;

import android.graphics.Rect;
import android.util.Rational;

import java.util.List;

public final class PictureInPictureParams implements android.os.Parcelable {
    private Rational mAspectRatio;
    private Rational mExpandedAspectRatio;
    private List<RemoteAction> mUserActions;
    private RemoteAction mCloseAction;
    private Rect mSourceRectHint;
    private CharSequence mTitle;
    private CharSequence mSubtitle;
    private Boolean mAutoEnterEnabled;
    private Boolean mSeamlessResizeEnabled;

    PictureInPictureParams() {}

    public Rational getAspectRatio() { return mAspectRatio; }
    public Rational getAspectRatioRational() { return mAspectRatio; }
    public boolean hasSetAspectRatio() { return mAspectRatio != null; }
    public Rational getExpandedAspectRatio() { return mExpandedAspectRatio; }
    public List<RemoteAction> getActions() { return mUserActions; }
    public RemoteAction getCloseAction() { return mCloseAction; }
    public Rect getSourceRectHint() { return mSourceRectHint; }
    public CharSequence getTitle() { return mTitle; }
    public CharSequence getSubtitle() { return mSubtitle; }
    public boolean isAutoEnterEnabled() { return mAutoEnterEnabled != null && mAutoEnterEnabled; }
    public boolean isSeamlessResizeEnabled() { return mSeamlessResizeEnabled == null || mSeamlessResizeEnabled; }

    // ---- Parcelable ----
    //
    // ⚠ Until v1.0.940 this was an empty body plus a CREATOR that answered a
    // blank params object. That was invisible while android.os.Parcel faked
    // marshalling with a side list of live objects; now that it marshals for
    // real, a video app that stores its PiP params in onSaveInstanceState (the
    // documented pattern, since the aspect ratio has to survive a config
    // change) would get every field back as null and re-enter PiP with no
    // aspect ratio and no actions.
    //
    // Field order follows android13-release's writeToParcel
    // (frameworks/base/core/java/android/app/PictureInPictureParams.java
    // :682-730), not android11's, because android11's class predates six of
    // the nine fields this tree declares -- expandedAspectRatio, closeAction,
    // title, subtitle, autoEnterEnabled and seamlessResizeEnabled are all API
    // 31+. The three fields android11 does have keep the same relative
    // positions in both, so this is a superset rather than a divergence.
    // OMITTED: mIsLaunchIntoPip (android13's tenth field); no field here.
    //
    // The Boolean-typed flags are genuinely three-valued -- null means "the app
    // never called the setter", which isSeamlessResizeEnabled() reads as TRUE
    // and isAutoEnterEnabled() reads as false -- so each gets AOSP's present
    // marker rather than being flattened to a boolean.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeRationalToParcel(mAspectRatio, dest);
        writeRationalToParcel(mExpandedAspectRatio, dest);
        if (mUserActions != null) {
            dest.writeInt(1);
            // ⚠ RemoteAction still has an EMPTY writeToParcel on this tree, so
            // each element currently rides Parcel.writeParcelableBody's
            // measured fallback (a live object reference plus a one-time
            // warning) rather than real bytes. That is correct today and
            // becomes real marshalling with no edit here the moment
            // RemoteAction grows a body -- the fallback is measurement-driven.
            dest.writeTypedList(mUserActions, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeTypedObject(mCloseAction, flags);
        if (mSourceRectHint != null) {
            dest.writeInt(1);
            mSourceRectHint.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (mAutoEnterEnabled != null) {
            dest.writeInt(1);
            dest.writeBoolean(mAutoEnterEnabled);
        } else {
            dest.writeInt(0);
        }
        if (mSeamlessResizeEnabled != null) {
            dest.writeInt(1);
            dest.writeBoolean(mSeamlessResizeEnabled);
        } else {
            dest.writeInt(0);
        }
        if (mTitle != null) {
            dest.writeInt(1);
            dest.writeCharSequence(mTitle);
        } else {
            dest.writeInt(0);
        }
        if (mSubtitle != null) {
            dest.writeInt(1);
            dest.writeCharSequence(mSubtitle);
        } else {
            dest.writeInt(0);
        }
    }

    /** AOSP's private writeRationalToParcel: Rational is not Parcelable, so it
     *  travels as a present marker plus its two ints. */
    private static void writeRationalToParcel(Rational rational, android.os.Parcel out) {
        if (rational != null) {
            out.writeInt(1);
            out.writeInt(rational.getNumerator());
            out.writeInt(rational.getDenominator());
        } else {
            out.writeInt(0);
        }
    }

    private static Rational readRationalFromParcel(android.os.Parcel in) {
        return (in.readInt() != 0) ? new Rational(in.readInt(), in.readInt()) : null;
    }

    /** AOSP's `PictureInPictureParams(Parcel in)`. */
    private PictureInPictureParams(android.os.Parcel in) {
        mAspectRatio = readRationalFromParcel(in);
        mExpandedAspectRatio = readRationalFromParcel(in);
        if (in.readInt() != 0) {
            mUserActions = in.createTypedArrayList(RemoteAction.CREATOR);
        }
        mCloseAction = in.readTypedObject(RemoteAction.CREATOR);
        if (in.readInt() != 0) {
            mSourceRectHint = Rect.CREATOR.createFromParcel(in);
        }
        if (in.readInt() != 0) mAutoEnterEnabled = in.readBoolean();
        if (in.readInt() != 0) mSeamlessResizeEnabled = in.readBoolean();
        if (in.readInt() != 0) mTitle = in.readCharSequence();
        if (in.readInt() != 0) mSubtitle = in.readCharSequence();
    }

    public static final Creator<PictureInPictureParams> CREATOR = new Creator<PictureInPictureParams>() {
        @Override public PictureInPictureParams createFromParcel(android.os.Parcel s) {
            return new PictureInPictureParams(s);
        }
        @Override public PictureInPictureParams[] newArray(int size) { return new PictureInPictureParams[size]; }
    };

    public static class Builder {
        private final PictureInPictureParams p = new PictureInPictureParams();
        public Builder() {}
        public Builder(PictureInPictureParams base) {
            if (base != null) {
                p.mAspectRatio = base.mAspectRatio;
                p.mExpandedAspectRatio = base.mExpandedAspectRatio;
                p.mUserActions = base.mUserActions;
                p.mCloseAction = base.mCloseAction;
                p.mSourceRectHint = base.mSourceRectHint;
                p.mTitle = base.mTitle;
                p.mSubtitle = base.mSubtitle;
                p.mAutoEnterEnabled = base.mAutoEnterEnabled;
                p.mSeamlessResizeEnabled = base.mSeamlessResizeEnabled;
            }
        }
        public Builder setAspectRatio(Rational aspectRatio) { p.mAspectRatio = aspectRatio; return this; }
        public Builder setExpandedAspectRatio(Rational expandedAspectRatio) { p.mExpandedAspectRatio = expandedAspectRatio; return this; }
        public Builder setActions(List<RemoteAction> actions) { p.mUserActions = actions; return this; }
        public Builder setCloseAction(RemoteAction closeAction) { p.mCloseAction = closeAction; return this; }
        public Builder setSourceRectHint(Rect launchBounds) { p.mSourceRectHint = launchBounds; return this; }
        public Builder setTitle(CharSequence title) { p.mTitle = title; return this; }
        public Builder setSubtitle(CharSequence subtitle) { p.mSubtitle = subtitle; return this; }
        public Builder setAutoEnterEnabled(boolean autoEnterEnabled) { p.mAutoEnterEnabled = autoEnterEnabled; return this; }
        public Builder setSeamlessResizeEnabled(boolean seamlessResizeEnabled) { p.mSeamlessResizeEnabled = seamlessResizeEnabled; return this; }
        public PictureInPictureParams build() { return p; }
    }
}
