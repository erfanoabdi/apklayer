package apklayer.host;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Priority;
import org.robolectric.internal.ShadowProvider;
import org.robolectric.internal.ClassTracker;

/**
 * apklayer's shadows, found through META-INF/services like Robolectric's own. A higher priority than
 * Robolectric's providers makes ours the first match for a class (ShadowMap takes the first that matches).
 */
@Priority(1000)
public final class HostShadowProvider implements ShadowProvider {
    @Override public void reset(ClassTracker classTracker) {}

    @Override public String[] getProvidedPackageNames() { return new String[0]; }

    @Override public Collection<Map.Entry<String, String>> getShadows() {
        var entries = new ArrayList<>(List.of(
                Map.entry("android.app.ApplicationPackageManager", ShadowHostPackageManager.class.getName()),
                Map.entry("android.net.ConnectivityManager", ShadowHostConnectivityManager.class.getName()),
                Map.entry("android.app.DownloadManager", ShadowHostDownloadManager.class.getName()),
                Map.entry("android.app.ContextImpl", ShadowHostContextImpl.class.getName()),
                Map.entry("android.view.Display", ShadowHostDisplay.class.getName()),
                Map.entry("android.app.ActivityManager", ShadowHostActivityManager.class.getName()),
                // real Binder, Parcel and the instance hub: system services across apps are processes behind real binder
                Map.entry("android.os.Parcel", ShadowHostRealParcel.class.getName()),
                Map.entry("android.os.Binder", ShadowHostRealBinder.class.getName()),
                Map.entry("libcore.io.Linux", ShadowHostRealLinux.class.getName()),
                Map.entry("android.os.BinderProxy", ShadowHostRealBinderProxy.class.getName()),
                Map.entry("com.android.internal.os.BinderInternal", ShadowHostRealBinderInternal.class.getName()),
                Map.entry("android.os.Process", ShadowHostProcess.class.getName()),
                Map.entry("android.app.NotificationManager", ShadowHostNotificationManager.class.getName()),
                Map.entry("android.app.PendingIntent", ShadowHostRealPendingIntent.class.getName()),
                Map.entry("android.app.Service", ShadowHostService.class.getName()),
                Map.entry("android.os.ParcelFileDescriptor", ShadowHostRealParcelFileDescriptor.class.getName()),
                Map.entry("android.content.ContentResolver", ShadowHostContentResolver.class.getName()),
                Map.entry("android.database.CursorWindow", ShadowHostRealCursorWindow.class.getName()),
                // native media, audio and the window system: Android's own services and natives behind the original classes
                Map.entry("android.media.MediaCodec", ShadowHostNativeMediaCodec.class.getName()),
                Map.entry("android.media.MediaCodecList", ShadowHostNativeMediaCodecList.class.getName()),
                Map.entry("android.media.MediaExtractor", ShadowHostNativeMediaExtractor.class.getName()),
                Map.entry("android.media.MediaPlayer", ShadowHostNativeMediaPlayer.class.getName()),
                Map.entry("android.media.AudioTrack", ShadowHostNativeAudioTrack.class.getName()),
                Map.entry("android.media.AudioSystem", ShadowHostNativeAudioSystem.class.getName()),
                Map.entry("android.media.AudioRecord", ShadowHostNativeAudioRecord.class.getName()),
                Map.entry("android.media.MediaRecorder", ShadowHostNativeMediaRecorder.class.getName()),
                Map.entry("android.media.AudioManager", ShadowHostNativeAudioManager.class.getName()),
                Map.entry("android.content.AttributionSource.ScopedParcelState", ShadowHostNativeAttributionParcel.class.getName()),
                // a SurfaceView owns a real BLAST-backed window: native code (NDK, Vulkan, EGL) needs an ANativeWindow
                Map.entry("android.view.SurfaceView", ShadowHostNativeSurfaceView.class.getName()),
                Map.entry("android.view.TextureView", ShadowHostRealTextureView.class.getName()),
                Map.entry("android.graphics.SurfaceTexture", HostGLShadows.SurfaceTexture.class.getName()),
                Map.entry("android.graphics.TextureLayer", HostGLShadows.TextureLayer.class.getName()),
                Map.entry("android.opengl.GLES20", HostGLShadows.GLES20.class.getName()),
                Map.entry("android.opengl.GLUtils", HostGLShadows.GLUtils.class.getName()),
                Map.entry("android.hardware.HardwareBuffer", ShadowHostNativeHardwareBuffer.class.getName()),
                Map.entry("dalvik.system.BaseDexClassLoader", ShadowHostBaseDexClassLoader.class.getName())));
        if (System.getProperty("apklayer.webviewprovider") != null) { // the original WebView implementation (a provider APK)
            entries.add(Map.entry("android.webkit.WebView", ShadowHostRealWebView.class.getName()));
            entries.add(Map.entry("android.webkit.WebViewDelegate", ShadowHostWebViewDelegate.class.getName()));
            entries.add(Map.entry("android.webkit.WebViewFactory", ShadowHostWebViewFactory.class.getName()));
        }
        return entries;
    }

    /** Classes that Robolectric picks the shadow for by a ShadowPicker: ours must be listed here to take part. */
    @Override public Map<String, String> getShadowPickerMap() {
        return Map.of("android.content.res.ApkAssets", ShadowHostApkAssets.Picker.class.getName(),
                "android.os.MessageQueue", ShadowHostMessageQueue.Picker.class.getName(),
                "android.graphics.BitmapFactory", ShadowHostBitmapFactory.Picker.class.getName(),
                "android.graphics.ImageDecoder", ShadowHostImageDecoder.Picker.class.getName(),
                "android.database.CursorWindow", ShadowHostRealCursorWindow.Picker.class.getName());
    }
}
