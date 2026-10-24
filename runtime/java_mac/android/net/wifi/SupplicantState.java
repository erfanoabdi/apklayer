// android.net.wifi.SupplicantState
//
// The wpa_supplicant association state enum. Mac.apk has no Wi-Fi stack; apps
// reference these enum values when reading WifiInfo.getSupplicantState (which
// reports a canned state). The enum + isValidState must resolve. Mirrors
// frameworks/base wifi android.net.wifi.SupplicantState public API.
package android.net.wifi;

public enum SupplicantState {
    DISCONNECTED,
    INTERFACE_DISABLED,
    INACTIVE,
    SCANNING,
    AUTHENTICATING,
    ASSOCIATING,
    ASSOCIATED,
    FOUR_WAY_HANDSHAKE,
    GROUP_HANDSHAKE,
    COMPLETED,
    DORMANT,
    UNINITIALIZED,
    INVALID;

    public static boolean isValidState(SupplicantState state) {
        return state != UNINITIALIZED && state != INVALID;
    }
}
