package apklayer.host;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.os.IUserManager;
import android.os.UserManager;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.RuntimeEnvironment;

/**
 * system_server's "package" and "user" services for apps that call them directly (ServiceManager.getService + IPackageManager/IUserManager.Stub.asInterface,
 * App Manager's no-root mode, which transacts through a wrapper binder of its own): Robolectric serves no "package" and a "user" that answers null, so such an
 * app saw no packages and no users. Each call answers from this app's own PackageManager and UserManager (backed by the hub's package data); the rest keep the
 * AIDL defaults. One user: userId is not checked. The binders are real Stubs (HostBinderStubs): transact() answers as a local call does.
 */
final class HostPackageService extends IPackageManager.Default {
    private static android.os.Binder packages, users;

    /** The service's binder for ShadowHostServiceManager, null for other names. */
    static synchronized android.os.IBinder binder(String name) {
        if ("package".equals(name)) return packages != null ? packages : (packages = HostBinderStubs.delegating(IPackageManager.Stub.class, IPackageManager.class, new HostPackageService()));
        if (android.content.Context.USER_SERVICE.equals(name)) return users != null ? users : (users = HostBinderStubs.delegating(IUserManager.Stub.class, IUserManager.class, new Users()));
        return null;
    }

    private static PackageManager pm() { return RuntimeEnvironment.getApplication().getPackageManager(); }

    @Override public android.content.pm.PackageInfoList getInstalledPackages(long flags, int userId) { return new android.content.pm.PackageInfoList(pm().getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))); }
    @Override public ParceledListSlice<ApplicationInfo> getInstalledApplications(long flags, int userId) { return new ParceledListSlice<>(pm().getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags))); }
    @Override public List<String> getAllPackages() {
        List<String> names = new ArrayList<>();
        for (PackageInfo info : pm().getInstalledPackages(0)) names.add(info.packageName);
        return names;
    }
    @Override public boolean isPackageAvailable(String packageName, int userId) { return getPackageInfo(packageName, 0, userId) != null; }
    // as PackageManagerService: an unknown package is null (or -1), not an exception
    @Override public PackageInfo getPackageInfo(String packageName, long flags, int userId) {
        try { return pm().getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags)); } catch (PackageManager.NameNotFoundException e) { return null; }
    }
    @Override public ApplicationInfo getApplicationInfo(String packageName, long flags, int userId) {
        try { return pm().getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags)); } catch (PackageManager.NameNotFoundException e) { return null; }
    }
    @Override public int getPackageUid(String packageName, long flags, int userId) {
        try { return pm().getPackageUid(packageName, (int) flags); } catch (PackageManager.NameNotFoundException e) { return -1; }
    }
    @Override public android.content.pm.ActivityInfo getActivityInfo(ComponentName name, long flags, int userId) {
        try { return pm().getActivityInfo(name, PackageManager.ComponentInfoFlags.of(flags)); } catch (PackageManager.NameNotFoundException e) { return null; }
    }
    @Override public android.content.pm.ActivityInfo getReceiverInfo(ComponentName name, long flags, int userId) {
        try { return pm().getReceiverInfo(name, PackageManager.ComponentInfoFlags.of(flags)); } catch (PackageManager.NameNotFoundException e) { return null; }
    }
    @Override public android.content.pm.ServiceInfo getServiceInfo(ComponentName name, long flags, int userId) {
        try { return pm().getServiceInfo(name, PackageManager.ComponentInfoFlags.of(flags)); } catch (PackageManager.NameNotFoundException e) { return null; }
    }
    @Override public android.content.pm.ProviderInfo getProviderInfo(ComponentName name, long flags, int userId) {
        try { return pm().getProviderInfo(name, PackageManager.ComponentInfoFlags.of(flags)); } catch (PackageManager.NameNotFoundException e) { return null; }
    }
    @Override public String[] getPackagesForUid(int uid) { return pm().getPackagesForUid(uid); }
    @Override public String getNameForUid(int uid) { return pm().getNameForUid(uid); }
    @Override public ParceledListSlice<android.content.pm.ResolveInfo> queryIntentActivities(Intent intent, String resolvedType, long flags, int userId) {
        return new ParceledListSlice<>(pm().queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags)));
    }
    @Override public android.content.pm.ResolveInfo resolveIntent(Intent intent, String resolvedType, long flags, int userId) { return pm().resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags)); }
    @Override public String getInstallerPackageName(String packageName) { return null; }  // sideloaded, as getInstallSourceInfo
    @Override public android.content.pm.InstallSourceInfo getInstallSourceInfo(String packageName, int userId) {
        try { return pm().getInstallSourceInfo(packageName); } catch (PackageManager.NameNotFoundException e) { return null; }
    }
    @Override public int getApplicationEnabledSetting(String packageName, int userId) {
        try { return pm().getApplicationEnabledSetting(packageName); } catch (IllegalArgumentException e) { return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT; }
    }
    @Override public int getComponentEnabledSetting(ComponentName name, int userId) { return pm().getComponentEnabledSetting(name); }
    @Override public boolean hasSystemFeature(String name, int version) { return pm().hasSystemFeature(name, version); }
    @Override public int checkPermission(String permission, String packageName, int userId) { return pm().checkPermission(permission, packageName); }

    /** The "user" service: the one desktop user and no profiles, from UserManager (Robolectric's user store). */
    static final class Users extends IUserManager.Default {
        private static UserManager um() { return RuntimeEnvironment.getApplication().getSystemService(UserManager.class); }

        @Override public List<UserInfo> getUsers(boolean excludeDying) { return um().getUsers(); }
        @Override public UserInfo getUserInfo(int userId) { return um().getUserInfo(userId); }
        @Override public UserInfo getPrimaryUser() { return um().getUserInfo(android.os.UserHandle.USER_SYSTEM); }
        @Override public int getMainUserId() { return android.os.UserHandle.USER_SYSTEM; }
        // UserManager.getProfiles itself asks this service: answer from the user store, not through it
        @Override public List<UserInfo> getProfiles(int userId, boolean enabledOnly) {
            List<UserInfo> profiles = new ArrayList<>();
            UserInfo user = um().getUserInfo(userId);
            if (user != null) profiles.add(user);
            return profiles;
        }
        @Override public int[] getProfileIds(int userId, boolean enabledOnly) { return new int[] {userId}; }
        @Override public int getCredentialOwnerProfile(int userId) { return userId; }
        @Override public int getProfileParentId(int userId) { return userId; }
        @Override public int getUserSerialNumber(int userId) { return um().getUserSerialNumber(userId); }
        @Override public int getUserHandle(int serialNumber) { return um().getUserHandle(serialNumber); }
        @Override public android.os.Bundle getUserRestrictions(int userId) { return new android.os.Bundle(); }
        @Override public boolean isUserUnlocked(int userId) { return true; }
        @Override public boolean isUserRunning(int userId) { return true; }
        @Override public String getUserName() { return um().getUserName(); }
    }
}
