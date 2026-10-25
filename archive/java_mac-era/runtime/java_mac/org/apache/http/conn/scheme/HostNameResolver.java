// org.apache.http.conn.scheme.HostNameResolver -- pluggable DNS for a
// SocketFactory. AOSP ships this as part of org.apache.http.legacy.
//
// Apache HttpClient 4.x, org/apache/http/conn/scheme/HostNameResolver.java
// (Apache License 2.0).
package org.apache.http.conn.scheme;

import java.io.IOException;
import java.net.InetAddress;

/** @deprecated Use {@link org.apache.http.conn.DnsResolver}. */
@Deprecated
public interface HostNameResolver {
    InetAddress resolve(String hostname) throws IOException;
}
