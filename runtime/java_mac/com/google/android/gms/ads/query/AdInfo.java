package com.google.android.gms.ads.query;
public final class AdInfo {
    private final QueryInfo mQueryInfo;
    private final String mAdString;
    public AdInfo(QueryInfo queryInfo, String adString) { mQueryInfo = queryInfo; mAdString = adString; }
    public QueryInfo getQueryInfo() { return mQueryInfo; }
    public String getAdString() { return mAdString; }
}
