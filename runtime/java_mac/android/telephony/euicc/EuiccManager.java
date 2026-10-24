package android.telephony.euicc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;

/**
 * Access to the embedded SIM (eUICC).
 *
 * A Mac has no eUICC and no LPA (the eSIM management app). isEnabled() reports
 * false, which is precisely the check AOSP tells apps to make first, and every
 * mutating call is a no-op that fires no callback intent -- because AOSP fires
 * those from the LPA, and inventing a success or a specific failure code would
 * be reporting an outcome from a component that does not exist.
 *
 * Source: frameworks/base/telephony/java/android/telephony/euicc/EuiccManager.java
 */
public class EuiccManager {

    public static final String ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS =
            "android.telephony.euicc.action.MANAGE_EMBEDDED_SUBSCRIPTIONS";
    public static final String ACTION_START_EUICC_ACTIVATION =
            "android.telephony.euicc.action.START_EUICC_ACTIVATION";
    public static final String ACTION_NOTIFY_CARRIER_SETUP_INCOMPLETE =
            "android.telephony.euicc.action.NOTIFY_CARRIER_SETUP_INCOMPLETE";
    public static final String META_DATA_CARRIER_ICON = "android.telephony.euicc.carriericon";

    public static final int EMBEDDED_SUBSCRIPTION_RESULT_OK = 0;
    public static final int EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR = 1;
    public static final int EMBEDDED_SUBSCRIPTION_RESULT_ERROR = 2;

    public static final String EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE =
            "android.telephony.euicc.extra.EMBEDDED_SUBSCRIPTION_DETAILED_CODE";

    /** AOSP's INVALID_CARD_ID. */
    public static final int INVALID_CARD_ID = -1;

    private final int mCardId;

    /** @hide -- obtained via Context.getSystemService(EUICC_SERVICE). */
    public EuiccManager() { this(INVALID_CARD_ID); }

    private EuiccManager(int cardId) { mCardId = cardId; }

    public EuiccManager createForCardId(int cardId) { return new EuiccManager(cardId); }

    /**
     * The gate AOSP tells every caller to check first. No eUICC hardware and no
     * LPA app, so false.
     */
    public boolean isEnabled() { return false; }

    /** AOSP returns null when there is no eUICC to read an EID from. */
    public String getEid() { return null; }

    /** AOSP returns null when the eUICC is unavailable. */
    public EuiccInfo getEuiccInfo() { return null; }

    /** No eUICC, so no port is available. */
    public boolean isSimPortAvailable(int portIndex) { return false; }

    /** AOSP returns the free eUICC storage; there is none. */
    public long getAvailableMemoryInBytes() { return 0L; }

    // The mutating operations below are all driven by the LPA on a device. With
    // no LPA there is nothing to drive them and nothing that can report back, so
    // the callback intent is deliberately NOT fired -- see the class comment.
    public void downloadSubscription(DownloadableSubscription subscription,
            boolean switchAfterDownload, PendingIntent callbackIntent) { }

    public void deleteSubscription(int subscriptionId, PendingIntent callbackIntent) { }

    public void switchToSubscription(int subscriptionId, PendingIntent callbackIntent) { }

    public void switchToSubscription(int subscriptionId, int portIndex,
            PendingIntent callbackIntent) { }

    public void updateSubscriptionNickname(int subscriptionId, String nickname,
            PendingIntent callbackIntent) { }

    /**
     * AOSP launches the LPA's resolution UI. There is no LPA, so nothing can be
     * started; AOSP throws SendIntentException when the sender cannot be sent,
     * which is exactly this case.
     */
    public void startResolutionActivity(Activity activity, int requestCode, Intent resultIntent,
            PendingIntent callbackIntent) throws IntentSender.SendIntentException {
        throw new IntentSender.SendIntentException("no eUICC LPA installed");
    }
}
