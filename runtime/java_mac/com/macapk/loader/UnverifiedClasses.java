// UnverifiedClasses -- shout when a class that CANNOT pass HotSpot's bytecode
// verifier is loaded anyway.
//
// WHY THIS EXISTS. runtime/src/jvm_loader.cpp passes -Xverify:none, so a class
// whose StackMapTable we got wrong loads SILENTLY and only misbehaves later, if
// at all. That is the whole reason the flag is a liability: with verification on
// you would get a named VerifyError at the exact class; with it off you get a
// mystery downstream.
//
// It also cannot be detected in-process. -Xverify:none is VM-wide, so nothing
// throws no matter how the class is linked -- measured, not assumed. The list
// therefore has to be produced OUT OF PROCESS by the real verifier
// (debug/dexconv_verify/VerifyCheck.java, driven by debug/dexconv_one.sh) and
// dropped next to classes.jar as:
//
//   <app_dir>/classes.unverified.txt      one internal class name per line
//                                         ("com/example/Foo"), # comments ok
//
// No file means no report, which is the normal shipping case: this costs
// nothing unless someone has actually triaged the APK. When the file IS there,
// the first load of a listed class prints one loud line naming it, so a
// bug-hunt starts at the right class instead of at the symptom.
//
// Deliberately NOT a hard failure. The class loads exactly as it does today;
// this only makes the fact visible. Turning it into a throw would change
// behaviour on apps that work fine today because the unverifiable method is
// never called.

package com.macapk.loader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class UnverifiedClasses {

    private static final String TAG = "AppClassLoader";
    private static final String SIDECAR = "classes.unverified.txt";

    /** Internal names ("com/example/Foo") known to fail the real verifier. */
    private static volatile Set<String> names;
    /** Report each class once; a class can be asked for many times. */
    private static final Set<String> shouted =
        Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private UnverifiedClasses() {}

    /**
     * Load the sidecar sitting next to classes.jar, if there is one.
     *
     * @param classpath the loader's own URLs; the sidecar is a sibling of
     *     whichever entry is the app's classes.jar.
     */
    static void init(URL[] classpath) {
        if (names != null || classpath == null) {
            return;
        }
        Set<String> found = null;
        for (URL u : classpath) {
            if (u == null || !"file".equals(u.getProtocol())) continue;
            String p = u.getPath();
            if (p == null || !p.endsWith("classes.jar")) continue;
            try {
                Path side = Paths.get(p).resolveSibling(SIDECAR);
                if (!Files.isReadable(side)) continue;
                found = read(side);
            } catch (Exception ignored) {
                // A malformed or unreadable report must never break class
                // loading; it is a diagnostic, not a dependency.
            }
            break;
        }
        names = found == null ? Collections.<String>emptySet() : found;
        if (!names.isEmpty()) {
            NativeLog.w(TAG, "*** " + names.size() + " class(es) in this app CANNOT pass "
                + "bytecode verification; they load only because -Xverify:none is set. "
                + "Each will be named on first load. Source: " + SIDECAR);
        }
    }

    private static Set<String> read(Path p) throws Exception {
        Set<String> out = new HashSet<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Files.newInputStream(p), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                out.add(line.replace('.', '/'));
            }
        }
        return out;
    }

    /** Shout once if {@code classDotted} is known-unverifiable. */
    static void check(String classDotted) {
        Set<String> n = names;
        if (n == null || n.isEmpty() || classDotted == null) {
            return;
        }
        String internal = classDotted.replace('.', '/');
        if (!n.contains(internal) || !shouted.add(internal)) {
            return;
        }
        NativeLog.e(TAG, "*** UNVERIFIABLE CLASS LOADED: " + classDotted
            + " -- this class FAILS HotSpot's bytecode verifier and is running only "
            + "because -Xverify:none is set. If this app misbehaves, suspect this "
            + "class first. Re-run debug/dexconv_one.sh on the apk for the reason.");
    }
}
