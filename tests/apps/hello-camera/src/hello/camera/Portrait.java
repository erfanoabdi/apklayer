package hello.camera;

import android.app.Activity;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A portrait-locked activity (the manifest's screenOrientation, -Dhello.portrait: Main starts it) as a phone-only app: its portrait window
 * reports rotation 0, so cameraserver rotates and crops each camera's frames (ANDROID_SCALER_ROTATE_AND_CROP, aosp/hals/camera). For the back
 * and the front fake camera: the capture result's mode, the YUV frame turned a quarter with only the centre 3:4 slice of the bars, then the
 * TextureView preview held while tests/vm/camera-portrait.py screenshots it (upright bars; the front one mirrored).
 */
public final class Portrait extends Activity {
    private static final int W = 640, H = 480;
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
                for (String id : manager.getCameraIdList()) preview(manager, id, view.getSurfaceTexture());
                System.out.println("[cam] PASS");
            } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[cam] FAIL " + failure); }
        }, "camera-check").start();
    }

    private void preview(CameraManager manager, String id, SurfaceTexture texture) throws Exception {
        CameraCharacteristics c = manager.getCameraCharacteristics(id);
        boolean front = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        String name = front ? "front" : "back";
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
        texture.setDefaultBufferSize(W, H);
        Surface preview = new Surface(texture);
        ImageReader yuv = ImageReader.newInstance(W, H, ImageFormat.YUV_420_888, 3);
        CountDownLatch checked = new CountDownLatch(1);
        String[] result = new String[1];
        yuv.setOnImageAvailableListener(r -> {
            try (Image image = r.acquireLatestImage()) {
                if (image != null && checked.getCount() > 0) { result[0] = checkTurned(image, front); checked.countDown(); }
            }
        }, handler);
        CameraCaptureSession[] session = new CameraCaptureSession[1];
        CountDownLatch configured = new CountDownLatch(1);
        device[0].createCaptureSession(Arrays.asList(preview, yuv.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override public void onConfigured(CameraCaptureSession s) { session[0] = s; configured.countDown(); }
            @Override public void onConfigureFailed(CameraCaptureSession s) { configured.countDown(); }
        }, handler);
        check(configured.await(10, TimeUnit.SECONDS) && session[0] != null, "the session was not configured");
        Integer[] mode = new Integer[1];
        CountDownLatch completed = new CountDownLatch(1);
        CaptureRequest.Builder repeating = device[0].createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        repeating.addTarget(preview);
        repeating.addTarget(yuv.getSurface());
        session[0].setRepeatingRequest(repeating.build(), new CameraCaptureSession.CaptureCallback() {
            @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult t) {
                mode[0] = t.get(CaptureResult.SCALER_ROTATE_AND_CROP);
                completed.countDown();
            }
        }, handler);
        check(checked.await(10, TimeUnit.SECONDS) && completed.await(5, TimeUnit.SECONDS), "no YUV frame or capture result");
        System.out.println("[cam] portrait " + name + " result rotate-and-crop " + mode[0] + ", yuv " + result[0]);
        check(Integer.valueOf(front ? CaptureRequest.SCALER_ROTATE_AND_CROP_270 : CaptureRequest.SCALER_ROTATE_AND_CROP_90).equals(mode[0]), "rotate-and-crop not applied");
        check(result[0].startsWith("ok"), "the YUV frame is not turned and cropped");
        int shown = previewFrames.get();
        for (int i = 0; i < 50 && previewFrames.get() < shown + 5; i++) Thread.sleep(100);
        System.out.println("[cam] portrait " + name + " shown");  // the screenshot is taken now
        Thread.sleep(2500);
        session[0].close();
        yuv.close();
        device[0].close();
        thread.quitSafely();
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
