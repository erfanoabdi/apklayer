package com.google.ads;
public class AdRequest {
    public enum ErrorCode { INTERNAL_ERROR, INVALID_REQUEST, NETWORK_ERROR, NO_FILL }
    public enum Gender    { UNKNOWN, MALE, FEMALE }
    public AdRequest() {}
    public static class Builder {
        public Builder addTestDevice(String deviceId) { return this; }
        public Builder setBirthday(java.util.Date birthday) { return this; }
        public Builder setGender(Gender gender) { return this; }
        public AdRequest build() { return new AdRequest(); }
    }
}
