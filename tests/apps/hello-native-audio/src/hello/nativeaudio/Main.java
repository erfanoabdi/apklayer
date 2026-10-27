package hello.nativeaudio;

import android.app.Activity;
import android.content.AttributionSource;
import android.os.Binder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Method;

public class Main extends Activity {
    private TextView result;
    private static void require(boolean yes, String detail) {
        if (!yes) throw new AssertionError(detail);
    }
    private static void different(Method matches, AttributionSource source, Parcel parcel,
            Object payload, String fieldName, Object changed) throws Exception {
        var field = payload.getClass().getField(fieldName);
        Object original = field.get(payload);
        try {
            field.set(payload, changed);
            require(!(Boolean) matches.invoke(null, source, parcel), "native field " + fieldName);
        } finally { field.set(payload, original); }
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        result = new TextView(this); result.setTextSize(22);
        result.setText("Checking native attribution Parcel…"); setContentView(result);
        new Thread(this::check, "native-audio-check").start();
    }
    private void check() {
        try {
            Class<?> helper = Class.forName("apklayer.host.NativeAttribution");
            Method matches = helper.getMethod("matches", AttributionSource.class, Parcel.class);
            Method active = helper.getMethod("activeParcels");
            AttributionSource tail = new AttributionSource.Builder(Process.myUid() + 1)
                    .setPid(321).setAttributionTag(null).setPackageName(null).build();
            AttributionSource source = new AttributionSource.Builder(Process.myUid())
                    .setPid(123).setDeviceId(0).setPackageName(getPackageName())
                    .setAttributionTag("tag-تست-😀").setNext(tail).build();
            Object payload = AttributionSource.class.getMethod("asState").invoke(source);
            payload.getClass().getField("renouncedPermissions").set(payload,
                    new String[]{"android.permission.RECORD_AUDIO", null, "permission.😀"});
            Method scoped = AttributionSource.class.getMethod("asScopedParcelState");
            for (int n = 0; n < 3; n++) {
                try (AutoCloseable state = (AutoCloseable) scoped.invoke(source)) {
                    Object shadow = Class.forName("org.robolectric.shadow.api.Shadow")
                            .getMethod("extract", Object.class).invoke(null, state);
                    System.out.println("[hello-native-audio] scope=" + shadow.getClass().getName()
                            + " active=" + active.invoke(null));
                    Parcel parcel = (Parcel) state.getClass().getMethod("getParcel").invoke(state);
                    require((Boolean) matches.invoke(null, source, parcel), "native state roundtrip");
                    require(((Number) active.invoke(null)).intValue() == 1, "scoped native ownership");
                    different(matches, source, parcel, payload, "uid", source.getUid() + 1);
                    different(matches, source, parcel, payload, "pid", source.getPid() + 1);
                    different(matches, source, parcel, payload, "deviceId", 42);
                    different(matches, source, parcel, payload, "packageName", "different.package");
                    different(matches, source, parcel, payload, "attributionTag", "tag-تست-😁");
                    different(matches, source, parcel, payload, "renouncedPermissions",
                            new String[]{"android.permission.RECORD_AUDIO", null, "permission.😁"});
                    different(matches, source, parcel, payload, "next",
                            java.lang.reflect.Array.newInstance(payload.getClass(), 0));
                    Object leaf = AttributionSource.class.getMethod("asState").invoke(tail);
                    different(matches, source, parcel, leaf, "pid", 999);
                    different(matches, source, parcel, leaf, "packageName", "leaf.package");
                    var tokenField = payload.getClass().getField("token");
                    Object original = tokenField.get(payload);
                    tokenField.set(payload, new Binder());
                    require(!(Boolean) matches.invoke(null, source, parcel), "distinct local token identity");
                    tokenField.set(payload, original);
                    require((Boolean) matches.invoke(null, source, parcel), "stable local token identity");
                }
                require(((Number) active.invoke(null)).intValue() == 0, "native Parcel released");
                Parcel ordinary = Parcel.obtain();
                ordinary.writeInt(12345); ordinary.setDataPosition(0);
                require(ordinary.readInt() == 12345, "ordinary Java Parcel still usable"); ordinary.recycle();
            }
            var nextField = payload.getClass().getField("next");
            Object originalNext = nextField.get(payload);
            Object cycle = java.lang.reflect.Array.newInstance(payload.getClass(), 1);
            java.lang.reflect.Array.set(cycle, 0, payload);
            try {
                nextField.set(payload, cycle);
                try { scoped.invoke(source); throw new AssertionError("cyclic attribution accepted"); }
                catch (java.lang.reflect.InvocationTargetException expected) {
                    require(expected.getCause() instanceof IllegalArgumentException, "cyclic source error");
                }
            } finally { nextField.set(payload, originalNext); }
            require(((Number) active.invoke(null)).intValue() == 0, "failed scope released");
            System.out.println("[hello-native-audio] attribution PASS fields/Unicode/chain/tokens/scoped release");
            if (Boolean.getBoolean("apklayer.nativeaudio")) {
                ParcelCheck.run();
                TrackCheck.run(this);
                if (System.getProperty("hello.rate") != null) RateCheck.run();
                require(((Number) active.invoke(null)).intValue() == 0, "AudioTrack attribution scope released");
            }
            runOnUiThread(() -> {
                Runnable show = () -> {
                    result.getViewTreeObserver().registerFrameCommitCallback(
                            () -> System.out.println("[hello-native-audio] FRAME PASS"));
                    result.setText(Boolean.getBoolean("apklayer.nativeaudio")
                            ? "PASS: original AudioTrack JNI\nAudioFlinger → PipeWire\nPCM, clock, marker, timestamp"
                            : "PASS: real attribution Parcel\nFields, Unicode, chain, Binder identity\nScoped native ownership");
                    result.invalidate();
                };
                if (result.isAttachedToWindow()) show.run();
                else result.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View view) {
                        view.removeOnAttachStateChangeListener(this); view.post(show);
                    }
                    public void onViewDetachedFromWindow(View view) {}
                });
            });
        } catch (Throwable error) {
            error.printStackTrace(); System.out.println("[hello-native-audio] FAIL " + error);
            runOnUiThread(() -> result.setText("FAIL: " + error));
        }
    }
}
