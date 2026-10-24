// android.security.advancedprotection.AdvancedProtectionManager -- the API-36
// query surface for Android Advanced Protection Mode (@FlaggedApi
// android.security.aapm_api): a device-wide, user-enabled hardening mode that
// apps may READ so they can tighten their own behaviour to match.
//
// AOSP source: frameworks/base/core/java/android/security/advancedprotection/
// AdvancedProtectionManager.java (android16-release). Obtained on a device via
// Context.getSystemService(AdvancedProtectionManager.class).
//
// Mac.apk answer: the mode is a platform-wide setting owned by a system service
// we do not have, and the user has never enabled it -- so
// isAdvancedProtectionEnabled() is false and a registered callback simply never
// fires, because the state never changes. That is the same observable behaviour
// as a device where the user left the mode off, which is the default.
//
// Note this is a READ-ONLY surface. There is no API here to turn the mode ON,
// so answering false weakens nothing: it reports our state honestly, and an app
// that hardens itself when the mode is on is free to do so the moment a real
// platform reports true.
package android.security.advancedprotection;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;

public final class AdvancedProtectionManager {

    /** Notified when the device's advanced-protection state changes. */
    public interface Callback {
        void onAdvancedProtectionChanged(boolean enabled);
    }

    // Registered callbacks are held so unregister is coherent and so a caller
    // that registers twice does not leak; nothing ever dispatches, because the
    // state cannot change here.
    private final Map<Callback, Executor> mCallbacks =
            Collections.synchronizedMap(new WeakHashMap<Callback, Executor>());

    /** Mac.apk wiring, not AOSP API -- AOSP's constructor is @hide. */
    public AdvancedProtectionManager() {}

    /** False: advanced protection mode is not enabled on this device. */
    public boolean isAdvancedProtectionEnabled() { return false; }

    /** AOSP invokes the callback once with the CURRENT state on registration,
     *  then on every change. We do the first half faithfully; there is no
     *  second half, since the state is constant. */
    public void registerAdvancedProtectionCallback(Executor executor, Callback callback) {
        if (executor == null) throw new NullPointerException("executor == null");
        if (callback == null) throw new NullPointerException("callback == null");
        mCallbacks.put(callback, executor);
        final boolean enabled = isAdvancedProtectionEnabled();
        try {
            executor.execute(new Runnable() {
                @Override public void run() { callback.onAdvancedProtectionChanged(enabled); }
            });
        } catch (Throwable ignored) {
            // A rejected execution is the executor's business, not ours.
        }
    }

    public void unregisterAdvancedProtectionCallback(Callback callback) {
        if (callback == null) throw new NullPointerException("callback == null");
        mCallbacks.remove(callback);
    }
}
