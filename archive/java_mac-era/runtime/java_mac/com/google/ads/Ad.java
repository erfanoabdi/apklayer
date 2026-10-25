package com.google.ads;
public interface Ad {
    void loadAd(AdRequest adRequest);
    boolean isReady();
    void setAdListener(AdListener adListener);
    void stopLoading();
}
