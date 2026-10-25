// android.util.Base64DataException -- AOSP-faithful (frameworks/base
// core/java/android/util/Base64DataException.java). Thrown by
// Base64OutputStream / Base64InputStream when the underlying Base64.Coder
// rejects malformed base-64 input. Mirrors AOSP exactly (an IOException
// subclass). Needed so those stream classes link.
package android.util;

import java.io.IOException;

/**
 * This exception is thrown by {@link Base64InputStream} or {@link
 * Base64OutputStream} when an error is detected in the data being
 * decoded.  This allows problems with the base64 data to be detected
 * even when it is embedded in the read()/write() interface of a
 * stream.
 */
public class Base64DataException extends IOException {
    public Base64DataException(String detailMessage) {
        super(detailMessage);
    }
}
