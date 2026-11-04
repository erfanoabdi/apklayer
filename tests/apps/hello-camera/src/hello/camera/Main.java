package hello.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
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
 * camera2 against the stack's fake camera (tests/vm/camera.sh): the camera list, a preview into a TextureView, a YUV_420_888 ImageReader frame
 * with the fake pattern's colour bars, a JPEG still that decodes with the same colours.
 */
public final class Main extends Activity {
    private static final int W = 640, H = 480;
    // the fake backend's bars (bridge/hals/camera/fake.c): BT.601 Y, U, V of white, yellow, cyan, green, magenta, red, blue, black
    private static final int[][] BARS = {{235, 128, 128}, {210, 16, 146}, {170, 166, 16}, {145, 54, 34}, {106, 202, 222}, {81, 90, 240}, {41, 240, 110}, {16, 128, 128}};

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
                run(view);
                System.out.println("[cam] PASS");
            } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[cam] FAIL " + failure); }
        }, "camera-check").start();
    }

    private void run(TextureView view) throws Exception {
        CameraManager manager = getSystemService(CameraManager.class);
        String[] ids = manager.getCameraIdList();
        System.out.println("[cam] cameras " + Arrays.toString(ids));
        check(ids.length > 0, "no camera");
        for (String id : ids) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            System.out.println("[cam] camera " + id + " facing=" + c.get(CameraCharacteristics.LENS_FACING) + " orientation=" + c.get(CameraCharacteristics.SENSOR_ORIENTATION)
                    + " yuv=" + Arrays.toString(c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.YUV_420_888)));
        }
        check(textureReady.await(10, TimeUnit.SECONDS), "the TextureView got no SurfaceTexture");

        HandlerThread thread = new HandlerThread("camera");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        CameraDevice[] device = new CameraDevice[1];
        CountDownLatch opened = new CountDownLatch(1);
        manager.openCamera(ids[0], new CameraDevice.StateCallback() {
            @Override public void onOpened(CameraDevice d) { device[0] = d; opened.countDown(); }
            @Override public void onDisconnected(CameraDevice d) { System.out.println("[cam] disconnected"); opened.countDown(); }
            @Override public void onError(CameraDevice d, int error) { System.out.println("[cam] open error " + error); opened.countDown(); }
        }, handler);
        check(opened.await(10, TimeUnit.SECONDS) && device[0] != null, "the camera did not open");
        System.out.println("[cam] opened " + device[0].getId());

        SurfaceTexture texture = view.getSurfaceTexture();
        texture.setDefaultBufferSize(W, H);
        Surface preview = new Surface(texture);
        ImageReader yuv = ImageReader.newInstance(W, H, ImageFormat.YUV_420_888, 3);
        ImageReader jpeg = ImageReader.newInstance(W, H, ImageFormat.JPEG, 2);
        CountDownLatch yuvChecked = new CountDownLatch(1), jpegChecked = new CountDownLatch(1);
        String[] yuvResult = new String[1], jpegResult = new String[1];
        yuv.setOnImageAvailableListener(r -> {
            try (Image image = r.acquireLatestImage()) {
                if (image != null && yuvChecked.getCount() > 0) { yuvResult[0] = checkYuv(image); yuvChecked.countDown(); }
            }
        }, handler);
        jpeg.setOnImageAvailableListener(r -> {
            try (Image image = r.acquireLatestImage()) {
                if (image != null) { jpegResult[0] = checkJpeg(image); jpegChecked.countDown(); }
            }
        }, handler);

        CameraCaptureSession[] session = new CameraCaptureSession[1];
        CountDownLatch configured = new CountDownLatch(1);
        device[0].createCaptureSession(Arrays.asList(preview, yuv.getSurface(), jpeg.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override public void onConfigured(CameraCaptureSession s) { session[0] = s; configured.countDown(); }
            @Override public void onConfigureFailed(CameraCaptureSession s) { System.out.println("[cam] configure failed"); configured.countDown(); }
        }, handler);
        check(configured.await(10, TimeUnit.SECONDS) && session[0] != null, "the session was not configured");

        AtomicInteger results = new AtomicInteger();
        CaptureRequest.Builder repeating = device[0].createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        repeating.addTarget(preview);
        repeating.addTarget(yuv.getSurface());
        session[0].setRepeatingRequest(repeating.build(), new CameraCaptureSession.CaptureCallback() {
            @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) { results.incrementAndGet(); }
        }, handler);
        check(yuvChecked.await(10, TimeUnit.SECONDS), "no YUV frame");
        System.out.println("[cam] yuv " + yuvResult[0]);
        check(yuvResult[0].startsWith("ok"), "the YUV frame's colours differ");

        Thread.sleep(1500);
        System.out.println("[cam] preview: " + results.get() + " results, " + previewFrames.get() + " frames shown");
        check(results.get() >= 10 && previewFrames.get() >= 5, "too few preview frames");

        CaptureRequest.Builder still = device[0].createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        still.addTarget(jpeg.getSurface());
        session[0].capture(still.build(), null, handler);
        check(jpegChecked.await(10, TimeUnit.SECONDS), "no JPEG");
        System.out.println("[cam] jpeg " + jpegResult[0]);
        check(jpegResult[0].startsWith("ok"), "the JPEG differs");

        session[0].close();
        device[0].close();
        yuv.close();
        jpeg.close();
        thread.quitSafely();
    }

    /** The Y, U, V of each bar's centre, a quarter down the frame, against the fake pattern's. */
    private static String checkYuv(Image image) {
        Image.Plane[] p = image.getPlanes();
        StringBuilder seen = new StringBuilder();
        boolean ok = image.getWidth() == W && image.getHeight() == H;
        for (int bar = 0; bar < 8; bar++) {
            int x = (2 * bar + 1) * W / 16, y = H / 4;
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

    /** The still decodes at the asked size; the red and blue bars are red and blue. */
    private static String checkJpeg(Image image) {
        ByteBuffer b = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[b.remaining()];
        b.get(data);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap == null) return "bad: does not decode (" + data.length + " bytes)";
        int red = bitmap.getPixel(11 * W / 16, H / 4), blue = bitmap.getPixel(13 * W / 16, H / 4);
        boolean ok = bitmap.getWidth() == W && bitmap.getHeight() == H && (red >> 16 & 0xff) > 200 && (red & 0xff) < 60 && (blue & 0xff) > 200 && (blue >> 16 & 0xff) < 60;
        return (ok ? "ok" : "bad") + String.format(" %d bytes %dx%d red=#%06x blue=#%06x", data.length, bitmap.getWidth(), bitmap.getHeight(), red & 0xffffff, blue & 0xffffff);
    }

    private static void check(boolean condition, String failure) { if (!condition) throw new AssertionError(failure); }
}
