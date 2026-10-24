// android.net.SSLSessionCache
//
// A persistent TLS session cache passed to SSLCertificateSocketFactory to
// enable session resumption. JSSE manages its own session cache, so this is a
// handle-only value type (no separate persistent store on the host); apps
// construct it and hand it off, and resumption is governed by the JDK's
// SSLSessionContext. Mirrors
// frameworks/base/core/java/android/net/SSLSessionCache.java public API.
package android.net;

import android.content.Context;

import java.io.File;
import java.io.IOException;

public final class SSLSessionCache {

    public SSLSessionCache(File dir) throws IOException {}
    public SSLSessionCache(Context context) {}
}
