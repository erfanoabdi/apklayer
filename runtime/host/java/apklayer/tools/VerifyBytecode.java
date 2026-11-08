package apklayer.tools;

import apklayer.host.AppClassLoader;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HotSpot's own bytecode verifier over every class of a converted app, once, as ART verifies an app at install: the app JVMs then run without
 * verification (-XX:-BytecodeVerificationRemote, cli/run.cpp). Each class is loaded the way the app JVM loads it (an AppClassLoader over the
 * framework, Robolectric and host jars its sandbox serves) and linked: Class.getDeclaredFields links first. Which classes the verifier refused comes
 * from the JVM's class+init log (Verifier::verify logs every failure there), since a NoClassDefFoundError from getDeclaredFields can also be
 * reflection resolving a field's type, which is not a verification failure. The caller runs this JVM with -Xlog:class+init=info:file=&lt;log&gt;.
 */
final class VerifyBytecode {
    private VerifyBytecode() {}

    // Verifier::verify's log_end_verification (JDK 17-25): "Verification for <name> has exception pending '<class> <message>'" or "... failed"
    private static final Pattern FAILED = Pattern.compile("Verification for (\\S+) (?:has exception pending '?(\\S+) ?(.*?)'?|failed)\\s*$");

    /**
     * The classes of {@code jar} (internal names) the verifier refused, each with the exception class (internal name) and message an app JVM with
     * verification would have thrown when linking it. {@code appJars} come first in the app's loader (the app's own classes.jar and APK), the
     * framework class path is its parent. Classes the app loader takes from its parent (a framework class of the same name) are not the app's.
     */
    static Map<String, String[]> refused(Path jar, List<Path> appJars, String frameworkClasspath, Path log) throws IOException {
        List<URL> framework = new ArrayList<>();
        for (String entry : frameworkClasspath.split(":")) if (!entry.isEmpty()) framework.add(Path.of(entry).toUri().toURL());
        List<String> names = new ArrayList<>();
        try (java.util.jar.JarFile in = new java.util.jar.JarFile(jar.toFile())) {
            for (var e : java.util.Collections.list(in.entries()))
                if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) names.add(e.getName().substring(0, e.getName().length() - 6));
        }
        Map<String, Throwable> thrown = new LinkedHashMap<>();
        try (URLClassLoader parent = new URLClassLoader("verify-framework", framework.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
             AppClassLoader app = new AppClassLoader("verify-app", appJars.toArray(new Path[0]), parent)) {
            for (String name : names) {
                Class<?> c;
                try { c = Class.forName(name.replace('/', '.'), false, app); }
                catch (Throwable notLoadable) { continue; }  // fails to load in the app JVM too (a missing superclass): no verification involved
                if (c.getClassLoader() != app) continue;
                try { c.getDeclaredFields(); }
                catch (Throwable t) { thrown.put(name, t); }
            }
        }
        Set<String> own = new java.util.HashSet<>(names);
        Map<String, String[]> refused = new LinkedHashMap<>();
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            Matcher m = FAILED.matcher(line);
            if (!m.find()) continue;
            String name = m.group(1).replace('.', '/');
            if (!own.contains(name) || refused.containsKey(name)) continue;
            Throwable t = thrown.get(name);
            String type = m.group(2) != null ? m.group(2) : t != null ? t.getClass().getName() : "java.lang.VerifyError";
            String message = m.group(2) != null ? m.group(3) : t != null && t.getMessage() != null ? t.getMessage() : "verification failed";
            message = message.replaceAll("\\s+", " ");
            refused.put(name, new String[] {type.replace('.', '/'), message.substring(0, Math.min(message.length(), 300))});
        }
        return refused;
    }
}
