package android.service.quickaccesswallet;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.IBinder;

/**
 * Base for a wallet app's quick-access provider.
 *
 * PACKAGE NOTE -- see GetWalletCardsRequest. System UI's power-button wallet
 * binds this service; Mac.apk has no such surface, so nothing binds it and none
 * of the three abstract callbacks fires. The request/response value objects are
 * faithful, and nothing here handles a payment credential -- AOSP's WalletCard
 * carries only an Icon, a label and a PendingIntent back into the wallet app.
 *
 * Constants transcribed verbatim from /tmp/aosp_api/android16.txt.
 *
 * Source: frameworks/base/core/java/android/service/quickaccesswallet/QuickAccessWalletService.java
 */
public abstract class QuickAccessWalletService extends android.app.Service {

    public static final String SERVICE_INTERFACE =
            "android.service.quickaccesswallet.QuickAccessWalletService";
    public static final String SERVICE_META_DATA = "android.quickaccesswallet";
    public static final String ACTION_VIEW_WALLET =
            "android.service.quickaccesswallet.action.VIEW_WALLET";
    public static final String ACTION_VIEW_WALLET_SETTINGS =
            "android.service.quickaccesswallet.action.VIEW_WALLET_SETTINGS";

    public QuickAccessWalletService() { }

    public abstract void onWalletCardsRequested(GetWalletCardsRequest request,
            GetWalletCardsCallback callback);

    public abstract void onWalletCardSelected(SelectWalletCardRequest request);

    public abstract void onWalletDismissed();

    /** AOSP returns the wallet binder. android.app.Service.onBind already
     *  returns null in this tree, so this override loses nothing. */
    @Override public IBinder onBind(Intent intent) { return null; }

    /** AOSP forwards the event to the bound wallet UI. Nothing is bound. */
    public final void sendWalletServiceEvent(WalletServiceEvent serviceEvent) { }

    /** API 33. AOSP's default returns null, meaning "use ACTION_VIEW_WALLET
     *  instead"; a service overrides it. */
    public PendingIntent getTargetActivityPendingIntent() { return null; }

    /** API 36 double-power-press target; same default as above. */
    public PendingIntent getGestureTargetActivityPendingIntent() { return null; }
}
