package android.service.settings.preferences;

import android.content.Intent;
import android.os.IBinder;
import android.os.OutcomeReceiver;

/**
 * Base for an app that exposes its own preferences to the Settings catalyst
 * API.
 *
 * See the package note on SettingsPreferenceValue: nothing binds this here, so
 * the three abstract callbacks never fire.
 *
 * Constant transcribed verbatim from /tmp/aosp_api/android16.txt:
 *   ACTION_PREFERENCE_SERVICE =
 *     "android.service.settings.preferences.action.PREFERENCE_SERVICE"
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/SettingsPreferenceService.java
 */
public abstract class SettingsPreferenceService extends android.app.Service {

    public static final String ACTION_PREFERENCE_SERVICE =
            "android.service.settings.preferences.action.PREFERENCE_SERVICE";

    public SettingsPreferenceService() { }

    public abstract void onGetAllPreferenceMetadata(MetadataRequest request,
            OutcomeReceiver<MetadataResult, Exception> callback);

    public abstract void onGetPreferenceValue(GetValueRequest request,
            OutcomeReceiver<GetValueResult, Exception> callback);

    public abstract void onSetPreferenceValue(SetValueRequest request,
            OutcomeReceiver<SetValueResult, Exception> callback);

    /** AOSP declares this final and @NonNull, returning the preference binder.
     *  android.app.Service.onBind already returns null in this tree. */
    @Override public final IBinder onBind(Intent intent) { return null; }
}
