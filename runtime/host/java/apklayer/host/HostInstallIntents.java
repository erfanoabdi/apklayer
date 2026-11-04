package apklayer.host;

import android.app.Activity;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.os.IBinder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.util.List;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;

/**
 * The package installer's activities on a phone (PackageInstaller's InstallStart and UninstallerActivity, Settings' "install unknown apps"):
 * ACTION_INSTALL_PACKAGE and ACTION_VIEW of an APK, ACTION_DELETE and ACTION_UNINSTALL_PACKAGE, ACTION_MANAGE_UNKNOWN_APP_SOURCES. They go to
 * the instance hub's installer, which asks the person (HubInstaller). The APK is opened here, with the app's own access to its URI. A
 * startActivityForResult gets its result when the hub is done, as the PackageInstaller app sets it: with EXTRA_RETURN_RESULT RESULT_OK or
 * RESULT_FIRST_USER and EXTRA_INSTALL_RESULT (the legacy INSTALL_* / DELETE_* code), RESULT_CANCELED when the person said no or the caller did not ask for it.
 */
final class HostInstallIntents {
    private static final String APK = "application/vnd.android.package-archive";

    private HostInstallIntents() {}

    /** True when the intent was one of these and went to the hub; {@code requester}: the activity that waits for its result (null: none). */
    static boolean handle(Intent intent, Activity requester) {
        String action = intent.getAction();
        if (action == null || !HubClient.connected()) return false;
        Uri data = intent.getData();
        boolean install = Intent.ACTION_INSTALL_PACKAGE.equals(action) || (Intent.ACTION_VIEW.equals(action) && data != null && APK.equals(type(intent)));
        if (install && data != null) {
            try (ParcelFileDescriptor apk = RuntimeEnvironment.getApplication().getContentResolver().openFileDescriptor(data, "r")) {
                HubClient.current().commitInstall(List.of("base.apk"), List.of(apk), result(intent, requester));
            } catch (java.io.IOException | RuntimeException failure) { System.out.println("[apklayer] cannot install " + data + ": " + failure); }
            return true;
        }
        if ((Intent.ACTION_DELETE.equals(action) || Intent.ACTION_UNINSTALL_PACKAGE.equals(action)) && data != null && "package".equals(data.getScheme())) {
            HubClient.current().uninstall(data.getSchemeSpecificPart(), result(intent, requester), true);
            return true;
        }
        if (android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES.equals(action)) {
            HubClient.current().installSource(true);
            return true;
        }
        return false;
    }

    /** The hub's final status as the requester's activity result; null without a requester. */
    private static IntentSender result(Intent request, Activity requester) {
        if (requester == null) return null;
        boolean returnResult = request.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false);
        return new IntentSender((IIntentSender) new IIntentSender.Stub() {
            @Override public void send(int code, Intent fill, String type, IBinder token, IIntentReceiver finished, String permission, Bundle options) {
                int status = fill.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                int result = !returnResult || status == PackageInstaller.STATUS_FAILURE_ABORTED ? Activity.RESULT_CANCELED
                        : status == PackageInstaller.STATUS_SUCCESS ? Activity.RESULT_OK : Activity.RESULT_FIRST_USER;
                Intent data = returnResult ? new Intent().putExtra(Intent.EXTRA_INSTALL_RESULT, fill.getIntExtra(PackageInstaller.EXTRA_LEGACY_STATUS, 0)) : null;
                requester.runOnUiThread(() -> { if (!requester.isDestroyed()) ((ShadowActivity) Shadow.extract(requester)).receiveResult(request, result, data); });
            }
        });
    }

    /** The intent's type, or its content URI's (Intent.resolveTypeIfNeeded, as startActivity resolves it on Android). */
    private static String type(Intent intent) {
        if (intent.getType() != null || !"content".equals(intent.getData().getScheme())) return intent.getType();
        try { return RuntimeEnvironment.getApplication().getContentResolver().getType(intent.getData()); }
        catch (RuntimeException unknown) { return null; }
    }
}
