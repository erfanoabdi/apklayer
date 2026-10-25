// android.view.translation.TranslationCapability -- what the on-device
// translator can do between one source and target spec. Mac.apk ships no
// on-device translator, so TranslationManager reports NO capabilities at all
// (an empty set) rather than advertising one it cannot honour; the type still
// has to exist because View.dispatchCreateViewTranslationRequest names it.
// Source: frameworks/base core/java/android/view/translation/TranslationCapability.java
package android.view.translation;

public final class TranslationCapability implements android.os.Parcelable {
    public static final int STATE_AVAILABLE_TO_DOWNLOAD = 1;
    public static final int STATE_DOWNLOADING = 2;
    public static final int STATE_ON_DEVICE = 3;
    public static final int STATE_NOT_AVAILABLE = 4;

    private final int mState;
    private final TranslationSpec mSourceSpec;
    private final TranslationSpec mTargetSpec;
    private final boolean mUiTranslationEnabled;
    private final int mSupportedTranslationFlags;

    public TranslationCapability(int state, TranslationSpec sourceSpec,
            TranslationSpec targetSpec, boolean uiTranslationEnabled,
            int supportedTranslationFlags) {
        mState = state;
        mSourceSpec = sourceSpec;
        mTargetSpec = targetSpec;
        mUiTranslationEnabled = uiTranslationEnabled;
        mSupportedTranslationFlags = supportedTranslationFlags;
    }

    public int getState() { return mState; }
    public TranslationSpec getSourceSpec() { return mSourceSpec; }
    public TranslationSpec getTargetSpec() { return mTargetSpec; }
    public boolean isUiTranslationEnabled() { return mUiTranslationEnabled; }
    public int getSupportedTranslationFlags() { return mSupportedTranslationFlags; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mState);
        dest.writeParcelable(mSourceSpec, flags);
        dest.writeParcelable(mTargetSpec, flags);
        dest.writeBoolean(mUiTranslationEnabled);
        dest.writeInt(mSupportedTranslationFlags);
    }

    public static final android.os.Parcelable.Creator<TranslationCapability> CREATOR =
            new android.os.Parcelable.Creator<TranslationCapability>() {
        @Override public TranslationCapability createFromParcel(android.os.Parcel source) {
            ClassLoader cl = TranslationSpec.class.getClassLoader();
            return new TranslationCapability(source.readInt(),
                    (TranslationSpec) source.readParcelable(cl),
                    (TranslationSpec) source.readParcelable(cl),
                    source.readBoolean(), source.readInt());
        }
        @Override public TranslationCapability[] newArray(int size) {
            return new TranslationCapability[size];
        }
    };
}
