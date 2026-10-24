package android.service.carrier;

import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.List;

/**
 * Base for a carrier app that wants to intercept, filter or send SMS/MMS on the
 * carrier's own network path.
 *
 * PACKAGE NOTE -- see CarrierIdentifier. Mac.apk has no radio, no SIM and no
 * telephony stack, so nothing binds this service and none of the on* callbacks
 * fires. The result objects are plain value types and are faithful.
 *
 * ⚠ EVERY constant below was EXTRACTED MECHANICALLY from
 * /tmp/aosp_api/android16.txt (signature format 2.0) rather than retyped. There
 * are FIVE interleaved numeric runs here -- SEND_STATUS_* (0..2),
 * DOWNLOAD_STATUS_* (0..2), RECEIVE_OPTIONS_* (0..2), the API-36
 * SEND_STATUS_RESULT_* block (200..218), and two 400-/600- MMS error blocks --
 * which is precisely the shape where a hand transcription is wrong invisibly.
 *
 * Source: frameworks/base/telephony/java/android/service/carrier/CarrierMessagingService.java
 */
public abstract class CarrierMessagingService extends android.app.Service {

    public static final int DOWNLOAD_STATUS_ERROR = 2;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_CONFIGURATION_ERROR = 606;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_DATA_DISABLED = 610;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_HTTP_FAILURE = 603;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_INACTIVE_SUBSCRIPTION = 609;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_INVALID_APN = 601;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_INVALID_SUBSCRIPTION_ID = 608;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_IO_ERROR = 604;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_MMS_DISABLED_BY_CARRIER = 611;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_NO_DATA_NETWORK = 607;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_RETRY = 605;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_UNABLE_CONNECT_MMS = 602;
    public static final int DOWNLOAD_STATUS_MMS_ERROR_UNSPECIFIED = 600;
    public static final int DOWNLOAD_STATUS_OK = 0;
    public static final int DOWNLOAD_STATUS_RETRY_ON_CARRIER_NETWORK = 1;
    public static final int RECEIVE_OPTIONS_DEFAULT = 0;
    public static final int RECEIVE_OPTIONS_DROP = 1;
    public static final int RECEIVE_OPTIONS_SKIP_NOTIFY_WHEN_CREDENTIAL_PROTECTED_STORAGE_UNAVAILABLE = 2;
    public static final int SEND_FLAG_REQUEST_DELIVERY_STATUS = 1;
    public static final int SEND_STATUS_ERROR = 2;
    public static final int SEND_STATUS_MMS_ERROR_CONFIGURATION_ERROR = 406;
    public static final int SEND_STATUS_MMS_ERROR_DATA_DISABLED = 410;
    public static final int SEND_STATUS_MMS_ERROR_HTTP_FAILURE = 403;
    public static final int SEND_STATUS_MMS_ERROR_INACTIVE_SUBSCRIPTION = 409;
    public static final int SEND_STATUS_MMS_ERROR_INVALID_APN = 401;
    public static final int SEND_STATUS_MMS_ERROR_INVALID_SUBSCRIPTION_ID = 408;
    public static final int SEND_STATUS_MMS_ERROR_IO_ERROR = 404;
    public static final int SEND_STATUS_MMS_ERROR_MMS_DISABLED_BY_CARRIER = 411;
    public static final int SEND_STATUS_MMS_ERROR_NO_DATA_NETWORK = 407;
    public static final int SEND_STATUS_MMS_ERROR_RETRY = 405;
    public static final int SEND_STATUS_MMS_ERROR_UNABLE_CONNECT_MMS = 402;
    public static final int SEND_STATUS_MMS_ERROR_UNSPECIFIED = 400;
    public static final int SEND_STATUS_OK = 0;
    public static final int SEND_STATUS_RESULT_CANCELLED = 215;
    public static final int SEND_STATUS_RESULT_ENCODING_ERROR = 212;
    public static final int SEND_STATUS_RESULT_ERROR_FDN_CHECK_FAILURE = 204;
    public static final int SEND_STATUS_RESULT_ERROR_GENERIC_FAILURE = 200;
    public static final int SEND_STATUS_RESULT_ERROR_LIMIT_EXCEEDED = 203;
    public static final int SEND_STATUS_RESULT_ERROR_NO_SERVICE = 202;
    public static final int SEND_STATUS_RESULT_ERROR_NULL_PDU = 201;
    public static final int SEND_STATUS_RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED = 206;
    public static final int SEND_STATUS_RESULT_ERROR_SHORT_CODE_NOT_ALLOWED = 205;
    public static final int SEND_STATUS_RESULT_INVALID_ARGUMENTS = 208;
    public static final int SEND_STATUS_RESULT_INVALID_SMSC_ADDRESS = 213;
    public static final int SEND_STATUS_RESULT_INVALID_SMS_FORMAT = 210;
    public static final int SEND_STATUS_RESULT_INVALID_STATE = 209;
    public static final int SEND_STATUS_RESULT_NETWORK_ERROR = 211;
    public static final int SEND_STATUS_RESULT_NETWORK_REJECT = 207;
    public static final int SEND_STATUS_RESULT_OPERATION_NOT_ALLOWED = 214;
    public static final int SEND_STATUS_RESULT_REQUEST_NOT_SUPPORTED = 216;
    public static final int SEND_STATUS_RESULT_SMS_BLOCKED_DURING_EMERGENCY = 217;
    public static final int SEND_STATUS_RESULT_SMS_SEND_RETRY_FAILED = 218;
    public static final int SEND_STATUS_RETRY_ON_CARRIER_NETWORK = 1;
    public static final String SERVICE_INTERFACE = "android.service.carrier.CarrierMessagingService";

    public CarrierMessagingService() { }

    /** @deprecated at API 24 in favour of onReceiveTextSms. */
    @Deprecated
    public void onFilterSms(MessagePdu pdu, String format, int destPort,
            int subId, ResultCallback<Boolean> callback) {
        // AOSP's default reports "do not filter" so an app that overrode only
        // the newer callback still lets messages through.
        deliver(callback, Boolean.FALSE);
    }

    /** API 24. AOSP's default answers RECEIVE_OPTIONS_DEFAULT. */
    public void onReceiveTextSms(MessagePdu pdu, String format, int destPort,
            int subId, ResultCallback<Integer> callback) {
        deliver(callback, Integer.valueOf(RECEIVE_OPTIONS_DEFAULT));
    }

    /** @deprecated at API 24; use the sendSmsFlags overload. */
    @Deprecated
    public void onSendTextSms(String text, int subId, String destAddress,
            ResultCallback<SendSmsResult> callback) {
        deliver(callback, new SendSmsResult(SEND_STATUS_RETRY_ON_CARRIER_NETWORK, 0));
    }

    /** API 24. AOSP's default asks the platform to send over the carrier
     *  network itself, which is what a service that declines to handle the
     *  message wants. */
    public void onSendTextSms(String text, int subId, String destAddress,
            int sendSmsFlag, ResultCallback<SendSmsResult> callback) {
        onSendTextSms(text, subId, destAddress, callback);
    }

    /** @deprecated at API 24; use the sendSmsFlags overload. */
    @Deprecated
    public void onSendDataSms(byte[] data, int subId, String destAddress, int destPort,
            ResultCallback<SendSmsResult> callback) {
        deliver(callback, new SendSmsResult(SEND_STATUS_RETRY_ON_CARRIER_NETWORK, 0));
    }

    public void onSendDataSms(byte[] data, int subId, String destAddress, int destPort,
            int sendSmsFlag, ResultCallback<SendSmsResult> callback) {
        onSendDataSms(data, subId, destAddress, destPort, callback);
    }

    /** @deprecated at API 24; use the sendSmsFlags overload. */
    @Deprecated
    public void onSendMultipartTextSms(List<String> parts, int subId, String destAddress,
            ResultCallback<SendMultipartSmsResult> callback) {
        deliver(callback,
                new SendMultipartSmsResult(SEND_STATUS_RETRY_ON_CARRIER_NETWORK, null));
    }

    public void onSendMultipartTextSms(List<String> parts, int subId, String destAddress,
            int sendSmsFlag, ResultCallback<SendMultipartSmsResult> callback) {
        onSendMultipartTextSms(parts, subId, destAddress, callback);
    }

    public void onSendMms(Uri pduUri, int subId, Uri location,
            ResultCallback<SendMmsResult> callback) {
        deliver(callback, new SendMmsResult(SEND_STATUS_RETRY_ON_CARRIER_NETWORK, null));
    }

    public void onDownloadMms(Uri contentUri, int subId, Uri location,
            ResultCallback<Integer> callback) {
        deliver(callback, Integer.valueOf(DOWNLOAD_STATUS_RETRY_ON_CARRIER_NETWORK));
    }

    /** AOSP returns the carrier-messaging binder. android.app.Service.onBind
     *  already returns null in this tree, so this override loses nothing. */
    @Override public IBinder onBind(Intent intent) { return null; }

    // AOSP's defaults DO call the callback -- a callback that never fires hangs
    // the platform's send path -- so the forwarding is reproduced. RemoteException
    // is swallowed because there is no remote end to report it to.
    private static <T> void deliver(ResultCallback<T> callback, T value) {
        if (callback == null) return;
        try {
            callback.onReceiveResult(value);
        } catch (RemoteException e) {
            // Ignored, as AOSP does.
        }
    }

    /** How a service returns its answer; may be called on any thread. */
    public interface ResultCallback<T> {
        void onReceiveResult(T result) throws RemoteException;
    }

    /** Result of onSendTextSms / onSendDataSms. */
    public static final class SendSmsResult {
        private final int mSendStatus;
        private final int mMessageRef;

        public SendSmsResult(int sendStatus, int messageRef) {
            mSendStatus = sendStatus;
            mMessageRef = messageRef;
        }

        public int getSendStatus() { return mSendStatus; }

        public int getMessageRef() { return mMessageRef; }
    }

    /** Result of onSendMultipartTextSms. */
    public static final class SendMultipartSmsResult {
        private final int mSendStatus;
        private final int[] mMessageRefs;

        public SendMultipartSmsResult(int sendStatus, int[] messageRefs) {
            mSendStatus = sendStatus;
            mMessageRefs = messageRefs;
        }

        public int getSendStatus() { return mSendStatus; }

        public int[] getMessageRefs() { return mMessageRefs; }
    }

    /** Result of onSendMms. */
    public static final class SendMmsResult {
        private final int mSendStatus;
        private final byte[] mSendConfPdu;

        public SendMmsResult(int sendStatus, byte[] sendConfPdu) {
            mSendStatus = sendStatus;
            mSendConfPdu = sendConfPdu;
        }

        public int getSendStatus() { return mSendStatus; }

        public byte[] getSendConfPdu() { return mSendConfPdu; }
    }
}
