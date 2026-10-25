// android.view.autofill.AutofillId -- a unique identifier for an autofillable
// view (or virtual child). Mirrors AOSP
// frameworks/base/core/java/android/view/autofill/AutofillId.java enough for
// callers that only treat it as an opaque, equals/hashCode-comparable handle.
//
// Jetpack Compose's AndroidComposeView creates an AndroidAutofill in its
// constructor (API 26+) which calls View.getAutofillId() and stores the
// result; if getAutofillId() returns null / the wrong type, Compose fails to
// initialize (Reddit's RedditComposeView.setContent NoSuchMethodError'd on
// View.getAutofillId() returning Object instead of AutofillId). So
// View.getAutofillId() must return a real AutofillId of THIS exact type. We
// back it with the view's id and implement value equality so it works as a
// map key.
package android.view.autofill;

// AOSP declares `public final class AutofillId implements Parcelable`. The
// missing `implements` is invisible to a member-level API diff but loud at
// runtime: an AutofillId travels inside Bundles and Intent extras on every
// autofill path, and a failed TYPE check there is silent where a
// NoSuchMethodError would at least be diagnosable.
public final class AutofillId implements android.os.Parcelable {
    private final int mViewId;
    private final int mVirtualIntId;
    private final boolean mVirtual;

    public AutofillId() { this(-1); }
    public AutofillId(int id) {
        mViewId = id;
        mVirtualIntId = 0;
        mVirtual = false;
    }
    public AutofillId(AutofillId host, int virtualChildId) {
        mViewId = host != null ? host.mViewId : -1;
        mVirtualIntId = virtualChildId;
        mVirtual = true;
    }

    public int getViewId() { return mViewId; }

    /** @hide */
    public boolean isVirtualInt() { return mVirtual; }

    @Override
    public int hashCode() {
        int result = 31 + mViewId;
        result = 31 * result + mVirtualIntId;
        result = 31 * result + (mVirtual ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AutofillId)) return false;
        AutofillId other = (AutofillId) obj;
        return mViewId == other.mViewId
            && mVirtualIntId == other.mVirtualIntId
            && mVirtual == other.mVirtual;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(mViewId);
        if (mVirtual) sb.append(':').append(mVirtualIntId);
        return sb.toString();
    }

    // ---- AOSP API-36 surface completion (android.view lane) -----------------

    /** AOSP AutofillId.create(View, int virtualId) (API 36): build the id of a
     *  VIRTUAL child of the given host view -- the same thing the
     *  AutofillId(AutofillId, int) constructor above does, from the host View
     *  rather than its id. */
    public static AutofillId create(android.view.View host, int virtualId) {
        java.util.Objects.requireNonNull(host);
        return new AutofillId(host.getAutofillId(), virtualId);
    }

    /** AOSP AutofillId.isVirtual() (API 36): does this id name a virtual child
     *  rather than a real View? Same state isVirtualInt() already reports. */
    public boolean isVirtual() { return isVirtualInt(); }

    /** AOSP AutofillId.getAutofillVirtualId() (API 36): the virtual child id,
     *  meaningful only when isVirtual(). */
    public int getAutofillVirtualId() { return mVirtualIntId; }

    /** AOSP AutofillId.getSessionId() / isInAutofillSession() (API 36): which
     *  autofill SESSION this id belongs to. AOSP's NO_SESSION sentinel is 0, and
     *  an id built by an app (which is every id here -- Mac.apk bridges no
     *  autofill service, so no session is ever created) carries no session. */
    public int getSessionId() { return 0; }
    public boolean isInAutofillSession() { return false; }

    // Parcelable. The three fields ARE the whole identity, so unlike Surface or
    // AccessibilityNodeInfo this really does marshal and rebuild.
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mViewId);
        dest.writeInt(mVirtualIntId);
        dest.writeBoolean(mVirtual);
    }
    public static final android.os.Parcelable.Creator<AutofillId> CREATOR =
            new android.os.Parcelable.Creator<AutofillId>() {
        @Override public AutofillId createFromParcel(android.os.Parcel source) {
            final int viewId = source.readInt();
            final int virtualIntId = source.readInt();
            final boolean virtual = source.readBoolean();
            AutofillId id = new AutofillId(viewId);
            return virtual ? new AutofillId(id, virtualIntId) : id;
        }
        @Override public AutofillId[] newArray(int size) { return new AutofillId[size]; }
    };
}
