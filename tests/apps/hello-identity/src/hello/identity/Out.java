package hello.identity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** The probe results: one "area.key=value" line each, printed as "[identity] ..." and written to files/identity.txt. */
final class Out {
    private static final List<String> lines = new ArrayList<>();

    private Out() {}

    static void put(String key, Object value) {
        String text = String.valueOf(value).replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
        String line = key + "=" + text;
        lines.add(line);
        System.out.println("[identity] " + line);
    }

    /** Lines "key=value" from native code, each under the area prefix. */
    static void putAll(String area, String block) {
        if (block == null) { put(area + ".error", "null block"); return; }
        for (String line : block.split("\n")) {
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq < 0) put(area + ".raw", line);
            else put(area + "." + line.substring(0, eq), line.substring(eq + 1));
        }
    }

    /** One probe; its exception becomes its value. */
    static void probe(String key, Probe probe) {
        try { put(key, probe.run()); }
        catch (Throwable t) { put(key, "THROWS " + t.getClass().getName() + ": " + t.getMessage()); }
    }

    interface Probe { Object run() throws Throwable; }

    static void write(File dir) {
        try (FileOutputStream out = new FileOutputStream(new File(dir, "identity.txt"))) {
            for (String line : lines) out.write((line + "\n").getBytes("UTF-8"));
        } catch (Exception e) { System.out.println("[identity] write failed " + e); }
    }
}
