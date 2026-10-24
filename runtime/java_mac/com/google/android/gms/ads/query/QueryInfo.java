package com.google.android.gms.ads.query;
import android.content.Context;
/** A signals query for programmatic ad requests. No ad backend, so generation
 *  fails (onFailure). */
public final class QueryInfo {
    private QueryInfo() {}
    public String getQuery() { return ""; }
    public android.os.Bundle getQueryBundle() { return new android.os.Bundle(); }
    public static void generate(Context context, com.google.android.gms.ads.AdFormat adFormat,
            com.google.android.gms.ads.AdRequest adRequest, QueryInfoGenerationCallback callback) {
        if (callback != null) callback.onFailure("Ad services unavailable");
    }
}
