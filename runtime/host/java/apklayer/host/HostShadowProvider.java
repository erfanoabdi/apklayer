package apklayer.host;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Priority;
import org.robolectric.internal.ShadowProvider;

/**
 * apklayer's shadows, found through META-INF/services like Robolectric's own. A higher priority than
 * Robolectric's providers makes ours the first match for a class (ShadowMap takes the first that matches).
 */
@Priority(1000)
public final class HostShadowProvider implements ShadowProvider {
    @Override public void reset() {}  // the tree's ShadowProvider (Robolectric_all.jar); Maven's Robolectric takes a ClassTracker

    @Override public String[] getProvidedPackageNames() { return new String[0]; }
    @Override public Collection<Map.Entry<String, String>> getShadows() {
        var entries = new ArrayList<>(List.of(
                Map.entry("android.app.ApplicationPackageManager", ShadowHostPackageManager.class.getName()),
                Map.entry("android.content.pm.PackageInstaller", ShadowHostPackageInstaller.class.getName()),  // sessions go to the hub's installer
                Map.entry("android.content.pm.PackageInstaller.Session", ShadowHostPackageInstaller.Session.class.getName()),
                Map.entry("android.net.ConnectivityManager", ShadowHostConnectivityManager.class.getName()),
                Map.entry("android.app.DownloadManager", ShadowHostDownloadManager.class.getName()),
                Map.entry("android.app.ContextImpl", ShadowHostContextImpl.class.getName()),
                Map.entry("android.app.SystemServiceRegistry", ShadowHostSystemServiceRegistry.class.getName()),
                Map.entry("android.app.LoadedApk", ShadowHostLoadedApk.class.getName()),
                Map.entry("android.view.Display", ShadowHostDisplay.class.getName()),
                Map.entry("android.view.ViewConfiguration", ShadowHostDisplay.ViewConfigurationShadow.class.getName()),
                Map.entry("android.app.ActivityManager", ShadowHostActivityManager.class.getName()),
                Map.entry("android.accounts.AccountManager", HostPlainShadows.AccountManager.class.getName()),
                Map.entry("android.app.UiModeManager.Globals", HostConfiguration.ShadowGlobals.class.getName()),
                // real Binder, Parcel and the instance hub: system services across apps are processes behind real binder
                Map.entry("android.os.Parcel", ShadowHostRealParcel.class.getName()),
                Map.entry("android.os.Binder", ShadowHostRealBinder.class.getName()),
                Map.entry("libcore.io.Linux", ShadowHostRealLinux.class.getName()),
                Map.entry("android.os.BinderProxy", ShadowHostRealBinderProxy.class.getName()),
                Map.entry("com.android.internal.os.BinderInternal", ShadowHostRealBinderInternal.class.getName()),
                Map.entry("android.os.Process", ShadowHostProcess.class.getName()),
                Map.entry("android.os.ServiceManager", ShadowHostServiceManager.class.getName()),  // reports calls that land on Robolectric's null/deep proxies
                Map.entry("android.app.NotificationManager", ShadowHostNotificationManager.class.getName()),
                // Bluetooth over the host's BlueZ (HostBluetooth, HubBluetooth)
                Map.entry("android.bluetooth.BluetoothAdapter", ShadowHostBluetoothAdapter.class.getName()),
                Map.entry("android.bluetooth.le.BluetoothLeScanner", ShadowHostBluetoothLeScanner.class.getName()),
                Map.entry("android.bluetooth.BluetoothDevice", ShadowHostBluetoothDevice.class.getName()),
                Map.entry("android.bluetooth.BluetoothGatt", ShadowHostBluetoothGatt.class.getName()),
                Map.entry("android.bluetooth.BluetoothSocket", ShadowHostBluetoothSocket.class.getName()),
                Map.entry("android.bluetooth.BluetoothServerSocket", ShadowHostBluetoothServerSocket.class.getName()),
                Map.entry("android.app.JobSchedulerImpl", ShadowHostJobScheduler.class.getName()),
                Map.entry("android.app.AlarmManager", ShadowHostAlarmManager.class.getName()),
                Map.entry("android.drm.DrmManagerClient", ShadowHostDrmManagerClient.class.getName()),
                Map.entry("android.app.StatsManager", ShadowHostStatsManager.class.getName()),
                Map.entry("android.net.Network", ShadowHostNetwork.class.getName()),
                Map.entry("android.app.PendingIntent", ShadowHostRealPendingIntent.class.getName()),
                Map.entry("android.app.Service", ShadowHostService.class.getName()),
                Map.entry("android.app.IntentService", ShadowHostIntentService.class.getName()),  // its shadow must be a ShadowHostService (stopSelf)
                Map.entry("android.app.job.JobService", ShadowHostJobService.class.getName()),  // the same, and the real jobFinished reaches HostJobs
                Map.entry("android.os.ParcelFileDescriptor", ShadowHostRealParcelFileDescriptor.class.getName()),
                Map.entry("android.os.SharedMemory", ShadowHostSharedMemory.class.getName()),  // a real memfd, mappable in the process it is sent to
                Map.entry("android.content.ContentResolver", ShadowHostContentResolver.class.getName()),
                Map.entry("android.provider.Settings", ShadowHostSettings.class.getName()),
                Map.entry("android.app.Instrumentation", ShadowHostInstrumentation.class.getName()),
                Map.entry("android.app.Activity", ShadowHostActivity.class.getName()),
                Map.entry("android.os.UserManager", ShadowHostUserManager.class.getName()),
                Map.entry("android.os.StatFs", ShadowHostStatFs.class.getName()),
                Map.entry("android.compat.Compatibility", ShadowHostCompatibility.class.getName()),
                Map.entry("android.os.storage.StorageManager", ShadowHostStorageManager.class.getName()),
                Map.entry("android.content.ContentProviderClient", ShadowHostContentProviderClient.class.getName()),
                Map.entry("android.database.CursorWindow", ShadowHostRealCursorWindow.class.getName()),
                // native media, audio and the window system: Android's own services and natives behind the original classes
                Map.entry("android.media.MediaCodec", ShadowHostNativeMediaCodec.class.getName()),
                Map.entry("android.media.MediaCodecList", ShadowHostNativeMediaCodecList.class.getName()),
                Map.entry("android.media.MediaExtractor", ShadowHostNativeMediaExtractor.class.getName()),
                Map.entry("android.media.MediaPlayer", ShadowHostNativeMediaPlayer.class.getName()),
                Map.entry("android.media.AudioTrack", ShadowHostNativeAudioTrack.class.getName()),
                Map.entry("android.media.AudioSystem", ShadowHostNativeAudioSystem.class.getName()),
                Map.entry("android.media.audiopolicy.AudioProductStrategy", ShadowHostNativeAudioSystem.ProductStrategy.class.getName()),
                Map.entry("android.media.AudioRecord", ShadowHostNativeAudioRecord.class.getName()),
                Map.entry("android.media.MediaRecorder", ShadowHostNativeMediaRecorder.class.getName()),
                Map.entry("android.media.AudioManager", ShadowHostNativeAudioManager.class.getName()),
                Map.entry("android.hardware.biometrics.BiometricPrompt", ShadowHostBiometricPrompt.class.getName()),  // the device credential through the hub
                Map.entry("android.hardware.biometrics.BiometricManager", ShadowHostBiometricPrompt.Manager.class.getName()),
                Map.entry("android.content.AttributionSource.ScopedParcelState", ShadowHostNativeAttributionParcel.class.getName()),
                // a SurfaceView owns a real BLAST-backed window: native code (NDK, Vulkan, EGL) needs an ANativeWindow
                Map.entry("android.view.SurfaceView", ShadowHostNativeSurfaceView.class.getName()),
                Map.entry("android.view.TextureView", ShadowHostRealTextureView.class.getName()),
                Map.entry("android.view.SurfaceSession", HostPlainShadows.SurfaceSession.class.getName()),
                Map.entry("android.net.LocalSocketImpl", HostPlainShadows.LocalSocketImpl.class.getName()),
                Map.entry("android.view.PixelCopy", HostPlainShadows.PixelCopy.class.getName()),
                Map.entry("android.graphics.SurfaceTexture", HostGLShadows.SurfaceTexture.class.getName()),
                Map.entry("android.graphics.TextureLayer", HostGLShadows.TextureLayer.class.getName()),
                Map.entry("android.opengl.GLES20", HostGLShadows.GLES20.class.getName()),
                Map.entry("android.opengl.GLUtils", HostGLShadows.GLUtils.class.getName()),
                Map.entry("android.opengl.EGL14", HostGLShadows.EGL14.class.getName()),
                Map.entry("android.opengl.EGL15", HostGLShadows.EGL15.class.getName()),
                Map.entry("android.opengl.EGLExt", HostGLShadows.EGLExt.class.getName()),
                Map.entry("android.opengl.GLES10", HostGLShadows.GLES10.class.getName()),
                Map.entry("android.opengl.GLES10Ext", HostGLShadows.GLES10Ext.class.getName()),
                Map.entry("android.opengl.GLES11", HostGLShadows.GLES11.class.getName()),
                Map.entry("android.opengl.GLES11Ext", HostGLShadows.GLES11Ext.class.getName()),
                Map.entry("android.opengl.GLES30", HostGLShadows.GLES30.class.getName()),
                Map.entry("android.opengl.GLES31", HostGLShadows.GLES31.class.getName()),
                Map.entry("android.opengl.GLES31Ext", HostGLShadows.GLES31Ext.class.getName()),
                Map.entry("android.opengl.GLES32", HostGLShadows.GLES32.class.getName()),
                Map.entry("android.media.MediaMetadataRetriever", HostMediaShadows.MediaMetadataRetriever.class.getName()),
                Map.entry("android.media.MediaMuxer", HostMediaShadows.MediaMuxer.class.getName()),
                Map.entry("android.app.usage.StorageStatsManager", ShadowHostStorageStatsManager.class.getName()),
                Map.entry("android.content.om.OverlayManager", ShadowHostOverlayManager.class.getName()),  // no overlays: empty lists, not null
                Map.entry("android.os.Vibrator", HostPlainShadows.Vibrator.class.getName()),
                Map.entry("android.os.SystemVibrator", HostPlainShadows.SystemVibrator.class.getName()),
                Map.entry("android.widget.Toast", ShadowHostToast.class.getName()),
                Map.entry("android.view.inputmethod.InputMethodManager", HostTextBridge.ImeShadow.class.getName()),  // every hide request reaches the shell
                // USB without a HAL: the host's devices and the framework's own connection code over the device node (HostUsb, HubUsb)
                Map.entry("android.hardware.usb.UsbManager", ShadowHostUsbManager.class.getName()),
                Map.entry("android.hardware.usb.UsbDevice", HostPlainShadows.UsbDevice.class.getName()),
                Map.entry("android.hardware.usb.UsbDeviceConnection", HostPlainShadows.UsbDeviceConnection.class.getName()),
                Map.entry("android.hardware.usb.UsbRequest", HostPlainShadows.UsbRequest.class.getName()),
                Map.entry("android.os.SystemProperties", ShadowHostSystemProperties.class.getName()),
                Map.entry("android.hardware.HardwareBuffer", ShadowHostNativeHardwareBuffer.class.getName()),
                Map.entry("android.app.DownloadManager", ShadowHostDownloadManager.class.getName()),  // the real one talks to DownloadProvider (DownloadStorageProvider reads it)
                Map.entry("android.util.Xml", ShadowHostXml.class.getName()),
                Map.entry("dalvik.system.VMRuntime", ShadowHostVMRuntime.class.getName()),
                Map.entry("dalvik.system.BaseDexClassLoader", ShadowHostBaseDexClassLoader.class.getName())));
        if (HostCameraShadows.declared()) entries.addAll(HostCameraShadows.entries());  // the stack runs cameraserver over the camera HAL
        if (System.getProperty("apklayer.webviewprovider") != null) { // the original WebView implementation (a provider APK)
            entries.add(Map.entry("android.webkit.WebView", ShadowHostRealWebView.class.getName()));
            entries.add(Map.entry("android.webkit.WebViewDelegate", ShadowHostWebViewDelegate.class.getName()));
            entries.add(Map.entry("android.webkit.WebViewFactory", ShadowHostWebViewFactory.class.getName()));
            entries.add(Map.entry("android.webkit.CookieManager", HostPlainShadows.CookieManager.class.getName()));
            entries.add(Map.entry("android.webkit.WebStorage", HostPlainShadows.WebStorage.class.getName()));
            entries.add(Map.entry("android.webkit.WebViewDatabase", HostPlainShadows.WebViewDatabase.class.getName()));
        }
        if (HostSensorShadows.declared()) {  // the stack runs SensorService over the sensors HAL (providers.toml)
            entries.add(Map.entry("android.hardware.SensorManager", HostSensorShadows.SensorManager.class.getName()));
            entries.add(Map.entry("android.hardware.SystemSensorManager", HostSensorShadows.SystemSensorManager.class.getName()));
            entries.add(Map.entry("android.hardware.SystemSensorManager.BaseEventQueue", HostSensorShadows.BaseEventQueue.class.getName()));
        }
        if (systemApp("com.android.providers.settings")) {
            // the tree's SettingsProvider is installed: Settings.* talk to it through the hub like on a device, not to Robolectric's in-memory tables.
            // Keys of nested classes are canonical names (ShadowMap looks them up by Class.getCanonicalName()).
            entries.add(Map.entry("android.provider.Settings.System", HostPlainShadows.SettingsSystem.class.getName()));
            entries.add(Map.entry("android.provider.Settings.Secure", HostPlainShadows.SettingsSecure.class.getName()));
            entries.add(Map.entry("android.provider.Settings.Global", HostPlainShadows.SettingsGlobal.class.getName()));
            entries.add(Map.entry("android.provider.Settings.Config", HostPlainShadows.SettingsConfig.class.getName()));
        }
        return entries;
    }

    private static boolean systemApp(String packageName) {
        String apps = System.getProperty("apklayer.apps");
        return apps != null && new java.io.File(apps, packageName + "/classes.jar").exists();
    }

    /** Classes that Robolectric picks the shadow for by a ShadowPicker: ours must be listed here to take part. */
    @Override public Map<String, String> getShadowPickerMap() {
        return Map.of("android.content.res.ApkAssets", ShadowHostApkAssets.Picker.class.getName(),
                "android.os.MessageQueue", ShadowHostMessageQueue.Picker.class.getName(),
                "android.database.CursorWindow", ShadowHostRealCursorWindow.Picker.class.getName(),
                "android.graphics.HardwareRenderer", ShadowHostHardwareRenderer.Picker.class.getName());
    }
}
