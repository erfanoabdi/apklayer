package apklayer.host;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.robolectric.RuntimeEnvironment;

/**
 * Other installed apps (PLAN P6, the first slice of apklayerd): an implicit intent no activity of this app can handle
 * is matched against the intent filters recorded at install time (info.properties of every installed app), and the
 * match is started in its own process through the CLI with the intent handed over in a file. Only string extras,
 * action, type, data and the target component travel; a chooser for several matches is still to do (the first wins).
 */
final class HostApps {
    private HostApps() {}

    private static final String INTENT_ENV = "APKLAYER_INTENT_FILE";

    /** An intent handed to another app; the result file is where its answer will appear (null if none is wanted). */
    record Routed(Process process, Path resultFile) {}

    private static final String RESULT_ENV = "APKLAYER_RESULT_FILE";

    /** Starts the best other app for the intent; null if none handles it. */
    static Routed route(Intent intent, boolean wantsResult) {
        String apps = System.getProperty("apklayer.apps"), cli = System.getProperty("apklayer.cli");
        if (apps == null || cli == null || intent.getAction() == null) return null;
        String self = RuntimeEnvironment.getApplication().getPackageName();
        try (var dirs = Files.list(Path.of(apps))) {
            for (Path dir : (Iterable<Path>) dirs.sorted()::iterator) {
                String pkg = dir.getFileName().toString();
                Properties info = new Properties();
                Path file = dir.resolve("info.properties");
                if (pkg.equals(self) || !Files.exists(file)) continue;
                try (InputStream in = Files.newInputStream(file)) { info.load(in); }
                for (String key : info.stringPropertyNames()) {
                    if (!key.startsWith("filter.")) continue;
                    ManifestFilters.Filter f = ManifestFilters.Filter.decode(info.getProperty(key));
                    if (matches(f, intent)) {
                        return start(cli, pkg, f.activity(), intent, wantsResult);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[apklayer] cannot read the installed apps: " + e.getMessage());
        }
        return null;
    }

    static boolean matches(ManifestFilters.Filter f, Intent intent) {
        if (!f.actions().contains(intent.getAction())) return false;
        if (!f.categories().contains(Intent.CATEGORY_DEFAULT)) return false;
        String type = intent.getType();
        if (!f.mimeTypes().isEmpty()) {
            if (type == null || f.mimeTypes().stream().noneMatch(m -> mimeMatches(m, type))) return false;
        } else if (type != null) {
            return false;
        }
        Uri data = intent.getData();
        return f.schemes().isEmpty() ? data == null : data != null && f.schemes().contains(data.getScheme());
    }

    private static boolean mimeMatches(String pattern, String type) {
        return pattern.equals("*/*") || pattern.equals(type) || pattern.endsWith("/*") && type.startsWith(pattern.substring(0, pattern.length() - 1));
    }

    private static Routed start(String cli, String pkg, String activity, Intent intent, boolean wantsResult) throws IOException {
        Properties p = toProperties(intent);
        p.setProperty("component", activity);
        Path file = Files.createTempFile("apklayer-intent-", ".properties");
        try (OutputStream out = Files.newOutputStream(file)) { p.store(out, "apklayer cross-app intent"); }
        ProcessBuilder pb = new ProcessBuilder(cli, "run", pkg).inheritIO();
        pb.environment().put(INTENT_ENV, file.toString());
        Path result = null;
        if (wantsResult) {
            result = Files.createTempFile("apklayer-result-", ".properties");
            Files.delete(result); // the callee creates it when it has an answer
            pb.environment().put(RESULT_ENV, result.toString());
        }
        Process process = pb.start();
        System.out.println("[apklayer] intent " + intent.getAction() + " sent to " + pkg + "/" + activity);
        return new Routed(process, result);
    }

    private static Properties toProperties(Intent intent) {
        Properties p = new Properties();
        if (intent.getAction() != null) p.setProperty("action", intent.getAction());
        if (intent.getType() != null) p.setProperty("type", intent.getType());
        if (intent.getData() != null) p.setProperty("data", intent.getData().toString());
        Bundle extras = intent.getExtras();
        if (extras != null) for (String k : extras.keySet()) if (extras.get(k) instanceof String s) p.setProperty("extra." + k, s);
        return p;
    }

    private static Intent fromProperties(Properties p) {
        Intent intent = new Intent(p.getProperty("action"));
        if (p.getProperty("type") != null) intent.setType(p.getProperty("type"));
        if (p.getProperty("data") != null) intent.setData(Uri.parse(p.getProperty("data")));
        Map<String, String> extras = new TreeMap<>();
        for (String k : p.stringPropertyNames()) if (k.startsWith("extra.")) extras.put(k.substring(6), p.getProperty(k));
        extras.forEach(intent::putExtra);
        return intent;
    }

    /** What the callee answered: {code, data intent}, or null while there is no answer yet. */
    static Object[] readResult(Routed routed) {
        if (routed.resultFile() == null || !Files.exists(routed.resultFile())) return null;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(routed.resultFile())) {
            p.load(in);
            Files.deleteIfExists(routed.resultFile());
        } catch (IOException e) {
            return null;
        }
        return new Object[] {Integer.parseInt(p.getProperty("resultCode", "0")), p.getProperty("action") == null && p.getProperty("data") == null && p.stringPropertyNames().stream().noneMatch(k -> k.startsWith("extra.")) ? null : fromProperties(p)};
    }

    /** In the callee: tells the app that started this process how the activity it was started for ended. */
    static void writeResult(int resultCode, Intent data) {
        String path = System.getenv(RESULT_ENV);
        if (path == null) return;
        Properties p = data == null ? new Properties() : toProperties(data);
        p.setProperty("resultCode", String.valueOf(resultCode));
        try {
            Path tmp = Path.of(path + ".part");
            try (OutputStream out = Files.newOutputStream(tmp)) { p.store(out, "apklayer cross-app result"); }
            Files.move(tmp, Path.of(path), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println("[apklayer] cannot write the result: " + e.getMessage());
        }
    }

    /** The intent another app handed to this process at start (null for a normal launch). */
    static Intent incoming() {
        String path = System.getenv(INTENT_ENV);
        if (path == null) return null;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            p.load(in);
            Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            return null;
        }
        Intent intent = fromProperties(p);
        intent.setComponent(new ComponentName(RuntimeEnvironment.getApplication().getPackageName(), p.getProperty("component")));
        return intent;
    }
}
