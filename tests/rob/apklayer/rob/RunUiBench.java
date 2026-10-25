package apklayer.rob;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/**
 * apklayer driver on the Robolectric method: unmodified framework (android-all-instrumented)
 * + shadows; the app is the dex2jvm-converted uibench classes; the manifest/resources come
 * from the real APK (binary resources mode, AGP's test_config.properties contract: the real
 * PackageParser reads the APK, so package/themes are unmodified). Frames: real View tree -> Canvas -> Bitmap ->
 * presented on the host Wayland window (driver-side, outside the sandbox).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = 27, qualifiers = "w480dp-h800dp")
public final class RunUiBench {

    @Test(timeout = 120_000)
    public void runUi() throws Exception {
        Class<?> actClass = getClass().getClassLoader()
                .loadClass("com.android.test.uibench.MainActivity");

        ActivityController<?> controller = Robolectric.buildActivity((Class) actClass);
        Activity activity = controller.create().get();

        System.out.println("[apklayer-rob] created " + activity + " pkg=" + activity.getPackageName());

        controller.start().postCreate(null).resume().visible();
        System.out.println("[apklayer-rob] activity resumed: " + activity);

        View decor = activity.getWindow().getDecorView();
        int w = decor.getWidth() > 0 ? decor.getWidth() : 480;
        int h = decor.getHeight() > 0 ? decor.getHeight() : 800;
        System.out.println("[apklayer-rob] decor " + w + "x" + h);

        HostWindow win = HostWindow.get(w, h);
        if (win == null) {
            System.out.println("[apklayer-rob] host window unavailable (headless?)");
            return;
        }

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int[] px = new int[w * h];

        long t0 = System.currentTimeMillis();
        int frames = 0;
        while (!win.shouldClose() && System.currentTimeMillis() - t0 < 60_000) {
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
            decor.draw(canvas);
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            win.present(px, w, h);
            frames++;
            if (frames == 30) dumpPng(px, w, h, "/tmp/apklayer-frame.png");
            if (frames % 60 == 1) {
                System.out.println("[apklayer-rob] frame " + frames + " ("
                        + (System.currentTimeMillis() - t0) + " ms)");
            }
            Thread.sleep(16);
        }
        System.out.println("[apklayer-rob] DONE frames=" + frames);
    }

    /** Debug snapshot of a presented frame (what the Wayland window shows). */
    private static void dumpPng(int[] px, int w, int h, String path) throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, px, 0, w);
        javax.imageio.ImageIO.write(img, "png", new java.io.File(path));
        System.out.println("[apklayer-rob] snapshot -> " + path);
    }
}
