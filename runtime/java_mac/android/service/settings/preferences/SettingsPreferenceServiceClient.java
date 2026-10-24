package android.service.settings.preferences;

import android.content.Context;
import android.os.OutcomeReceiver;

import java.util.concurrent.Executor;

/**
 * The CLIENT half of the Settings catalyst API: binds another app's
 * SettingsPreferenceService and reads or writes its preferences.
 *
 * ⚠ THIS IS THE ONE CLASS IN THE PACKAGE WHERE THE HONEST ANSWER MATTERS.
 * Mac.apk runs one app: there is no second app hosting a
 * SettingsPreferenceService to bind, and a guest does not get to read or write
 * the host's settings. So the constructor's connection receiver reports a
 * FAILURE, and every request reports a failure too.
 *
 * It reports rather than hangs on purpose. Each of these methods takes an
 * OutcomeReceiver, and a receiver that is never called leaves the caller
 * waiting forever -- which on a device is a bug, not a state. AOSP calls
 * onError with an IllegalStateException when the service cannot be bound, so
 * that is what happens here and the app's own error path runs.
 *
 * See the package note on SettingsPreferenceValue.
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/SettingsPreferenceServiceClient.java
 */
public class SettingsPreferenceServiceClient implements AutoCloseable {

    private final String mPackageName;
    private boolean mClosed;

    public SettingsPreferenceServiceClient(Context context, String packageName,
            Executor executor,
            OutcomeReceiver<SettingsPreferenceServiceClient, Exception> callback) {
        if (context == null) throw new NullPointerException("context");
        if (packageName == null) throw new NullPointerException("packageName");
        mPackageName = packageName;
        fail(executor, callback);
    }

    public void getAllPreferenceMetadata(MetadataRequest request, Executor executor,
            OutcomeReceiver<MetadataResult, Exception> callback) {
        fail(executor, callback);
    }

    public void getPreferenceValue(GetValueRequest request, Executor executor,
            OutcomeReceiver<GetValueResult, Exception> callback) {
        fail(executor, callback);
    }

    public void setPreferenceValue(SetValueRequest request, Executor executor,
            OutcomeReceiver<SetValueResult, Exception> callback) {
        fail(executor, callback);
    }

    @Override public void close() { mClosed = true; }

    private void fail(Executor executor,
            final OutcomeReceiver<?, Exception> callback) {
        if (callback == null) return;
        final Exception e = new IllegalStateException(
                "No SettingsPreferenceService available for " + mPackageName);
        Runnable r = new Runnable() {
            @Override public void run() { callback.onError(e); }
        };
        if (executor != null) executor.execute(r); else r.run();
    }
}
