package apklayer.host;

import org.robolectric.annotation.Implements;

/** Shadows without methods: the class each one is mapped to runs its original code (Robolectric's in-memory fakes of it are not used). */
final class HostPlainShadows {
    private HostPlainShadows() {}

    @Implements(className = "android.provider.Settings$System", isInAndroidSdk = false) public static final class SettingsSystem {}
    @Implements(className = "android.provider.Settings$Secure", isInAndroidSdk = false) public static final class SettingsSecure {}
    @Implements(className = "android.provider.Settings$Global", isInAndroidSdk = false) public static final class SettingsGlobal {}
    @Implements(className = "android.provider.Settings$Config", isInAndroidSdk = false) public static final class SettingsConfig {}
    /** The framework's AccountManager over the instance's account service in the hub (HubAccounts), instead of Robolectric's per-JVM store. */
    @Implements(className = "android.accounts.AccountManager", isInAndroidSdk = false) public static final class AccountManager {}
}
