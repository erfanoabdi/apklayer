package org.apache.http.conn.ssl;
import java.io.IOException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocket;
public interface X509HostnameVerifier extends HostnameVerifier {
    void verify(String host, SSLSocket ssl) throws IOException;
    void verify(String host, java.security.cert.X509Certificate cert) throws javax.net.ssl.SSLException;
    void verify(String host, String[] cns, String[] subjectAlts) throws javax.net.ssl.SSLException;
}
