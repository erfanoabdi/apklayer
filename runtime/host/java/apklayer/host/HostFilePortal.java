package apklayer.host;

import android.content.Intent;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The Storage Access Framework pickers through xdg-desktop-portal's FileChooser (bridge/portal/file-chooser.py, one D-Bus connection for the request and its unicast
 * Response): the desktop's own dialog, and the same call works inside a Flatpak sandbox. Returns the chosen files as a result intent, null when the user cancelled, and throws when the portal is not there (the caller falls back).
 */
final class HostFilePortal {
    private HostFilePortal() {}

    private static int helperCalls;

    static Intent pick(Intent intent) throws IOException {
        String action = intent.getAction();
        List<String> command = new ArrayList<>(List.of("python3", helper(), Intent.ACTION_CREATE_DOCUMENT.equals(action) ? "save" : Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action) ? "directory" : "open"));
        if (intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)) command.add("--multiple");
        String name = intent.getStringExtra(Intent.EXTRA_TITLE);
        if (Intent.ACTION_CREATE_DOCUMENT.equals(action) && name != null) { command.add("--name"); command.add(name); }
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        List<String> uris = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (String line; (line = in.readLine()) != null; ) {
                if (line.startsWith("unavailable ")) throw new IOException(line.substring(12));
                if (line.startsWith("request ")) System.out.println("[apklayer] portal " + line);
                if (line.startsWith("uri ")) uris.add(Uri.decode(line.substring(4)));
            }
            process.waitFor();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new IOException(interrupted);
        }
        if (uris.isEmpty()) return null;  // cancelled
        Intent result = new Intent().setData(Uri.parse(uris.get(0)));
        if (uris.size() > 1) {
            android.content.ClipData clip = new android.content.ClipData(null, new String[] {"*/*"}, new android.content.ClipData.Item(Uri.parse(uris.get(0))));
            for (int i = 1; i < uris.size(); i++) clip.addItem(new android.content.ClipData.Item(Uri.parse(uris.get(i))));
            result.setClipData(clip);
        }
        System.out.println("[apklayer] portal picked " + String.join(" ", uris));
        return result;
    }

    /** The helper script lives in the install root (bridge/portal), found from the CLI's own location (-Dapklayer.cli). */
    private static String helper() {
        java.io.File cli = new java.io.File(System.getProperty("apklayer.cli", ""));
        for (java.io.File dir = cli.getAbsoluteFile().getParentFile(); dir != null; dir = dir.getParentFile()) {
            java.io.File candidate = new java.io.File(dir, "bridge/portal/file-chooser.py");
            if (candidate.exists()) return candidate.getPath();
        }
        return "bridge/portal/file-chooser.py";
    }
}
