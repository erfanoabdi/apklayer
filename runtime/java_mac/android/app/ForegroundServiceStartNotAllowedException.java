package android.app;

import android.os.Parcel;
import android.os.Parcelable;

/** Thrown when an app tries to start a foreground service when it is not
 *  allowed to (API 31).
 *
 *  AOSP ref: frameworks/base/core/java/android/app/
 *  ForegroundServiceStartNotAllowedException.java (android14-release),
 *  mirrored member for member: final, extends ServiceStartNotAllowedException
 *  (so catch (ServiceStartNotAllowedException) and
 *  catch (IllegalStateException) both see it, and getCause() is always null
 *  as the base class requires), a single public (String) constructor, and
 *  Parcelable because the ActivityManager marshals the exception back to the
 *  caller across the Binder that rejected the start. AOSP parcels only the
 *  message. The SDK has no public no-arg constructor, so neither do we; no
 *  caller in runtime/java used ours.
 *
 *  Re-parented 2026-09-26 (docs/fwgap-new-platform-classes_2026-09-26.md,
 *  patch P1); debug/fwgap_gate.sh checks this shape (touched.txt) and the
 *  hierarchy (FwGapProbe), and fails if either regresses. */
public final class ForegroundServiceStartNotAllowedException
        extends ServiceStartNotAllowedException implements Parcelable {
    public ForegroundServiceStartNotAllowedException(String message) {
        super(message);
    }

    ForegroundServiceStartNotAllowedException(Parcel source) {
        super(source.readString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
    }

    public static final Creator<ForegroundServiceStartNotAllowedException> CREATOR =
            new Creator<ForegroundServiceStartNotAllowedException>() {
                @Override
                public ForegroundServiceStartNotAllowedException createFromParcel(Parcel source) {
                    return new ForegroundServiceStartNotAllowedException(source);
                }

                @Override
                public ForegroundServiceStartNotAllowedException[] newArray(int size) {
                    return new ForegroundServiceStartNotAllowedException[size];
                }
            };
}
