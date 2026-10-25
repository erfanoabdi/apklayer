package com.google.android.gms.iid;
/** Legacy token-refresh callback service. Never bound (no push backend). */
public class InstanceIDListenerService extends android.app.Service {
    public InstanceIDListenerService() {}
    public void onTokenRefresh() { }
    @Override public android.os.IBinder onBind(android.content.Intent intent) { return null; }
}
