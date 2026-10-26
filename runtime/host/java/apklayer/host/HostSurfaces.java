package apklayer.host;

import android.graphics.PixelFormat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.robolectric.shadows.ShadowSurfaceView;
import org.robolectric.shadow.api.Shadow;

/**
 * SurfaceView support (PLAN P4.3). Android's window manager creates the surface and tells the holder's
 * callbacks; Robolectric's fake holder never does (a GLSurfaceView's render thread then waits forever).
 * Once a SurfaceView has a size: give its callbacks a holder with a real {@link Surface}
 * ({@link HostSurface}, which the libandroid shim turns into an ANativeWindow) and deliver
 * surfaceCreated + surfaceChanged. Native GL renders into $APKLAYER_GLDIR/<surface id>.rgba (libEGL shim);
 * {@link #compose} blits those frames over the SurfaceView's area of the window.
 */
final class HostSurfaces {
    private HostSurfaces() {}

    /** The Surface of a SurfaceView; the libandroid shim reads width(), height() and hashCode() over JNI. */
    public static final class HostSurface extends Surface {
        final SurfaceView view;
        int seen; // seq of the last GL frame picked up

        HostSurface(SurfaceView view) { this.view = view; }
        public int width() { return view.getWidth(); }
        public int height() { return view.getHeight(); }
    }

    private static final class HostHolder extends ShadowSurfaceView.FakeSurfaceHolder {
        final HostSurface surface;
        HostHolder(HostSurface surface) { this.surface = surface; }
        @Override public Surface getSurface() { return surface; }
    }

    private static final Map<SurfaceView, HostSurface> surfaces = new WeakHashMap<>();
    private static final Path DIR = System.getenv("APKLAYER_GLDIR") == null ? null : Path.of(System.getenv("APKLAYER_GLDIR"));

    /** Called every host loop: notifies new SurfaceViews and repaints those with a new GL frame. */
    static void follow() {
        List<View> windows = org.robolectric.util.ReflectionHelpers.getField(android.view.WindowManagerGlobal.getInstance(), "mViews");
        for (View w : windows) scan(w);
    }

    private static void scan(View v) {
        if (v instanceof SurfaceView sv) {
            if (sv.getWidth() <= 0 || sv.getHeight() <= 0) return;
            HostSurface s = surfaces.get(sv);
            if (s == null) {
                surfaces.put(sv, s = new HostSurface(sv));
                ShadowSurfaceView.FakeSurfaceHolder fake = ((ShadowSurfaceView) Shadow.extract(sv)).getFakeSurfaceHolder();
                HostHolder holder = new HostHolder(s);
                System.out.println("[apklayer] surface " + s.hashCode() + " " + sv.getWidth() + "x" + sv.getHeight() + " -> " + fake.getCallbacks().size() + " callbacks");
                for (SurfaceHolder.Callback c : fake.getCallbacks().toArray(new SurfaceHolder.Callback[0])) {
                    c.surfaceCreated(holder);
                    c.surfaceChanged(holder, PixelFormat.RGBA_8888, sv.getWidth(), sv.getHeight());
                }
            } else if (frameSeq(s) != s.seen) {
                s.seen = frameSeq(s);
                sv.invalidate(); // a new frame: make Robolectric produce a window frame to compose onto
            }
        } else if (v instanceof ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) scan(g.getChildAt(i));
        }
    }

    private static Path file(HostSurface s) { return DIR == null ? null : DIR.resolve(s.hashCode() + ".rgba"); }

    private static int frameSeq(HostSurface s) {
        Path f = file(s);
        if (f == null || !Files.exists(f)) return 0;
        try (var in = Files.newInputStream(f)) {
            byte[] h = in.readNBytes(12);
            return h.length < 12 ? 0 : ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).getInt(8);
        } catch (IOException e) {
            return 0;
        }
    }

    /** Blits each SurfaceView's latest GL frame into the composed window frame (ARGB, w x h). */
    static void compose(int[] argb, int w, int h) {
        for (Map.Entry<SurfaceView, HostSurface> e : surfaces.entrySet()) {
            SurfaceView sv = e.getKey();
            Path f = file(e.getValue());
            if (f == null || !Files.exists(f) || !sv.isShown()) continue;
            try {
                ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(f)).order(ByteOrder.LITTLE_ENDIAN);
                int fw = b.getInt(0), fh = b.getInt(4);
                int[] loc = new int[2];
                sv.getLocationOnScreen(loc);
                for (int y = 0; y < sv.getHeight() && loc[1] + y < h; y++) {
                    for (int x = 0; x < sv.getWidth() && loc[0] + x < w; x++) {
                        if (loc[0] + x < 0 || loc[1] + y < 0) continue;
                        int sx = x * fw / sv.getWidth(), sy = y * fh / sv.getHeight();
                        int p = 12 + (sy * fw + sx) * 4;
                        argb[(loc[1] + y) * w + loc[0] + x] = 0xFF000000 | (b.get(p) & 0xFF) << 16 | (b.get(p + 1) & 0xFF) << 8 | (b.get(p + 2) & 0xFF);
                    }
                }
            } catch (IOException | RuntimeException ex) {
                // a half-written or vanished frame: keep the window as is
            }
        }
    }
}
