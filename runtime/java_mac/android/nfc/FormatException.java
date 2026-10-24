// android.nfc.FormatException
//
// Thrown when an NDEF binary cannot be parsed. Mirrors
// frameworks/base/core/java/android/nfc/FormatException.java.
package android.nfc;

public class FormatException extends Exception {
    public FormatException() { super(); }
    public FormatException(String message) { super(message); }
    public FormatException(String message, Throwable e) { super(message, e); }
}
