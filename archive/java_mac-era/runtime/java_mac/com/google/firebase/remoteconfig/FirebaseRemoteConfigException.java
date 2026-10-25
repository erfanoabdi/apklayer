package com.google.firebase.remoteconfig;
public class FirebaseRemoteConfigException extends com.google.firebase.FirebaseException {
    public enum Code {
        UNKNOWN, CONFIG_UPDATE_STREAM_ERROR, CONFIG_UPDATE_MESSAGE_INVALID,
        CONFIG_UPDATE_NOT_FETCHED, CONFIG_UPDATE_UNAVAILABLE
    }
    public FirebaseRemoteConfigException(String detailMessage) { super(detailMessage); }
    public FirebaseRemoteConfigException(String detailMessage, Throwable cause) { super(detailMessage, cause); }
    public Code getCode() { return Code.UNKNOWN; }
}
