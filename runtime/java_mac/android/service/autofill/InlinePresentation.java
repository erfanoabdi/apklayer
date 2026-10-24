package android.service.autofill;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * API 30 inline suggestion: the Slice that renders one chip in the keyboard's
 * suggestion strip, plus whether it is pinned.
 *
 * ⚠ INCOMPLETE, and deliberately so. AOSP's constructor,
 * createTooltipPresentation() and getInlinePresentationSpec() all name
 * android.widget.inline.InlinePresentationSpec, which does not exist anywhere
 * in this tree (the whole android.widget.inline package is absent). Declaring
 * them would not compile. The three members are therefore MISSING here and are
 * reported as a residual gap rather than faked; adding
 * android.widget.inline.InlinePresentationSpec unblocks them, and this class is
 * where to come back to.
 *
 * The rest is faithful. Mac.apk shows no inline suggestion strip -- there is no
 * IME surface to host one -- so a presentation is never rendered, exactly as on
 * a device whose keyboard does not support inline suggestions.
 *
 * Source: frameworks/base/core/java/android/service/autofill/InlinePresentation.java
 */
public final class InlinePresentation implements Parcelable {

    private final Slice mSlice;
    private final boolean mPinned;

    // Package-visible until InlinePresentationSpec exists; see the class note.
    InlinePresentation(Slice slice, boolean pinned) {
        if (slice == null) throw new NullPointerException("slice");
        mSlice = slice;
        mPinned = pinned;
    }

    public Slice getSlice() { return mSlice; }

    public boolean isPinned() { return mPinned; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        mSlice.writeToParcel(dest, flags);
        dest.writeBoolean(mPinned);
    }

    @Override public String toString() {
        return "InlinePresentation[pinned=" + mPinned + "]";
    }

    public static final Creator<InlinePresentation> CREATOR =
            new Creator<InlinePresentation>() {
        @Override public InlinePresentation createFromParcel(Parcel in) {
            Slice slice = Slice.CREATOR.createFromParcel(in);
            boolean pinned = in.readBoolean();
            return new InlinePresentation(slice, pinned);
        }
        @Override public InlinePresentation[] newArray(int size) {
            return new InlinePresentation[size];
        }
    };
}
