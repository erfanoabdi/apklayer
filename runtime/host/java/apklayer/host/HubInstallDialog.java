package apklayer.host;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The package installer's confirmation, as the system's PackageInstaller app shows it on a phone: a window of the instance's shell drawn by the hub
 * (the system), never by the app that asks. It uses the shell's plain shared-memory window (bridge/shell/shell.h WINDOW_CREATE/FRAME, the
 * protocol apps used before SurfaceFlinger): the desktop shows it like an app window, tests reach it by its client name ("apklayer.installer")
 * through the shell's test input. In the shell's mobile style it is a full-screen page with big buttons (Android's permission screen on a
 * phone). Clicks and touches answer it. Without a shell there is nobody to ask: the answer is no.
 */
final class HubInstallDialog {
    static final String CLIENT = "apklayer.installer";
    private static final int HELLO = 1, WINDOW_CREATE = 2, FRAME = 3, WINDOW_DESTROY = 4, INPUT = 101, CLOSE = 103, KEY = 105, CONFIGURE = 107, TOUCH = 111;
    private static final int W = 440, H = 200, KEYCODE_BACK = 4, KEYCODE_ESCAPE = 111;
    private static final int HEADING = 0xFF1C1B1F, BODY = 0xFF49454F, ACCENT = 0xFF6750A4;
    private static int windows;

    private HubInstallDialog() {}

    /** One drawn layout: its size (buffer pixels), its pixels and the two buttons {x, y, w, h}. */
    private record Layout(int w, int h, int[] pixels, int[] yes, int[] no) {}

    /** Shows the question and waits for the person's answer: true for the yes button, false for no, the window's close button, Escape, Back or no shell. */
    static boolean ask(String heading, String body, String yes, String no) {
        String socket = System.getenv("XDG_RUNTIME_DIR") == null ? null : System.getenv("XDG_RUNTIME_DIR") + "/apklayer/shell.sock";
        if (socket == null || !Files.exists(Path.of(socket))) { System.out.println("[apklayer-hub] no shell to ask \"" + heading + "\": refused"); return false; }
        Path shm = Path.of(System.getenv("XDG_RUNTIME_DIR"), "apklayer", "installer-" + ProcessHandle.current().pid() + "-" + (++windows) + ".shm");
        try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket)); RandomAccessFile file = new RandomAccessFile(shm.toFile(), "rw")) {
            byte[] name = CLIENT.getBytes(StandardCharsets.UTF_8);
            send(channel, HELLO, buf(4 + name.length).putInt((int) ProcessHandle.current().pid()).put(name));
            Layout shown = show(channel, file, shm, heading, desktop(heading, body, yes, no));
            Boolean answer = null;
            boolean told = false;
            int touchX = -1, touchY = -1;
            ByteBuffer head = buf(8);
            while (answer == null) {
                head.clear();
                if (!read(channel, head)) break;
                ByteBuffer msg = buf(head.getInt(4));
                if (!read(channel, msg)) break;
                int type = head.getInt(0);
                if (type == CONFIGURE && msg.capacity() >= 24) {
                    // {id, logical w, h, scale %, dpi, navigation bar px, mobile}: in the mobile style (bridge/shell/mobile.c) the question becomes
                    // a page filling the window the shell maximized (Lipstick's has no navigation bar); the desktop style keeps the small dialog
                    int cw = msg.getInt(4), ch = msg.getInt(8), scale = Math.max(msg.getInt(12), 1), dpi = msg.getInt(16), nav = msg.getInt(20);
                    int pw = cw * scale / 100, ph = ch * scale / 100;
                    boolean mobile = (msg.capacity() >= 28 ? msg.getInt(24) != 0 : nav > 0) && pw > 0 && ph > 0;
                    if (mobile && (pw != shown.w || ph != shown.h)) shown = show(channel, file, shm, heading, page(heading, body, yes, no, pw, ph, dpi, nav));
                    else if (!mobile && shown.w != W) shown = show(channel, file, shm, heading, desktop(heading, body, yes, no));
                    if (!told) {  // the test reads where the buttons are (logical window content coordinates), once the window has its size
                        told = true;
                        System.out.println("[apklayer-hub] dialog shown: \"" + heading + "\" yes=" + centre(shown.yes, scale) + " no=" + centre(shown.no, scale)
                                + " size=" + cw + "x" + ch + (mobile ? " mobile" : " desktop"));
                    }
                }
                else if (type == CLOSE) answer = false;
                else if (type == KEY && msg.getInt(4) == 0 && (msg.getInt(8) == KEYCODE_ESCAPE || msg.getInt(8) == KEYCODE_BACK)) answer = false;
                else if (type == INPUT && msg.getInt(4) == 1) answer = hit(shown, msg.getInt(8), msg.getInt(12));  // MotionEvent UP at (x, y): a click ends there
                else if (type == TOUCH && msg.capacity() >= 20) {  // {id, action, finger, x, y}: a tap ends where the finger last was (an up carries no position)
                    int action = msg.getInt(4);
                    if (action == 0 || action == 2) { touchX = msg.getInt(12); touchY = msg.getInt(16); }
                    else if (action == 1) answer = hit(shown, touchX, touchY);
                    else touchX = touchY = -1;
                }
            }
            send(channel, WINDOW_DESTROY, buf(4).putInt(1));
            System.out.println("[apklayer-hub] dialog answered: \"" + heading + "\" " + (Boolean.TRUE.equals(answer) ? yes : no));
            return Boolean.TRUE.equals(answer);
        } catch (IOException failure) {
            System.out.println("[apklayer-hub] dialog failed: " + failure);
            return false;
        } finally {
            try { Files.deleteIfExists(shm); } catch (IOException ignored) { /* the runtime directory goes with the session */ }
        }
    }

    /** Puts the layout in the shared memory and shows it: the first call creates window 1, a later one rebuffers it at its new size (WINDOW_CREATE of the same id). */
    private static Layout show(SocketChannel channel, RandomAccessFile file, Path shm, String heading, Layout l) throws IOException {
        long bytes = (long) l.w * l.h * 4;
        if (file.length() < bytes * 2) file.setLength(bytes * 2);  // the shell maps two buffers; the first is drawn
        file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(l.pixels);
        byte[] path = (shm + "\0" + heading).getBytes(StandardCharsets.UTF_8);
        send(channel, WINDOW_CREATE, buf(12 + path.length).putInt(1).putInt(l.w).putInt(l.h).put(path));
        send(channel, FRAME, buf(24).putInt(1).putInt(0).putInt(0).putInt(0).putInt(l.w).putInt(l.h));
        return l;
    }

    /** The answer of a click or tap at (x, y) in buffer pixels; null off both buttons (the question stays). */
    private static Boolean hit(Layout l, int x, int y) { return inside(x, y, l.yes) ? Boolean.TRUE : inside(x, y, l.no) ? Boolean.FALSE : null; }
    private static boolean inside(int x, int y, int[] b) { return x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]; }
    private static String centre(int[] b, int scale) { return (b[0] + b[2] / 2) * 100 / scale + "," + (b[1] + b[3] / 2) * 100 / scale; }

    /** The desktop dialog: heading, wrapped body text, the two buttons side by side at the bottom right (yes filled). Android's own Canvas: the
     *  hub JVM's Java2D natives are not usable next to the device libraries. */
    private static Layout desktop(String heading, String body, String yes, String no) {
        int bw = 110, bh = 36;
        int[] yesBox = {W - 16 - bw, H - 16 - bh, bw, bh}, noBox = {W - 2 * (16 + bw), H - 16 - bh, bw, bh};
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFFAFAFA);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        float y = lines(canvas, text, heading, 18, Typeface.DEFAULT_BOLD, HEADING, 20, 36, W - 40, 24, false);  // a permission question takes two lines
        lines(canvas, text, body, 14, Typeface.DEFAULT, BODY, 20, y + 4, W - 40, 20, false);
        buttons(canvas, text, yes, no, yesBox, noBox, 14, 18, 1.5f);
        return new Layout(W, H, pixels(bitmap), yesBox, noBox);
    }

    /** The mobile page, as Android's permission screen on a phone: a question icon, the heading and body centred, full-width buttons stacked at the
     *  bottom above the navigation bar (nav pixels), in dp of the shell's density so a finger hits them (56 dp high). */
    private static Layout page(String heading, String body, String yes, String no, int w, int h, int dpi, int nav) {
        float dp = Math.max(dpi, 160) / 160f;
        int margin = Math.round(24 * dp), bh = Math.round(56 * dp), gap = Math.round(12 * dp), bw = w - 2 * margin;
        int[] noBox = {margin, h - nav - margin - bh, bw, bh}, yesBox = {margin, noBox[1] - gap - bh, bw, bh};
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFFAFAFA);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ACCENT);
        float cy = 96 * dp;
        canvas.drawCircle(w / 2f, cy, 28 * dp, paint);
        paint.setColor(0xFFFFFFFF); paint.setTextSize(32 * dp); paint.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics m = paint.getFontMetrics();
        canvas.drawText("?", w / 2f - paint.measureText("?") / 2, cy - (m.ascent + m.descent) / 2, paint);
        float y = lines(canvas, paint, heading, 24 * dp, Typeface.DEFAULT, HEADING, margin, cy + 72 * dp, bw, 32 * dp, true);
        lines(canvas, paint, body, 16 * dp, Typeface.DEFAULT, BODY, margin, y + 8 * dp, bw, 24 * dp, true);
        buttons(canvas, paint, yes, no, yesBox, noBox, 18 * dp, bh / 2f, 2 * dp);
        return new Layout(w, h, pixels(bitmap), yesBox, noBox);
    }

    /** Draws text wrapped to width from (x, baseline y), one line per step; returns the next baseline. */
    private static float lines(Canvas canvas, Paint paint, String text, float size, Typeface face, int color, float x, float y, int width, float step, boolean centred) {
        if (text.isEmpty()) return y;
        paint.setTextSize(size); paint.setTypeface(face); paint.setColor(color);
        for (String line : wrap(text, paint, width)) { canvas.drawText(line, centred ? x + (width - paint.measureText(line)) / 2 : x, y, paint); y += step; }
        return y;
    }

    private static void buttons(Canvas canvas, Paint text, String yes, String no, int[] yesBox, int[] noBox, float size, float radius, float stroke) {
        Paint button = new Paint(Paint.ANTI_ALIAS_FLAG);
        button.setColor(ACCENT);
        canvas.drawRoundRect(yesBox[0], yesBox[1], yesBox[0] + yesBox[2], yesBox[1] + yesBox[3], radius, radius, button);
        button.setStyle(Paint.Style.STROKE); button.setStrokeWidth(stroke);
        canvas.drawRoundRect(noBox[0] + stroke, noBox[1] + stroke, noBox[0] + noBox[2] - stroke, noBox[1] + noBox[3] - stroke, radius, radius, button);
        text.setTextSize(size); text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setColor(0xFFFFFFFF); centered(canvas, text, yes, yesBox);
        text.setColor(ACCENT); centered(canvas, text, no, noBox);
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        bitmap.recycle();
        return pixels;
    }

    private static void centered(Canvas canvas, Paint paint, String text, int[] box) {
        Paint.FontMetrics m = paint.getFontMetrics();
        canvas.drawText(text, box[0] + (box[2] - paint.measureText(text)) / 2, box[1] + (box[3] - m.ascent - m.descent) / 2, paint);
    }

    private static List<String> wrap(String text, Paint paint, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() > 0 && paint.measureText(line + " " + word) > width) { lines.add(line.toString()); line.setLength(0); }
                line.append(line.length() > 0 ? " " : "").append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static boolean read(SocketChannel channel, ByteBuffer b) throws IOException {
        while (b.hasRemaining()) if (channel.read(b) < 0) return false;
        return true;
    }

    private static void send(SocketChannel channel, int type, ByteBuffer payload) throws IOException {
        payload.flip();
        ByteBuffer msg = buf(8 + payload.remaining()).putInt(type).putInt(payload.remaining()).put(payload);
        msg.flip();
        while (msg.hasRemaining()) channel.write(msg);
    }

    private static ByteBuffer buf(int n) { return ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN); }
}
