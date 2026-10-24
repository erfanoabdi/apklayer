package com.google.android.gms.appset;
/** A resettable per-app-set identifier. */
public final class AppSetIdInfo {
    public static final int SCOPE_APP = 1;
    public static final int SCOPE_DEVELOPER = 2;
    private final String mId;
    private final int mScope;
    public AppSetIdInfo(String id, int scope) { mId = id; mScope = scope; }
    public String getId() { return mId; }
    public int getScope() { return mScope; }
}
