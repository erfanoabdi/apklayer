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
        var entries = new ArrayList<>(List.of(Map.entry("android.webkit.WebView", ShadowHostWebView.class.getName()),
                Map.entry("android.media.MediaPlayer", ShadowHostMediaPlayer.class.getName()),
                Map.entry("android.media.SoundPool", ShadowHostSoundPool.class.getName()),
                Map.entry("android.app.ApplicationPackageManager", ShadowHostPackageManager.class.getName()),
                Map.entry("android.net.ConnectivityManager", ShadowHostConnectivityManager.class.getName()),
                Map.entry("android.app.DownloadManager", ShadowHostDownloadManager.class.getName()),
                Map.entry("android.media.MediaCodec", ShadowHostMediaCodec.class.getName()),
                Map.entry("android.view.SurfaceView", ShadowHostSurfaceView.class.getName()),
                Map.entry("android.app.ContextImpl", ShadowHostContextImpl.class.getName()),
                Map.entry("android.media.AudioTrack", ShadowHostAudioTrack.class.getName()),
                Map.entry("android.view.Display", ShadowHostDisplay.class.getName()),
                Map.entry("android.app.ActivityManager", ShadowHostActivityManager.class.getName())));
        if (Boolean.getBoolean("apklayer.nativemedia")) {
            entries.removeIf(entry -> entry.getKey().equals("android.media.MediaCodec"));
            entries.add(Map.entry("android.media.MediaCodec", ShadowHostNativeMediaCodec.class.getName()));
            entries.add(Map.entry("android.media.MediaCodecList", ShadowHostNativeMediaCodecList.class.getName()));
            entries.add(Map.entry("android.media.MediaExtractor", ShadowHostNativeMediaExtractor.class.getName()));
            if (Boolean.getBoolean("apklayer.gpu")) {
                entries.removeIf(entry -> entry.getKey().equals("android.view.SurfaceView"));
                entries.add(Map.entry("android.view.SurfaceView", ShadowHostNativeSurfaceView.class.getName()));
            }
        }
        if (Boolean.getBoolean("apklayer.gpu"))
            entries.add(Map.entry("android.hardware.HardwareBuffer", ShadowHostNativeHardwareBuffer.class.getName()));
        if (Boolean.getBoolean("apklayer.nativeattribution") || Boolean.getBoolean("apklayer.nativeaudio")) {
            entries.add(Map.entry("android.content.AttributionSource.ScopedParcelState", ShadowHostNativeAttributionParcel.class.getName()));
        }
        if (Boolean.getBoolean("apklayer.nativeaudio")) {
            entries.removeIf(entry -> entry.getKey().equals("android.media.AudioTrack"));
            entries.add(Map.entry("android.media.AudioTrack", ShadowHostNativeAudioTrack.class.getName()));
            entries.add(Map.entry("android.media.AudioSystem", ShadowHostNativeAudioSystem.class.getName()));
            entries.add(Map.entry("android.media.AudioRecord", ShadowHostNativeAudioRecord.class.getName()));
            entries.add(Map.entry("android.media.MediaRecorder", ShadowHostNativeMediaRecorder.class.getName()));
            entries.add(Map.entry("android.media.AudioManager", ShadowHostNativeAudioManager.class.getName()));
        }
        if (Boolean.getBoolean("apklayer.nativeparcels")) {
            entries.add(Map.entry("android.os.Parcel", ShadowHostNativeParcel.class.getName()));
        }
        if (Boolean.getBoolean("apklayer.nativeplayer")) {
            if (!Boolean.getBoolean("apklayer.nativeaudio") || !Boolean.getBoolean("apklayer.nativeparcels")
                    || !Boolean.getBoolean("apklayer.nativemedia"))
                throw new IllegalStateException("Native player requires nativeaudio, nativeparcels and nativemedia");
            entries.removeIf(entry -> entry.getKey().equals("android.media.MediaPlayer"));
            entries.add(Map.entry("android.media.MediaPlayer", ShadowHostNativeMediaPlayer.class.getName()));
        }
        if (Boolean.getBoolean("apklayer.realbinder")) {
            entries.removeIf(entry -> entry.getKey().equals("android.os.Parcel"));
            entries.add(Map.entry("android.os.Parcel", ShadowHostRealParcel.class.getName()));
            entries.add(Map.entry("android.os.Binder", ShadowHostRealBinder.class.getName()));
            entries.add(Map.entry("android.os.BinderProxy", ShadowHostRealBinderProxy.class.getName()));
            entries.add(Map.entry("com.android.internal.os.BinderInternal", ShadowHostRealBinderInternal.class.getName()));
            entries.add(Map.entry("android.os.Process", ShadowHostProcess.class.getName()));
            entries.add(Map.entry("android.app.Service", ShadowHostService.class.getName()));
            entries.add(Map.entry("android.os.ParcelFileDescriptor", ShadowHostRealParcelFileDescriptor.class.getName()));
            entries.add(Map.entry("android.content.ContentResolver", ShadowHostContentResolver.class.getName()));
            entries.add(Map.entry("android.database.CursorWindow", ShadowHostRealCursorWindow.class.getName()));
        }
        return entries;
    }

    /** Classes that Robolectric picks the shadow for by a ShadowPicker: ours must be listed here to take part. */
    @Override public Map<String, String> getShadowPickerMap() {
        if (Boolean.getBoolean("apklayer.realbinder"))
            return Map.of("android.content.res.ApkAssets", ShadowHostApkAssets.Picker.class.getName(),
                    "android.os.MessageQueue", ShadowHostMessageQueue.Picker.class.getName(),
                    "android.graphics.BitmapFactory", ShadowHostBitmapFactory.Picker.class.getName(),
                    "android.graphics.ImageDecoder", ShadowHostImageDecoder.Picker.class.getName(),
                    "android.database.CursorWindow", ShadowHostRealCursorWindow.Picker.class.getName());
        return Map.of("android.content.res.ApkAssets", ShadowHostApkAssets.Picker.class.getName(),
                "android.os.MessageQueue", ShadowHostMessageQueue.Picker.class.getName(),
                "android.graphics.BitmapFactory", ShadowHostBitmapFactory.Picker.class.getName(),
                "android.graphics.ImageDecoder", ShadowHostImageDecoder.Picker.class.getName());
    }
}
