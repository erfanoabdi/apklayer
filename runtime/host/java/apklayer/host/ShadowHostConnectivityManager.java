package apklayer.host;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowConnectivityManager;

/**
 * Network callbacks that fire (PLAN P5.4). Robolectric registers a NetworkCallback and never calls it, so an app that
 * waits for onAvailable believes it is offline. The desktop's connection is announced to every new callback as
 * an available, validated, unmetered network; following NetworkManager's real state is the later step.
 */
@Implements(ConnectivityManager.class)
public class ShadowHostConnectivityManager extends ShadowConnectivityManager {
    @RealObject private ConnectivityManager real;

    private void announce(ConnectivityManager.NetworkCallback callback) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Network network = real.getActiveNetwork();
            if (network == null) return;
            NetworkCapabilities caps = new NetworkCapabilities();
            caps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            for (int c : new int[] {NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED, NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED})
                caps.addCapability(c);
            callback.onAvailable(network);
            callback.onCapabilitiesChanged(network, caps);
        });
    }

    @Implementation protected void registerNetworkCallback(NetworkRequest request, ConnectivityManager.NetworkCallback cb, Handler handler) {
        super.registerNetworkCallback(request, cb, handler);
        announce(cb);
    }

    @Implementation protected void registerDefaultNetworkCallback(ConnectivityManager.NetworkCallback cb) {
        super.registerDefaultNetworkCallback(cb);
        announce(cb);
    }

    @Implementation protected void registerDefaultNetworkCallback(ConnectivityManager.NetworkCallback cb, Handler handler) {
        super.registerDefaultNetworkCallback(cb, handler);
        announce(cb);
    }
}
