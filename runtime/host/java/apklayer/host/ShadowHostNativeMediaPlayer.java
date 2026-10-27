package apklayer.host;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Parcel;
import android.os.IBinder;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Original player state machine/JNI; select real request and reply payloads explicitly. */
@Implements(value = MediaPlayer.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeMediaPlayer {
    @RealObject private MediaPlayer real;
    @Implementation protected static void __staticInitializer__() { /* completed after JNI registration */ }
    @Implementation protected void native_setup(Object weak, Parcel attribution, int session) {
        NativeAudio.ensureInitialized();
        NativeParcel.requireCallbackFactory();
        nativeCall("native_setup", ClassParameter.from(Object.class, weak),
                ClassParameter.from(Parcel.class, attribution), ClassParameter.from(int.class, session));
    }
    @Implementation protected void nativeSetDataSource(IBinder http, String path, String[] keys, String[] values) {
        if (http != null) throw new UnsupportedOperationException("Native HTTP requires the Java Binder bridge");
        nativeCall("nativeSetDataSource", ClassParameter.from(IBinder.class, http), ClassParameter.from(String.class, path),
                ClassParameter.from(String[].class, keys), ClassParameter.from(String[].class, values));
    }
    @Implementation protected Parcel newRequest() {
        Parcel request = NativeParcel.obtain();
        request.writeInterfaceToken("android.media.IMediaPlayer");
        return request;
    }
    private <T> T javaCall(String name, ClassParameter<?>... args) {
        try (NativeParcel.Scope scope = NativeParcel.scope()) {
            return ReflectionHelpers.callInstanceMethod(real,
                    Shadow.directMethodName(MediaPlayer.class.getName(), name), args);
        }
    }
    private <T> T nativeCall(String name, ClassParameter<?>... args) {
        return ReflectionHelpers.callInstanceMethod(real,
                Shadow.directNativeMethodName(MediaPlayer.class.getName(), name), args);
    }
    @Implementation protected MediaPlayer.TrackInfo[] getInbandTrackInfo() { return javaCall("getInbandTrackInfo"); }
    @Implementation protected int getSelectedTrack(int type) { return javaCall("getSelectedTrack", ClassParameter.from(int.class, type)); }
    @Implementation protected void selectOrDeselectInbandTrack(int index, boolean select) {
        javaCall("selectOrDeselectInbandTrack", ClassParameter.from(int.class, index), ClassParameter.from(boolean.class, select));
    }
    @Implementation protected void setVideoScalingMode(int mode) { javaCall("setVideoScalingMode", ClassParameter.from(int.class, mode)); }
    @Implementation protected void setAudioAttributes(AudioAttributes attributes) {
        javaCall("setAudioAttributes", ClassParameter.from(AudioAttributes.class, attributes));
    }
    @Implementation protected int _prepare(Parcel request) {
        NativeParcel.requireNative(request);
        return nativeCall("_prepare", ClassParameter.from(Parcel.class, request));
    }
    @Implementation protected int _prepareAsync(Parcel request) {
        NativeParcel.requireNative(request);
        return nativeCall("_prepareAsync", ClassParameter.from(Parcel.class, request));
    }
    @Implementation protected int native_invoke(Parcel request, Parcel reply) {
        NativeParcel.requireNative(request); NativeParcel.promote(reply);
        return nativeCall("native_invoke", ClassParameter.from(Parcel.class, request), ClassParameter.from(Parcel.class, reply));
    }
    @Implementation protected boolean setParameter(int key, Parcel value) {
        NativeParcel.requireNative(value);
        return nativeCall("setParameter", ClassParameter.from(int.class, key), ClassParameter.from(Parcel.class, value));
    }
    @Implementation protected int native_setMetadataFilter(Parcel request) {
        NativeParcel.requireNative(request);
        return nativeCall("native_setMetadataFilter", ClassParameter.from(Parcel.class, request));
    }
    @Implementation protected boolean native_getMetadata(boolean update, boolean filter, Parcel reply) {
        NativeParcel.promote(reply);
        return nativeCall("native_getMetadata", ClassParameter.from(boolean.class, update),
                ClassParameter.from(boolean.class, filter), ClassParameter.from(Parcel.class, reply));
    }
    @Implementation protected static int native_pullBatteryData(Parcel reply) {
        NativeParcel.promote(reply);
        return ReflectionHelpers.callStaticMethod(MediaPlayer.class,
                Shadow.directNativeMethodName(MediaPlayer.class.getName(), "native_pullBatteryData"), ClassParameter.from(Parcel.class, reply));
    }
}
