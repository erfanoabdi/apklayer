package android.app.slice;
import java.util.Collections;
import java.util.List;
/** One typed item in a Slice (text/int/long/icon/action/slice/bundle/input).
 *
 *  AOSP ref: frameworks/base/core/java/android/app/slice/SliceItem.java
 *  (android16-release). @Deprecated there, like the rest of android.app.slice. */
@Deprecated
public final class SliceItem implements android.os.Parcelable {
    /** AOSP FORMAT_*: the format string getFormat() returns, and the value an
     *  app switches on to decide which getter to call. */
    public static final String FORMAT_SLICE = "slice";
    public static final String FORMAT_TEXT = "text";
    public static final String FORMAT_IMAGE = "image";
    public static final String FORMAT_ACTION = "action";
    public static final String FORMAT_INT = "int";
    public static final String FORMAT_LONG = "long";
    public static final String FORMAT_REMOTE_INPUT = "input";
    public static final String FORMAT_BUNDLE = "bundle";

    private final String mFormat, mSubType;
    private final Object mValue;
    private final List<String> mHints;
    SliceItem(String format, String subType, Object value, List<String> hints) {
        mFormat = format; mSubType = subType; mValue = value;
        mHints = hints != null ? hints : Collections.emptyList();
    }
    public String getFormat() { return mFormat; }
    public String getSubType() { return mSubType; }
    public List<String> getHints() { return mHints; }
    public boolean hasHint(String hint) { return mHints.contains(hint); }
    public CharSequence getText() { return mValue instanceof CharSequence ? (CharSequence) mValue : null; }
    public int getInt() { return mValue instanceof Integer ? (Integer) mValue : 0; }
    public long getLong() { return mValue instanceof Long ? (Long) mValue : 0L; }
    public android.graphics.drawable.Icon getIcon() { return mValue instanceof android.graphics.drawable.Icon ? (android.graphics.drawable.Icon) mValue : null; }
    public android.app.PendingIntent getAction() { return mValue instanceof android.app.PendingIntent ? (android.app.PendingIntent) mValue : null; }
    public Slice getSlice() { return mValue instanceof Slice ? (Slice) mValue : null; }
    public android.os.Bundle getBundle() { return mValue instanceof android.os.Bundle ? (android.os.Bundle) mValue : null; }

    /** AOSP getRemoteInput(): the RemoteInput carried by a FORMAT_REMOTE_INPUT
     *  item (Slice.Builder.addRemoteInput), null for every other format --
     *  which is the same guard shape as the seven getters above. */
    public android.app.RemoteInput getRemoteInput() {
        return mValue instanceof android.app.RemoteInput ? (android.app.RemoteInput) mValue : null;
    }

    // ---- AOSP API-36 surface completion: Parcelable ----
    // A SliceItem crosses the Binder from the SliceProvider to the slice host.
    // The value slot is polymorphic, so the wire form tags it with the format
    // and writes the matching payload -- which is what AOSP's own
    // writeToParcel does.

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mFormat);
        dest.writeString(mSubType);
        dest.writeStringList(mHints);
        if (FORMAT_TEXT.equals(mFormat)) {
            dest.writeCharSequence(getText());
        } else if (FORMAT_INT.equals(mFormat)) {
            dest.writeInt(getInt());
        } else if (FORMAT_LONG.equals(mFormat)) {
            dest.writeLong(getLong());
        } else if (FORMAT_IMAGE.equals(mFormat)) {
            dest.writeTypedObject(getIcon(), flags);
        } else if (FORMAT_ACTION.equals(mFormat)) {
            dest.writeTypedObject(getAction(), flags);
        } else if (FORMAT_SLICE.equals(mFormat)) {
            dest.writeTypedObject(getSlice(), flags);
        } else if (FORMAT_REMOTE_INPUT.equals(mFormat)) {
            dest.writeTypedObject(getRemoteInput(), flags);
        } else if (FORMAT_BUNDLE.equals(mFormat)) {
            dest.writeBundle(getBundle());
        }
    }

    private static SliceItem fromParcel(android.os.Parcel in) {
        String format = in.readString();
        String subType = in.readString();
        java.util.ArrayList<String> hints = new java.util.ArrayList<String>();
        in.readStringList(hints);
        Object value = null;
        if (FORMAT_TEXT.equals(format)) {
            value = in.readCharSequence();
        } else if (FORMAT_INT.equals(format)) {
            value = Integer.valueOf(in.readInt());
        } else if (FORMAT_LONG.equals(format)) {
            value = Long.valueOf(in.readLong());
        } else if (FORMAT_IMAGE.equals(format)) {
            value = in.readTypedObject(android.graphics.drawable.Icon.CREATOR);
        } else if (FORMAT_ACTION.equals(format)) {
            value = in.readTypedObject(android.app.PendingIntent.CREATOR);
        } else if (FORMAT_SLICE.equals(format)) {
            value = in.readTypedObject(Slice.CREATOR);
        } else if (FORMAT_REMOTE_INPUT.equals(format)) {
            value = in.readTypedObject(android.app.RemoteInput.CREATOR);
        } else if (FORMAT_BUNDLE.equals(format)) {
            value = in.readBundle();
        }
        return new SliceItem(format, subType, value, hints);
    }

    public static final android.os.Parcelable.Creator<SliceItem> CREATOR =
            new android.os.Parcelable.Creator<SliceItem>() {
        @Override public SliceItem createFromParcel(android.os.Parcel in) {
            return fromParcel(in);
        }
        @Override public SliceItem[] newArray(int size) { return new SliceItem[size]; }
    };
}
