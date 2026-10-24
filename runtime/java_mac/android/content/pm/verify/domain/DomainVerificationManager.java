package android.content.pm.verify.domain;

/** android.content.pm.verify.domain.DomainVerificationManager (API 31) -- the
 *  system service through which an app reads its own APP LINK verification
 *  state. Obtained via
 *  Context.getSystemService(DOMAIN_VERIFICATION_SERVICE).
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/verify/domain/
 *  DomainVerificationManager.java (android16-release). The public surface is a
 *  single method; everything else upstream is @SystemApi (the verifier agent's
 *  side).
 *
 *  v1.0.2263: the verifier runs (com.macapk.loader.AppLinks): each
 *  autoVerify host's /.well-known/assetlinks.json is checked against the APK's
 *  REAL signing certificate, so the calling app's own package reports
 *  DOMAIN_STATE_VERIFIED exactly for the hosts its site really vouches for and
 *  DOMAIN_STATE_NONE for the rest. Another package, or one that declares no
 *  autoVerify domains, still answers NULL (AOSP: "@return the user state, or
 *  null if the package does not declare any domains"); MACAPK_APP_LINKS=0
 *  restores NULL for every package. */
public final class DomainVerificationManager {
    public DomainVerificationManager() {}
    public DomainVerificationManager(android.content.Context context) {}

    /** AOSP DomainVerificationManager.getDomainVerificationUserState(String):
     *  "@throws PackageManager.NameNotFoundException if the given package does
     *  not exist on the device", @Nullable otherwise.
     *
     *  The not-installed branch is real -- it consults the same PackageManager
     *  every other lookup uses -- so a caller can still tell "no link state"
     *  from "no such package", which is the distinction the exception exists
     *  for. */
    public DomainVerificationUserState getDomainVerificationUserState(String packageName)
            throws android.content.pm.PackageManager.NameNotFoundException {
        android.app.Application app = android.app.Application.macapk_get();
        android.content.pm.PackageManager pm = (app != null) ? app.getPackageManager() : null;
        if (pm == null) {
            throw new android.content.pm.PackageManager.NameNotFoundException(packageName);
        }
        pm.getApplicationInfo(packageName, 0);   // throws when not installed
        java.util.Map<String, Integer> states =
                com.macapk.loader.AppLinks.stateMapFor(packageName);
        if (states == null) return null;
        // Link handling is allowed unless the user turned it off; there is no
        // such user setting here, so it is on, as a device's default is.
        return new DomainVerificationUserState(packageName,
                android.os.Process.myUserHandle(), states, true);
    }
}
