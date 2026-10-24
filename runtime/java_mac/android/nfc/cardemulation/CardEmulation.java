// android.nfc.cardemulation.CardEmulation -- the manager an HCE app uses to
// register AIDs, query which service is default for a category, and take the
// foreground preference.
//
// NOTHING CAN BE REGISTERED, and the answers say so. getInstance(NfcAdapter)
// returns null, which is unreachable through supported code anyway because
// android.nfc.NfcAdapter.getDefaultAdapter() already returns null on a device
// without NFC -- an app cannot hold an adapter to pass in. The instance
// methods are therefore unreachable; each answers the way a device with no
// routing table would.
//
// The registration methods return FALSE rather than true. That distinction is
// the whole point: an app that got `true` from registerAidsForService would
// believe its AIDs were routed and would then wait for a
// processCommandApdu() that cannot arrive (see HostApduService). False routes
// it to the error path it already has, because AID registration genuinely
// fails on real devices too -- the routing table is a small, shared, finite
// resource.
//
// Mirrors packages/modules/Nfc
// framework/java/android/nfc/cardemulation/CardEmulation.java; public surface
// confirmed against the android.jar API-30 reference in android-emulator/.
package android.nfc.cardemulation;

import android.app.Activity;
import android.content.ComponentName;
import android.nfc.NfcAdapter;

import java.util.Collections;
import java.util.List;

public final class CardEmulation {

    public static final String ACTION_CHANGE_DEFAULT =
            "android.nfc.cardemulation.action.ACTION_CHANGE_DEFAULT";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_SERVICE_COMPONENT = "component";

    public static final String CATEGORY_PAYMENT = "payment";
    public static final String CATEGORY_OTHER = "other";

    public static final int SELECTION_MODE_PREFER_DEFAULT = 0;
    public static final int SELECTION_MODE_ALWAYS_ASK = 1;
    public static final int SELECTION_MODE_ASK_IF_CONFLICT = 2;

    private CardEmulation() {}

    /** Null: there is no NFC adapter to attach to. Unreachable in practice --
     *  NfcAdapter.getDefaultAdapter() already returns null here. */
    public static synchronized CardEmulation getInstance(NfcAdapter adapter) {
        return null;
    }

    public boolean isDefaultServiceForCategory(ComponentName service, String category) {
        return false;
    }

    public boolean isDefaultServiceForAid(ComponentName service, String aid) {
        return false;
    }

    /** AOSP: whether an app may take the foreground preference for a category.
     *  Nothing can hold a preference here. */
    public boolean categoryAllowsForegroundPreference(String category) { return false; }

    /** AOSP's default when no service is registered for the category. */
    public int getSelectionModeForCategory(String category) {
        return SELECTION_MODE_ASK_IF_CONFLICT;
    }

    /** False: there is no routing table to write into. See the class header
     *  for why false rather than an inert true. */
    public boolean registerAidsForService(ComponentName service, String category,
            List<String> aids) {
        return false;
    }

    /** Empty rather than null: no AIDs are registered, and AOSP returns the
     *  registered list for a service it knows. */
    public List<String> getAidsForService(ComponentName service, String category) {
        return Collections.emptyList();
    }

    /** A removal, so false simply reports that there was nothing to remove. */
    public boolean removeAidsForService(ComponentName service, String category) {
        return false;
    }

    public boolean setOffHostForService(ComponentName service, String offHostSecureElement) {
        return false;
    }

    public boolean unsetOffHostForService(ComponentName service) { return false; }

    public boolean setPreferredService(Activity activity, ComponentName service) {
        return false;
    }

    public boolean unsetPreferredService(Activity activity) { return false; }

    /** AOSP: whether the NFC controller supports registering an AID PREFIX.
     *  There is no controller, so false -- and false is the conservative
     *  answer an app must already handle, since many real controllers say so. */
    public boolean supportsAidPrefixRegistration() { return false; }

    public List<String> getAidsForPreferredPaymentService() {
        return Collections.emptyList();
    }

    public String getRouteDestinationForPreferredPaymentService() { return null; }

    public CharSequence getDescriptionForPreferredPaymentService() { return null; }
}
