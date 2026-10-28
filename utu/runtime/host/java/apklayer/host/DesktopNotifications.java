package apklayer.host;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The desktop's notification service (org.freedesktop.Notifications over the session bus, through gdbus and dbus-monitor): show, replace, close, and the user's clicks. */
final class DesktopNotifications {
    interface Listener { void action(int id, String key); void closed(int id, int reason); }

    private static final String SERVICE = "org.freedesktop.Notifications", PATH = "/org/freedesktop/Notifications";
    private final Listener listener;
    private Process monitor;

    DesktopNotifications(Listener listener) { this.listener = listener; }

    private static String quote(String text) { return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"; }

    private static ProcessBuilder tool(String... command) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (System.getenv("DBUS_SESSION_BUS_ADDRESS") == null && System.getenv("XDG_RUNTIME_DIR") != null)
            builder.environment().put("DBUS_SESSION_BUS_ADDRESS", "unix:path=" + System.getenv("XDG_RUNTIME_DIR") + "/bus");
        return builder;
    }

    /** The popup's id (0 when the desktop has no notification service); {@code actions} is key, label, key, label... */
    int show(String app, int replaces, String summary, String body, List<String> actions) {
        List<String> quoted = new ArrayList<>();
        for (String action : actions) quoted.add(quote(action));
        try {
            startMonitor();
            Process process = tool("gdbus", "call", "--session", "--dest", SERVICE, "--object-path", PATH, "--method", SERVICE + ".Notify", quote(app), String.valueOf(replaces),
                    quote(""), quote(summary), quote(body), "[" + String.join(",", quoted) + "]", "{}", "int32 -1").start();
            String out = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            Matcher id = Pattern.compile("uint32 (\\d+)").matcher(out);
            return id.find() ? Integer.parseInt(id.group(1)) : 0;
        } catch (IOException | InterruptedException failure) {
            System.out.println("[apklayer-hub] cannot show a notification: " + failure);
            return 0;
        }
    }

    void close(int id) {
        try { tool("gdbus", "call", "--session", "--dest", SERVICE, "--object-path", PATH, "--method", SERVICE + ".CloseNotification", String.valueOf(id)).start().waitFor(); }
        catch (IOException | InterruptedException failure) { System.out.println("[apklayer-hub] cannot close a notification: " + failure); }
    }

    private synchronized void startMonitor() throws IOException {
        if (monitor != null) return;
        monitor = tool("dbus-monitor", "--session", "type='signal',interface='" + SERVICE + "'").start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> monitor.destroyForcibly()));
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(monitor.getInputStream()))) {
                String member = null; Integer id = null;
                for (String line; (line = in.readLine()) != null; ) {
                    if (line.startsWith("signal ")) { member = line.contains("member=ActionInvoked") ? "action" : line.contains("member=NotificationClosed") ? "closed" : null; id = null; continue; }
                    if (member == null) continue;
                    String value = line.trim();
                    if (id == null && value.startsWith("uint32 ")) { id = Integer.parseInt(value.substring(7)); continue; }
                    if (id == null) continue;
                    if (member.equals("action") && value.startsWith("string \"")) { listener.action(id, value.substring(8, value.length() - 1)); member = null; }
                    else if (member.equals("closed") && value.startsWith("uint32 ")) { listener.closed(id, Integer.parseInt(value.substring(7))); member = null; }
                }
            } catch (IOException | RuntimeException ended) { /* the bus went away */ }
        }, "desktop-notification-signals");
        reader.setDaemon(true); reader.start();
    }
}
