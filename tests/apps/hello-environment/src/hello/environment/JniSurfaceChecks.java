package hello.environment;

import java.util.Objects;

/** The JNI surface app native code gets, compared with Android 13's ART (jni_surface.c; docs/runtime-identity-matrix.md, JNI rows). */
final class JniSurfaceChecks {
    private static native String[] probe();

    static int run() {
        String[] got;
        try { got = probe(); }
        catch (Throwable failure) {
            System.out.println("[environment] mismatch JNI surface exception " + failure);
            return 1;
        }
        String[] labels = {"GetVersion", "JNI_GetCreatedJavaVMs", "JNI_GetDefaultJavaVMInitArgs", "JNI_CreateJavaVM",
                "attached FindClass(app class)", "attached FindClass(framework class)", "attached FindClass(java.lang.String)",
                "attached thread name", "attached named thread", "native method FindClass(app class)"};
        String[] want = {"0x10006", "ret=0 count=1 same=1", "-1", "-1", "NOT FOUND java.lang.ClassNotFoundException", "found", "found",
                null, "java=jni-surface native=jni-surface", "found"};
        int failures = 0;
        for (int i = 0; i < labels.length; i++) {
            String w = want[i];
            if (w == null) {  /* an unnamed attach: the native name is the Java one (Thread-<n>) */
                String[] parts = got[i].split(" ");
                w = parts.length == 2 && parts[0].startsWith("java=Thread-") ? parts[0] + " native=" + parts[0].substring(5) : "java=Thread-<n> native=Thread-<n>";
            }
            boolean ok = Objects.equals(w, got[i]);
            if (!ok) failures++;
            System.out.println("[environment] " + (ok ? "ok " : "mismatch ") + "JNI surface " + labels[i] + " expected=" + w + " actual=" + got[i]);
        }
        System.out.println("[environment] JNI surface checks: " + (failures == 0 ? "PASS" : failures + " mismatches"));
        return failures;
    }
}
