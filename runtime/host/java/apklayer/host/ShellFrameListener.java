package apklayer.host;

import android.graphics.Bitmap;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.robolectric.simulator.FrameListener;

/**
 * Receives the composed screen from Robolectric's ScreenUpdateHandler (only when something
 * drew) and hands it to apklayer-shell. Keeps the last frame for debug snapshots.
 */
final class ShellFrameListener implements FrameListener {
    private final ShellClient shell;
    private int[] pixels = new int[0];
    private int width, height, frames;

    ShellFrameListener(ShellClient shell) {
        this.shell = shell;
    }

    @Override
    public void onFrame(Bitmap bitmap) {
        width = bitmap.getWidth();
        height = bitmap.getHeight();
        if (pixels.length != width * height) pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.recycle();
        frames++;
        if (shell != null) {
            try {
                shell.present(pixels, width, height);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    int frames() {
        return frames;
    }

    /** Writes the last presented frame as a PNG (headless verification). */
    void snapshot(String path) throws Exception {
        if (width == 0) return;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(img, "png", new File(path));
        System.out.println("[apklayer] snapshot -> " + path + " (frame " + frames + ")");
    }
}
