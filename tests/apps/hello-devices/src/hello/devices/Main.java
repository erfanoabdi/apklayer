package hello.devices;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import java.net.Inet4Address;

/** What the host's devices look like to an app: battery, network, USB, location (the test script compares each line with the host). */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try {
                Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                System.out.println("[dev] battery present=" + battery.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) + " level=" + battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        + " plugged=" + battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) + " status=" + battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
                ConnectivityManager cm = getSystemService(ConnectivityManager.class);
                NetworkCapabilities caps = cm.getActiveNetwork() == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
                System.out.println("[dev] network online=" + (caps != null) + " wifi=" + (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                        + " ethernet=" + (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) + " internet=" + (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));
                // what WebRTC's NetworkMonitorAutoDetect and Chromium's DnsConfig read: every network with its interface, addresses, routes, DNS
                for (Network network : cm.getAllNetworks()) {
                    LinkProperties link = cm.getLinkProperties(network);
                    NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                    StringBuilder v4 = new StringBuilder();
                    for (LinkAddress a : link == null ? java.util.List.<LinkAddress>of() : link.getLinkAddresses())
                        if (a.getAddress() instanceof Inet4Address) v4.append(a.getAddress().getHostAddress()).append(' ');
                    boolean defaultRoute = false;
                    for (RouteInfo r : link == null ? java.util.List.<RouteInfo>of() : link.getRoutes()) defaultRoute |= r.isDefaultRoute();
                    System.out.println("[dev] link handle=" + network.getNetworkHandle() + " iface=" + (link == null ? null : link.getInterfaceName()) + " v4=" + v4.toString().trim()
                            + " defaultRoute=" + defaultRoute + " dns=" + (link == null ? null : link.getDnsServers()) + " mtu=" + (link == null ? -1 : link.getMtu())
                            + " internet=" + (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));
                }
                System.out.println("[dev] usb devices=" + getSystemService(UsbManager.class).getDeviceList().size());
                LocationManager lm = getSystemService(LocationManager.class);
                System.out.println("[dev] location enabled=" + lm.isLocationEnabled() + " gps=" + lm.isProviderEnabled(LocationManager.GPS_PROVIDER) + " network=" + lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
                System.out.println("[dev] PASS");
            } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[dev] FAIL " + failure); }
        }).start();
    }
}
