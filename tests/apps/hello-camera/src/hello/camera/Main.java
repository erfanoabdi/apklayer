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
    private static final String REAL = System.getProperty("hello.real");  // the camera id of a real camera (a phone's): no colour bars to compare
    private static final String ID = System.getProperty("hello.id");  // the camera to open (default: the first)
    // the fake backend's bars (bridge/hals/camera/fake.c): BT.601 Y, U, V of white, yellow, cyan, green, magenta, red, blue, black
    private static final int[][] BARS = {{235, 128, 128}, {210, 16, 146}, {170, 166, 16}, {145, 54, 34}, {106, 202, 222}, {81, 90, 240}, {41, 240, 110}, {16, 128, 128}};

    private static volatile boolean turned, front;
    private final CountDownLatch textureReady = new CountDownLatch(1);
    private final AtomicInteger previewFrames = new AtomicInteger();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Boolean.getBoolean("hello.portrait")) {  // the portrait-locked check (tests/vm/camera-portrait.py)
            startActivity(new android.content.Intent(this, Portrait.class));
            finish();
            return;
        }
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
        // landscape-locked (the manifest): the cameras' own orientation, no rotate-and-crop (Portrait checks that); a portrait first window turns
        for (int i = 0; i < 50 && view.getWidth() < view.getHeight(); i++) Thread.sleep(100);
        System.out.println("[cam] window " + view.getWidth() + "x" + view.getHeight());

        HandlerThread thread = new HandlerThread("camera");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        CameraDevice[] device = new CameraDevice[1];
        CountDownLatch opened = new CountDownLatch(1), lost = new CountDownLatch(1);
        manager.openCamera(REAL != null ? REAL : ID != null ? ID : ids[0], new CameraDevice.StateCallback() {
            @Override public void onOpened(CameraDevice d) { device[0] = d; opened.countDown(); }
            @Override public void onDisconnected(CameraDevice d) { System.out.println("[cam] disconnected"); opened.countDown(); lost.countDown(); }
            @Override public void onError(CameraDevice d, int error) { System.out.println("[cam] open error " + error); opened.countDown(); lost.countDown(); }
        }, handler);
        check(opened.await(10, TimeUnit.SECONDS) && device[0] != null, "the camera did not open");
        System.out.println("[cam] opened " + device[0].getId());
        // a phone gives this landscape-locked activity a portrait window: cameraserver turns and crops the frames as in Portrait (camera compat)
        turned = view.getWidth() < view.getHeight();
        front = manager.getCameraCharacteristics(device[0].getId()).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;

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
        if (Boolean.getBoolean("hello.reload")) { reopenAfterLoss(manager, device[0].getId(), lost, handler); return; }

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
        yuv.close();
        jpeg.close();
        smallPreviewSession(manager, device[0], handler);
        device[0].close();
        camera1Preview(handler);
        thread.quitSafely();
    }

    /** The camera provider restarted under the open camera (tests/vm/camera-reload.sh does it at "[cam] reload"): the device reports an error or a
     *  disconnect, the camera comes back available and opens again with frames. */
    private void reopenAfterLoss(CameraManager manager, String id, CountDownLatch lost, Handler handler) throws Exception {
        System.out.println("[cam] reload");
        check(lost.await(30, TimeUnit.SECONDS), "the open camera reported no error or disconnect");
        java.util.concurrent.Semaphore available = new java.util.concurrent.Semaphore(0);
        manager.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
            @Override public void onCameraAvailable(String c) { if (c.equals(id)) available.release(); }
        }, handler);
        CameraDevice[] device = new CameraDevice[1];
        CountDownLatch opened = new CountDownLatch(1), frame = new CountDownLatch(1);
        check(available.tryAcquire(30, TimeUnit.SECONDS), "the camera did not come back");
        // the evicted client's disconnect reports the camera present after the dead provider removed it (CameraService does so on Android
        // too); the restarted provider's add then repeats that status and CameraManager drops it, so a slow provider (a phone's) is retried
        for (int tries = 0; ; tries++) {
            try {
                manager.openCamera(id, new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice d) { device[0] = d; opened.countDown(); }
                    @Override public void onDisconnected(CameraDevice d) { opened.countDown(); }
                    @Override public void onError(CameraDevice d, int error) { System.out.println("[cam] reopen error " + error); opened.countDown(); }
                }, handler);
                break;
            } catch (IllegalArgumentException gone) {
                if (tries == 0) System.out.println("[cam] reopen: " + gone.getMessage());
                check(tries < 40, "the camera stays unknown");
                Thread.sleep(250);
            }
        }
        check(opened.await(10, TimeUnit.SECONDS) && device[0] != null, "the camera did not open again");
        ImageReader yuv = ImageReader.newInstance(W, H, ImageFormat.YUV_420_888, 3);
        String[] result = new String[1];
        yuv.setOnImageAvailableListener(r -> {
            try (Image image = r.acquireLatestImage()) { if (image != null && frame.getCount() > 0) { result[0] = checkYuv(image); frame.countDown(); } }
        }, handler);
        CameraCaptureSession[] session = new CameraCaptureSession[1];
        CountDownLatch configured = new CountDownLatch(1);
        device[0].createCaptureSession(Arrays.asList(yuv.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override public void onConfigured(CameraCaptureSession s) { session[0] = s; configured.countDown(); }
            @Override public void onConfigureFailed(CameraCaptureSession s) { configured.countDown(); }
        }, handler);
        check(configured.await(10, TimeUnit.SECONDS) && session[0] != null, "the reopened camera's session was not configured");
        CaptureRequest.Builder repeating = device[0].createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        repeating.addTarget(yuv.getSurface());
        session[0].setRepeatingRequest(repeating.build(), null, handler);
        check(frame.await(10, TimeUnit.SECONDS), "no YUV frame after the reopen");
        System.out.println("[cam] reopened yuv " + result[0]);
        check(result[0].startsWith("ok"), "the reopened camera's YUV frame differs");
        device[0].close();
        yuv.close();
    }

    /** Open Camera's camera2 stream set on a small window: a SurfaceTexture preview no larger than the display (it drops bigger preview sizes;
     *  with none left it never configures a session) and a JPEG ImageReader at the largest picture size. */
    private void smallPreviewSession(CameraManager manager, CameraDevice device, Handler handler) throws Exception {
        android.hardware.camera2.params.StreamConfigurationMap map =
                manager.getCameraCharacteristics(device.getId()).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        android.util.Size preview = null, picture = null;
        for (android.util.Size s : map.getOutputSizes(SurfaceTexture.class))
            if (s.getWidth() <= 640 && s.getHeight() <= 360 && (preview == null || s.getWidth() * s.getHeight() > preview.getWidth() * preview.getHeight())) preview = s;
        for (android.util.Size s : map.getOutputSizes(ImageFormat.JPEG))
            if (picture == null || s.getWidth() * s.getHeight() > picture.getWidth() * picture.getHeight()) picture = s;
        System.out.println("[cam] small preview " + preview + ", picture " + picture);
        check(preview != null, "no preview size fits a 640x360 window");
        SurfaceTexture texture = new SurfaceTexture(false);
        texture.setDefaultBufferSize(preview.getWidth(), preview.getHeight());
        Surface surface = new Surface(texture);
        ImageReader jpeg = ImageReader.newInstance(picture.getWidth(), picture.getHeight(), ImageFormat.JPEG, 2);
        CountDownLatch configured = new CountDownLatch(1), still = new CountDownLatch(1);
        CameraCaptureSession[] session = new CameraCaptureSession[1];
        String[] size = new String[1];
        jpeg.setOnImageAvailableListener(r -> {
            try (Image image = r.acquireLatestImage()) {
                ByteBuffer b = image.getPlanes()[0].getBuffer();
                byte[] data = new byte[b.remaining()];
                b.get(data);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                size[0] = o.outWidth + "x" + o.outHeight;
                still.countDown();
            }
        }, handler);
        device.createCaptureSession(Arrays.asList(surface, jpeg.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override public void onConfigured(CameraCaptureSession s) { session[0] = s; configured.countDown(); }
            @Override public void onConfigureFailed(CameraCaptureSession s) { configured.countDown(); }
        }, handler);
        check(configured.await(10, TimeUnit.SECONDS) && session[0] != null, "the small-preview session was not configured");
        CaptureRequest.Builder repeating = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        repeating.addTarget(surface);
        session[0].setRepeatingRequest(repeating.build(), null, handler);
        CaptureRequest.Builder capture = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        capture.addTarget(jpeg.getSurface());
        session[0].capture(capture.build(), null, handler);
        check(still.await(10, TimeUnit.SECONDS), "no JPEG from the small-preview session");
        System.out.println("[cam] small-preview session jpeg " + size[0]);
        check(size[0].equals(picture.getWidth() + "x" + picture.getHeight()), "the JPEG is not the picture size");
        session[0].close();
        jpeg.close();
    }

    /** android.hardware.Camera (Open Camera's default API): the parameters build, preview frames reach a preview callback. */
    @SuppressWarnings("deprecation")
    private void camera1Preview(Handler handler) throws Exception {
        android.hardware.Camera[] camera = new android.hardware.Camera[1];
        CountDownLatch frames = new CountDownLatch(5);
        handler.post(() -> {  // the camera's events go to the looper of the thread that opens it
            try {
                camera[0] = android.hardware.Camera.open(0);
                android.hardware.Camera.Parameters p = camera[0].getParameters();
                System.out.println("[cam] camera1 preview " + p.getPreviewSize().width + "x" + p.getPreviewSize().height + " of " + p.getSupportedPreviewSizes().size()
                        + " sizes, video sizes " + (p.getSupportedVideoSizes() == null ? 0 : p.getSupportedVideoSizes().size()));
                camera[0].setPreviewTexture(new SurfaceTexture(false));
                camera[0].setPreviewCallback((data, c) -> frames.countDown());
                camera[0].startPreview();
            } catch (Exception failure) { System.out.println("[cam] camera1 " + failure); }
        });
        boolean ok = frames.await(10, TimeUnit.SECONDS);
        handler.post(() -> { if (camera[0] != null) { camera[0].setPreviewCallback(null); camera[0].release(); } });
        check(ok, "no Camera1 preview frames");
        System.out.println("[cam] camera1 preview frames arrive");
    }

    /** The Y, U, V of each bar's centre, a quarter down the frame, against the fake pattern's. */
    private static String checkYuv(Image image) {
        Image.Plane[] p = image.getPlanes();
        if (REAL == null && turned) return Portrait.checkTurned(image.getWidth(), image.getHeight(), Portrait.yuv(image), front) + " (turned)";
        if (REAL != null) {  // a real camera (-Dhello.real=<id>): the frame has the asked size and a luminance spread (a lens cap is flat), no bars
            long sum = 0; int min = 255, max = 0, n = 0;
            for (int y = 0; y < H; y += 8) for (int x = 0; x < W; x += 8) { int v = at(p[0], x, y); sum += v; min = Math.min(min, v); max = Math.max(max, v); n++; }
            return (image.getWidth() == W && image.getHeight() == H ? "ok" : "bad") + String.format(" real %dx%d Y mean %d min %d max %d", image.getWidth(), image.getHeight(), sum / n, min, max);
        }
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
        if (REAL != null) return "ok real " + data.length + " bytes " + bitmap.getWidth() + "x" + bitmap.getHeight();  // any scene
        // turned: the blue bar is outside the centre slice, the cyan one passes the same test (blue high, red low)
        int[] r = turned ? Portrait.turnedAt(440, front) : new int[] {11 * W / 16, H / 4}, b2 = turned ? Portrait.turnedAt(200, front) : new int[] {13 * W / 16, H / 4};
        int red = bitmap.getPixel(r[0], r[1]), blue = bitmap.getPixel(b2[0], b2[1]);
        boolean ok = bitmap.getWidth() == W && bitmap.getHeight() == H && (red >> 16 & 0xff) > 200 && (red & 0xff) < 60 && (blue & 0xff) > 200 && (blue >> 16 & 0xff) < 60;
        return (ok ? "ok" : "bad") + String.format(" %d bytes %dx%d red=#%06x blue=#%06x", data.length, bitmap.getWidth(), bitmap.getHeight(), red & 0xffffff, blue & 0xffffff);
    }

    private static void check(boolean condition, String failure) { if (!condition) throw new AssertionError(failure); }
}
