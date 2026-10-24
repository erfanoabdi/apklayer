// OneDSHttpClientStub -- a minimal stand-in for com.microsoft.applications.events.HttpClient,
// the Java side of Microsoft's 1DS/OneDS C++ telemetry SDK (github.com/microsoft/cpp_client_telemetry).
//
// WHY THIS EXISTS: an app that statically archives the OneDS C++ SDK (e.g. Minecraft's libmaesdk.so,
// via Xbox XAL/xsapi) crashes in LogManagerImpl construction if the SDK's Android HTTP client was
// never registered: HttpClientFactory::Create() (ANDROID branch) returns the empty static
// HttpClient_Android::s_client, and LogManagerImpl then does m_httpClient->ApplySettings() with NO
// null check -> a virtual call through a null IHttpClient -> a downstream lock on a garbage mutex ->
// SIGSEGV. On real Android, s_client is registered when the Java class
// com.microsoft.applications.events.HttpClient's constructor calls its native createClientInstance();
// a compat layer that runs the native SDK without constructing that Java object never registers it.
//
// This class is handed to the SDK's own createClientInstance registration entry point (see the native
// macapk_register_oneds_httpclient in native_methods.cpp) so the SDK caches a non-null client. Its
// createTask/executeTask methods carry EXACTLY the JNI signatures HttpClient_Android::SetClient looks
// up (GetMethodID). Telemetry HTTP uploads then degrade to a graceful no-op (the request "fails" and
// the SDK drops the event) instead of the process crashing -- offline telemetry, faithful to how the
// SDK behaves when it has no connectivity. See docs/pairip-libmaesdk-oneds-httpclient_2026-09-17.md.
package com.macapk.loader;

import java.util.concurrent.FutureTask;

public final class OneDSHttpClientStub {
    // HttpClient_Android::SetClient does:
    //   GetMethodID(cls, "createTask",
    //     "(Ljava/lang/String;Ljava/lang/String;[BLjava/lang/String;[I[B)Ljava/util/concurrent/FutureTask;")
    // args: url, method, requestBody, requestId, headerIndices, headerBuffer.
    public FutureTask<Boolean> createTask(String url, String method, byte[] body,
                                          String requestId, int[] headerIndices, byte[] headerBuffer) {
        // A no-op request that resolves to Boolean.FALSE (upload failed) so the 1DS uploader drops the
        // batch and moves on rather than blocking or retrying against a live network we do not provide.
        return new FutureTask<>(() -> Boolean.FALSE);
    }

    // GetMethodID(cls, "executeTask", "(Ljava/util/concurrent/FutureTask;)V")
    public void executeTask(FutureTask<Boolean> task) {
        // Complete the task inline so nothing waits on it forever; we intentionally do no network I/O.
        if (task != null) {
            try { task.run(); } catch (Throwable ignored) { /* never let telemetry take down the app */ }
        }
    }
}
