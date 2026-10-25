package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;

/**
 * One activity's worth of a fill request: the request id, the view that has
 * focus, and (in AOSP) the AssistStructure snapshot of the view hierarchy.
 *
 * ⚠ INCOMPLETE, and deliberately so. getStructure() returns
 * android.app.assist.AssistStructure, which does not exist in this tree
 * (android/app/assist/ contains only AssistContent). Declaring the method
 * would not compile. It is therefore MISSING and reported as a residual gap
 * rather than faked with a wrong return type; adding
 * android.app.assist.AssistStructure unblocks it, and this class is where to
 * come back to.
 *
 * Source: frameworks/base/core/java/android/service/autofill/FillContext.java
 */
public final class FillContext implements Parcelable {

    private final int mRequestId;
    private final AutofillId mFocusedId;

    FillContext(int requestId, AutofillId focusedId) {
        mRequestId = requestId;
        mFocusedId = focusedId;
    }

    public int getRequestId() { return mRequestId; }

    public AutofillId getFocusedId() { return mFocusedId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mRequestId);
        parcel.writeTypedObject(mFocusedId, flags);
    }

    @Override public String toString() {
        return "FillContext[id=" + mRequestId + ", focused=" + mFocusedId + "]";
    }

    public static final Creator<FillContext> CREATOR = new Creator<FillContext>() {
        @Override public FillContext createFromParcel(Parcel parcel) {
            return new FillContext(parcel.readInt(),
                    parcel.readTypedObject(AutofillId.CREATOR));
        }
        @Override public FillContext[] newArray(int size) { return new FillContext[size]; }
    };
}
