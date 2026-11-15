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
    /** LocalSocket/LocalServerSocket's natives (bind, connect, read, write: libandroid_runtime's) instead of Robolectric's no-ops. */
    @Implements(className = "android.net.LocalSocketImpl", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static final class LocalSocketImpl {}
    /** HWUI's readback of the window's real buffers (Magnifier, screenshots) instead of Robolectric's View.draw / ImageReader copy. */
    @Implements(className = "android.view.PixelCopy", isInAndroidSdk = false) public static final class PixelCopy {}
    /** With the real WebView provider: its cookie store, web storage and database (getInstance asks WebViewFactory's provider), not Robolectric's
     * in-memory ones, so cookies a page set in the WebView are what CookieManager returns (microG's sign-in reads Google's oauth cookie). */
    @Implements(className = "android.webkit.CookieManager", isInAndroidSdk = false) public static final class CookieManager {}
    @Implements(className = "android.webkit.WebStorage", isInAndroidSdk = false) public static final class WebStorage {}
    @Implements(className = "android.webkit.WebViewDatabase", isInAndroidSdk = false) public static final class WebViewDatabase {}
    /** The framework's vibrator over the instance's (absent) vibrator_manager service: hasVibrator() is false on a desktop, where Robolectric's said true. */
    @Implements(className = "android.os.Vibrator", isInAndroidSdk = false) public static final class Vibrator {}
    @Implements(className = "android.os.SystemVibrator", isInAndroidSdk = false) public static final class SystemVibrator {}
    /** HIDL's Java side (docs/HIDL.md): libandroid_runtime's HwBinder/HwParcel/HwBlob natives (registered by bridge/native/binder_runtime.c) instead of Robolectric's no-ops. */
    @Implements(className = "android.os.HwBinder", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class HwBinder {}
    @Implements(className = "android.os.HwRemoteBinder", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class HwRemoteBinder {}
    @Implements(className = "android.os.HwParcel", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class HwParcel {}
    @Implements(className = "android.os.HwBlob", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class HwBlob {}
    @Implements(className = "android.os.HidlSupport", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class HidlSupport {}
    @Implements(className = "android.os.HidlMemory", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class HidlMemory {}
    @Implements(className = "android.os.NativeHandle", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class NativeHandle {}
    /** libandroid_runtime's USB natives over libusbhost (usbdevfs ioctls on the node ShadowHostUsbManager.openDevice opens), not Robolectric's piped fakes. */
    @Implements(className = "android.hardware.usb.UsbDevice", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class UsbDevice {}
    @Implements(className = "android.hardware.usb.UsbDeviceConnection", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class UsbDeviceConnection {}
    @Implements(className = "android.hardware.usb.UsbRequest", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class UsbRequest {}
}
