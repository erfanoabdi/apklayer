// android.view.AbsSavedState -- baseclass for View / Preference saved
// instance state passed across activity recreation. AOSP signature:
//   public abstract class AbsSavedState implements Parcelable
// with the EMPTY_STATE singleton + a getSuperState() chain.
//
// androidx.preference.Preference and SeekBarPreference subclass this
// for their SavedState inner classes. Without the type symbol,
// LiveSeekBarPreference's <init> fails class-load at the constant
// pool resolution step.
package android.view;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class AbsSavedState implements Parcelable {

    /** AOSP-public empty marker. Used by Views that have no state to
     *  save -- chain to this so onSaveInstanceState always returns a
     *  non-null value. */
    public static final AbsSavedState EMPTY_STATE = new AbsSavedState((Parcelable) null) {
        @Override public int  describeContents()                { return 0; }
        @Override public void writeToParcel(Parcel out, int flags) {}
    };

    private final Parcelable mSuperState;

    /** AOSP-spec constructor -- subclasses chain `super(superState)`.
     *  The super state must be non-null and distinct from EMPTY_STATE
     *  (otherwise we'd loop). */
    protected AbsSavedState(Parcelable superState) {
        this.mSuperState = (superState == EMPTY_STATE) ? null : superState;
    }

    /** Constructor for unmarshaling from a Parcel. AOSP signature. */
    protected AbsSavedState(Parcel source) {
        this.mSuperState = null;  // Parcel-based restore is not honored
    }
    protected AbsSavedState(Parcel source, ClassLoader loader) {
        this.mSuperState = null;
    }

    public final Parcelable getSuperState() { return mSuperState; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel out, int flags) {
        // AOSP writes the super state's parcelable identity. Apps
        // typically chain to super.writeToParcel and then write
        // their own fields. We accept and discard.
    }

    /** AOSP AbsSavedState.CREATOR -- a ClassLoaderCreator whose createFromParcel
     *  asserts the super state is null and hands back EMPTY_STATE. AOSP's body:
     *      Parcelable superState = in.readParcelable(loader);
     *      if (superState != null) throw new IllegalStateException("superState must be null");
     *      return EMPTY_STATE;
     *  A missing CREATOR is not a missing feature: Parcel.readParcelable resolves
     *  a Parcelable's creator by NAME, so its absence is a runtime failure for
     *  any subclass that chains through AbsSavedState. */
    public static final Parcelable.Creator<AbsSavedState> CREATOR =
            new Parcelable.ClassLoaderCreator<AbsSavedState>() {
        @Override public AbsSavedState createFromParcel(Parcel in) {
            return createFromParcel(in, null);
        }
        @Override public AbsSavedState createFromParcel(Parcel in, ClassLoader loader) {
            Parcelable superState = in.readParcelable(loader);
            if (superState != null) {
                throw new IllegalStateException("superState must be null");
            }
            return EMPTY_STATE;
        }
        @Override public AbsSavedState[] newArray(int size) {
            return new AbsSavedState[size];
        }
    };
}
