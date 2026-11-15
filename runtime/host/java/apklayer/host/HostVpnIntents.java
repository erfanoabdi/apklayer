package apklayer.host;

import android.app.Activity;
import android.content.Intent;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;

/**
 * VpnService.prepare()'s Intent (VpnConfig.getIntentForConfirmation: com.android.vpndialogs' ConfirmDialog, a package an instance does not have): the
 * hub asks the person "Allow X to set up a VPN connection?" (HubVpn.consent) and the requester gets RESULT_OK once they agreed, RESULT_CANCELED
 * otherwise; the agreement is remembered for the app.
 */
final class HostVpnIntents {
    private HostVpnIntents() {}

    static boolean handle(Intent intent, Activity requester, boolean wantsResult) {
        if (intent.getComponent() == null || !"com.android.vpndialogs".equals(intent.getComponent().getPackageName()) || !HubClient.connected()) return false;
        Thread asking = new Thread(() -> {
            boolean agreed;
            try { agreed = HubClient.current().vpnConsent(); }
            catch (RuntimeException noHub) { agreed = false; }
            System.out.println("[apklayer] VPN consent " + (agreed ? "given" : "refused"));
            boolean ok = agreed;
            if (requester != null) requester.runOnUiThread(() -> {
                if (wantsResult && !requester.isDestroyed()) ((ShadowActivity) Shadow.extract(requester)).receiveResult(intent, ok ? Activity.RESULT_OK : Activity.RESULT_CANCELED, null);
            });
        }, "vpn-consent");
        asking.setDaemon(true);
        asking.start();
        return true;
    }
}
