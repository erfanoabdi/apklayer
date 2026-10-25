// android.os.strictmode.WebViewMethodCalledOnWrongThreadViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/WebViewMethodCalledOnWrongThreadViolation.java
// (android16-release). Constructor bodies verbatim.
package android.os.strictmode;

public final class WebViewMethodCalledOnWrongThreadViolation extends Violation {
    public WebViewMethodCalledOnWrongThreadViolation(Throwable originStack) {
        super(null);
        setStackTrace(originStack.getStackTrace());
    }
}
