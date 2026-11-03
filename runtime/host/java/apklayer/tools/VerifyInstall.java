package apklayer.tools;

import io.github.kksimp.dex2jvm.DexClass;
import io.github.kksimp.dex2jvm.DexFile;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Checks a converted app before `apklayer install` marks it installed (cli/apps.cpp): every class of the APK's DEX files is in the jar, every
 * jar class passes ASM's structural check, and the APK's java resources and META-INF/services providers are reachable the way the app JVM
 * finds them (classes.jar, then base.apk: cli/run.cpp). The only intended drop is a class a later DEX file defines again: the first
 * definition wins, as on ART and in dex2jvm. A class dex2jvm could not translate is missing too and fails the install: the jar is cached.
 *
 * <p>Usage: java -Xbootclasspath/a:Robolectric_all.jar -cp host-classes:dex2jvm.jar apklayer.tools.VerifyInstall <base.apk> <classes.jar>. Exit 1 on a failure.
 */
public final class VerifyInstall {
    private static final int SHOWN = 10;

    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        Path apk = Path.of(args[0]), jar = Path.of(args[1]);
        List<String> problems = new ArrayList<>();

        // the APK's classes, in the order dex2jvm reads them (the same dex selection)
        Set<String> dexClasses = new LinkedHashSet<>();
        int duplicates = 0;
        for (byte[] dex : io.github.kksimp.dex2jvm.Main.readDexes(apk))
            for (DexClass c : DexFile.parse(dex).classes()) if (!dexClasses.add(c.name())) duplicates++;

        Set<String> jarClasses = new HashSet<>();
        int checked = 0;
        try (JarFile in = new JarFile(jar.toFile())) {
            for (JarEntry e : Collections.list(in.entries())) {
                String n = e.getName();
                if (!n.endsWith(".class")) continue;
                try (InputStream is = in.getInputStream(e)) {
                    ClassReader r = new ClassReader(is.readAllBytes());
                    if (!(r.getClassName() + ".class").equals(n)) throw new IllegalStateException("entry holds class " + r.getClassName());
                    r.accept(new CheckClassAdapter(null, false), 0);
                    jarClasses.add(r.getClassName());
                    checked++;
                } catch (Exception | LinkageError bad) {
                    problems.add("class " + n + " fails the structural check: " + bad);
                }
            }
        }

        // DenyInstrument moves an app's copy of a host library (and its own java.* classes) under apklayer/relocated/
        List<String> missing = new ArrayList<>();
        for (String c : dexClasses) if (!jarClasses.contains(c) && !jarClasses.contains("apklayer/relocated/" + c)) missing.add(c);
        if (!missing.isEmpty()) problems.add(missing.size() + " of " + dexClasses.size() + " DEX classes are not in the jar (see convert.log for those dex2jvm could not translate): "
                + String.join(", ", missing.subList(0, Math.min(SHOWN, missing.size()))) + (missing.size() > SHOWN ? ", ..." : ""));
        if (jarClasses.size() > dexClasses.size()) problems.add("the jar has " + jarClasses.size() + " classes, the DEX files " + dexClasses.size());

        int resources = 0, providers = 0;
        try (JarFile in = new JarFile(apk.toFile());
             URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL(), apk.toUri().toURL()}, null)) {
            for (JarEntry e : Collections.list(in.entries())) {
                String n = e.getName();
                if (e.isDirectory() || !isJavaResource(n)) continue;
                resources++;
                if (loader.getResource(n) == null) problems.add("resource " + n + " is not reachable");
                if (!n.startsWith("META-INF/services/")) continue;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(loader.getResourceAsStream(n), StandardCharsets.UTF_8))) {
                    for (String line; (line = r.readLine()) != null; ) {
                        String provider = line.replaceAll("#.*", "").trim();
                        if (provider.isEmpty()) continue;
                        providers++;
                        // a provider the DEX files define is covered by the class check (relocated: the host's copy answers the name); one they lack is missing on a device too
                        if (!dexClasses.contains(provider.replace('.', '/'))) System.out.println("[apklayer] verify: note: service provider " + provider + " (" + n + ") is not in the APK");
                    }
                }
            }
        }

        System.out.println("[apklayer] verified " + checked + " classes (" + dexClasses.size() + " in the DEX files, " + duplicates + " defined twice), " + resources
                + " java resources, " + providers + " service providers in " + (System.currentTimeMillis() - t0) + " ms");
        if (problems.isEmpty()) return;
        for (String p : problems.subList(0, Math.min(SHOWN, problems.size()))) System.out.println("[apklayer] verify: " + p);
        if (problems.size() > SHOWN) System.out.println("[apklayer] verify: ... and " + (problems.size() - SHOWN) + " more");
        System.exit(1);
    }

    /** What a class loader serves from the APK: not the DEX, the Android resources and manifest, native libraries, assets or the signature. */
    private static boolean isJavaResource(String n) {
        if (n.endsWith(".class") || n.endsWith(".dex") || n.startsWith("res/") || n.startsWith("assets/") || n.startsWith("lib/") || n.equals("AndroidManifest.xml")
                || n.equals("resources.arsc")) return false;
        if (!n.startsWith("META-INF/")) return true;
        String upper = n.toUpperCase(java.util.Locale.ROOT);
        return !(upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC") || upper.equals("META-INF/MANIFEST.MF"));
    }
}
