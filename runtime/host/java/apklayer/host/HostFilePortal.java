package apklayer.host;

import android.content.Intent;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Storage Access Framework pickers through xdg-desktop-portal's FileChooser (`gdbus` on the session bus): the desktop's own dialog, and the same call works
 * inside a Flatpak sandbox. Returns the chosen files as a result intent, null when the user cancelled, and throws when the portal is not there (the caller falls back).
 */
final class HostFilePortal {
    private HostFilePortal() {}

    private static final String DESKTOP = "org.freedesktop.portal.Desktop", PATH = "/org/freedesktop/portal/desktop";
    private static int tokens;

    static Intent pick(Intent intent) throws IOException {
        String action = intent.getAction();
        boolean save = Intent.ACTION_CREATE_DOCUMENT.equals(action);
        String token = "apklayer" + (++tokens);
        StringBuilder options = new StringBuilder("{'handle_token': <'" + token + "'>");
        if (Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action)) options.append(", 'directory': <true>");
        if (intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)) options.append(", 'multiple': <true>");
        String name = intent.getStringExtra(Intent.EXTRA_TITLE);
        if (save && name != null) options.append(", 'current_name': <'").append(name.replace("'", "\\'")).append("'>");
        options.append('}');
        // the monitor first: the answer can come as soon as the call returns
        Process monitor = new ProcessBuilder("gdbus", "monitor", "--session", "--dest", DESKTOP).redirectErrorStream(true).start();
        try {
            Process call = new ProcessBuilder("gdbus", "call", "--session", "--dest", DESKTOP, "--object-path", PATH, "--method",
                    "org.freedesktop.portal.FileChooser." + (save ? "SaveFile" : "OpenFile"), "", "apklayer", options.toString()).redirectErrorStream(true).start();
            String reply = new String(call.getInputStream().readAllBytes());
            if (call.waitFor() != 0) throw new IOException("portal: " + reply.trim());
            Matcher handle = Pattern.compile("'(/org/freedesktop/portal/desktop/request/[^']+)'").matcher(reply);
            if (!handle.find()) throw new IOException("portal: no request handle in " + reply.trim());
            System.out.println("[apklayer] portal request " + handle.group(1));
            try (BufferedReader in = new BufferedReader(new InputStreamReader(monitor.getInputStream()))) {
                for (String line; (line = in.readLine()) != null; ) {
                    if (!line.contains(handle.group(1)) || !line.contains("Request.Response")) continue;
                    Matcher code = Pattern.compile("Response \\((?:uint32 )?(\\d+),").matcher(line);
                    if (!code.find() || !code.group(1).equals("0")) return null;  // cancelled or failed
                    List<String> uris = new ArrayList<>();
                    Matcher uri = Pattern.compile("'(file://[^']*)'").matcher(line);
                    while (uri.find()) uris.add(Uri.decode(uri.group(1)));
                    if (uris.isEmpty()) return null;
                    Intent result = new Intent().setData(Uri.parse(uris.get(0)));
                    if (uris.size() > 1) {
                        android.content.ClipData clip = new android.content.ClipData(null, new String[] {"*/*"}, new android.content.ClipData.Item(Uri.parse(uris.get(0))));
                        for (int i = 1; i < uris.size(); i++) clip.addItem(new android.content.ClipData.Item(Uri.parse(uris.get(i))));
                        result.setClipData(clip);
                    }
                    System.out.println("[apklayer] portal picked " + String.join(" ", uris));
                    return result;
                }
            }
            throw new IOException("portal: the session bus closed before the answer");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
        } finally { monitor.destroy(); }
    }
}
