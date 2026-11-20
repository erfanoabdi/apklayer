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

    /** In the sandbox through apklayerd as the user (`apklayer portal bus`): the bus knows the namespace's uid 1000 only when it is the user's own. */
    private static ProcessBuilder tool(String... command) {
        List<String> line = new ArrayList<>(List.of(command));
        if ("1".equals(System.getenv("APKLAYER_SANDBOX"))) line.addAll(0, List.of(System.getProperty("apklayer.cli"), "portal", "bus"));
        ProcessBuilder builder = new ProcessBuilder(line).redirectErrorStream(true);
        if (System.getenv("DBUS_SESSION_BUS_ADDRESS") == null && System.getenv("XDG_RUNTIME_DIR") != null)
            builder.environment().put("DBUS_SESSION_BUS_ADDRESS", "unix:path=" + System.getenv("XDG_RUNTIME_DIR") + "/bus");
        return builder;
    }

    private static String server;

    /** Which shell answers: "lipstick" (Sailfish), "lomiri" (Ubuntu Touch) or "other" (GNOME, KDE...); asked once. */
    static synchronized String shell() {
        if (server != null) return server;
        String found = "other";  // not kept when the service did not answer (the shell is still starting): asked again at the next notification
        try {
            Process process = tool("gdbus", "call", "--session", "--dest", SERVICE, "--object-path", PATH, "--method", SERVICE + ".GetServerInformation").start();
            String out = new String(process.getInputStream().readAllBytes()).toLowerCase();
            process.waitFor();
            if (out.contains("lipstick")) found = "lipstick"; else if (out.contains("lomiri")) found = "lomiri";
            if (out.startsWith("(")) server = found;
        } catch (IOException | InterruptedException failure) { /* no service: show() answers 0 */ }
        return found;
    }

    private static String variant(Object value) {
        if (value instanceof Boolean || value instanceof Double) return "<" + value + ">";
        if (value instanceof Integer) return "<int32 " + value + ">";
        if (value instanceof Byte) return "<byte " + value + ">";
        return "<" + quote(String.valueOf(value)) + ">";
    }

    /** The popup's id (0 when the desktop has no notification service); {@code actions} is key, label, key, label...; {@code hints} the Notify hints (Boolean, Integer, Byte, Double or String values). */
    int show(String app, int replaces, String icon, String summary, String body, List<String> actions, java.util.Map<String, Object> hints) {
        if (useBus()) {  // Lomiri drops a popup when its sender leaves the bus: one connection for all of them
            String text = "(" + String.join(", ", quote(app), String.valueOf(replaces), quote(icon), quote(summary), quote(body)) + ", [" + String.join(",", actions.stream().map(DesktopNotifications::quote).toList()) + "], {"
                    + String.join(",", hints.entrySet().stream().map(h -> quote(h.getKey()) + ": " + variant(h.getValue())).toList()) + "}, int32 -1)";
            try { startMonitor(); } catch (IOException failure) { return 0; }
            Matcher id = Pattern.compile("uint32 (\\d+)").matcher(busCall("notify " + text.replace("\n", "\\n").replace("\r", "")));
            return id.find() ? Integer.parseInt(id.group(1)) : 0;
        }
        List<String> quoted = new ArrayList<>();
        for (String action : actions) quoted.add(quote(action));
        List<String> hintList = new ArrayList<>();
        for (java.util.Map.Entry<String, Object> hint : hints.entrySet()) hintList.add(quote(hint.getKey()) + ": " + variant(hint.getValue()));
        try {
            startMonitor();
            Process process = tool("gdbus", "call", "--session", "--dest", SERVICE, "--object-path", PATH, "--method", SERVICE + ".Notify", quote(app), String.valueOf(replaces),
                    quote(icon), quote(summary), quote(body), "[" + String.join(",", quoted) + "]", "{" + String.join(",", hintList) + "}", "int32 -1").start();
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
        if (useBus()) { busCall("close " + id); return; }
        try { tool("gdbus", "call", "--session", "--dest", SERVICE, "--object-path", PATH, "--method", SERVICE + ".CloseNotification", String.valueOf(id)).start().waitFor(); }
        catch (IOException | InterruptedException failure) { System.out.println("[apklayer-hub] cannot close a notification: " + failure); }
    }

    private Process bus;
    private java.io.BufferedWriter busIn;
    private BufferedReader busOut;

    private boolean useBus() { return "lomiri".equals(shell()) && !"1".equals(System.getenv("APKLAYER_SANDBOX")); }

    /** One request line to bridge/portal/notify-bus.py (started on first use), its one answer line ("" when it is gone). */
    private synchronized String busCall(String line) {
        try {
            if (bus == null || !bus.isAlive()) {
                bus = tool("python3", HostFilePortal.helper("notify-bus.py")).redirectErrorStream(false).start();
                busIn = new java.io.BufferedWriter(new java.io.OutputStreamWriter(bus.getOutputStream()));
                busOut = new BufferedReader(new InputStreamReader(bus.getInputStream()));
                Process started = bus;
                Runtime.getRuntime().addShutdownHook(new Thread(started::destroyForcibly));
            }
            busIn.write(line + "\n"); busIn.flush();
            String answer = busOut.readLine();
            return answer == null ? "" : answer;
        } catch (IOException failure) {
            System.out.println("[apklayer-hub] notification bus helper: " + failure);
            return "";
        }
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
