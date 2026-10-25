package android.service.chooser;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * API 35 payload the system chooser sends back through the IntentSender passed
 * as Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER: what the user picked, and
 * whether they picked a component, a shortcut, "copy", or "edit".
 *
 * Mac.apk shows no chooser, so we never send one -- an app's result callback
 * simply never fires, which is also what happens on a device when the user
 * dismisses the sheet. The class must exist because the callback's signature
 * names the type, and a value object costs nothing to get right.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   CHOOSER_RESULT_UNKNOWN = -1 (0xffffffff)
 *   CHOOSER_RESULT_SELECTED_COMPONENT = 0
 *   CHOOSER_RESULT_COPY = 1
 *   CHOOSER_RESULT_EDIT = 2
 * ⚠ UNKNOWN is -1, not 0; SELECTED_COMPONENT is the zero value.
 *
 * Source: frameworks/base/core/java/android/service/chooser/ChooserResult.java
 */
public final class ChooserResult implements Parcelable {

    public static final int CHOOSER_RESULT_UNKNOWN = -1;
    public static final int CHOOSER_RESULT_SELECTED_COMPONENT = 0;
    public static final int CHOOSER_RESULT_COPY = 1;
    public static final int CHOOSER_RESULT_EDIT = 2;

    private final int mType;
    private final ComponentName mComponentName;
    private final boolean mIsShortcut;

    // AOSP's constructor is @hide -- only the chooser constructs these.
    ChooserResult(int type, ComponentName componentName, boolean isShortcut) {
        mType = type;
        mComponentName = componentName;
        mIsShortcut = isShortcut;
    }

    public int getType() { return mType; }

    /** Null unless the type is CHOOSER_RESULT_SELECTED_COMPONENT. */
    public ComponentName getSelectedComponent() { return mComponentName; }

    public boolean isShortcut() { return mIsShortcut; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeTypedObject(mComponentName, flags);
        dest.writeBoolean(mIsShortcut);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChooserResult)) return false;
        ChooserResult other = (ChooserResult) o;
        return mType == other.mType
                && mIsShortcut == other.mIsShortcut
                && java.util.Objects.equals(mComponentName, other.mComponentName);
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(Integer.valueOf(mType), mComponentName,
                Boolean.valueOf(mIsShortcut));
    }

    @Override public String toString() {
        return "ChooserResult[type=" + mType + ", component=" + mComponentName
                + ", isShortcut=" + mIsShortcut + "]";
    }

    public static final Creator<ChooserResult> CREATOR = new Creator<ChooserResult>() {
        @Override public ChooserResult createFromParcel(Parcel in) {
            int type = in.readInt();
            ComponentName cn = in.readTypedObject(ComponentName.CREATOR);
            boolean isShortcut = in.readBoolean();
            return new ChooserResult(type, cn, isShortcut);
        }
        @Override public ChooserResult[] newArray(int size) {
            return new ChooserResult[size];
        }
    };
}
