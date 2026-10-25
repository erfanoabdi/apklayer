package com.google.android.gms.appset;
import android.content.Context;
public final class AppSet {
    private AppSet() {}
    public static AppSetIdClient getClient(Context context) { return new AppSetIdClient() { }; }
}
