// com.google.android.gms.ads.initialization.OnInitializationCompleteListener
//
// GMS Ads reference: callback fired when MobileAds.initialize completes.
// Mac.apk fires it with a core-ready status so the app's init flow
// proceeds (then individual loads fail NO_FILL).
package com.google.android.gms.ads.initialization;

public interface OnInitializationCompleteListener {
    void onInitializationComplete(InitializationStatus initializationStatus);
}
