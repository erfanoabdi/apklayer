// android.view.displayhash.DisplayHashManager -- Context.DISPLAY_HASH_SERVICE.
// Real Android backs this with a system service that HMACs a captured frame
// with a key apps never see. Mac.apk has no such service and does not forge
// one: getSupportedHashAlgorithms() honestly reports an EMPTY set (AOSP returns
// whatever the service advertises, and a device with no algorithms returns
// empty), and verifyDisplayHash() returns null, which AOSP documents as "the
// hash could not be verified".
// Source: frameworks/base core/java/android/view/displayhash/DisplayHashManager.java
package android.view.displayhash;

public final class DisplayHashManager {
    public DisplayHashManager() {}

    public java.util.Set<String> getSupportedHashAlgorithms() {
        return java.util.Collections.emptySet();
    }

    public VerifiedDisplayHash verifyDisplayHash(DisplayHash displayHash) {
        return null;
    }
}
