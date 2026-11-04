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
    /** A real SurfaceComposerClient behind a session (Magnifier's layers): without this its native create returned Robolectric's 0. */
    @Implements(className = "android.view.SurfaceSession", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static final class SurfaceSession {}
    /** HWUI's readback of the window's real buffers (Magnifier, screenshots) instead of Robolectric's View.draw / ImageReader copy. */
    @Implements(className = "android.view.PixelCopy", isInAndroidSdk = false) public static final class PixelCopy {}
}
