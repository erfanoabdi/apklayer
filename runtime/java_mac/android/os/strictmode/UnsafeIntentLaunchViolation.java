// android.os.strictmode.UnsafeIntentLaunchViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/
// UnsafeIntentLaunchViolation.java (android16-release). The Intent field is
// `transient` upstream too (a Violation is Serializable through Throwable, and
// an Intent is not).
package android.os.strictmode;

import android.content.Intent;

import java.util.Objects;

public final class UnsafeIntentLaunchViolation extends Violation {
    private transient Intent mIntent;

    public UnsafeIntentLaunchViolation(Intent intent) {
        super("Launch of unsafe intent: " + intent);
        mIntent = Objects.requireNonNull(intent);
    }

    public UnsafeIntentLaunchViolation(Intent intent, String message) {
        super(message);
        mIntent = Objects.requireNonNull(intent);
    }

    public Intent getIntent() {
        return mIntent;
    }
}
