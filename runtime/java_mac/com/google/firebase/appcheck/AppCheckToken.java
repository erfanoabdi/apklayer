package com.google.firebase.appcheck;
public abstract class AppCheckToken {
    public abstract String getToken();
    public abstract long   getExpireTimeMillis();
}
