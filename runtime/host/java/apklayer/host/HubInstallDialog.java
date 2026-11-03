package apklayer.host;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
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
 * through the shell's test input. Without a shell there is nobody to ask: the answer is no.
 */
final class HubInstallDialog {
    static final String CLIENT = "apklayer.installer";
    private static final int HELLO = 1, WINDOW_CREATE = 2, FRAME = 3, WINDOW_DESTROY = 4, INPUT = 101, CLOSE = 103, KEY = 105;
    private static final int W = 440, H = 200, BUTTON_W = 110, BUTTON_H = 36, KEYCODE_ESCAPE = 111;
    private static int windows;

    private HubInstallDialog() {}

    /** Shows the question and waits for the person's answer: true for the yes button, false for no, the window's close button, Escape or no shell. */
    static boolean ask(String heading, String body, String yes, String no) {
        String socket = System.getenv("XDG_RUNTIME_DIR") == null ? null : System.getenv("XDG_RUNTIME_DIR") + "/apklayer/shell.sock";
        if (socket == null || !Files.exists(Path.of(socket))) { System.out.println("[apklayer-hub] no shell to ask \"" + heading + "\": refused"); return false; }
        Path shm = Path.of(System.getenv("XDG_RUNTIME_DIR"), "apklayer", "installer-" + ProcessHandle.current().pid() + "-" + (++windows) + ".shm");
        try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket)); RandomAccessFile file = new RandomAccessFile(shm.toFile(), "rw")) {
            file.setLength((long) W * H * 4 * 2);
            byte[] name = CLIENT.getBytes(StandardCharsets.UTF_8);
            send(channel, HELLO, buf(4 + name.length).putInt((int) ProcessHandle.current().pid()).put(name));
            int[] yesBox = {W - 16 - BUTTON_W, H - 16 - BUTTON_H}, noBox = {W - 2 * (16 + BUTTON_W), H - 16 - BUTTON_H};
            IntBuffer pixels = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, (long) W * H * 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            pixels.put(draw(heading, body, yes, no, yesBox, noBox));
            byte[] path = (shm + "\0" + heading).getBytes(StandardCharsets.UTF_8);
            send(channel, WINDOW_CREATE, buf(12 + path.length).putInt(1).putInt(W).putInt(H).put(path));
            send(channel, FRAME, buf(24).putInt(1).putInt(0).putInt(0).putInt(0).putInt(W).putInt(H));
            // the test reads where the buttons are (window content coordinates)
            System.out.println("[apklayer-hub] dialog shown: \"" + heading + "\" yes=" + (yesBox[0] + BUTTON_W / 2) + "," + (yesBox[1] + BUTTON_H / 2) + " no=" + (noBox[0] + BUTTON_W / 2) + "," + (noBox[1] + BUTTON_H / 2));
            Boolean answer = null;
            ByteBuffer head = buf(8);
            while (answer == null) {
                head.clear();
                if (!read(channel, head)) break;
                ByteBuffer msg = buf(head.getInt(4));
                if (!read(channel, msg)) break;
                int type = head.getInt(0);
                if (type == CLOSE) answer = false;
                else if (type == KEY && msg.getInt(4) == 0 && msg.getInt(8) == KEYCODE_ESCAPE) answer = false;
                else if (type == INPUT && msg.getInt(4) == 1) {  // MotionEvent UP at (x, y): a click ends there
                    int x = msg.getInt(8), y = msg.getInt(12);
                    if (inside(x, y, yesBox)) answer = true;
                    else if (inside(x, y, noBox)) answer = false;
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

    private static boolean inside(int x, int y, int[] box) { return x >= box[0] && x < box[0] + BUTTON_W && y >= box[1] && y < box[1] + BUTTON_H; }

    /** ARGB pixels of the dialog: heading, wrapped body text, the two buttons (yes filled). */
    private static int[] draw(String heading, String body, String yes, String no, int[] yesBox, int[] noBox) {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xFAFAFA)); g.fillRect(0, 0, W, H);
        g.setColor(new Color(0x1C1B1F)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18)); g.drawString(heading, 20, 36);
        g.setColor(new Color(0x49454F)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        int y = 64;
        for (String line : wrap(body, g.getFontMetrics(), W - 40)) { g.drawString(line, 20, y); y += 20; }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.setColor(new Color(0x6750A4)); g.fillRoundRect(yesBox[0], yesBox[1], BUTTON_W, BUTTON_H, 18, 18);
        g.setColor(Color.WHITE); centered(g, yes, yesBox);
        g.setColor(new Color(0x6750A4)); g.drawRoundRect(noBox[0], noBox[1], BUTTON_W - 1, BUTTON_H - 1, 18, 18); centered(g, no, noBox);
        g.dispose();
        return image.getRGB(0, 0, W, H, null, 0, W);
    }

    private static void centered(Graphics2D g, String text, int[] box) {
        FontMetrics m = g.getFontMetrics();
        g.drawString(text, box[0] + (BUTTON_W - m.stringWidth(text)) / 2, box[1] + (BUTTON_H + m.getAscent() - m.getDescent()) / 2);
    }

    private static List<String> wrap(String text, FontMetrics m, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() > 0 && m.stringWidth(line + " " + word) > width) { lines.add(line.toString()); line.setLength(0); }
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
