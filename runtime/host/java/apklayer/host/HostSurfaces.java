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

    static Path file(HostSurface s) { return DIR == null ? null : DIR.resolve(s.hashCode() + ".rgba"); }

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
        for (Map.Entry<SurfaceView, HostSurface> e : surfaces.entrySet())
            blitView(e.getKey(), file(e.getValue()), argb, w, h);
    }

    private static void blitView(View v, Path f, int[] argb, int w, int h) {
        if (!v.isShown()) return;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        blit(loc[0], loc[1], v.getWidth(), v.getHeight(), f, argb, w, h);
    }

    /** Copies the frame file {w,h,seq,RGBA rows} over the rectangle (x, y, vw, vh) of the window frame, scaled to it. */
    static void blit(int x0, int y0, int vw, int vh, Path f, int[] argb, int w, int h) {
        if (f == null || vw <= 0 || vh <= 0 || !Files.exists(f)) return;
        try {
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(f)).order(ByteOrder.LITTLE_ENDIAN);
            int fw = b.getInt(0), fh = b.getInt(4);
            java.nio.IntBuffer px = b.position(12).asIntBuffer();
            for (int y = 0; y < vh && y0 + y < h; y++) {
                for (int x = 0; x < vw && x0 + x < w; x++) {
                    if (x0 + x < 0 || y0 + y < 0) continue;
                    int c = px.get((y * fh / vh) * fw + x * fw / vw); // little-endian RGBA bytes
                    argb[(y0 + y) * w + x0 + x] = 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF);
                }
            }
        } catch (IOException | RuntimeException ex) {
            // a half-written or vanished frame: keep the window as is
        }
    }
}
