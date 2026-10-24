package android.content.pm;

/** android.content.pm.LauncherActivityInfo (API 21) -- one launchable activity
 *  as seen by a LAUNCHER app, wrapping the ActivityInfo with the user it
 *  belongs to and the icon/label loading a launcher needs.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/LauncherActivityInfo.java
 *  (android16-release). AOSP's constructors are @hide (only LauncherApps builds
 *  one); the constructor here is public for the same reason -- nothing else in
 *  this tree could build one.
 *
 *  Mac.apk is not a launcher and hosts one app, so LauncherApps.getActivityList
 *  correctly reports an empty list and no instance is ever handed out today.
 *  The CLASS must exist because we present API 36: a `List<LauncherActivityInfo>`
 *  local, an enhanced-for over the empty list, or a checkcast all have to
 *  resolve the type. */
public class LauncherActivityInfo {
    private final ActivityInfo mActivityInfo;
    private final android.content.ComponentName mComponentName;
    private final android.os.UserHandle mUser;
    private final long mFirstInstallTime;

    public LauncherActivityInfo(ActivityInfo info, android.os.UserHandle user,
            long firstInstallTime) {
        mActivityInfo = info;
        mUser = user;
        mFirstInstallTime = firstInstallTime;
        mComponentName = (info == null) ? null
                : new android.content.ComponentName(info.packageName, info.name);
    }

    /** AOSP: "the component name of this activity." */
    public android.content.ComponentName getComponentName() { return mComponentName; }

    /** AOSP: "the UserHandle for the user that this activity belongs to." */
    public android.os.UserHandle getUser() { return mUser; }

    /** AOSP: "the label for the activity." */
    public CharSequence getLabel() {
        if (mActivityInfo == null) return "";
        PackageManager pm = macapkPackageManager();
        CharSequence label = mActivityInfo.loadLabel(pm);
        return label != null ? label : "";
    }

    /** AOSP LauncherActivityInfo.getLoadingProgress() (API 31): the incremental
     *  install progress in [0,1]. Mac.apk has no incremental delivery, so a
     *  package that exists is fully present -> 1.0, which is what AOSP reports
     *  for any non-incremental install. */
    public float getLoadingProgress() { return 1.0f; }

    /** AOSP: "the icon for this activity, without any badging for the user."
     *  `density` is a DisplayMetrics.DENSITY_* value; 0 means the default. */
    public android.graphics.drawable.Drawable getIcon(int density) {
        PackageManager pm = macapkPackageManager();
        if (mActivityInfo != null && pm != null) {
            try { return pm.getActivityIcon(mComponentName); }
            catch (Throwable ignored) {}
        }
        return (pm != null) ? pm.getDefaultActivityIcon() : null;
    }

    /** AOSP: "the icon for this activity, badged for the user." Mac.apk has a
     *  single user and no managed profile, so there is no badge to apply and
     *  the badged icon IS the icon -- the same result
     *  PackageManager.getUserBadgedIcon gives here. */
    public android.graphics.drawable.Drawable getBadgedIcon(int density) {
        return getIcon(density);
    }

    /** AOSP: "the application flags... of the activity's owning app." */
    public ApplicationInfo getApplicationInfo() {
        return mActivityInfo != null ? mActivityInfo.applicationInfo : null;
    }

    /** AOSP: "the activity info." */
    public ActivityInfo getActivityInfo() { return mActivityInfo; }

    /** AOSP: "the name of this activity's class." */
    public String getName() { return mActivityInfo != null ? mActivityInfo.name : null; }

    /** AOSP LauncherActivityInfo.getFirstInstallTime() -- when the OWNING
     *  PACKAGE was first installed, in System.currentTimeMillis() units. */
    public long getFirstInstallTime() { return mFirstInstallTime; }

    private static PackageManager macapkPackageManager() {
        try {
            android.app.Application app = android.app.Application.macapk_get();
            return (app != null) ? app.getPackageManager() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
