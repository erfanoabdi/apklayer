// android.nfc.NfcAdapter
//
// The handle to the device NFC radio. Mac.apk has no NFC hardware. AOSP's
// NfcAdapter.getDefaultAdapter(Context) returns null on devices without NFC,
// which is exactly our situation, so callers take their "NFC unavailable" path.
// We still ship the class so the type resolves (apps reference NfcAdapter in
// fields/casts even when they expect it to be null) and so isEnabled()-style
// probes on a (rare) non-null instance behave honestly.
// Mirrors frameworks/base/core/java/android/nfc/NfcAdapter.java surface used by
// real apps.
package android.nfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

public final class NfcAdapter {

    public NfcAdapter() {}

    // Reader-mode polling flags, for use with enableReaderMode(). Values are
    // AOSP's verbatim (packages/modules/Nfc framework NfcAdapter.java lines
    // 323/330/337/344/351/374/382/391); they are compile-time constants that
    // DEX inlines, but apps also read them reflectively and library code
    // re-exports them, so the exact values matter.
    public static final int FLAG_READER_NFC_A = 0x1;
    public static final int FLAG_READER_NFC_B = 0x2;
    public static final int FLAG_READER_NFC_F = 0x4;
    public static final int FLAG_READER_NFC_V = 0x8;
    public static final int FLAG_READER_NFC_BARCODE = 0x10;
    public static final int FLAG_READER_SKIP_NDEF_CHECK = 0x80;
    public static final int FLAG_READER_NO_PLATFORM_SOUNDS = 0x100;
    public static final String EXTRA_READER_PRESENCE_CHECK_DELAY = "presence";

    // The tag-dispatch intent actions and extras. An app declares these in its
    // manifest intent-filters and reads the extras out of the delivered
    // Intent; they are plain strings with no device dependency, so they are
    // mirrored verbatim. (No tag is ever dispatched here, so an app's filter
    // simply never matches -- which is what happens on any device without NFC.)
    public static final String ACTION_NDEF_DISCOVERED = "android.nfc.action.NDEF_DISCOVERED";
    public static final String ACTION_TECH_DISCOVERED = "android.nfc.action.TECH_DISCOVERED";
    public static final String ACTION_TAG_DISCOVERED = "android.nfc.action.TAG_DISCOVERED";
    public static final String ACTION_ADAPTER_STATE_CHANGED =
            "android.nfc.action.ADAPTER_STATE_CHANGED";
    public static final String ACTION_TRANSACTION_DETECTED =
            "android.nfc.action.TRANSACTION_DETECTED";
    public static final String ACTION_PREFERRED_PAYMENT_CHANGED =
            "android.nfc.action.PREFERRED_PAYMENT_CHANGED";

    public static final String EXTRA_TAG = "android.nfc.extra.TAG";
    public static final String EXTRA_ID = "android.nfc.extra.ID";
    public static final String EXTRA_NDEF_MESSAGES = "android.nfc.extra.NDEF_MESSAGES";
    public static final String EXTRA_ADAPTER_STATE = "android.nfc.extra.ADAPTER_STATE";
    public static final String EXTRA_AID = "android.nfc.extra.AID";
    public static final String EXTRA_DATA = "android.nfc.extra.DATA";
    public static final String EXTRA_SECURE_ELEMENT_NAME =
            "android.nfc.extra.SECURE_ELEMENT_NAME";
    public static final String EXTRA_PREFERRED_PAYMENT_CHANGED_REASON =
            "android.nfc.extra.PREFERRED_PAYMENT_CHANGED_REASON";

    public static final int STATE_OFF = 1;
    public static final int STATE_TURNING_ON = 2;
    public static final int STATE_ON = 3;
    public static final int STATE_TURNING_OFF = 4;

    public static final int PREFERRED_PAYMENT_LOADED = 1;
    public static final int PREFERRED_PAYMENT_CHANGED = 2;
    public static final int PREFERRED_PAYMENT_UPDATED = 3;

    /** AOSP getDefaultAdapter(Context): null on a no-NFC device (our case). */
    public static NfcAdapter getDefaultAdapter(Context context) {
        return null;
    }

    /** Deprecated AOSP overload; same no-NFC result. */
    public static NfcAdapter getDefaultAdapter() {
        return null;
    }

    public boolean isEnabled() { return false; }
    public boolean isSecureNfcEnabled() { return false; }
    public boolean isSecureNfcSupported() { return false; }

    /** AOSP's nested reader-mode callback interface. This has to exist as a
     *  TYPE even though Mac.apk never dispatches a tag: implementing an
     *  interface is a class-structure dependency the JVM resolves when the
     *  implementing class is DEFINED, not when its methods run, so a bundled
     *  SDK carrying an unrelated `implements NfcAdapter.ReaderCallback` class
     *  would otherwise NoClassDefFoundError and abort whatever Activity
     *  touched it. Signature verbatim from packages/modules/Nfc
     *  framework/java/android/nfc/NfcAdapter.java:635-637. */
    public interface ReaderCallback {
        void onTagDiscovered(Tag tag);
    }

    // The Android Beam callback interfaces. Beam was REMOVED from Android in
    // API 34 and there is no NFC radio here either, so none of these is ever
    // invoked -- but each is a class-STRUCTURE dependency for any app that
    // declares `implements NfcAdapter.CreateNdefMessageCallback`, resolved when
    // that class is DEFINED. Signatures verbatim from packages/modules/Nfc
    // framework/java/android/nfc/NfcAdapter.java.

    public interface CreateNdefMessageCallback {
        NdefMessage createNdefMessage(NfcEvent event);
    }

    public interface CreateBeamUrisCallback {
        Uri[] createBeamUris(NfcEvent event);
    }

    public interface OnNdefPushCompleteCallback {
        void onNdefPushComplete(NfcEvent event);
    }

    public interface OnTagRemovedListener {
        void onTagRemoved();
    }

    // ---- Foreground dispatch and Android Beam. ----
    //
    // Every method below is unreachable through supported code, because
    // getDefaultAdapter() correctly returns null on a device without NFC. They
    // are here so an app that holds an NfcAdapter reference LINKS, and each
    // one answers the way a no-NFC device does.
    //
    // enableForegroundDispatch / enableForegroundNdefPush are registrations
    // that leave the app expecting a callback, so they follow the same rule as
    // enableReaderMode above and raise AOSP's own no-NFC-feature
    // UnsupportedOperationException rather than going quiet. The DISABLE twins
    // are removals, so they are no-ops: throwing out of an onPause() teardown
    // would be a regression, not honesty.

    public void enableForegroundDispatch(Activity activity, PendingIntent intent,
            IntentFilter[] filters, String[][] techLists) {
        throw new UnsupportedOperationException();
    }

    public void disableForegroundDispatch(Activity activity) {}

    /** @deprecated Android Beam was removed in API 34. */
    @Deprecated
    public void setNdefPushMessage(NdefMessage message, Activity activity,
            Activity... activities) {
        throw new UnsupportedOperationException();
    }

    /** @deprecated Android Beam was removed in API 34. */
    @Deprecated
    public void setNdefPushMessageCallback(CreateNdefMessageCallback callback,
            Activity activity, Activity... activities) {
        throw new UnsupportedOperationException();
    }

    /** @deprecated Android Beam was removed in API 34. */
    @Deprecated
    public void setOnNdefPushCompleteCallback(OnNdefPushCompleteCallback callback,
            Activity activity, Activity... activities) {
        throw new UnsupportedOperationException();
    }

    /** @deprecated Android Beam was removed in API 34. */
    @Deprecated
    public void setBeamPushUris(Uri[] uris, Activity activity) {
        throw new UnsupportedOperationException();
    }

    /** @deprecated Android Beam was removed in API 34. */
    @Deprecated
    public void setBeamPushUrisCallback(CreateBeamUrisCallback callback, Activity activity) {
        throw new UnsupportedOperationException();
    }

    /** @deprecated Android Beam was removed in API 34. */
    @Deprecated
    public void enableForegroundNdefPush(Activity activity, NdefMessage message) {
        throw new UnsupportedOperationException();
    }

    /** A removal -- see the block comment above. @deprecated with Beam. */
    @Deprecated
    public void disableForegroundNdefPush(Activity activity) {}

    /** @deprecated Android Beam was removed in API 34. AOSP returns whether
     *  the beam was started; nothing can start one here. */
    @Deprecated
    public boolean invokeBeam(Activity activity) { return false; }

    /** @deprecated Android Beam was removed in API 34. */
    @Deprecated
    public boolean isNdefPushEnabled() { return false; }

    /** AOSP: stop dispatching a tag until it leaves the field, reporting
     *  whether the tag was successfully ignored. No tag can exist, so false --
     *  and the listener is correspondingly never called, which is exactly what
     *  AOSP does when it returns false. */
    public boolean ignore(Tag tag, int debounceMs, OnTagRemovedListener tagRemovedListener,
            Handler handler) {
        return false;
    }

    /** AOSP enableReaderMode's first act is `if (!sHasNfcFeature) throw new
     *  UnsupportedOperationException();` (NfcAdapter.java:1737-1740). Mac.apk
     *  has no NFC radio, so sHasNfcFeature is false for us and that is the
     *  faithful answer -- not a silent no-op, which would leave an app waiting
     *  forever for an onTagDiscovered that cannot come. In practice this is
     *  unreachable from well-behaved code because getDefaultAdapter() correctly
     *  returns null on a no-NFC device. */
    public void enableReaderMode(Activity activity, ReaderCallback callback, int flags,
            Bundle extras) {
        throw new UnsupportedOperationException();
    }

    /** Same no-NFC-feature contract as enableReaderMode (NfcAdapter.java:1754-1757). */
    public void disableReaderMode(Activity activity) {
        throw new UnsupportedOperationException();
    }
}
