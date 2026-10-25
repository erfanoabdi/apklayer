// android.telephony.SmsManager
//
// Sends SMS/MMS. Mac.apk has no cellular radio. AOSP's send*TextMessage
// javadoc (frameworks/base/telephony/java/android/telephony/SmsManager.java)
// documents sentIntent precisely: "broadcast when the message is successfully
// sent, or failed", with the failure resultCode drawn from RESULT_ERROR_*
// (RESULT_ERROR_NO_SERVICE among them) -- NOT "never fires, message pending
// forever". An app showing a spinner until sentIntent reports back (the
// documented async idiom) hung forever under a bare no-op. sentIntent now
// fires once, immediately, with RESULT_ERROR_NO_SERVICE -- the honest "no
// radio" outcome, reported as fast as a real no-service failure is.
// deliveryIntent legitimately stays silent: AOSP only fires it after a
// carrier DELIVERED report for a message that actually left the device, and
// one that was never sent can never earn that.
//
// KNOWN LIMITATION (cross-lane, not fixed here): BroadcastReceiver.getResultCode()
// (android/content/BroadcastReceiver.java) is hardcoded to always return 0 --
// Mac.apk's sendBroadcast has no ordered-broadcast result-code propagation
// anywhere in the tree, in any package (PendingIntent.send(int) does not
// thread its code through to the delivered broadcast either). So an app
// reading the specific RESULT_ERROR_NO_SERVICE value back out of its receiver
// will not see it; what it WILL see is the broadcast arriving at all
// (unblocking anything gated on "did sending finish"), on the failure side of
// a RESULT_OK check (0 != Activity.RESULT_OK). Fully wiring the result code
// needs Context.sendBroadcast/BroadcastReceiver work outside this package.
// Mirrors frameworks/base/telephony/java/android/telephony/SmsManager.java surface.
package android.telephony;

import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public final class SmsManager {

    public static final int RESULT_ERROR_NONE = 0;
    public static final int RESULT_ERROR_GENERIC_FAILURE = 1;
    public static final int RESULT_ERROR_RADIO_OFF = 2;
    public static final int RESULT_ERROR_NULL_PDU = 3;
    public static final int RESULT_ERROR_NO_SERVICE = 4;
    public static final int RESULT_ERROR_LIMIT_EXCEEDED = 5;

    public static final int STATUS_ON_ICC_FREE = 0;
    public static final int STATUS_ON_ICC_READ = 1;
    public static final int STATUS_ON_ICC_UNREAD = 3;
    public static final int STATUS_ON_ICC_SENT = 5;
    public static final int STATUS_ON_ICC_UNSENT = 7;

    public static final int DEFAULT_SUBSCRIPTION_ID = Integer.MAX_VALUE;

    private final int mSubId;

    private SmsManager(int subId) { mSubId = subId; }

    /** AOSP getDefault(): the system default SmsManager. Always available as a
     *  handle even with no radio. */
    public static SmsManager getDefault() { return new SmsManager(DEFAULT_SUBSCRIPTION_ID); }

    /** AOSP getSmsManagerForSubscriptionId(int). */
    public static SmsManager getSmsManagerForSubscriptionId(int subId) {
        return new SmsManager(subId);
    }

    /** Modern (API 31+) accessor; same handle. */
    public SmsManager createForSubscriptionId(int subId) { return new SmsManager(subId); }

    public int getSubscriptionId() { return mSubId; }
    public static int getDefaultSmsSubscriptionId() { return -1; }

    // No radio: messages are accepted but never transmitted. See the header
    // comment for the sentIntent contract this now honors.
    public void sendTextMessage(String destinationAddress, String scAddress, String text,
            android.app.PendingIntent sentIntent, android.app.PendingIntent deliveryIntent) {
        fireSentIntent(sentIntent);
    }

    public void sendTextMessage(String destinationAddress, String scAddress, String text,
            android.app.PendingIntent sentIntent, android.app.PendingIntent deliveryIntent,
            long messageId) {
        fireSentIntent(sentIntent);
    }

    public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort,
            byte[] data, android.app.PendingIntent sentIntent,
            android.app.PendingIntent deliveryIntent) {
        fireSentIntent(sentIntent);
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress,
            ArrayList<String> parts, ArrayList<android.app.PendingIntent> sentIntents,
            ArrayList<android.app.PendingIntent> deliveryIntents) {
        fireSentIntents(sentIntents);
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress,
            ArrayList<String> parts, ArrayList<android.app.PendingIntent> sentIntents,
            ArrayList<android.app.PendingIntent> deliveryIntents, long messageId) {
        fireSentIntents(sentIntents);
    }

    /** Posted async (never called inline) for the same re-entrancy reason as
     *  the rest of this audit's fixes: a caller that writes state right after
     *  send*Message() returns must not observe the result nested inside that
     *  same call frame -- a real send crosses into the phone process and back
     *  over Binder, it is never synchronous with the call that started it. */
    private static void fireSentIntent(android.app.PendingIntent sentIntent) {
        if (sentIntent == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try { sentIntent.send(RESULT_ERROR_NO_SERVICE); }
            catch (android.app.PendingIntent.CanceledException ignored) {}
        });
    }

    /** AOSP fires one sentIntent per message part -- a multipart send is N
     *  independent PDUs, each with its own success/failure outcome. */
    private static void fireSentIntents(ArrayList<android.app.PendingIntent> sentIntents) {
        if (sentIntents == null) return;
        for (android.app.PendingIntent pi : sentIntents) fireSentIntent(pi);
    }

    /** AOSP splits a long message into 70/160-char parts. We mirror the GSM
     *  7-bit single-part limit so callers that pre-split get a faithful count. */
    public ArrayList<String> divideMessage(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (text == null) return out;
        final int limit = 160;
        if (text.length() <= limit) { out.add(text); return out; }
        for (int i = 0; i < text.length(); i += limit) {
            out.add(text.substring(i, Math.min(text.length(), i + limit)));
        }
        return out;
    }

    // ========================================================================
    // API-36 surface added 2026-08-24.
    //
    // There is no cellular radio, no SIM and no MMS transport. The additions
    // below keep the SAME contract the existing send path established: an
    // operation that AOSP reports through a PendingIntent fires that intent
    // once, promptly, with the honest failure result, rather than leaving the
    // caller waiting forever; an operation that returns a value returns the
    // documented "unavailable" one.
    // Source: frameworks/base/telephony/java/android/telephony/SmsManager.java
    // ========================================================================

    public static final String EXTRA_MMS_DATA = "android.telephony.extra.MMS_DATA";
    public static final String EXTRA_MMS_HTTP_STATUS = "android.telephony.extra.MMS_HTTP_STATUS";

    public static final String MMS_CONFIG_ALIAS_ENABLED = "aliasEnabled";
    public static final String MMS_CONFIG_ALIAS_MAX_CHARS = "aliasMaxChars";
    public static final String MMS_CONFIG_ALIAS_MIN_CHARS = "aliasMinChars";
    public static final String MMS_CONFIG_ALLOW_ATTACH_AUDIO = "allowAttachAudio";
    public static final String MMS_CONFIG_APPEND_TRANSACTION_ID = "enabledTransID";
    public static final String MMS_CONFIG_EMAIL_GATEWAY_NUMBER = "emailGatewayNumber";
    public static final String MMS_CONFIG_GROUP_MMS_ENABLED = "enableGroupMms";
    public static final String MMS_CONFIG_HTTP_PARAMS = "httpParams";
    public static final String MMS_CONFIG_HTTP_SOCKET_TIMEOUT = "httpSocketTimeout";
    public static final String MMS_CONFIG_MAX_IMAGE_HEIGHT = "maxImageHeight";
    public static final String MMS_CONFIG_MAX_IMAGE_WIDTH = "maxImageWidth";
    public static final String MMS_CONFIG_MAX_MESSAGE_SIZE = "maxMessageSize";
    public static final String MMS_CONFIG_MESSAGE_TEXT_MAX_SIZE = "maxMessageTextSize";
    public static final String MMS_CONFIG_MMS_DELIVERY_REPORT_ENABLED = "enableMMSDeliveryReports";
    public static final String MMS_CONFIG_MMS_ENABLED = "enabledMMS";
    public static final String MMS_CONFIG_MMS_READ_REPORT_ENABLED = "enableMMSReadReports";
    public static final String MMS_CONFIG_MULTIPART_SMS_ENABLED = "enableMultipartSMS";
    public static final String MMS_CONFIG_NAI_SUFFIX = "naiSuffix";
    public static final String MMS_CONFIG_NOTIFY_WAP_MMSC_ENABLED = "enabledNotifyWapMMSC";
    public static final String MMS_CONFIG_RECIPIENT_LIMIT = "recipientLimit";
    public static final String MMS_CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES =
            "sendMultipartSmsAsSeparateMessages";
    public static final String MMS_CONFIG_SHOW_CELL_BROADCAST_APP_LINKS =
            "config_cellBroadcastAppLinks";
    public static final String MMS_CONFIG_SMS_DELIVERY_REPORT_ENABLED = "enableSMSDeliveryReports";
    public static final String MMS_CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD =
            "smsToMmsTextLengthThreshold";
    public static final String MMS_CONFIG_SUBJECT_MAX_LENGTH = "maxSubjectLength";
    public static final String MMS_CONFIG_SUPPORT_HTTP_CHARSET_HEADER =
            "supportHttpCharsetHeader";
    public static final String MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION =
            "supportMmsContentDisposition";
    public static final String MMS_CONFIG_UA_PROF_TAG_NAME = "uaProfTagName";
    public static final String MMS_CONFIG_UA_PROF_URL = "uaProfUrl";
    public static final String MMS_CONFIG_USER_AGENT = "userAgent";

    /**
     * AOSP is @NonNull. There is no carrier, so there is no carrier MMS config
     * -- an EMPTY bundle, so every getX(key, default) the caller makes returns
     * its own default rather than a value we invented for a network we do not
     * have.
     */
    public Bundle getCarrierConfigValues() { return new Bundle(); }

    /**
     * AOSP returns a token an app can embed in an outgoing SMS so an inbound
     * reply is routed straight back to it. That routing needs an inbound SMS
     * path, which does not exist here, so no token can be honoured and AOSP's
     * documented null (the @Nullable overload) is returned.
     *
     * ⚠ createAppSpecificSmsToken(PendingIntent) is @NonNull in AOSP; returning
     * an unroutable token would be worse than an empty string, which at least
     * cannot be mistaken for a working one.
     */
    public String createAppSpecificSmsToken(PendingIntent intent) { return ""; }

    public String createAppSpecificSmsTokenWithPackageInfo(String prefixes,
            PendingIntent intent) {
        return null;
    }

    /** No ICC is present, so it stores zero messages. */
    public int getSmsCapacityOnIcc() { return 0; }

    /** AOSP is @Nullable: null when no SMSC address is configured. */
    public String getSmscAddress() { return null; }

    /** No SMSC to set. AOSP returns whether the write succeeded. */
    public boolean setSmscAddress(String smsc) { return false; }

    /** No inbound SMS path, so a PDU cannot be injected; report the failure. */
    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        fireSentIntent(receivedIntent);
    }

    public void sendTextMessageWithoutPersisting(String destinationAddress, String scAddress,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        fireSentIntent(sentIntent);
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents, long messageId) {
        fireSentIntentList(sentIntents);
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents, String packageName, String attributionTag) {
        fireSentIntentList(sentIntents);
    }

    // --- MMS ---------------------------------------------------------------
    // AOSP reports the outcome of an MMS send/download through the supplied
    // PendingIntent, with the HTTP status in EXTRA_MMS_HTTP_STATUS. There is no
    // MMSC to talk to, so the intent fires immediately on the failure side --
    // the same "do not hang the caller" reasoning as the SMS path above.

    public void sendMultimediaMessage(Context context, Uri contentUri, String locationUrl,
            Bundle configOverrides, PendingIntent sentIntent) {
        fireSentIntent(sentIntent);
    }

    public void sendMultimediaMessage(Context context, Uri contentUri, String locationUrl,
            Bundle configOverrides, PendingIntent sentIntent, long messageId) {
        fireSentIntent(sentIntent);
    }

    public void downloadMultimediaMessage(Context context, String locationUrl, Uri contentUri,
            Bundle configOverrides, PendingIntent downloadedIntent) {
        fireSentIntent(downloadedIntent);
    }

    public void downloadMultimediaMessage(Context context, String locationUrl, Uri contentUri,
            Bundle configOverrides, PendingIntent downloadedIntent, long messageId) {
        fireSentIntent(downloadedIntent);
    }

    /**
     * AOSP hands back a CursorWindow of financial SMS. There is no SMS store to
     * read, so the callback receives null -- which AOSP also passes when the
     * query cannot be satisfied. Delivered on the caller's Executor.
     */
    public void getSmsMessagesForFinancialApp(Bundle params, Executor executor,
            FinancialSmsCallback callback) {
        if (executor == null || callback == null) return;
        executor.execute(() -> {
            try { callback.onFinancialSmsMessages(null); } catch (Throwable ignored) { }
        });
    }

    /** AOSP declares this as an abstract static class with one abstract method. */
    public abstract static class FinancialSmsCallback {
        public abstract void onFinancialSmsMessages(android.database.CursorWindow msgs);
    }

    private static void fireSentIntentList(List<PendingIntent> sentIntents) {
        if (sentIntents == null) return;
        for (PendingIntent pi : sentIntents) fireSentIntent(pi);
    }
}
