package android.net.nsd;
import java.util.HashMap;
import java.util.Map;
/** Network service discovery (mDNS/Bonjour) descriptor. A value holder the app
 *  fills in for register/resolve. The atlas notes mDNS maps to the host's
 *  mDNSResponder (dns_sd.h) -- that wiring is future work; the descriptor itself
 *  round-trips. Source: frameworks/base/core/java/android/net/nsd/NsdServiceInfo.java */
public final class NsdServiceInfo {
    private String mServiceName;
    private String mServiceType;
    private int mPort;
    private java.net.InetAddress mHost;
    private final Map<String, byte[]> mTxtRecord = new HashMap<>();
    public NsdServiceInfo() { }
    public String getServiceName() { return mServiceName; }
    public void setServiceName(String s) { mServiceName = s; }
    public String getServiceType() { return mServiceType; }
    public void setServiceType(String s) { mServiceType = s; }
    public int getPort() { return mPort; }
    public void setPort(int p) { mPort = p; }
    public java.net.InetAddress getHost() { return mHost; }
    public void setHost(java.net.InetAddress h) { mHost = h; }
    public void setAttribute(String key, String value) {
        mTxtRecord.put(key, value != null ? value.getBytes() : null);
    }
    public void removeAttribute(String key) { mTxtRecord.remove(key); }
    public Map<String, byte[]> getAttributes() { return mTxtRecord; }
}
