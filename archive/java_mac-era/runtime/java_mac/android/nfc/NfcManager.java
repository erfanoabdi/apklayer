// android.nfc.NfcManager
//
// AOSP's high-level NFC service. Apps fetch it via getSystemService(NFC_SERVICE)
// then call getDefaultAdapter(). Mac.apk has no NFC hardware, so the adapter is
// null (AOSP itself returns null on devices without NFC); the service object is
// non-null so the cast and the null-check chain
// (`getDefaultAdapter() == null -> "NFC unsupported"`) work as on a no-NFC
// device. Mirrors frameworks/base/core/java/android/nfc/NfcManager.java.
package android.nfc;

import android.content.Context;

public final class NfcManager {

    public NfcManager() {}
    public NfcManager(Context context) {}

    /** AOSP getDefaultAdapter(): null when the device has no NFC, which is the
     *  honest result on macOS. Apps null-check this before using NFC. */
    public NfcAdapter getDefaultAdapter() {
        return NfcAdapter.getDefaultAdapter(null);
    }
}
