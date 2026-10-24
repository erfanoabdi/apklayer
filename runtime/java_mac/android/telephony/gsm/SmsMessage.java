package android.telephony.gsm;

/**
 * The pre-API-4 GSM SMS message type.
 *
 * @deprecated AOSP deprecated this in API 4 in favour of
 * android.telephony.SmsMessage; still shipped, and still what long-tail apps
 * link against. Every method delegates to the modern class, so behaviour is
 * identical (including its "no TPDU decoded" answers -- there is no radio).
 *
 * Source: frameworks/base/telephony/java/android/telephony/gsm/SmsMessage.java
 */
@Deprecated
public class SmsMessage {

    @Deprecated public static final int ENCODING_UNKNOWN = 0;
    @Deprecated public static final int ENCODING_7BIT = 1;
    @Deprecated public static final int ENCODING_8BIT = 2;
    @Deprecated public static final int ENCODING_16BIT = 3;

    @Deprecated public static final int MAX_USER_DATA_BYTES = 140;
    @Deprecated public static final int MAX_USER_DATA_SEPTETS = 160;
    @Deprecated public static final int MAX_USER_DATA_SEPTETS_WITH_HEADER = 153;

    /** @deprecated the modern class's MessageClass. */
    @Deprecated
    public enum MessageClass { UNKNOWN, CLASS_0, CLASS_1, CLASS_2, CLASS_3 }

    /** @deprecated the modern class's SubmitPdu. */
    @Deprecated
    public static class SubmitPdu {
        @Deprecated public byte[] encodedScAddress;
        @Deprecated public byte[] encodedMessage;

        public SubmitPdu() { }

        /** @hide */
        SubmitPdu(android.telephony.SmsMessage.SubmitPdu p) {
            if (p == null) return;
            encodedScAddress = p.encodedScAddress;
            encodedMessage = p.encodedMessage;
        }

        @Override public String toString() {
            return "SubmitPdu: encodedScAddress = "
                    + java.util.Arrays.toString(encodedScAddress)
                    + ", encodedMessage = " + java.util.Arrays.toString(encodedMessage);
        }
    }

    private final android.telephony.SmsMessage mDelegate;

    @Deprecated
    public SmsMessage() { mDelegate = new android.telephony.SmsMessage(); }

    private SmsMessage(android.telephony.SmsMessage delegate) { mDelegate = delegate; }

    @Deprecated
    public static SmsMessage createFromPdu(byte[] pdu) {
        android.telephony.SmsMessage m = android.telephony.SmsMessage.createFromPdu(pdu);
        return m == null ? null : new SmsMessage(m);
    }

    @Deprecated public static int[] calculateLength(CharSequence messageBody,
            boolean use7bitOnly) {
        return android.telephony.SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    @Deprecated public static int[] calculateLength(String messageBody, boolean use7bitOnly) {
        return android.telephony.SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    @Deprecated public static int getTPLayerLengthForPDU(String pdu) {
        return android.telephony.SmsMessage.getTPLayerLengthForPDU(pdu);
    }

    @Deprecated public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress,
            String message, boolean statusReportRequested) {
        return new SubmitPdu(android.telephony.SmsMessage.getSubmitPdu(scAddress,
                destinationAddress, message, statusReportRequested));
    }

    @Deprecated public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress,
            short destinationPort, byte[] data, boolean statusReportRequested) {
        return new SubmitPdu(android.telephony.SmsMessage.getSubmitPdu(scAddress,
                destinationAddress, destinationPort, data, statusReportRequested));
    }

    @Deprecated public String getServiceCenterAddress() {
        return mDelegate.getServiceCenterAddress();
    }
    @Deprecated public String getOriginatingAddress() { return mDelegate.getOriginatingAddress(); }
    @Deprecated public String getDisplayOriginatingAddress() {
        return mDelegate.getDisplayOriginatingAddress();
    }
    @Deprecated public String getMessageBody() { return mDelegate.getMessageBody(); }
    @Deprecated public String getDisplayMessageBody() {
        return mDelegate.getDisplayMessageBody();
    }
    @Deprecated public String getPseudoSubject() { return mDelegate.getPseudoSubject(); }
    @Deprecated public long getTimestampMillis() { return mDelegate.getTimestampMillis(); }
    @Deprecated public boolean isEmail() { return mDelegate.isEmail(); }
    @Deprecated public String getEmailBody() { return mDelegate.getEmailBody(); }
    @Deprecated public String getEmailFrom() { return mDelegate.getEmailFrom(); }
    @Deprecated public byte[] getPdu() { return mDelegate.getPdu(); }
    @Deprecated public byte[] getUserData() { return mDelegate.getUserData(); }
    @Deprecated public int getProtocolIdentifier() { return mDelegate.getProtocolIdentifier(); }
    @Deprecated public boolean isReplace() { return mDelegate.isReplace(); }
    @Deprecated public boolean isCphsMwiMessage() { return mDelegate.isCphsMwiMessage(); }
    @Deprecated public boolean isMWIClearMessage() { return mDelegate.isMWIClearMessage(); }
    @Deprecated public boolean isMWISetMessage() { return mDelegate.isMWISetMessage(); }
    @Deprecated public boolean isMwiDontStore() { return mDelegate.isMwiDontStore(); }
    @Deprecated public boolean isStatusReportMessage() {
        return mDelegate.isStatusReportMessage();
    }
    @Deprecated public boolean isReplyPathPresent() { return mDelegate.isReplyPathPresent(); }
    @Deprecated public int getStatus() { return mDelegate.getStatus(); }
    @Deprecated public int getIndexOnSim() { return mDelegate.getIndexOnIcc(); }
    @Deprecated public int getStatusOnSim() { return mDelegate.getStatusOnIcc(); }

    @Deprecated public MessageClass getMessageClass() {
        // The two enums are separate types with identical constants.
        return MessageClass.valueOf(mDelegate.getMessageClass().name());
    }
}
