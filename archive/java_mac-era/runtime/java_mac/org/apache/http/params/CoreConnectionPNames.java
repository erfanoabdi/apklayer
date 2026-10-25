// org.apache.http.params.CoreConnectionPNames -- the parameter NAMES for
// connection tuning. Apache HttpCore 4.x (Apache License 2.0); shipped by
// Android's org.apache.http.legacy.
//
// The string literals are the wire contract with HttpParams: a typo here does
// not fail, it silently makes a setting unreadable. They are transcribed from
// AOSP core/api/current.txt, which records each value.
package org.apache.http.params;

/** @deprecated Use the constants on the HttpClient configuration classes. */
@Deprecated
public interface CoreConnectionPNames {
    /** Socket read timeout, ms. Integer. */
    String SO_TIMEOUT = "http.socket.timeout";
    /** TCP_NODELAY (disable Nagle). Boolean. */
    String TCP_NODELAY = "http.tcp.nodelay";
    /** Internal socket buffer size, bytes. Integer. */
    String SOCKET_BUFFER_SIZE = "http.socket.buffer-size";
    /** SO_LINGER, seconds; -1 to leave it alone. Integer. */
    String SO_LINGER = "http.socket.linger";
    /** Connect timeout, ms. Integer. */
    String CONNECTION_TIMEOUT = "http.connection.timeout";
    /** Whether to check a reused connection for staleness. Boolean. */
    String STALE_CONNECTION_CHECK = "http.connection.stalecheck";
    /** Maximum line length accepted, bytes; <= 0 for unlimited. Integer. */
    String MAX_LINE_LENGTH = "http.connection.max-line-length";
    /** Maximum header count accepted; <= 0 for unlimited. Integer. */
    String MAX_HEADER_COUNT = "http.connection.max-header-count";
}
