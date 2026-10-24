package com.google.ads;
public interface AdListener {
    void onReceiveAd(Ad ad);
    void onFailedToReceiveAd(Ad ad, AdRequest.ErrorCode error);
    void onPresentScreen(Ad ad);
    void onDismissScreen(Ad ad);
    void onLeaveApplication(Ad ad);
    void onClick(Ad ad);
}
