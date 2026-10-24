// android.nfc.cardemulation.NfcFCardEmulation -- the NFC-F (FeliCa) twin of
// CardEmulation: registers a system code and an NFCID2 for a HostNfcFService.
//
// Identical standing and identical reasoning: read CardEmulation's header.
// getInstance(NfcAdapter) returns null (and is unreachable anyway, since
// NfcAdapter.getDefaultAdapter() is null here), registration returns false so
// no app believes it is routed, and the getters return null because there is
// nothing registered to report.
//
// Mirrors packages/modules/Nfc
// framework/java/android/nfc/cardemulation/NfcFCardEmulation.java.
package android.nfc.cardemulation;

import android.app.Activity;
import android.content.ComponentName;
import android.nfc.NfcAdapter;

public final class NfcFCardEmulation {

    private NfcFCardEmulation() {}

    /** Null: there is no NFC adapter to attach to. */
    public static synchronized NfcFCardEmulation getInstance(NfcAdapter adapter) {
        return null;
    }

    public String getSystemCodeForService(ComponentName service) throws RuntimeException {
        return null;
    }

    public boolean registerSystemCodeForService(ComponentName service, String systemCode)
            throws RuntimeException {
        return false;
    }

    /** A removal: nothing is registered, so there is nothing to unregister. */
    public boolean unregisterSystemCodeForService(ComponentName service)
            throws RuntimeException {
        return false;
    }

    public String getNfcid2ForService(ComponentName service) throws RuntimeException {
        return null;
    }

    public boolean setNfcid2ForService(ComponentName service, String nfcid2)
            throws RuntimeException {
        return false;
    }

    public boolean enableService(Activity activity, ComponentName service)
            throws RuntimeException {
        return false;
    }

    public boolean disableService(Activity activity) throws RuntimeException {
        return false;
    }
}
