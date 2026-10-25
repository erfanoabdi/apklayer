package android.location;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * Base class for a service that injects a row into the system Location settings
 * screen.
 *
 * Mac.apk has no Settings app, so nothing ever binds to a subclass of this.
 * The class is still implemented faithfully rather than stubbed: an app can
 * declare and start the service itself, and AOSP's contract -- onStartCommand
 * calls onGetSummary()/onGetEnabled() and replies through the Messenger in the
 * intent, then stops itself and returns START_NOT_STICKY -- is entirely
 * in-process and costs nothing to honour.
 *
 * Source: frameworks/base/location/java/android/location/SettingInjectorService.java
 */
public abstract class SettingInjectorService extends Service {

    public static final String ACTION_SERVICE_INTENT = "android.location.SettingInjectorService";

    public static final String META_DATA_NAME = "android.location.SettingInjectorService";

    public static final String ATTRIBUTES_NAME = "injected-location-setting";

    public static final String ACTION_INJECTED_SETTING_CHANGED =
            "android.location.InjectedSettingChanged";

    /** @hide in AOSP, but part of the documented messenger protocol. */
    public static final String SUMMARY_KEY = "summary";
    /** @hide in AOSP. */
    public static final String ENABLED_KEY = "enabled";
    /** @hide in AOSP. */
    public static final String MESSENGER_KEY = "messenger";

    private final String mName;

    public SettingInjectorService(String name) { mName = name; }

    @Override public final IBinder onBind(Intent intent) { return null; }

    @Override public final void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override public final int onStartCommand(Intent intent, int flags, int startId) {
        onHandleIntent(intent);
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private void onHandleIntent(Intent intent) {
        String summary = null;
        boolean enabled = false;
        try {
            summary = onGetSummary();
            enabled = onGetEnabled();
        } finally {
            // AOSP: send the status even when the callbacks threw, so the injector
            // can move on; the exception is deliberately left uncaught.
            sendStatus(intent, summary, enabled);
        }
    }

    private void sendStatus(Intent intent, String summary, boolean enabled) {
        Messenger messenger = intent.getParcelableExtra(MESSENGER_KEY, Messenger.class);
        // AOSP bails out rather than crashing on a malicious intent with no messenger.
        if (messenger == null) {
            return;
        }
        Message message = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putString(SUMMARY_KEY, summary);
        bundle.putBoolean(ENABLED_KEY, enabled);
        message.setData(bundle);
        try {
            messenger.send(message);
        } catch (RemoteException e) {
            // AOSP logs and swallows; the receiver going away is not the service's problem.
        }
    }

    protected abstract String onGetSummary();

    protected abstract boolean onGetEnabled();

    public static final void refreshSettings(Context context) {
        Intent intent = new Intent(ACTION_INJECTED_SETTING_CHANGED);
        context.sendBroadcast(intent);
    }

    /** Name passed to the constructor, used by AOSP only for log messages. */
    protected final String getInjectorName() { return mName; }
}
