package android.media.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Description of one TV input. No TV inputs exist on this platform -- Mac.apk is
 * not a TV device, and TvInputManager reports an empty input list, which is what
 * AOSP reports on any phone or tablet -- so an app never receives a populated
 * instance. The full accessor surface is still implemented because an app that
 * DID obtain one (from its own TvInputService) reads all of it, and because the
 * type has to be Parcelable for a TvInputService's own callbacks to resolve.
 *
 * Source: frameworks/base/media/java/android/media/tv/TvInputInfo.java
 * (android16-release).
 */
public final class TvInputInfo implements Parcelable {

    /** Intent extra naming an input id. */
    public static final String EXTRA_INPUT_ID = "android.media.tv.extra.INPUT_ID";

    public static final int TYPE_TUNER = 0;
    public static final int TYPE_OTHER = 1000;
    public static final int TYPE_COMPOSITE = 1001;
    public static final int TYPE_SVIDEO = 1002;
    public static final int TYPE_SCART = 1003;
    public static final int TYPE_COMPONENT = 1004;
    public static final int TYPE_VGA = 1005;
    public static final int TYPE_DVI = 1006;
    public static final int TYPE_HDMI = 1007;
    public static final int TYPE_DISPLAY_PORT = 1008;

    private final String mId;
    private final String mParentId;
    private final int mType;
    private final boolean mIsPassthroughInput;
    private final int mTunerCount;
    private final boolean mCanRecord;
    private final boolean mCanPauseRecording;
    private final Bundle mExtras;
    private final ServiceInfo mServiceInfo;

    TvInputInfo(String id) {
        this(id, null, TYPE_OTHER, false, 0, false, false, null, null);
    }

    TvInputInfo(String id, String parentId, int type, boolean isPassthroughInput, int tunerCount,
            boolean canRecord, boolean canPauseRecording, Bundle extras, ServiceInfo serviceInfo) {
        mId = id;
        mParentId = parentId;
        mType = type;
        mIsPassthroughInput = isPassthroughInput;
        mTunerCount = tunerCount;
        mCanRecord = canRecord;
        mCanPauseRecording = canPauseRecording;
        mExtras = extras;
        mServiceInfo = serviceInfo;
    }

    public String getId() { return mId; }

    /** AOSP: the id of the input this one is derived from (an HDMI CEC device
     *  under its port), or null when there is none. */
    public String getParentId() { return mParentId; }

    public int getType() { return mType; }

    public boolean isPassthroughInput() { return mIsPassthroughInput; }

    /** AOSP: how many tuners this input has; 0 for a non-tuner input. */
    public int getTunerCount() { return mTunerCount; }

    public boolean canRecord() { return mCanRecord; }

    public boolean canPauseRecording() { return mCanPauseRecording; }

    /** AOSP: the input's own extras, never null. */
    public Bundle getExtras() { return mExtras == null ? new Bundle() : mExtras; }

    /** AOSP: the ServiceInfo of the TvInputService backing this input. Null here
     *  because no such service is ever resolved -- there are no inputs. */
    public ServiceInfo getServiceInfo() { return mServiceInfo; }

    /**
     * AOSP builds an Intent at the component named by the input's
     * android.media.tv.input meta-data (setupActivity / settingsActivity), and
     * returns NULL when the input declares none. No input here declares one, so
     * null is the correct answer -- and it is a value AOSP itself returns and
     * apps already null-check before startActivity.
     */
    public Intent createSetupIntent() { return null; }

    /** @deprecated in AOSP since API 27; same null contract as createSetupIntent. */
    @Deprecated public Intent createSettingsIntent() { return null; }

    /** AOSP reads the per-input "hidden" flag out of TvInputManager's shared
     *  preferences. There are no inputs and no such preference store, so no
     *  input is hidden. */
    public boolean isHidden(Context context) { return false; }

    /** AOSP loads the service's label; with no ServiceInfo there is nothing to
     *  load, and its own contract permits null. */
    public CharSequence loadLabel(Context context) { return null; }

    /** AOSP: the app-supplied custom label from the input's meta-data, null when
     *  the input declares none. */
    public CharSequence loadCustomLabel(Context context) { return null; }

    /** AOSP loads the service's icon; nothing to load here. */
    public Drawable loadIcon(Context context) { return null; }

    // AOSP additionally parcels a ResolveInfo, an icon Uri set and an HDMI
    // device id, none of which is modelled here (there is no input to describe).
    // Writing our own field order rather than AOSP's exact wire shape, per the
    // absent-fields-not-padded rule.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeString(mParentId);
        dest.writeInt(mType);
        dest.writeInt(mIsPassthroughInput ? 1 : 0);
        dest.writeInt(mTunerCount);
        dest.writeInt(mCanRecord ? 1 : 0);
        dest.writeInt(mCanPauseRecording ? 1 : 0);
        dest.writeBundle(mExtras);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TvInputInfo)) return false;
        return java.util.Objects.equals(mId, ((TvInputInfo) obj).mId);
    }

    @Override public int hashCode() { return mId == null ? 0 : mId.hashCode(); }

    @Override public String toString() { return "TvInputInfo{id=" + mId + "}"; }

    public static final Parcelable.Creator<TvInputInfo> CREATOR =
            new Parcelable.Creator<TvInputInfo>() {
        @Override public TvInputInfo createFromParcel(Parcel in) {
            String id = in.readString();
            String parentId = in.readString();
            int type = in.readInt();
            boolean passthrough = in.readInt() != 0;
            int tunerCount = in.readInt();
            boolean canRecord = in.readInt() != 0;
            boolean canPause = in.readInt() != 0;
            return new TvInputInfo(id, parentId, type, passthrough, tunerCount, canRecord,
                    canPause, in.readBundle(), null);
        }
        @Override public TvInputInfo[] newArray(int size) { return new TvInputInfo[size]; }
    };
}
