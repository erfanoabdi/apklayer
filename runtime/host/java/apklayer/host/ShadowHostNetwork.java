package apklayer.host;

import android.net.Network;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowNetwork;

/** The desktop has one network: a connection or lookup "on" it is the JDK's own (Android's okhttp fork and InetAddress.getAllByNameOnNet are not there). */
@Implements(Network.class)
public class ShadowHostNetwork extends ShadowNetwork {
    @Implementation protected URLConnection openConnection(URL url) throws IOException { return url.openConnection(); }
    @Implementation protected URLConnection openConnection(URL url, Proxy proxy) throws IOException { return url.openConnection(proxy); }
    @Implementation protected InetAddress[] getAllByName(String host) throws UnknownHostException { return InetAddress.getAllByName(host); }
    @Implementation protected InetAddress getByName(String host) throws UnknownHostException { return InetAddress.getByName(host); }
}
