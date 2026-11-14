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
 * phone). Clicks and touches answer it. Without a shell there is nobody to ask: the answer is no. A password question (askSecret) adds a
 * field: keys and the input method type into it (shown as dots), Enter or the yes button answers.
 */
final class HubInstallDialog {
    static final String CLIENT = "apklayer.installer";
    private static final int HELLO = 1, WINDOW_CREATE = 2, FRAME = 3, WINDOW_DESTROY = 4, TEXT_INPUT = 6, INPUT = 101, CLOSE = 103, KEY = 105, CONFIGURE = 107, IME = 110, TOUCH = 111;
    private static final int W = 440, H = 200, FIELD = 56, KEYCODE_BACK = 4, KEYCODE_ESCAPE = 111, KEYCODE_ENTER = 66, KEYCODE_DEL = 67, KEYCODE_NUMPAD_ENTER = 160;
    private static final int HEADING = 0xFF1C1B1F, BODY = 0xFF49454F, ACCENT = 0xFF6750A4;
    private static int windows;

    private HubInstallDialog() {}

    /** One drawn layout: its size (buffer pixels), its pixels and the boxes {x, y, w, h}: the radio rows first, then the buttons. */
    private record Layout(int w, int h, int[] pixels, int[][] boxes) {}

    /** A question: radio rows (one picked, {@code picked[0]}), the buttons (the first is the filled one, the last the refusal) and the names the test finds them by. */
    private record Q(String heading, String body, String[] radios, int[] picked, String[] buttons, String[] ids) {}

    private static final int RADIO = -2;  // hit(): a radio row was chosen, no button

    /** Shows the question and waits for the person's answer: true for the yes button, false for no, the window's close button, Escape, Back or no shell. */
    static boolean ask(String heading, String body, String yes, String no) { return ask(heading, body, yes, no, null); }

    /**
     * A question with radio rows and any number of buttons (the permission screen's "Precise" / "Approximate" and its three answers): the index of the
     * button pressed, the last one on close, Escape, Back or no shell; {@code picked[0]} holds the radio row left selected. {@code ids} name the buttons
     * (and the first word of the lower-cased radio labels the rows) in the "dialog shown" line the tests click by.
     */
    static int choose(String heading, String body, String[] radios, int[] picked, String[] buttons, String[] ids) {
        return run(new Q(heading, body, radios, picked, buttons, ids), null);
    }

    /** The question with a password field: what the person typed when they answered yes, null otherwise. */
    static String askSecret(String heading, String body, String yes, String no) {
        StringBuilder typed = new StringBuilder();
        return ask(heading, body, yes, no, typed) ? typed.toString() : null;
    }

    private static final java.util.Map<Thread, SocketChannel> OPEN = new java.util.concurrent.ConcurrentHashMap<>();  // the asking thread -> its dialog

    /** Closes the question the thread is asking (answered no), as when the person closes it: the matter ended without them (a pairing's end). */
    static void dismiss(Thread asker) {
        SocketChannel channel = asker == null ? null : OPEN.get(asker);
        try { if (channel != null) channel.close(); } catch (IOException ignored) { /* closed already */ }
    }

    /** {@code secret} non-null: the field's text, edited by the person's keys and input method. */
    private static boolean ask(String heading, String body, String yes, String no, StringBuilder secret) {
        return run(new Q(heading, body, new String[0], new int[1], new String[] {yes, no}, new String[] {"yes", "no"}), secret) == 0;
    }

    private static Layout draw(Q q, StringBuilder secret, boolean mobile, int w, int h, int dpi, int nav) {
        return mobile ? page(q, secret, w, h, dpi, nav) : desktop(q, secret);
    }

    private static int run(Q q, StringBuilder secret) {
        String heading = q.heading;
        int last = q.buttons.length - 1;
        String socket = System.getenv("XDG_RUNTIME_DIR") == null ? null : System.getenv("XDG_RUNTIME_DIR") + "/apklayer/shell.sock";
        if (socket == null || !Files.exists(Path.of(socket))) { System.out.println("[apklayer-hub] no shell to ask \"" + heading + "\": refused"); return last; }
        Path shm = Path.of(System.getenv("XDG_RUNTIME_DIR"), "apklayer", "installer-" + ProcessHandle.current().pid() + "-" + (++windows) + ".shm");
        try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket)); RandomAccessFile file = new RandomAccessFile(shm.toFile(), "rw")) {
            OPEN.put(Thread.currentThread(), channel);
            byte[] name = CLIENT.getBytes(StandardCharsets.UTF_8);
            send(channel, HELLO, buf(4 + name.length).putInt((int) ProcessHandle.current().pid()).put(name));
            Layout shown = show(channel, file, shm, heading, desktop(q, secret), null);
            if (secret != null) send(channel, TEXT_INPUT, buf(8).putInt(1).putInt(1));  // the input method (an on-screen keyboard on a phone) types into the field
            int answer = -1;
            boolean told = false, mobile = false;
            int dpi = 160, nav = 0;
            int touchX = -1, touchY = -1;
            ByteBuffer head = buf(8);
            while (answer < 0) {
                head.clear();
                if (!read(channel, head)) break;
                ByteBuffer msg = buf(head.getInt(4));
                if (!read(channel, msg)) break;
                int type = head.getInt(0), tapped = -1;
                if (type == CONFIGURE && msg.capacity() >= 24) {
                    // {id, logical w, h, scale %, dpi, navigation bar px, mobile}: in the mobile style (bridge/shell/mobile.c) the question becomes
                    // a page filling the window the shell maximized (Lipstick's has no navigation bar); the desktop style keeps the small dialog
                    int cw = msg.getInt(4), ch = msg.getInt(8), scale = Math.max(msg.getInt(12), 1);
                    dpi = msg.getInt(16); nav = msg.getInt(20);
                    int pw = cw * scale / 100, ph = ch * scale / 100;
                    mobile = (msg.capacity() >= 28 ? msg.getInt(24) != 0 : nav > 0) && pw > 0 && ph > 0;
                    if (mobile && (pw != shown.w || ph != shown.h)) shown = show(channel, file, shm, heading, page(q, secret, pw, ph, dpi, nav), shown);
                    else if (!mobile && shown.w != W) shown = show(channel, file, shm, heading, desktop(q, secret), shown);
                    if (!told) {  // the test reads where the buttons are (logical window content coordinates), once the window has its size
                        told = true;
                        StringBuilder where = new StringBuilder();
                        for (int i = 0; i < shown.boxes.length; i++) {
                            int button = i - q.radios.length;
                            where.append(' ').append(button < 0 ? q.radios[i].toLowerCase().split(" ")[0] : q.ids[button]).append('=').append(centre(shown.boxes[i], cw, shown.w));
                        }
                        System.out.println("[apklayer-hub] dialog shown: \"" + heading + "\"" + where + " size=" + cw + "x" + ch + (mobile ? " mobile" : " desktop"));
                    }
                }
                else if (type == CLOSE) answer = last;
                else if (type == KEY && msg.getInt(4) == 0 && (msg.getInt(8) == KEYCODE_ESCAPE || msg.getInt(8) == KEYCODE_BACK)) answer = last;
                else if (secret != null && (type == KEY || type == IME)) {  // the field: a key's character, Backspace, Enter; the input method's commit
                    int length = secret.length();
                    if (type == KEY && msg.getInt(4) == 0 && (msg.getInt(8) == KEYCODE_ENTER || msg.getInt(8) == KEYCODE_NUMPAD_ENTER)) { answer = 0; continue; }
                    if (type == KEY && msg.getInt(4) == 0 && msg.getInt(8) == KEYCODE_DEL && length > 0) secret.setLength(length - 1);
                    else if (type == KEY && msg.getInt(4) == 0 && msg.capacity() >= 16 && msg.getInt(12) >= ' ' && msg.getInt(12) != 0x7F) secret.appendCodePoint(msg.getInt(12));
                    else if (type == IME && msg.capacity() > 12) {
                        // shortcut: the input method's delete counts bytes, the field characters; the same for what one types into a password
                        secret.setLength(Math.max(0, length - msg.getInt(4)));
                        byte[] bytes = new byte[msg.capacity() - 12];
                        msg.position(12); msg.get(bytes);
                        String text = new String(bytes, StandardCharsets.UTF_8);
                        secret.append(text, 0, text.indexOf('\0') < 0 ? text.length() : text.indexOf('\0'));  // the commit; the preedit after it is not typed yet
                    }
                    if (secret.length() != length) shown = show(channel, file, shm, heading, draw(q, secret, mobile, shown.w, shown.h, dpi, nav), shown);
                }
                else if (type == INPUT && msg.getInt(4) == 1) tapped = hit(shown, q, msg.getInt(8), msg.getInt(12));  // MotionEvent UP at (x, y): a click ends there
                else if (type == TOUCH && msg.capacity() >= 20) {  // {id, action, finger, x, y}: a tap ends where the finger last was (an up carries no position)
                    int action = msg.getInt(4);
                    if (action == 0 || action == 2) { touchX = msg.getInt(12); touchY = msg.getInt(16); }
                    else if (action == 1) tapped = hit(shown, q, touchX, touchY);
                    else touchX = touchY = -1;
                }
                if (tapped == RADIO) shown = show(channel, file, shm, heading, draw(q, secret, mobile, shown.w, shown.h, dpi, nav), shown);  // the chosen row moved
                else if (tapped >= 0) answer = tapped;
            }
            if (answer < 0) answer = last;  // the connection ended
            if (secret != null && answer != 0) secret.setLength(0);
            send(channel, WINDOW_DESTROY, buf(4).putInt(1));
            System.out.println("[apklayer-hub] dialog answered: \"" + heading + "\" " + q.buttons[answer] + (q.radios.length > 0 ? " (" + q.radios[q.picked[0]] + ")" : ""));
            return answer;
        } catch (IOException failure) {
            System.out.println("[apklayer-hub] dialog " + (failure instanceof java.nio.channels.AsynchronousCloseException ? "closed: \"" + heading + "\"" : "failed: " + failure));
            return last;
        } finally {
            OPEN.remove(Thread.currentThread());
            try { Files.deleteIfExists(shm); } catch (IOException ignored) { /* the runtime directory goes with the session */ }
        }
    }

    /** Puts the layout in the shared memory and shows it: the first call creates window 1, a later one rebuffers it at a new size (WINDOW_CREATE of the
     *  same id); at the same size it is a new frame only. */
    private static Layout show(SocketChannel channel, RandomAccessFile file, Path shm, String heading, Layout l, Layout before) throws IOException {
        long bytes = (long) l.w * l.h * 4;
        if (file.length() < bytes * 2) file.setLength(bytes * 2);  // the shell maps two buffers; the first is drawn
        file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(l.pixels);
        byte[] path = (shm + "\0" + heading).getBytes(StandardCharsets.UTF_8);
        if (before == null || before.w != l.w || before.h != l.h) send(channel, WINDOW_CREATE, buf(12 + path.length).putInt(1).putInt(l.w).putInt(l.h).put(path));
        send(channel, FRAME, buf(24).putInt(1).putInt(0).putInt(0).putInt(0).putInt(l.w).putInt(l.h));
        return l;
    }

    /** The button a click or tap at (x, y) in buffer pixels presses; RADIO (the row is picked now) or -1 (off every box: the question stays). */
    private static int hit(Layout l, Q q, int x, int y) {
        for (int i = 0; i < l.boxes.length; i++) {
            if (!inside(x, y, l.boxes[i])) continue;
            if (i >= q.radios.length) return i - q.radios.length;
            q.picked[0] = i;
            return RADIO;
        }
        return -1;
    }
    private static boolean inside(int x, int y, int[] b) { return x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]; }
    /** A button's centre in logical content coordinates: the buffer (bw pixels wide) is shown cw logical wide (the desktop dialog 1:1 at any scale). */
    private static String centre(int[] b, int cw, int bw) { return (b[0] + b[2] / 2) * cw / bw + "," + (b[1] + b[3] / 2) * cw / bw; }

    /** The desktop dialog: heading, wrapped body text, the radio rows, the buttons: two side by side at the bottom right (the first filled), more
     *  (or with radio rows) stacked full width. Android's own Canvas: the hub JVM's Java2D natives are not usable next to the device libraries. */
    private static Layout desktop(Q q, CharSequence field) {
        int bh = 36, nr = q.radios.length, n = q.buttons.length;
        boolean stacked = n > 2 || nr > 0;
        int h = H - 52 + (field == null ? 0 : FIELD) + nr * 32 + (stacked ? n * 44 + 16 : 52);
        int[][] boxes = new int[nr + n][];
        for (int i = 0; i < n; i++)
            boxes[nr + i] = stacked ? new int[] {20, h - 16 - (n - i) * bh - (n - 1 - i) * 8, W - 40, bh} : i == 0 ? new int[] {W - 16 - 110, h - 16 - bh, 110, bh} : new int[] {W - 2 * (16 + 110), h - 16 - bh, 110, bh};
        Bitmap bitmap = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFFAFAFA);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        float y = lines(canvas, text, q.heading, 18, Typeface.DEFAULT_BOLD, HEADING, 20, 36, W - 40, 24, false);  // a permission question takes two lines
        y = lines(canvas, text, q.body, 14, Typeface.DEFAULT, BODY, 20, y + 4, W - 40, 20, false);
        for (int i = 0; i < nr; i++) boxes[i] = new int[] {20, (int) y + i * 32, W - 40, 32};
        radios(canvas, text, q, boxes, 14, 8, 1.5f);
        if (field != null) field(canvas, text, field, 20, boxes[nr][1] - 16 - 40, W - 40, 40, 1.5f);
        buttons(canvas, text, q.buttons, boxes, nr, 14, 18, 1.5f);
        return new Layout(W, h, pixels(bitmap), boxes);
    }

    /** The radio rows: a ring (filled inside for the picked one) and the label. */
    private static void radios(Canvas canvas, Paint paint, Q q, int[][] boxes, float size, float r, float stroke) {
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setColor(ACCENT); ring.setStrokeWidth(stroke);
        for (int i = 0; i < q.radios.length; i++) {
            int[] b = boxes[i];
            float cx = b[0] + r * 2, cy = b[1] + b[3] / 2f;
            ring.setStyle(Paint.Style.STROKE); canvas.drawCircle(cx, cy, r, ring);
            if (i == q.picked[0]) { ring.setStyle(Paint.Style.FILL); canvas.drawCircle(cx, cy, r / 2, ring); }
            paint.setTextSize(size); paint.setTypeface(Typeface.DEFAULT); paint.setColor(HEADING);
            Paint.FontMetrics m = paint.getFontMetrics();
            canvas.drawText(q.radios[i], cx + r * 2, cy - (m.ascent + m.descent) / 2, paint);
        }
    }

    /** The password field: an outlined box, one dot per typed character, the cursor after them. */
    private static void field(Canvas canvas, Paint paint, CharSequence typed, float x, float y, float w, float h, float stroke) {
        Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setColor(ACCENT); box.setStyle(Paint.Style.STROKE); box.setStrokeWidth(stroke * 1.5f);
        canvas.drawRoundRect(x, y, x + w, y + h, h / 8, h / 8, box);
        paint.setColor(HEADING);
        float r = h / 10, cx = x + h / 3;
        for (int i = 0; i < typed.length() && cx < x + w - h / 3; i++, cx += r * 3) canvas.drawCircle(cx + r, y + h / 2, r, paint);
        canvas.drawRect(cx, y + h / 4, cx + stroke, y + h * 3 / 4, paint);
    }

    /** The mobile page, as Android's permission screen on a phone: a question icon, the heading and body centred, the radio rows, full-width buttons
     *  stacked at the bottom above the navigation bar (nav pixels), in dp of the shell's density so a finger hits them (56 dp high). */
    private static Layout page(Q q, CharSequence field, int w, int h, int dpi, int nav) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float dp = Math.max(dpi, 160) / 160f;
        while (dp > 1 && height(paint, q, w, dp) + (field == null ? 0 : 68 * dp) > h - nav) dp *= 0.9f;  // a short window (landscape): smaller, never overlapping
        int margin = Math.round(24 * dp), bh = Math.round(56 * dp), gap = Math.round(12 * dp), bw = w - 2 * margin, nr = q.radios.length, n = q.buttons.length, row = Math.round(48 * dp);
        int[][] boxes = new int[nr + n][];
        for (int i = 0; i < n; i++) boxes[nr + i] = new int[] {margin, h - nav - margin - bh - (n - 1 - i) * (bh + gap), bw, bh};
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFFAFAFA);
        paint.setColor(ACCENT);
        float cy = 96 * dp;
        canvas.drawCircle(w / 2f, cy, 28 * dp, paint);
        paint.setColor(0xFFFFFFFF); paint.setTextSize(32 * dp); paint.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics m = paint.getFontMetrics();
        canvas.drawText("?", w / 2f - paint.measureText("?") / 2, cy - (m.ascent + m.descent) / 2, paint);
        float y = lines(canvas, paint, q.heading, 24 * dp, Typeface.DEFAULT, HEADING, margin, cy + 72 * dp, bw, 32 * dp, true);
        y = lines(canvas, paint, q.body, 16 * dp, Typeface.DEFAULT, BODY, margin, y + 8 * dp, bw, 24 * dp, true);
        for (int i = 0; i < nr; i++) boxes[i] = new int[] {margin, Math.round(y + 8 * dp) + i * row, bw, row};
        radios(canvas, paint, q, boxes, 18 * dp, 10 * dp, 2 * dp);
        if (field != null) field(canvas, paint, field, margin, Math.min(y + 8 * dp, boxes[nr][1] - bh - gap), bw, bh, 2 * dp);
        buttons(canvas, paint, q.buttons, boxes, nr, 18 * dp, bh / 2f, 2 * dp);
        return new Layout(w, h, pixels(bitmap), boxes);
    }

    /** The page's height at dp: the icon, the wrapped heading and body (as page() draws them), the radio rows, the buttons with their margins. */
    private static float height(Paint paint, Q q, int w, float dp) {
        int width = w - 2 * Math.round(24 * dp), n = q.buttons.length;
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(24 * dp);
        float y = 168 * dp + (q.heading.isEmpty() ? 0 : wrap(q.heading, paint, width).size() * 32 * dp);
        paint.setTextSize(16 * dp);
        return y + 8 * dp + (q.body.isEmpty() ? 0 : wrap(q.body, paint, width).size() * 24 * dp) + q.radios.length * 48 * dp + (24 + 56 * n + 12 * (n - 1) + 24) * dp;
    }

    /** Draws text wrapped to width from (x, baseline y), one line per step; returns the next baseline. */
    private static float lines(Canvas canvas, Paint paint, String text, float size, Typeface face, int color, float x, float y, int width, float step, boolean centred) {
        if (text.isEmpty()) return y;
        paint.setTextSize(size); paint.setTypeface(face); paint.setColor(color);
        for (String line : wrap(text, paint, width)) { canvas.drawText(line, centred ? x + (width - paint.measureText(line)) / 2 : x, y, paint); y += step; }
        return y;
    }

    /** The buttons from boxes[from]: the first filled, the others outlined. */
    private static void buttons(Canvas canvas, Paint text, String[] labels, int[][] boxes, int from, float size, float radius, float stroke) {
        Paint button = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(size); text.setTypeface(Typeface.DEFAULT_BOLD);
        for (int i = 0; i < labels.length; i++) {
            int[] b = boxes[from + i];
            button.setColor(ACCENT);
            if (i == 0) { button.setStyle(Paint.Style.FILL); canvas.drawRoundRect(b[0], b[1], b[0] + b[2], b[1] + b[3], radius, radius, button); }
            else { button.setStyle(Paint.Style.STROKE); button.setStrokeWidth(stroke); canvas.drawRoundRect(b[0] + stroke, b[1] + stroke, b[0] + b[2] - stroke, b[1] + b[3] - stroke, radius, radius, button); }
            text.setColor(i == 0 ? 0xFFFFFFFF : ACCENT);
            centered(canvas, text, labels[i], b);
        }
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
