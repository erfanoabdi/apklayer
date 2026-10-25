// com.google.android.gms.ads.OnPaidEventListener
//
// GMS Ads reference: callback for ad paid events. Never invoked on a
// no-backend device (no real revenue), but apps register it.
package com.google.android.gms.ads;

public interface OnPaidEventListener {
    void onPaidEvent(AdValue adValue);
}
