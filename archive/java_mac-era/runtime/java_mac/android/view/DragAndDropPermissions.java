// android.view.DragAndDropPermissions -- wraps the content-URI grant a
// DragEvent's ClipData carries (FLAG_GRANT_READ/WRITE_URI_PERMISSION),
// obtained via Activity.requestDragAndDropPermissions(DragEvent) or
// DragAndDropPermissions.obtain(DragEvent). AOSP wraps a Binder handle
// (IDragAndDropPermissions) whose lifetime the system DragDropController
// owns; Mac.apk is a single process with no cross-app URI grant to revoke,
// so a plain granted/released flag pair stands in for it and
// take()/takeTransient()/release() are idempotent local state instead of
// Binder calls.
//
// Scope note: this class's OWN contract (Parcelable shape, take/
// takeTransient/release) is implemented here in full. The static
// `obtain(DragEvent)` factory AOSP declares on this class, and
// Activity.requestDragAndDropPermissions(DragEvent), both need
// DragEvent.getClipData() to decide whether a grant exists -- DragEvent.java
// is currently an empty stub (no methods at all) owned outside this file,
// so that wiring is NOT done here. The package-private constructor below is
// what a future DragEvent-aware Activity.requestDragAndDropPermissions
// implementation should call once it can inspect the event's ClipData.
//
// AOSP source mirror: frameworks/base/core/java/android/view/
// DragAndDropPermissions.java.
package android.view;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

public final class DragAndDropPermissions implements Parcelable {
    private boolean granted;
    private boolean released;

    /** Package-private: constructed once a caller (Activity.
     *  requestDragAndDropPermissions, or this class's own obtain(DragEvent))
     *  has determined the drag event actually carries a URI grant. Real
     *  APK bytecode never constructs this class directly -- AOSP's own
     *  constructor is private, obtained only via the system -- so there is
     *  no third-party-compiled call site to stay binary-compatible with
     *  here. */
    DragAndDropPermissions(boolean granted) {
        this.granted = granted;
    }
    private DragAndDropPermissions(Parcel in) {
        granted = in.readInt() != 0;
        released = in.readInt() != 0;
    }

    /** AOSP: binds the permission's lifetime to the given Activity (revoked
     *  automatically when that activity is destroyed); returns whether the
     *  grant is still valid to take. Mac.apk has a single Activity host
     *  process rather than AOSP's separate ActivityClientController
     *  lifecycle to bind to, so the observable result collapses to "was
     *  this ever granted and not yet released". */
    public boolean take(IBinder activityToken) {
        return granted && !released;
    }
    /** AOSP: binds the permission's lifetime to this object instead of an
     *  Activity (released on GC or an explicit release() call). Same
     *  local-only semantics as take() in this single-process model. */
    public boolean takeTransient() {
        return granted && !released;
    }
    /** AOSP: explicitly revoke early. Idempotent -- a real device's repeat
     *  call is a Binder no-op too, since IDragAndDropPermissions#release
     *  itself tolerates a second revoke. */
    public void release() {
        released = true;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(granted ? 1 : 0);
        dest.writeInt(released ? 1 : 0);
    }
    public static final Parcelable.Creator<DragAndDropPermissions> CREATOR =
        new Parcelable.Creator<DragAndDropPermissions>() {
            @Override public DragAndDropPermissions createFromParcel(Parcel in) {
                return new DragAndDropPermissions(in);
            }
            @Override public DragAndDropPermissions[] newArray(int size) {
                return new DragAndDropPermissions[size];
            }
        };
}
