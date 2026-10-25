// android.util.TimeFormatException -- thrown by android.text.format.Time
// when parsing a malformed time string. AOSP:
// frameworks/base/core/java/android/util/TimeFormatException.java
//
// The public API declares NO public constructor (AOSP's is package-private),
// so apps can catch it but cannot construct one. Mirrored exactly: widening
// it to public would let an app compile against a constructor the real
// platform does not have, which is a divergence in the wrong direction.
package android.util;

public class TimeFormatException extends RuntimeException {
    /** @hide package-private in AOSP */
    TimeFormatException() {
        super();
    }

    /** @hide package-private in AOSP */
    TimeFormatException(String s) {
        super(s);
    }
}
