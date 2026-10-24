package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseIntArray;

/**
 * API 29 OnClickAction that flips the visibility of one or more children of a
 * CustomDescription when the user taps a view (the "show password" eye).
 *
 * Pure value object with a builder. See the package note on AutofillService.
 *
 * Source: frameworks/base/core/java/android/service/autofill/VisibilitySetterAction.java
 */
public final class VisibilitySetterAction implements OnClickAction, Parcelable {

    private final SparseIntArray mVisibilities;

    private VisibilitySetterAction(SparseIntArray visibilities) {
        mVisibilities = visibilities;
    }

    SparseIntArray getVisibilities() { return mVisibilities; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        final int size = mVisibilities.size();
        final int[] ids = new int[size];
        final int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            ids[i] = mVisibilities.keyAt(i);
            values[i] = mVisibilities.valueAt(i);
        }
        dest.writeIntArray(ids);
        dest.writeIntArray(values);
    }

    @Override public String toString() {
        return "VisibilitySetterAction[" + mVisibilities + "]";
    }

    public static final Creator<VisibilitySetterAction> CREATOR =
            new Creator<VisibilitySetterAction>() {
        @Override public VisibilitySetterAction createFromParcel(Parcel parcel) {
            final int[] ids = parcel.createIntArray();
            final int[] values = parcel.createIntArray();
            final Builder builder = new Builder(ids[0], values[0]);
            for (int i = 1; i < ids.length; i++) {
                builder.setVisibility(ids[i], values[i]);
            }
            return builder.build();
        }
        @Override public VisibilitySetterAction[] newArray(int size) {
            return new VisibilitySetterAction[size];
        }
    };

    public static final class Builder {
        private final SparseIntArray mVisibilities = new SparseIntArray();
        private boolean mDestroyed;

        public Builder(int id, int visibility) {
            setVisibility(id, visibility);
        }

        /** AOSP validates against the three View visibility constants and
         *  throws otherwise, rather than silently storing a bad value. */
        public Builder setVisibility(int id, int visibility) {
            throwIfDestroyed();
            switch (visibility) {
                case android.view.View.VISIBLE:
                case android.view.View.INVISIBLE:
                case android.view.View.GONE:
                    mVisibilities.put(id, visibility);
                    return this;
                default:
                    throw new IllegalArgumentException("Invalid visibility: " + visibility);
            }
        }

        public VisibilitySetterAction build() {
            throwIfDestroyed();
            mDestroyed = true;
            return new VisibilitySetterAction(mVisibilities);
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called build()");
        }
    }
}
