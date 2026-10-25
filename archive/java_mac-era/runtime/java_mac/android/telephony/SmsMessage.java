// android.telephony.SmsMessage
//
// A received/parsed SMS PDU. Mac.apk never receives SMS, so these are not
// produced from the radio, but apps that parse SMS_RECEIVED broadcast PDUs
// reference the static createFromPdu factory + getters. We parse what the
// caller hands us where trivial and otherwise return honest empties. Mirrors
// frameworks/base/telephony/java/android/telephony/SmsMessage.java surface.
package android.telephony;

public class SmsMessage {

    public static final int ENCODING_UNKNOWN = 0;
    public static final int ENCODING_7BIT = 1;
    public static final int ENCODING_8BIT = 2;
    public static final int ENCODING_16BIT = 3;

    public static final int ENCODING_KSC5601 = 4;

    public static final int MAX_USER_DATA_BYTES = 140;
    public static final int MAX_USER_DATA_BYTES_WITH_HEADER = 134;
    public static final int MAX_USER_DATA_SEPTETS = 160;
    public static final int MAX_USER_DATA_SEPTETS_WITH_HEADER = 153;

    public static final String FORMAT_3GPP = "3gpp";
    public static final String FORMAT_3GPP2 = "3gpp2";

    public enum MessageClass { UNKNOWN, CLASS_0, CLASS_1, CLASS_2, CLASS_3 }

    private byte[] mPdu;

    public SmsMessage() {}

    /** AOSP createFromPdu(byte[], String format). We retain the raw PDU but do
     *  not decode the GSM/CDMA TPDU (no radio stack); body getters return null.
     *  Returns null if the PDU is null, mirroring AOSP's failure return. */
    public static SmsMessage createFromPdu(byte[] pdu, String format) {
        if (pdu == null) return null;
        SmsMessage m = new SmsMessage();
        m.mPdu = pdu;
        return m;
    }

    public static SmsMessage createFromPdu(byte[] pdu) {
        return createFromPdu(pdu, "3gpp");
    }

    public byte[] getPdu() { return mPdu; }
    public String getMessageBody() { return null; }
    public String getDisplayMessageBody() { return null; }
    public String getOriginatingAddress() { return null; }
    public String getDisplayOriginatingAddress() { return null; }
    public String getServiceCenterAddress() { return null; }
    public long getTimestampMillis() { return 0L; }
    public int getStatusOnIcc() { return SmsManager.STATUS_ON_ICC_FREE; }
    public int getProtocolIdentifier() { return 0; }
    public boolean isReplace() { return false; }
    public boolean isCphsMwiMessage() { return false; }
    public boolean isMWISetMessage() { return false; }
    public boolean isMWIClearMessage() { return false; }
    public boolean isStatusReportMessage() { return false; }
    public byte[] getUserData() { return null; }

    public static int[] calculateLength(CharSequence msgBody, boolean use7bitOnly) {
        int len = msgBody == null ? 0 : msgBody.length();
        // [messageCount, codeUnitCount, codeUnitsRemaining, codeUnitSize]
        return new int[] { 1, len, MAX_USER_DATA_SEPTETS - len, ENCODING_7BIT };
    }

    public static int[] calculateLength(String messageBody, boolean use7bitOnly) {
        return calculateLength((CharSequence) messageBody, use7bitOnly);
    }

    // ========================================================================
    // API-36 surface added 2026-08-24.
    //
    // Mac.apk has no radio, so no SmsMessage is ever produced FROM one; the
    // only instances that exist are the ones an app builds via createFromPdu.
    // Because this class does not decode the TPDU (there is no GSM/CDMA stack
    // to decode it against), the header/flag getters below report the same
    // "nothing decoded" answers the existing body getters already do -- an
    // absence, not an invented value.
    //
    // getSubmitPdu IS a real encoder: building a GSM 03.40 SUBMIT PDU is pure
    // arithmetic (BCD address + 7-bit septet packing), needs no radio, and is
    // the whole point of the method for an app that wants the bytes.
    // Source: frameworks/base/telephony/java/android/telephony/SmsMessage.java
    // ========================================================================

    /** Returns -1: no message is stored on an ICC that does not exist. */
    public int getIndexOnIcc() { return -1; }

    /** @deprecated AOSP renamed this to {@link #getIndexOnIcc()} in API 4. */
    @Deprecated public int getIndexOnSim() { return getIndexOnIcc(); }

    /** @deprecated AOSP renamed this to {@link #getStatusOnIcc()} in API 4. */
    @Deprecated public int getStatusOnSim() { return getStatusOnIcc(); }

    /** TP-Status of a STATUS-REPORT. Nothing was decoded, so 0. */
    public int getStatus() { return 0; }

    /** No TPDU was decoded, so the message class is UNKNOWN. */
    public MessageClass getMessageClass() { return MessageClass.UNKNOWN; }

    /** SMS-to-email gateway fields; nothing decoded. */
    public boolean isEmail() { return false; }
    public String getEmailBody() { return null; }
    public String getEmailFrom() { return null; }

    /** AOSP: the CPHS/email pseudo-subject. Nothing decoded. */
    public String getPseudoSubject() { return null; }

    /** TP-RP. Nothing decoded. */
    public boolean isReplyPathPresent() { return false; }

    /** MWI "do not store". Nothing decoded. */
    public boolean isMwiDontStore() { return false; }

    /** AOSP is @Nullable; the recipient is only present on an IMS-delivered SMS. */
    public String getRecipientAddress() { return null; }

    /**
     * AOSP: the TP-layer length of an SMS-SUBMIT PDU given its hex string.
     * Pure arithmetic -- the SC address is not counted, so the answer is the
     * PDU length in octets minus the SC-address field.
     */
    public static int getTPLayerLengthForPDU(String pdu) {
        if (pdu == null) return 0;
        int len = pdu.length() / 2;
        int smscLen = Integer.parseInt(pdu.substring(0, 2), 16);
        return len - smscLen - 1;
    }

    /** AOSP's SUBMIT-PDU container: two public byte[] fields, no getters. */
    public static class SubmitPdu {
        public byte[] encodedScAddress;
        public byte[] encodedMessage;

        @Override public String toString() {
            return "SubmitPdu: encodedScAddress = "
                    + java.util.Arrays.toString(encodedScAddress)
                    + ", encodedMessage = " + java.util.Arrays.toString(encodedMessage);
        }
    }

    /**
     * Builds a GSM 03.40 SMS-SUBMIT PDU for a text message.
     *
     * Real encoder: BCD-encoded addresses (via PhoneNumberUtils, which now
     * implements TS 24.008 10.5.4.7 properly) plus GSM 7-bit septet packing
     * when the body fits it, else UCS-2. Returns null when the body cannot fit
     * a single PDU or an address is unusable -- AOSP returns null on the same
     * failures rather than truncating.
     */
    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress,
            String message, boolean statusReportRequested) {
        if (message == null || destinationAddress == null) return null;

        boolean sevenBit = fitsInGsm7Bit(message);
        byte[] userData;
        int dcs;
        if (sevenBit) {
            if (message.length() > MAX_USER_DATA_SEPTETS) return null;
            userData = packSeptets(message);
            dcs = 0x00;
        } else {
            byte[] ucs2;
            try {
                ucs2 = message.getBytes("UTF-16BE");
            } catch (java.io.UnsupportedEncodingException e) {
                return null;
            }
            if (ucs2.length > MAX_USER_DATA_BYTES) return null;
            userData = ucs2;
            dcs = 0x08;
        }

        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(180);

        // TP-MTI = SUBMIT (0x01), TP-VPF = relative (0x10), plus TP-SRR.
        byte mtiByte = (byte) (0x01 | 0x10 | (statusReportRequested ? 0x20 : 0x00));
        bo.write(mtiByte);
        bo.write(0x00);  // TP-Message-Reference

        byte[] daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(destinationAddress);
        if (daBytes == null) return null;
        // TP-DA is <digit count>, <TOA>, <BCD digits>.
        int digitCount = 0;
        for (int i = 0; i < destinationAddress.length(); i++) {
            char c = destinationAddress.charAt(i);
            if (c != '+' && PhoneNumberUtils.isDialable(c)) digitCount++;
        }
        bo.write(digitCount);
        bo.write(daBytes, 0, daBytes.length);

        bo.write(0x00);   // TP-PID
        bo.write(dcs);    // TP-DCS
        bo.write(0xFF);   // TP-Validity-Period: maximum

        bo.write(sevenBit ? message.length() : userData.length);   // TP-UDL
        bo.write(userData, 0, userData.length);

        SubmitPdu ret = new SubmitPdu();
        ret.encodedMessage = bo.toByteArray();
        ret.encodedScAddress = scAddress == null
                ? null : PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(scAddress);
        return ret;
    }

    /** Data-message variant: 8-bit user data on an application port. */
    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress,
            short destinationPort, byte[] data, boolean statusReportRequested) {
        if (data == null || destinationAddress == null) return null;
        // 6-byte 16-bit application-port UDH (TS 23.040 9.2.3.24.4).
        if (data.length + 7 > MAX_USER_DATA_BYTES) return null;

        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(180);
        byte mtiByte = (byte) (0x01 | 0x10 | 0x40 /* TP-UDHI */
                | (statusReportRequested ? 0x20 : 0x00));
        bo.write(mtiByte);
        bo.write(0x00);   // TP-MR

        byte[] daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(destinationAddress);
        if (daBytes == null) return null;
        int digitCount = 0;
        for (int i = 0; i < destinationAddress.length(); i++) {
            char c = destinationAddress.charAt(i);
            if (c != '+' && PhoneNumberUtils.isDialable(c)) digitCount++;
        }
        bo.write(digitCount);
        bo.write(daBytes, 0, daBytes.length);

        bo.write(0x00);   // TP-PID
        bo.write(0x04);   // TP-DCS: 8-bit data
        bo.write(0xFF);   // TP-VP

        bo.write(7 + data.length);           // TP-UDL (UDH + data)
        bo.write(0x06);                      // UDHL
        bo.write(0x05);                      // IEI: 16-bit application port
        bo.write(0x04);                      // IEDL
        bo.write((destinationPort >> 8) & 0xFF);
        bo.write(destinationPort & 0xFF);
        bo.write(0x00);                      // originator port high
        bo.write(0x00);                      // originator port low
        bo.write(data, 0, data.length);

        SubmitPdu ret = new SubmitPdu();
        ret.encodedMessage = bo.toByteArray();
        ret.encodedScAddress = scAddress == null
                ? null : PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(scAddress);
        return ret;
    }

    /** GSM 03.38 default alphabet, in septet order. */
    private static final String GSM_7BIT_BASIC =
            "@£$¥èéùìòÇ\nØø\rÅå"
            + "Δ_ΦΓΛΩΠΨΣΘΞ￿"
            + "ÆæßÉ"
            + " !\"#¤%&'()*+,-./0123456789:;<=>?"
            + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
            + "¿abcdefghijklmnopqrstuvwxyzäöñüà";

    private static boolean fitsInGsm7Bit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (GSM_7BIT_BASIC.indexOf(s.charAt(i)) < 0) return false;
        }
        return true;
    }

    /** TS 23.038 6.1.2.1.1 septet packing. */
    private static byte[] packSeptets(String s) {
        int n = s.length();
        byte[] out = new byte[(n * 7 + 7) / 8];
        int bitPos = 0;
        for (int i = 0; i < n; i++) {
            int septet = GSM_7BIT_BASIC.indexOf(s.charAt(i)) & 0x7F;
            int bytePos = bitPos >> 3;
            int shift = bitPos & 7;
            out[bytePos] |= (byte) ((septet << shift) & 0xFF);
            if (shift > 1 && bytePos + 1 < out.length) {
                out[bytePos + 1] |= (byte) (septet >> (8 - shift));
            }
            bitPos += 7;
        }
        return out;
    }
}
