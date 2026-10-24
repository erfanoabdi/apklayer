// android.os.ParcelFormatException -- thrown when a Parcel's data is malformed
// during a read. extends RuntimeException, matching AOSP.
//
// AOSP ref: frameworks/base/core/java/android/os/ParcelFormatException.java
package android.os;

public class ParcelFormatException extends RuntimeException {
    public ParcelFormatException() { super(); }
    public ParcelFormatException(String reason) { super(reason); }
}
