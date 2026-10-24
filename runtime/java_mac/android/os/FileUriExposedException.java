// android.os.FileUriExposedException -- thrown by StrictMode when a file:// Uri
// is exposed beyond the app (e.g. handed to another app via Intent). Mac.apk
// does not enforce this, but apps reference the type. extends RuntimeException.
//
// AOSP ref: frameworks/base/core/java/android/os/FileUriExposedException.java
package android.os;

public class FileUriExposedException extends RuntimeException {
    public FileUriExposedException(String message) { super(message); }
}
