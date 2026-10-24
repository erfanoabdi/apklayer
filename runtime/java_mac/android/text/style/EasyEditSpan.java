// android.text.style.EasyEditSpan -- marks a range the editor should offer to
// delete or edit with one tap ("Undo the autocorrect"), optionally notifying a
// PendingIntent when the user does. Set by IMEs and by
// TextView's own suggestion machinery. Was absent entirely
// (NoClassDefFoundError). Mirrors
// frameworks/base/core/java/android/text/style/EasyEditSpan.java.
package android.text.style;

import android.app.PendingIntent;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextUtils;

public class EasyEditSpan implements ParcelableSpan {

    /**
     * AOSP EasyEditSpan.EXTRA_TEXT_CHANGED_TYPE -- the extra the notification
     * PendingIntent is fired with, whose value is TEXT_DELETED or
     * TEXT_MODIFIED.
     */
    public static final String EXTRA_TEXT_CHANGED_TYPE =
            "android.text.style.EXTRA_TEXT_CHANGED_TYPE";

    public static final int TEXT_DELETED = 1;
    public static final int TEXT_MODIFIED = 2;

    private final PendingIntent mPendingIntent;
    private boolean mDeleteEnabled;

    /** AOSP: an EasyEditSpan with no notification. */
    public EasyEditSpan() {
        mPendingIntent = null;
        mDeleteEnabled = true;
    }

    /**
     * AOSP: an EasyEditSpan that fires `pendingIntent` with
     * EXTRA_TEXT_CHANGED_TYPE when the covered text is deleted or modified.
     */
    public EasyEditSpan(PendingIntent pendingIntent) {
        mPendingIntent = pendingIntent;
        mDeleteEnabled = true;
    }

    /** AOSP EasyEditSpan(Parcel src). */
    public EasyEditSpan(Parcel src) {
        mPendingIntent = src.readParcelable(PendingIntent.class.getClassLoader(),
                PendingIntent.class);
        mDeleteEnabled = (src.readByte() == 1);
    }

    @Override
    public int getSpanTypeId() {
        return getSpanTypeIdInternal();
    }

    @Override
    public int getSpanTypeIdInternal() {
        return TextUtils.EASY_EDIT_SPAN;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }

    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeParcelable(mPendingIntent, flags);
        dest.writeByte((byte) (mDeleteEnabled ? 1 : 0));
    }

    /** @hide on AOSP; the editor reads it to decide whether to offer delete. */
    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    /** @hide on AOSP. */
    public void setDeleteEnabled(boolean value) {
        mDeleteEnabled = value;
    }

    /** @hide on AOSP. */
    public boolean isDeleteEnabled() {
        return mDeleteEnabled;
    }
}
