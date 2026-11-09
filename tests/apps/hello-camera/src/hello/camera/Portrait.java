package hello.camera;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A portrait-locked activity (the manifest's screenOrientation, -Dhello.portrait: Main starts it) as a phone-only app: its portrait window
 * reports rotation 0, so cameraserver rotates and crops each camera's frames (ANDROID_SCALER_ROTATE_AND_CROP, aosp/hals/camera). For the back
 * and the front fake camera: the capture result's mode, the YUV frame turned a quarter with only the centre 3:4 slice of the bars, then the
 * TextureView preview held while tests/vm/camera-portrait.py screenshots it (upright bars; the front one mirrored). With the back camera open
 * the activity then turns its window to landscape and back (setRequestedOrientation): the open camera's mode and frames follow.
 * -Dhello.real=<id>: only that camera, a real one (no bars to compare: the frames' size only; the script compares the screenshots).
 */
public final class Portrait extends Activity {
    private static final int W = 640, H = 480;
    private static final String REAL = System.getProperty("hello.real");
    // the fake pattern's bars (bridge/hals/camera/fake.c) as Y, U, V: cyan, green, magenta, red (bars 2-5 of 8, all inside the centre slice)
    private static final int[][] BARS = {{170, 166, 16}, {145, 54, 34}, {106, 202, 222}, {81, 90, 240}};

    private final CountDownLatch textureReady = new CountDownLatch(1);
    private final AtomicInteger previewFrames = new AtomicInteger();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextureView view = new TextureView(this);
        view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture t, int w, int h) { textureReady.countDown(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture t) { previewFrames.incrementAndGet(); }
        });
        setContentView(view);
        new Thread(() -> {
            try {
                check(textureReady.await(10, TimeUnit.SECONDS), "the TextureView got no SurfaceTexture");
                for (int i = 0; i < 50 && view.getWidth() >= view.getHeight(); i++) Thread.sleep(100);  // Main's landscape window turns
                System.out.println("[cam] portrait window " + view.getWidth() + "x" + view.getHeight() + " rotation " + getDisplay().getRotation());
                CameraManager manager = getSystemService(CameraManager.class);
                for (String id : REAL != null ? new String[] {REAL} : manager.getCameraIdList()) preview(manager, id, view);
                System.out.println("[cam] PASS");
            } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[cam] FAIL " + failure); }
        }, "camera-check").start();
    }

    private void preview(CameraManager manager, String id, TextureView view) throws Exception {
        CameraCharacteristics c = manager.getCameraCharacteristics(id);
        int facing = c.get(CameraCharacteristics.LENS_FACING);
        boolean front = facing == CameraCharacteristics.LENS_FACING_FRONT;
        String name = front ? "front" : facing == CameraCharacteristics.LENS_FACING_BACK ? "back" : "external";
        System.out.println("[cam] portrait " + name + " orientation " + c.get(CameraCharacteristics.SENSOR_ORIENTATION) + " rotate-and-crop modes "
                + Arrays.toString(c.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES)));
        HandlerThread thread = new HandlerThread("camera");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        CameraDevice[] device = new CameraDevice[1];
        CountDownLatch opened = new CountDownLatch(1);
        manager.openCamera(id, new CameraDevice.StateCallback() {
            @Override public void onOpened(CameraDevice d) { device[0] = d; opened.countDown(); }
            @Override public void onDisconnected(CameraDevice d) { opened.countDown(); }
            @Override public void onError(CameraDevice d, int error) { System.out.println("[cam] open error " + error); opened.countDown(); }
        }, handler);
        check(opened.await(10, TimeUnit.SECONDS) && device[0] != null, "camera " + id + " did not open");
        SurfaceTexture texture = view.getSurfaceTexture();
        texture.setDefaultBufferSize(W, H);
        Surface preview = new Surface(texture);
        ImageReader yuv = ImageReader.newInstance(W, H, ImageFormat.YUV_420_888, 3);
        AtomicReference<Function<Image, String>> checker = new AtomicReference<>();
        ArrayBlockingQueue<String> checked = new ArrayBlockingQueue<>(1);
        yuv.setOnImageAvailableListener(r -> {
            try (Image image = r.acquireLatestImage()) {
                Function<Image, String> f = image == null ? null : checker.getAndSet(null);
                if (f != null) checked.offer(f.apply(image));
            }
        }, handler);
        CameraCaptureSession[] session = new CameraCaptureSession[1];
        CountDownLatch configured = new CountDownLatch(1);
        device[0].createCaptureSession(Arrays.asList(preview, yuv.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override public void onConfigured(CameraCaptureSession s) { session[0] = s; configured.countDown(); }
            @Override public void onConfigureFailed(CameraCaptureSession s) { configured.countDown(); }
        }, handler);
        check(configured.await(10, TimeUnit.SECONDS) && session[0] != null, "the session was not configured");
        AtomicInteger mode = new AtomicInteger(-1);  // the latest capture result's rotate-and-crop
        CaptureRequest.Builder repeating = device[0].createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        repeating.addTarget(preview);
        repeating.addTarget(yuv.getSurface());
        session[0].setRepeatingRequest(repeating.build(), new CameraCaptureSession.CaptureCallback() {
            @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult t) {
                Integer m = t.get(CaptureResult.SCALER_ROTATE_AND_CROP);
                mode.set(m == null ? -1 : m);
            }
        }, handler);
        // cameraserver takes an external camera as a front one for the portrait rotation (CameraProviderManager::getCameraInfo)
        int turned = facing == CameraCharacteristics.LENS_FACING_BACK ? CaptureRequest.SCALER_ROTATE_AND_CROP_90 : CaptureRequest.SCALER_ROTATE_AND_CROP_270;
        Function<Image, String> portrait = REAL != null ? Portrait::checkSize : image -> checkTurned(image, front);
        String result = frame(mode, turned, portrait, checker, checked);
        System.out.println("[cam] portrait " + name + " result rotate-and-crop " + mode.get() + ", yuv " + result);
        check(mode.get() == turned, "rotate-and-crop not applied");
        check(result.startsWith("ok"), "the YUV frame is not turned and cropped");
        shown("portrait", name);
        if (!front || REAL != null) {  // the window turns while the camera stays open (cameraserver's proxy, HostCameraShadows.windowShape): the requests follow
            turn(view, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            result = frame(mode, CaptureRequest.SCALER_ROTATE_AND_CROP_NONE, REAL != null ? Portrait::checkSize : Portrait::checkUpright, checker, checked);
            System.out.println("[cam] live landscape " + view.getWidth() + "x" + view.getHeight() + " result rotate-and-crop " + mode.get() + ", yuv " + result);
            check(mode.get() == CaptureRequest.SCALER_ROTATE_AND_CROP_NONE && result.startsWith("ok"), "a landscape window kept rotate-and-crop");
            shown("landscape", name);
            turn(view, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            result = frame(mode, turned, portrait, checker, checked);
            System.out.println("[cam] live portrait " + view.getWidth() + "x" + view.getHeight() + " result rotate-and-crop " + mode.get() + ", yuv " + result);
            check(mode.get() == turned && result.startsWith("ok"), "the portrait window again got no rotate-and-crop");
            shown("portrait", name + "-live");
        }
        session[0].close();
        yuv.close();
        device[0].close();
        thread.quitSafely();
    }

    /** Holds the preview while tests/vm/camera-portrait.py screenshots it ("shown"). */
    private void shown(String shape, String name) throws InterruptedException {
        int shown = previewFrames.get();
        for (int i = 0; i < 50 && previewFrames.get() < shown + 5; i++) Thread.sleep(100);
        System.out.println("[cam] " + shape + " " + name + " shown");  // the screenshot is taken now
        Thread.sleep(2500);
    }

    /** Asks for the window shape (the shell turns the window, AppHost.hostResize) and waits for the view to take it. */
    private void turn(TextureView view, int orientation) throws InterruptedException {
        boolean landscape = orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        runOnUiThread(() -> setRequestedOrientation(orientation));
        for (int i = 0; i < 50 && (view.getWidth() > view.getHeight()) != landscape; i++) Thread.sleep(100);
        check((view.getWidth() > view.getHeight()) == landscape, "the window did not turn: " + view.getWidth() + "x" + view.getHeight());
        // as a camera app does for a turned display (Camera2Basic's configureTransform): the preview turned back by the display's rotation,
        // here stretched over the whole view (the screenshot then holds the whole scene)
        int rotation = getDisplay().getRotation();
        System.out.println("[cam] window " + view.getWidth() + "x" + view.getHeight() + " rotation " + rotation);
        CountDownLatch set = new CountDownLatch(1);
        runOnUiThread(() -> {
            android.graphics.Matrix m = new android.graphics.Matrix();
            float w = view.getWidth(), h = view.getHeight(), cx = w / 2, cy = h / 2;
            if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                m.postRotate(90 * (rotation - 2), cx, cy);
                m.postScale(w / h, h / w, cx, cy);
            }
            view.setTransform(m);
            set.countDown();
        });
        set.await(5, TimeUnit.SECONDS);
    }

    /** Once the capture results carry the mode, a YUV frame a few frames later through the check. */
    private static String frame(AtomicInteger mode, int want, Function<Image, String> check, AtomicReference<Function<Image, String>> checker,
                                ArrayBlockingQueue<String> checked) throws InterruptedException {
        for (int i = 0; i < 100 && mode.get() != want; i++) Thread.sleep(50);
        if (mode.get() != want) return "no result with mode " + want + " (last " + mode.get() + ")";
        Thread.sleep(300);  // frames in flight with the old mode
        checked.clear();
        checker.set(check);
        String r = checked.poll(5, TimeUnit.SECONDS);
        return r == null ? "no YUV frame" : r;
    }

    /** A real camera's frame: the asked size, not black. */
    private static String checkSize(Image image) {
        long sum = 0;
        for (int y = 0; y < H; y += 16) for (int x = 0; x < W; x += 16) sum += at(image.getPlanes()[0], x, y);
        long mean = sum / ((H / 16) * (W / 16));
        return (image.getWidth() == W && image.getHeight() == H && mean > 20 ? "ok" : "bad") + " " + image.getWidth() + "x" + image.getHeight() + " mean luma " + mean;
    }

    /** No rotate-and-crop: the bars across the frame, the whole scene (bars 2-5 at their own columns, a quarter down). */
    private static String checkUpright(Image image) {
        Image.Plane[] p = image.getPlanes();
        StringBuilder seen = new StringBuilder();
        boolean ok = image.getWidth() == W && image.getHeight() == H;
        for (int bar = 0; bar < 4; bar++) {
            int x = 200 + 80 * bar, y = H / 4;
            int Y = at(p[0], x, y), U = at(p[1], x / 2, y / 2), V = at(p[2], x / 2, y / 2);
            seen.append(String.format(" %d/%d/%d", Y, U, V));
            ok &= Math.abs(Y - BARS[bar][0]) <= 6 && Math.abs(U - BARS[bar][1]) <= 8 && Math.abs(V - BARS[bar][2]) <= 8;
        }
        return (ok ? "ok" : "bad") + " " + image.getWidth() + "x" + image.getHeight() + seen;
    }

    /** The bars, upright in the scene, run along the frame's height once it is turned a quarter: counterclockwise for the back camera
     *  (ROTATE_AND_CROP_90), so the bars go from the bottom up; clockwise for the front one (270). The centre slice (360 of 640 columns) leaves
     *  bars 2-5 at these rows; a column a quarter into the bars' side (the scene's top part). */
    private static String checkTurned(Image image, boolean front) {
        Image.Plane[] p = image.getPlanes();
        StringBuilder seen = new StringBuilder();
        boolean ok = image.getWidth() == W && image.getHeight() == H;
        int x = front ? 3 * W / 4 : W / 4;
        for (int bar = 0; bar < 4; bar++) {
            int sceneX = 200 + 80 * bar, offset = (sceneX - 140) * H / 360;  // the bar's centre in the 640-wide scene, its distance into the slice
            int y = Math.min(H - 2, front ? offset : H - 1 - offset);
            int Y = at(p[0], x, y), U = at(p[1], x / 2, y / 2), V = at(p[2], x / 2, y / 2);
            seen.append(String.format(" %d/%d/%d", Y, U, V));
            ok &= Math.abs(Y - BARS[bar][0]) <= 6 && Math.abs(U - BARS[bar][1]) <= 8 && Math.abs(V - BARS[bar][2]) <= 8;
        }
        return (ok ? "ok" : "bad") + " " + image.getWidth() + "x" + image.getHeight() + seen;
    }

    private static int at(Image.Plane plane, int x, int y) {
        ByteBuffer b = plane.getBuffer();
        return b.get(y * plane.getRowStride() + x * plane.getPixelStride()) & 0xff;
    }

    private static void check(boolean condition, String failure) { if (!condition) throw new AssertionError(failure); }
}
