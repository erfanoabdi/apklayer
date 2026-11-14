package hello.tls;

import android.app.Activity;
import android.os.Bundle;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Real TLS through Android's providers (Conscrypt): a good certificate passes, bad ones fail, a pinned store rejects the real chain. Prints "[tls] PASS|FAIL <what>" lines, then "[tls] END". */
public final class Main extends Activity {
    private int failures;

    private void report(boolean ok, String what) { if (!ok) failures++; System.out.println("[tls] " + (ok ? "PASS " : "FAIL ") + what); }

    private static String get(String url, SSLContext context) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        if (context != null) c.setSSLSocketFactory(context.getSocketFactory());
        try (InputStream in = c.getInputStream()) { in.readNBytes(256); return String.valueOf(c.getResponseCode()); }
    }

    private void expectFail(String url, String what) {
        try { String code = get(url, null); report(false, what + " was accepted (" + code + ")"); }
        catch (javax.net.ssl.SSLException e) { report(true, what + " rejected: " + e.getClass().getSimpleName()); }
        catch (java.io.IOException e) { if (String.valueOf(e.getMessage()).contains("hostname")) report(true, what + " rejected: " + e.getMessage()); else report(false, what + " failed without a TLS error: " + e); }
        catch (Exception e) { report(false, what + " failed without a TLS error: " + e); }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            StringBuilder names = new StringBuilder();
            for (Provider p : Security.getProviders()) names.append(p.getName()).append(' ');
            System.out.println("[tls] providers " + names.toString().trim());
            try {
                report("AndroidOpenSSL".equals(SSLContext.getDefault().getProvider().getName()), "default SSLContext is AndroidOpenSSL");
                try { report("200".equals(get("https://example.com/", null)), "GET https://example.com/ = 200"); } catch (Exception e) { report(false, "GET https://example.com/: " + e); }
                try (javax.net.ssl.SSLSocket sock = (javax.net.ssl.SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket("example.com", 443)) {
                    sock.startHandshake(); report(true, "socket handshake with example.com: " + sock.getSession().getProtocol() + " peerHost=" + sock.getSession().getPeerHost());
                } catch (Exception e) { report(false, "socket handshake with example.com: " + e); }
                expectFail("https://expired.badssl.com/", "expired certificate");
                expectFail("https://self-signed.badssl.com/", "self-signed certificate");
                expectFail("https://wrong.host.badssl.com/", "wrong host name");
                expectFail("https://untrusted-root.badssl.com/", "untrusted root");
                // a pinned trust store (an empty one: nothing is trusted) must reject the real chain
                KeyStore empty = KeyStore.getInstance(KeyStore.getDefaultType()); empty.load(null, null);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); tmf.init(empty);
                SSLContext pinned = SSLContext.getInstance("TLS"); pinned.init(null, tmf.getTrustManagers(), null);
                try { String code = get("https://example.com/", pinned); report(false, "pinned (empty) store accepted example.com (" + code + ")"); }
                catch (javax.net.ssl.SSLException e) { report(true, "pinned (empty) store rejects example.com"); }
                KeyStore ca = KeyStore.getInstance("AndroidCAStore"); ca.load(null);
                report(ca.size() > 100, "AndroidCAStore holds " + ca.size() + " roots");
            } catch (Throwable t) { t.printStackTrace(System.out); report(false, "unexpected " + t); }
            System.out.println("[tls] " + (failures == 0 ? "PASS" : "FAIL") + " summary");
            System.out.println("[tls] END");
            System.out.flush();
            finish();
        }).start();
    }
}
