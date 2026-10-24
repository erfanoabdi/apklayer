package apklayer.probe;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Class-load probe: tries to LINK (without initializing) every converted app class against
 * the current framework seed and buckets failures by the root missing class. Output is the
 * data-driven work list for growing the seed.
 *
 * Usage: java -cp out apklayer.probe.LoadProbe <converted-classes.jar>
 */
public final class LoadProbe {
    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        List<String> names = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            for (JarEntry e : Collections.list(jf.entries())) {
                String n = e.getName();
                if (n.endsWith(".class") && !n.contains("$")) {
                    names.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        }
        URLClassLoader cl = new URLClassLoader(
                new URL[]{jar.toURI().toURL()}, LoadProbe.class.getClassLoader());
        int ok = 0;
        Map<String, Integer> missing = new TreeMap<>();
        for (String n : names) {
            try {
                Class.forName(n, false, cl);
                ok++;
            } catch (Throwable t) {
                Throwable root = t;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                String key;
                if (root instanceof NoClassDefFoundError || root instanceof ClassNotFoundException) {
                    key = String.valueOf(root.getMessage()).replace('/', '.');
                } else {
                    key = root.getClass().getSimpleName() + ": " + root.getMessage();
                }
                missing.merge(key, 1, Integer::sum);
            }
        }
        System.out.println("classes=" + names.size() + " linked=" + ok + " failed=" + (names.size() - ok));
        System.out.println("== missing roots (count = app classes blocked) ==");
        missing.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(40)
                .forEach(en -> System.out.println(en.getValue() + "\t" + en.getKey()));
    }
}
