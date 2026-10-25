// android.os.NetworkOnMainThreadException -- thrown by StrictMode when an app
// performs a networking operation on the main thread. Mac.apk does not enforce
// StrictMode, so this is never thrown by us, but apps reference the type (catch
// blocks, instanceof checks). extends RuntimeException, matching AOSP.
//
// AOSP ref: frameworks/base/core/java/android/os/NetworkOnMainThreadException.java
package android.os;

public class NetworkOnMainThreadException extends RuntimeException {
    public NetworkOnMainThreadException() {}
}
