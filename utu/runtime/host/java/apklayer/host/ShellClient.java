package apklayer.host;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Pure-Java client of apklayer-shell (bridge/shell/shell.h documents the protocol): takes over the input, state and size of the instance's
 * display window (the frames are SurfaceFlinger's, shown by the shell from dmabufs), and relays input, clipboard, IME and vsync.
 */
final class ShellClient {
    private static final int HELLO = 1, ATTACH_INPUT = 9;
    /** The title the display HAL gives its window (bridge/hals/graphics display, opened by the composer). */
    private static final byte[] DISPLAY_WINDOW = System.getProperty("apklayer.displaywindow", "apklayer").getBytes(StandardCharsets.UTF_8);
    private static final int INPUT = 101, CLOSE = 103, KEY = 105, SCROLL = 106,
            CONFIGURE = 107, STATE = 108, CLIPBOARD = 109, CLIPBOARD_SET = 5, TEXT_INPUT = 6, IME = 110, TOUCH = 111,
            DISPLAY = 112, VSYNC = 113, VSYNC_REQUEST = 7;
    private static final int WINDOW_ID = 1;

    private final SocketChannel channel;
    private final ConcurrentLinkedQueue<int[]> input = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Object[]> imeIn = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> clipboardIn = new ConcurrentLinkedQueue<>();
    private volatile boolean closed;
    private HostVsync vsync;
    private int width, height;

    private ShellClient(SocketChannel channel) {
        this.channel = channel;
    }

    /** Connects to the session's shell, or returns null if none is running (headless). */
    static ShellClient connect(String pkg) {
        String path = System.getProperty("apklayer.shell");
        if (path == null) {
            String rt = System.getenv("XDG_RUNTIME_DIR");
            if (rt == null) return null;
            path = rt + "/apklayer/shell.sock";
        }
        if (!Files.exists(Path.of(path))) return null;
        try {
            SocketChannel ch = SocketChannel.open(UnixDomainSocketAddress.of(path));
            ShellClient c = new ShellClient(ch);
            byte[] name = pkg.getBytes(StandardCharsets.UTF_8);
            c.send(HELLO, buf(4 + name.length).putInt((int) ProcessHandle.current().pid()).put(name));
            c.vsync = HostVsync.start(c);
            Thread reader = new Thread(c::readLoop, "apklayer-shell-reader");
            reader.setDaemon(true);
            reader.start();
            return c;
        } catch (IOException e) {
            System.out.println("[apklayer] shell unavailable: " + e);
            return null;
        }
    }

    /** The window is the display of the instance's SurfaceFlinger (shown by its HWC); this app takes over that window's input, state and size. */
    void createWindow(int w, int h, String title) throws IOException {
        width = w;
        height = h;
        send(ATTACH_INPUT, buf(4 + DISPLAY_WINDOW.length).putInt(WINDOW_ID).put(DISPLAY_WINDOW));
    }

    /** Next input event {type, a, b, 0} (see shell.c), or null. */
    int[] poll() {
        return input.poll();
    }

    /** Text the system selection changed to (from the shell), or null. */
    String pollClipboard() {
        return clipboardIn.poll();
    }

    /** The app copied text: the shell becomes the selection owner. */
    void sendClipboard(String text) {
        try {
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            send(CLIPBOARD_SET, buf(utf8.length).put(utf8));
        } catch (IOException e) {
            System.out.println("[apklayer] clipboard send failed: " + e);
        }
    }

    /** IME event {delete_before, delete_after, commit, preedit}, or null. */
    Object[] pollIme() {
        return imeIn.poll();
    }

    /** A text field gained or lost focus: enable or disable the compositor's input method. */
    void sendTextInput(boolean enabled) {
        try {
            send(TEXT_INPUT, buf(8).putInt(WINDOW_ID).putInt(enabled ? 1 : 0));
        } catch (IOException e) {
            System.out.println("[apklayer] text input send failed: " + e);
        }
    }

    /** The native Choreographer wants the next frame: the shell answers with the compositor's frame callback. */
    void sendVsyncRequest() {
        try {
            send(VSYNC_REQUEST, buf(4).putInt(WINDOW_ID));
        } catch (IOException e) {
            // shell gone: the read loop notices
        }
    }

    boolean closed() {
        return closed;
    }

    private void readLoop() {
        ByteBuffer head = buf(8);
        try {
            while (true) {
                head.clear();
                readFully(head);
                int type = head.getInt(0), len = head.getInt(4);
                ByteBuffer body = buf(len);
                readFully(body);
                if (type == INPUT) {
                    input.add(new int[] {body.getInt(4), body.getInt(8), body.getInt(12), 0});
                } else if (type == KEY) {
                    // {12, action, android keycode, unicode, android meta}
                    input.add(new int[] {12, body.getInt(4), body.getInt(8), body.getInt(12),
                            body.getInt(16)});
                } else if (type == SCROLL) {
                    // {13, x, y, vscroll px, hscroll px}
                    input.add(new int[] {13, body.getInt(4), body.getInt(8), body.getInt(12),
                            body.getInt(16)});
                } else if (type == CONFIGURE) {
                    // {14, w, h, scale*100}
                    input.add(new int[] {14, body.getInt(4), body.getInt(8), body.getInt(12)});
                } else if (type == TOUCH) {
                    // {16, action, finger, x, y}
                    input.add(new int[] {16, body.getInt(4), body.getInt(8), body.getInt(12), body.getInt(16)});
                } else if (type == IME) {
                    // {id, delete_before, delete_after} utf8 commit \0 utf8 preedit
                    byte[] raw = body.array();
                    int split = 12;
                    while (split < len && raw[split] != 0) split++;
                    imeIn.add(new Object[] {body.getInt(4), body.getInt(8),
                            new String(raw, 12, split - 12, StandardCharsets.UTF_8),
                            split + 1 <= len ? new String(raw, split + 1, len - split - 1, StandardCharsets.UTF_8) : ""});
                } else if (type == CLIPBOARD) {
                    clipboardIn.add(new String(body.array(), 0, len, StandardCharsets.UTF_8));
                } else if (type == STATE) {
                    // {15, flags}: bit0 active, bit1 minimized
                    input.add(new int[] {15, body.getInt(4)});
                } else if (type == DISPLAY) {
                    if (vsync != null) vsync.display(body.getInt(0));
                } else if (type == VSYNC) {
                    if (vsync != null) vsync.vsync(body.getInt(4));
                } else if (type == CLOSE) {
                    closed = true;
                }
            }
        } catch (IOException e) {
            closed = true; // shell went away
        }
    }

    private void readFully(ByteBuffer b) throws IOException {
        while (b.hasRemaining()) {
            if (channel.read(b) < 0) throw new IOException("closed");
        }
    }

    private synchronized void send(int type, ByteBuffer payload) throws IOException {
        payload.flip();
        ByteBuffer msg = buf(8 + payload.remaining()).putInt(type).putInt(payload.remaining()).put(payload);
        msg.flip();
        while (msg.hasRemaining()) channel.write(msg);
    }

    private static ByteBuffer buf(int n) {
        return ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN);
    }
}
