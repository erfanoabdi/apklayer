package hello.hwbitmap;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Picture;
import android.os.Bundle;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/** Hardware bitmaps (gralloc buffers HWUI samples as EGLImages): made by copy, by ImageDecoder's default allocator and by BitmapFactory,
 *  read back on the CPU, and drawn full-window (orange with a blue top-left quarter), so a screenshot shows whether sampling works. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Bitmap sw = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        sw.eraseColor(0xffff8000);
        Paint blue = new Paint();
        blue.setColor(0xff0000ff);
        new Canvas(sw).drawRect(0, 0, 100, 100, blue);
        Bitmap hw = sw.copy(Bitmap.Config.HARDWARE, false);
        System.out.println("hwbitmap: copy config " + (hw == null ? null : hw.getConfig()));
        if (hw == null) return;
        Bitmap back = hw.copy(Bitmap.Config.ARGB_8888, false);
        System.out.println("hwbitmap: readback " + (back == null ? "null" : Integer.toHexString(back.getPixel(50, 50)) + " " + Integer.toHexString(back.getPixel(150, 150))));
        int crops = 0;  // crops of a hardware bitmap, as a seekbar preview cuts frames out of a storyboard
        for (int i = 0; i < 50; i++) {
            Bitmap crop = Bitmap.createBitmap(hw, i, i, 100, 100);
            Bitmap pixels = crop.getConfig() == Bitmap.Config.HARDWARE ? crop.copy(Bitmap.Config.ARGB_8888, false) : crop;
            if (pixels.getPixel(0, 0) == 0xff0000ff) crops++;
        }
        System.out.println("hwbitmap: crops " + crops + "/50");
        int pictures = 0;  // Bitmap.createBitmap(Picture) renders through HardwareRenderer.createHardwareBitmap (Compose's layer snapshots)
        String last = null;
        for (int i = 0; i < 20; i++) {
            int w = 64 + 97 * i, h = 48 + 61 * i;
            Picture picture = new Picture();
            picture.beginRecording(w, h).drawColor(0xff00ff00);
            picture.endRecording();
            try {
                Bitmap drawn = Bitmap.createBitmap(picture);
                Bitmap pixels = drawn.copy(Bitmap.Config.ARGB_8888, false);
                last = drawn.getConfig() + " " + drawn.getWidth() + "x" + drawn.getHeight() + " " + Integer.toHexString(pixels.getPixel(w / 2, h / 2));
                if (drawn.getConfig() == Bitmap.Config.HARDWARE && pixels.getPixel(w / 2, h / 2) == 0xff00ff00) pictures++;
            } catch (RuntimeException e) {
                last = e.toString();
            }
        }
        System.out.println("hwbitmap: pictures " + pictures + "/20 (last " + last + ")");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        sw.compress(Bitmap.CompressFormat.PNG, 100, png);
        try {
            Bitmap decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(png.toByteArray())));
            System.out.println("hwbitmap: ImageDecoder config " + decoded.getConfig());
        } catch (Exception e) {
            System.out.println("hwbitmap: ImageDecoder failed " + e);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.HARDWARE;
        Bitmap factory = BitmapFactory.decodeByteArray(png.toByteArray(), 0, png.size(), opts);
        System.out.println("hwbitmap: BitmapFactory config " + (factory == null ? null : factory.getConfig()));
        Bitmap stream = BitmapFactory.decodeStream(new java.io.ByteArrayInputStream(png.toByteArray()), null, opts);
        Bitmap resource = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_dialog_info, opts);
        System.out.println("hwbitmap: decodeStream " + (stream == null ? null : stream.getConfig()) + " decodeResource " + (resource == null ? null : resource.getConfig()));
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        view.setImageBitmap(hw);
        setContentView(view);
    }
}
