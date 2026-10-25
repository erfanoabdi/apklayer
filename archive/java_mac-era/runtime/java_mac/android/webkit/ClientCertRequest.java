package android.webkit;

import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

// android.webkit.ClientCertRequest (AOSP API-21).
// Passed to WebViewClient.onReceivedClientCertRequest for mutual-TLS. No web
// engine yet, so the proceed/cancel/ignore decision is recorded but not driven.
// Ref: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/ClientCertRequest.java
public abstract class ClientCertRequest {

    public abstract String[] getKeyTypes();

    public abstract Principal[] getPrincipals();

    public abstract String getHost();

    public abstract int getPort();

    public abstract void proceed(PrivateKey privateKey, X509Certificate[] chain);

    public abstract void ignore();

    public abstract void cancel();
}
