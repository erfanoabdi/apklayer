// com.google.android.gms.ads.VersionInfo
//
// GMS Ads reference: the SDK / adapter version triple returned by
// MobileAds.getVersion. Pure data; no backend.
package com.google.android.gms.ads;

public class VersionInfo {
    private final int major;
    private final int minor;
    private final int micro;

    public VersionInfo(int major, int minor, int micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
    }

    public int getMajorVersion() { return major; }

    public int getMinorVersion() { return minor; }

    public int getMicroVersion() { return micro; }

    @Override public String toString() {
        return major + "." + minor + "." + micro;
    }
}
