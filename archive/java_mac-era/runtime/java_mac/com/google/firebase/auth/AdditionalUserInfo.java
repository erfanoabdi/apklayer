package com.google.firebase.auth;
public interface AdditionalUserInfo {
    boolean isNewUser();
    String  getProviderId();
    String  getUsername();
}
