// android.util.AndroidRuntimeException -- base class for all unchecked
// exceptions thrown by the Android frameworks (android.app.Fragment's
// InstantiationException extends it, among others). Mirrors AOSP's
// frameworks/base/core/java/android/util/AndroidRuntimeException.java.
package android.util;
public class AndroidRuntimeException extends RuntimeException {
    public AndroidRuntimeException() { super(); }
    public AndroidRuntimeException(String name) { super(name); }
    public AndroidRuntimeException(String name, Throwable cause) { super(name, cause); }
    // AOSP types this parameter Exception, not Throwable -- a guest compiled
    // against real AOSP emits invokespecial with descriptor
    // (Ljava/lang/Exception;)V, which a (Ljava/lang/Throwable;)V overload here
    // does not match (NoSuchMethodError). Found by manual AOSP diff while
    // fixing the sibling android.os.RemoteException/android.util.AndroidException
    // superclass chain.
    public AndroidRuntimeException(Exception cause) { super(cause); }
}
