// com.google.android.gms.ads.ResponseInfo
//
// GMS Ads reference: information about an ad response. On a de-Googled
// device with no ad backend, a load never produces a response, so this
// is the empty shape apps read after a (failed) load. We never fabricate
// a mediation adapter or response id.
package com.google.android.gms.ads;

import java.util.ArrayList;
import java.util.List;

public class ResponseInfo {
    public String getResponseId() { return null; }

    public String getMediationAdapterClassName() { return null; }

    public List<AdapterResponseInfo> getAdapterResponses() {
        return new ArrayList<AdapterResponseInfo>();
    }

    public AdapterResponseInfo getLoadedAdapterResponseInfo() { return null; }

    public android.os.Bundle getResponseExtras() { return new android.os.Bundle(); }

    @Override public String toString() { return "{}"; }

    // Nested per-adapter info type from the GMS Ads reference; empty on
    // a no-backend device.
    public static class AdapterResponseInfo {
        public String getAdSourceId() { return null; }
        public String getAdSourceName() { return null; }
        public String getAdapterClassName() { return null; }
        public long getLatencyMillis() { return 0L; }
        public AdError getAdError() { return null; }
        public android.os.Bundle getCredentials() { return new android.os.Bundle(); }
        @Override public String toString() { return "{}"; }
    }
}
