package apklayer.host;

import android.net.VpnService;
import org.robolectric.annotation.Implements;

/**
 * A VpnService runs its real code (docs/VPN.md): prepare() asks the hub's vpn_management (consent), Builder.establish() gets the host's TUN, protect() the hub's
 * guard. Robolectric's ShadowVpnService answers prepare() with an empty Intent and protect() with true, and its shadow is not a ShadowHostService (stopSelf).
 */
@Implements(value = VpnService.class, isInAndroidSdk = false)
public class ShadowHostVpnService extends ShadowHostService {
}
