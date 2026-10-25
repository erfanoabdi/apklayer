package com.google.android.gms.ads.query;
public abstract class QueryInfoGenerationCallback {
    public QueryInfoGenerationCallback() {}
    public void onSuccess(QueryInfo queryInfo) {}
    public void onFailure(String errorMessage) {}
}
