// android.os.BadParcelableException -- thrown when unmarshalling a Parcelable
// fails (missing CREATOR, unknown type tag, etc). Apps catch this around Bundle
// reads. extends AndroidRuntimeException, matching AOSP.
//
// AOSP ref: frameworks/base/core/java/android/os/BadParcelableException.java
package android.os;

public class BadParcelableException extends android.util.AndroidRuntimeException {
    public BadParcelableException(String msg) { super(msg); }
    public BadParcelableException(Exception cause) { super(cause); }
    public BadParcelableException(String msg, Throwable cause) { super(msg, cause); }
}
