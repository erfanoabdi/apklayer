// android.content.pm.LauncherApps
//
// AOSP's service for LAUNCHER apps to enumerate launchable activities, app
// shortcuts and install sessions across user profiles. Apps fetch it via
// Context.getSystemService(LAUNCHER_APPS_SERVICE).
//
// ⚠ THE HONEST DEVICE MODEL, which every answer below follows: Mac.apk hosts
// ONE app, in ONE profile, and is not a launcher. So:
//   * the activity / shortcut / config-activity lists are EMPTY -- the same
//     answer a real launcher gets for a package that declares none, and the
//     same answer any non-launcher app gets because it lacks the
//     ACCESS_SHORTCUTS permission;
//   * hasShortcutHostPermission() is false, and AOSP's own contract is that
//     the shortcut methods throw SecurityException when it is
//     ("@throws SecurityException if the caller is not the default launcher");
//   * the start* family throws, because there is no launcher-privileged path
//     to start someone else's activity and quietly starting it in-process
//     would be a different action from the one the caller asked for.
// Nothing here is a placeholder for a capability we mean to add later: a
// single-app compatibility layer genuinely has no launcher surface.
//
// Mirrors frameworks/base/core/java/android/content/pm/LauncherApps.java
// (android16-release).
package android.content.pm;

import android.content.Context;
import android.os.UserHandle;

public class LauncherApps {

    /** AOSP LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT / _APPWIDGET and
     *  EXTRA_PIN_ITEM_REQUEST. Values verbatim -- a launcher matches the
     *  action string on the Intent it receives, so the VALUE is the contract. */
    public static final String ACTION_CONFIRM_PIN_SHORTCUT =
            "android.content.pm.action.CONFIRM_PIN_SHORTCUT";
    public static final String ACTION_CONFIRM_PIN_APPWIDGET =
            "android.content.pm.action.CONFIRM_PIN_APPWIDGET";
    public static final String EXTRA_PIN_ITEM_REQUEST =
            "android.content.pm.extra.PIN_ITEM_REQUEST";

    /** AOSP LauncherApps.Callback: package / profile / shortcut change
     *  notifications.
     *
     *  ⚠ AOSP declares onPackageRemoved / onPackageAdded / onPackageChanged /
     *  onPackagesAvailable / onPackagesUnavailable ABSTRACT and the rest
     *  concrete. They are all concrete here: an app compiled against the real
     *  SDK necessarily implements the abstract five, so overriding a concrete
     *  base is identical for it, while a concrete base additionally protects
     *  any in-tree subclass. No app can observe the difference (abstractness is
     *  not visible to a caller), so this is a strictly safer shape, not a
     *  behavioural divergence. */
    public abstract static class Callback {
        public void onPackageRemoved(String packageName, UserHandle user) {}
        public void onPackageAdded(String packageName, UserHandle user) {}
        public void onPackageChanged(String packageName, UserHandle user) {}
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {}
        public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {}

        /** AOSP API-25: the app's shortcuts changed. `shortcuts` is the FULL
         *  new set for that package, not a delta. */
        public void onShortcutsChanged(String packageName,
                java.util.List<ShortcutInfo> shortcuts, UserHandle user) {}

        /** AOSP API-24 two-arg / API-28 three-arg suspension callbacks. AOSP's
         *  own two-arg body is empty and the three-arg body calls the two-arg
         *  one, so a subclass that overrides only the older form still hears
         *  about a suspension carrying extras. Reproduced exactly. */
        public void onPackagesSuspended(String[] packageNames, UserHandle user) {}
        public void onPackagesSuspended(String[] packageNames, UserHandle user,
                android.os.Bundle launcherExtras) {
            onPackagesSuspended(packageNames, user);
        }
        public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {}

        /** AOSP API-31: incremental-install progress for a package. */
        public void onPackageLoadingProgressChanged(String packageName, UserHandle user,
                float progress) {}

        /** AOSP API-35: a profile's launcher configuration changed. */
        public void onUserConfigChanged(LauncherUserInfo user) {}
    }

    /** AOSP LauncherApps.ShortcutQuery (API 25): the filter passed to
     *  getShortcuts(). Every setter returns `this`.
     *
     *  ⚠ This class REPLACES an `Object`-typed parameter that used to stand in
     *  for it. The two have different method descriptors, so app bytecode
     *  calling `getShortcuts(ShortcutQuery, UserHandle)` -- the only spelling a
     *  real SDK compiles -- resolved to nothing and threw NoSuchMethodError.
     *  An arity-based API diff cannot see that class of gap; only the
     *  descriptor can. */
    public static class ShortcutQuery {
        public static final int FLAG_MATCH_DYNAMIC  = 1 << 0;
        public static final int FLAG_MATCH_PINNED   = 1 << 1;
        public static final int FLAG_GET_KEY_FIELDS_ONLY = 1 << 2;
        public static final int FLAG_MATCH_MANIFEST = 1 << 3;
        public static final int FLAG_MATCH_CACHED   = 1 << 4;
        public static final int FLAG_MATCH_PINNED_BY_ANY_LAUNCHER = 1 << 10;

        long mChangedSince;
        String mPackage;
        java.util.List<String> mShortcutIds;
        java.util.List<android.content.LocusId> mLocusIds;
        android.content.ComponentName mActivity;
        int mQueryFlags;

        public ShortcutQuery() {}

        public ShortcutQuery setChangedSince(long changedSince) {
            mChangedSince = changedSince; return this;
        }
        public ShortcutQuery setPackage(String packageName) {
            mPackage = packageName; return this;
        }
        public ShortcutQuery setShortcutIds(java.util.List<String> shortcutIds) {
            mShortcutIds = shortcutIds; return this;
        }
        public ShortcutQuery setLocusIds(java.util.List<android.content.LocusId> locusIds) {
            mLocusIds = locusIds; return this;
        }
        public ShortcutQuery setActivity(android.content.ComponentName activity) {
            mActivity = activity; return this;
        }
        public ShortcutQuery setQueryFlags(int queryFlags) {
            mQueryFlags = queryFlags; return this;
        }
    }

    /** AOSP LauncherApps.PinItemRequest (API 26): the request a launcher
     *  receives when an app calls requestPinShortcut / requestPinAppWidget.
     *
     *  Mac.apk never delivers one (ShortcutManager.isRequestPinShortcutSupported
     *  is false, so nothing ever creates a request), and getPinItemRequest()
     *  below returns null for every Intent -- AOSP's documented answer for an
     *  Intent that does not carry one. The class exists so a launcher-shaped
     *  app's `(PinItemRequest) intent.getParcelableExtra(...)` and its
     *  null-check both resolve. */
    public static final class PinItemRequest implements android.os.Parcelable {
        public static final int REQUEST_TYPE_SHORTCUT  = 1;
        public static final int REQUEST_TYPE_APPWIDGET = 2;

        private final int mRequestType;
        private final ShortcutInfo mShortcutInfo;
        private final android.appwidget.AppWidgetProviderInfo mAppWidgetInfo;
        private final android.os.Bundle mExtras;

        public PinItemRequest(int requestType, ShortcutInfo shortcutInfo,
                android.appwidget.AppWidgetProviderInfo appWidgetInfo,
                android.os.Bundle extras) {
            mRequestType = requestType;
            mShortcutInfo = shortcutInfo;
            mAppWidgetInfo = appWidgetInfo;
            mExtras = extras;
        }

        public int getRequestType() { return mRequestType; }
        public ShortcutInfo getShortcutInfo() {
            return mRequestType == REQUEST_TYPE_SHORTCUT ? mShortcutInfo : null;
        }
        public android.appwidget.AppWidgetProviderInfo getAppWidgetProviderInfo(Context context) {
            return mRequestType == REQUEST_TYPE_APPWIDGET ? mAppWidgetInfo : null;
        }
        public android.os.Bundle getExtras() { return mExtras; }

        /** AOSP: "whether this request is still valid" -- false once it has
         *  been accepted or the requesting app is gone. Nothing in Mac.apk
         *  keeps a request alive, so a constructed one is inert. */
        public boolean isValid() { return false; }

        /** AOSP: accept the pin request. There is no launcher to pin into, so
         *  this fails -- which is exactly what AOSP returns for an expired or
         *  already-accepted request, and what a launcher already handles. */
        public boolean accept(android.os.Bundle options) { return false; }
        public boolean accept() { return accept(null); }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeInt(mRequestType);
            dest.writeTypedObject(mShortcutInfo, flags);
            dest.writeTypedObject(mAppWidgetInfo, flags);
            dest.writeBundle(mExtras);
        }
        public static final android.os.Parcelable.Creator<PinItemRequest> CREATOR =
                new android.os.Parcelable.Creator<PinItemRequest>() {
            @Override public PinItemRequest createFromParcel(android.os.Parcel in) {
                int type = in.readInt();
                ShortcutInfo si = in.readTypedObject(ShortcutInfo.CREATOR);
                android.appwidget.AppWidgetProviderInfo awi =
                        in.readTypedObject(android.appwidget.AppWidgetProviderInfo.CREATOR);
                return new PinItemRequest(type, si, awi, in.readBundle());
            }
            @Override public PinItemRequest[] newArray(int size) {
                return new PinItemRequest[size];
            }
        };
    }

    /** AOSP LauncherApps.ArchiveCompatibilityParams (API 35): a launcher's
     *  opt-out from the two pieces of system UI that archiving adds -- the
     *  cloud overlay on an archived app's icon and the confirmation dialog
     *  shown when the user taps one.
     *
     *  Mac.apk archives nothing (PackageManager.getArchivedPackage is always
     *  null), so setArchiveCompatibility below has nothing to configure. The
     *  setters record their values so an object round-trips truthfully rather
     *  than reporting whatever the caller did not set. */
    public static class ArchiveCompatibilityParams {
        private boolean mEnableIconOverlay = true;
        private boolean mEnableUnarchivalConfirmation = true;

        public ArchiveCompatibilityParams() {}

        public void setEnableIconOverlay(boolean enableIconOverlay) {
            mEnableIconOverlay = enableIconOverlay;
        }
        public void setEnableUnarchivalConfirmation(boolean enableUnarchivalConfirmation) {
            mEnableUnarchivalConfirmation = enableUnarchivalConfirmation;
        }
        /** @hide-equivalent accessors so the stored state is readable. */
        public boolean isEnableIconOverlay() { return mEnableIconOverlay; }
        public boolean isEnableUnarchivalConfirmation() {
            return mEnableUnarchivalConfirmation;
        }
    }

    public LauncherApps() {}
    public LauncherApps(Context context) {}

    // ---- activity enumeration ---------------------------------------------

    /** AOSP: "Retrieves a list of launchable activities that match
     *  Intent.ACTION_MAIN and Intent.CATEGORY_LAUNCHER, for a specified user."
     *  LauncherAppsService.getLauncherActivities (android14-release) is a
     *  queryIntentActivities of MAIN + LAUNCHER, setPackage(packageName) (null =
     *  every package), for any caller whose own profile it is (canAccessProfile
     *  is true for targetUserId == callingUserId): no launcher role is needed.
     *  So the answer is our PackageManager's, which lists other installed Mac.apk
     *  apps the caller can see (MACAPK_XAPP_IMPLICIT). Another user: null, as
     *  the service answers when canAccessProfile fails.
     *  MACAPK_LAUNCHERAPPS_QUERY=0 restores the old empty list. */
    public java.util.List<LauncherActivityInfo> getActivityList(String packageName,
            UserHandle user) {
        java.util.List<LauncherActivityInfo> out = new java.util.ArrayList<>();
        if (!QUERY) return out;
        if (user != null && !UserHandle.SYSTEM.equals(user)
                && user.getIdentifier() != UserHandle.myUserId()) {
            return null;
        }
        PackageManager pm = macapkPm();
        if (pm == null) return out;
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            if (ri == null || ri.activityInfo == null) continue;
            out.add(macapkInfo(pm, ri.activityInfo, user));
        }
        return out;
    }

    /** AOSP: "Returns the activity info for a given intent and user handle, if
     *  it resolves. Otherwise it returns null." LauncherAppsService.resolveLauncherActivityInternal:
     *  the intent's COMPONENT, looked up with getActivityInfo, null when absent. */
    public LauncherActivityInfo resolveActivity(android.content.Intent intent, UserHandle user) {
        if (!QUERY || intent == null || intent.getComponent() == null) return null;
        PackageManager pm = macapkPm();
        if (pm == null) return null;
        try {
            ActivityInfo ai = pm.getActivityInfo(intent.getComponent(), 0);
            return ai != null ? macapkInfo(pm, ai, user) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final boolean QUERY =
        !"0".equals(System.getenv("MACAPK_LAUNCHERAPPS_QUERY"));

    private static LauncherActivityInfo macapkInfo(PackageManager pm, ActivityInfo ai,
            UserHandle user) {
        long first = 0;
        try {
            PackageInfo pi = pm.getPackageInfo(ai.packageName, 0);
            if (pi != null) first = pi.firstInstallTime;
        } catch (Throwable ignored) {}
        return new LauncherActivityInfo(ai, user != null ? user : UserHandle.SYSTEM, first);
    }

    /** AOSP API-26: "Returns a list of config activities offered by the app" --
     *  the `<activity>` entries with ACTION_CREATE_SHORTCUT. */
    public java.util.List<LauncherActivityInfo> getShortcutConfigActivityList(
            String packageName, UserHandle user) {
        return new java.util.ArrayList<>();
    }

    /** AOSP API-26: an IntentSender that starts a config activity, "or null if
     *  the activity is not a valid shortcut-config activity" -- the null branch
     *  a caller already handles. */
    public android.content.IntentSender getShortcutConfigActivityIntent(
            LauncherActivityInfo info) {
        return null;
    }

    /** AOSP: "Returns the list of profiles for which the calling app can use
     *  LauncherApps." Mac.apk has exactly one, the system user. */
    public java.util.List<UserHandle> getProfiles() {
        java.util.List<UserHandle> l = new java.util.ArrayList<>();
        l.add(UserHandle.SYSTEM);
        return l;
    }

    /** AOSP API-35: the LauncherUserInfo for a profile, "or null if the user is
     *  not a profile the caller can access." Only the system user exists here;
     *  anything else is null. */
    public LauncherUserInfo getLauncherUserInfo(UserHandle user) {
        if (!UserHandle.SYSTEM.equals(user)) return null;
        // android.os.usertype.full.SYSTEM is the UserManager type string of the
        // primary user, and serial number 0 is the system user's.
        return new LauncherUserInfo("android.os.usertype.full.SYSTEM", 0,
                new android.os.Bundle());
    }

    /** AOSP API-35: the packages pre-installed on the system image for a
     *  profile. Mac.apk installs nothing on a system image -- there is no
     *  system partition -- so the honest answer is empty. @NonNull. */
    public java.util.List<String> getPreInstalledSystemPackages(UserHandle user) {
        return new java.util.ArrayList<>();
    }

    /** AOSP API-35: an IntentSender to open the app market for a profile, "or
     *  null if the user is not a profile or there is no market." Mac.apk has no
     *  app-market app registered. */
    public android.content.IntentSender getAppMarketActivityIntent(String packageName,
            UserHandle user) {
        return null;
    }

    /** AOSP API-35: an IntentSender to open private-space settings, "or null if
     *  the private space does not exist". There is no private space here. */
    public android.content.IntentSender getPrivateSpaceSettingsIntent() { return null; }

    /** AOSP: "Retrieve all of the information we know about a particular
     *  package / application." @throws NameNotFoundException when the package
     *  is not installed for the user -- which is how we answer for anything
     *  that is not the running app. */
    public ApplicationInfo getApplicationInfo(String packageName, int flags, UserHandle user)
            throws PackageManager.NameNotFoundException {
        PackageManager pm = macapkPm();
        if (pm == null) throw new PackageManager.NameNotFoundException(packageName);
        return pm.getApplicationInfo(packageName, flags);
    }

    /** AOSP LauncherAppsService.isPackageEnabled: getPackageInfo(...) != null
     *  && applicationInfo.enabled. MACAPK_LAUNCHERAPPS_QUERY=0 -> false. */
    public boolean isPackageEnabled(String packageName, UserHandle user) {
        if (!QUERY || packageName == null) return false;
        PackageManager pm = macapkPm();
        try {
            PackageInfo pi = pm != null ? pm.getPackageInfo(packageName, 0) : null;
            return pi != null && pi.applicationInfo != null && pi.applicationInfo.enabled;
        } catch (Throwable t) {
            return false;
        }
    }
    /** AOSP LauncherAppsService.isActivityEnabled: the activity resolves for the
     *  caller (getActivityInfo != null). */
    public boolean isActivityEnabled(android.content.ComponentName component, UserHandle user) {
        if (!QUERY || component == null) return false;
        PackageManager pm = macapkPm();
        try {
            return pm != null && pm.getActivityInfo(component, 0) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** AOSP API-29: whether the package asked to be hidden from launcher
     *  suggestion surfaces (`<application android:hideFromSuggestions>` /
     *  DISTRACTION_HIDE_FROM_SUGGESTIONS). Nothing here sets it. */
    public boolean shouldHideFromSuggestions(String packageName, UserHandle user) {
        return false;
    }

    /** AOSP API-24: the launcher extras attached when a package was suspended.
     *  No package is ever suspended on Mac.apk, and AOSP returns null for a
     *  package that is not suspended. */
    public android.os.Bundle getSuspendedPackageLauncherExtras(String packageName,
            UserHandle user) {
        return null;
    }

    // ---- shortcuts ---------------------------------------------------------

    /** AOSP: "Returns true if the caller can access the shortcut information",
     *  i.e. is the default launcher. Mac.apk has no launcher role. */
    public boolean hasShortcutHostPermission() { return false; }

    /** AOSP: "Returns ShortcutInfos that match query. ... @throws
     *  SecurityException if the caller is not the default launcher."
     *
     *  hasShortcutHostPermission() is false here, so AOSP's contract says the
     *  answer is the SecurityException, not an empty list -- an empty list
     *  would tell the caller "you are the launcher and there are no shortcuts",
     *  which is a different and wrong fact. Returning null (the @Nullable
     *  branch, "if the user is locked") would be equally wrong. */
    public java.util.List<ShortcutInfo> getShortcuts(ShortcutQuery query, UserHandle user) {
        throw notLauncher();
    }

    /** AOSP API-25: pin the given shortcut ids for a package. Same
     *  default-launcher gate. */
    public void pinShortcuts(String packageName, java.util.List<String> shortcutIds,
            UserHandle user) {
        throw notLauncher();
    }

    /** AOSP API-25: the shortcut's icon at the given density, "or null if the
     *  shortcut does not have an icon". Same default-launcher gate as
     *  getShortcuts -- AOSP documents SecurityException for a non-launcher. */
    public android.graphics.drawable.Drawable getShortcutIconDrawable(ShortcutInfo shortcut,
            int density) {
        throw notLauncher();
    }

    /** AOSP API-25: the shortcut's icon with the profile badge applied. */
    public android.graphics.drawable.Drawable getShortcutBadgedIconDrawable(
            ShortcutInfo shortcut, int density) {
        throw notLauncher();
    }

    /** AOSP API-30: a PendingIntent that launches the shortcut, "or null if the
     *  shortcut does not exist". Same default-launcher gate. */
    public android.app.PendingIntent getShortcutIntent(String packageName, String shortcutId,
            android.os.Bundle opts, UserHandle user) {
        throw notLauncher();
    }

    public void startShortcut(ShortcutInfo shortcut, android.graphics.Rect sourceBounds,
            android.os.Bundle startActivityOptions) {
        throw notLauncher();
    }

    public void startShortcut(String packageName, String shortcutId,
            android.graphics.Rect sourceBounds, android.os.Bundle startActivityOptions,
            UserHandle user) {
        throw notLauncher();
    }

    /** AOSP API-26: the PinItemRequest carried by an
     *  ACTION_CONFIRM_PIN_SHORTCUT / _APPWIDGET Intent, "or null if the intent
     *  does not contain one". Mac.apk never sends such an Intent
     *  (ShortcutManager.isRequestPinShortcutSupported() is false), so no Intent
     *  a launcher could hand us carries one. */
    public PinItemRequest getPinItemRequest(android.content.Intent intent) { return null; }

    // ---- install sessions --------------------------------------------------

    /** AOSP API-29: "Return list of all known install sessions ... regardless
     *  of the installer." Mac.apk's PackageInstaller sessions are real, and the
     *  installing app can already see them through
     *  PackageInstaller.getAllSessions(); this is the launcher-facing view of
     *  the same registry, so it answers from the same source rather than
     *  reporting a different world. @NonNull. */
    public java.util.List<PackageInstaller.SessionInfo> getAllPackageInstallerSessions() {
        PackageManager pm = macapkPm();
        if (pm == null) return new java.util.ArrayList<>();
        return pm.getPackageInstaller().getAllSessions();
    }

    /** AOSP API-29: register a launcher-side session callback. Routed to the
     *  same PackageInstaller registry the sessions come from, so a launcher and
     *  an installer observe one consistent stream of events. The Executor is
     *  not honoured -- our backend posts to the main thread -- which is noted
     *  rather than hidden; see PackageInstaller.registerSessionCallback. */
    public void registerPackageInstallerSessionCallback(java.util.concurrent.Executor executor,
            PackageInstaller.SessionCallback callback) {
        PackageManager pm = macapkPm();
        if (pm != null) pm.getPackageInstaller().registerSessionCallback(callback);
    }

    public void unregisterPackageInstallerSessionCallback(
            PackageInstaller.SessionCallback callback) {
        PackageManager pm = macapkPm();
        if (pm != null) pm.getPackageInstaller().unregisterSessionCallback(callback);
    }

    /** AOSP API-29: open the system's session-details UI. Mac.apk has no
     *  system installer UI to open (the install confirmation happens natively
     *  inside the macOS install helper -- see PackageInstaller's header), so
     *  there is nothing to start and this is a no-op, which is also what AOSP
     *  does when no activity resolves. */
    public void startPackageInstallerSessionDetailsActivity(
            PackageInstaller.SessionInfo sessionInfo, android.graphics.Rect sourceBounds,
            android.os.Bundle opts) {}

    // ---- launching ---------------------------------------------------------

    /** AOSP: "Starts a Main activity in another profile. @throws
     *  SecurityException if the caller cannot access the profile."
     *
     *  LauncherAppsService.startActivityAsUser (android14-release :1362-1380):
     *  no launcher role, only canAccessProfile (true for the caller's own user;
     *  another user returns silently). getMainActivityLaunchIntent builds
     *  ACTION_MAIN + CATEGORY_LAUNCHER + NEW_TASK | RESET_TASK_IF_NEEDED,
     *  setPackage(component's package), and requires the component to be one of
     *  that query's rows, else SecurityException("Attempt to launch activity
     *  without category Intent.CATEGORY_LAUNCHER ..."). The start then goes
     *  through the ordinary start path (another installed Mac.apk app: the
     *  cross-app hand-off). MACAPK_LAUNCHERAPPS_QUERY=0 restores the old throw. */
    public void startMainActivity(android.content.ComponentName component, UserHandle user,
            android.graphics.Rect sourceBounds, android.os.Bundle opts) {
        if (!QUERY) throw notLauncher();
        if (component == null) throw new NullPointerException("component");
        if (user != null && !UserHandle.SYSTEM.equals(user)
                && user.getIdentifier() != UserHandle.myUserId()) {
            return;
        }
        android.content.Intent launch = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .setPackage(component.getPackageName());
        boolean canLaunch = false;
        PackageManager pm = macapkPm();
        if (pm != null) {
            for (ResolveInfo ri : pm.queryIntentActivities(launch, 0)) {
                if (ri != null && ri.activityInfo != null
                        && component.getPackageName().equals(ri.activityInfo.packageName)
                        && component.getClassName().equals(ri.activityInfo.name)) {
                    if (!ri.activityInfo.exported) {
                        throw new SecurityException("Cannot launch non-exported components "
                                + component);
                    }
                    canLaunch = true;
                    break;
                }
            }
        }
        if (!canLaunch) {
            throw new SecurityException("Attempt to launch activity without "
                    + " category Intent.CATEGORY_LAUNCHER " + component);
        }
        launch.setPackage(null);
        launch.setComponent(component);
        launch.setSourceBounds(sourceBounds);
        com.macapk.loader.ActivityHost.start(launch);
    }

    /** AOSP: "Starts the settings activity to show the application details for
     *  a package in the specified profile." Same gate. */
    public void startAppDetailsActivity(android.content.ComponentName component, UserHandle user,
            android.graphics.Rect sourceBounds, android.os.Bundle opts) {
        throw notLauncher();
    }

    // ---- callbacks ---------------------------------------------------------

    /** AOSP: register for package/shortcut change callbacks. Mac.apk's package
     *  set is fixed for the life of the process -- one app, installed before we
     *  start, never added, removed, suspended or updated underneath the running
     *  app -- so no event can ever fire and registration has nothing to attach
     *  to. Keeping the reference would only leak it. */
    public void registerCallback(Callback callback) {}
    public void registerCallback(Callback callback, android.os.Handler handler) {}
    public void unregisterCallback(Callback callback) {}

    /** AOSP API-35: a launcher's archive-compatibility opt-outs. Mac.apk
     *  archives nothing, so there is no icon overlay and no unarchival dialog
     *  for the caller to turn off. */
    public void setArchiveCompatibility(ArchiveCompatibilityParams params) {}

    /** AOSP's own wording for the shortcut-surface gate
     *  (LauncherApps: "Caller can't access shortcut information"). */
    private static SecurityException notLauncher() {
        return new SecurityException("Caller can't access shortcut information");
    }

    private static PackageManager macapkPm() {
        try {
            android.app.Application app = android.app.Application.macapk_get();
            return (app != null) ? app.getPackageManager() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
