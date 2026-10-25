package com.google.firebase.auth;
import android.net.Uri;
public interface UserInfo {
    String getUid();
    String getProviderId();
    String getDisplayName();
    String getPhotoUrl_string();  // some SDK versions; getPhotoUrl returns Uri
    Uri    getPhotoUrl();
    String getEmail();
    String getPhoneNumber();
    boolean isEmailVerified();
}
