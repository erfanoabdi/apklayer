package com.google.android.gms.iid;
import android.content.Context;
/** Deprecated Instance ID (pre-FirebaseMessaging). No push backend on a
 *  de-Googled device, so there is no token. */
public class InstanceID {
    private static final InstanceID INSTANCE = new InstanceID();
    protected InstanceID() {}
    public static InstanceID getInstance(Context context) { return INSTANCE; }
    public String getId() { return ""; }
    public long getCreationTime() { return 0L; }
    public String getToken(String authorizedEntity, String scope, android.os.Bundle extras) { return null; }
    public String getToken(String authorizedEntity, String scope) { return null; }
    public void deleteToken(String authorizedEntity, String scope) { }
    public void deleteInstanceID() { }
}
