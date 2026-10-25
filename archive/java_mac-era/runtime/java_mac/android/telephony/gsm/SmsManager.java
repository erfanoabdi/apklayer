package android.telephony.gsm;

import android.app.PendingIntent;

import java.util.ArrayList;

/**
 * The pre-API-4 GSM SMS manager.
 *
 * @deprecated AOSP deprecated this in API 4 in favour of
 * android.telephony.SmsManager; still shipped. Every method delegates there, so
 * the honest "no radio, sentIntent fires with RESULT_ERROR_NO_SERVICE" contract
 * that class documents applies unchanged.
 *
 * Source: frameworks/base/telephony/java/android/telephony/gsm/SmsManager.java
 */
@Deprecated
public final class SmsManager {

    @Deprecated public static final int STATUS_ON_SIM_FREE = 0;
    @Deprecated public static final int STATUS_ON_SIM_READ = 1;
    @Deprecated public static final int STATUS_ON_SIM_UNREAD = 3;
    @Deprecated public static final int STATUS_ON_SIM_SENT = 5;
    @Deprecated public static final int STATUS_ON_SIM_UNSENT = 7;

    @Deprecated public static final int RESULT_ERROR_GENERIC_FAILURE = 1;
    @Deprecated public static final int RESULT_ERROR_RADIO_OFF = 2;
    @Deprecated public static final int RESULT_ERROR_NULL_PDU = 3;
    @Deprecated public static final int RESULT_ERROR_NO_SERVICE = 4;

    private static final SmsManager INSTANCE = new SmsManager();

    private SmsManager() { }

    @Deprecated public static SmsManager getDefault() { return INSTANCE; }

    @Deprecated
    public void sendTextMessage(String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        android.telephony.SmsManager.getDefault().sendTextMessage(destinationAddress, scAddress,
                text, sentIntent, deliveryIntent);
    }

    @Deprecated
    public ArrayList<String> divideMessage(String text) {
        return android.telephony.SmsManager.getDefault().divideMessage(text);
    }

    @Deprecated
    public void sendMultipartTextMessage(String destinationAddress, String scAddress,
            ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        android.telephony.SmsManager.getDefault().sendMultipartTextMessage(destinationAddress,
                scAddress, parts, sentIntents, deliveryIntents);
    }

    @Deprecated
    public void sendDataMessage(String destinationAddress, String scAddress,
            short destinationPort, byte[] data, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        android.telephony.SmsManager.getDefault().sendDataMessage(destinationAddress, scAddress,
                destinationPort, data, sentIntent, deliveryIntent);
    }
}
