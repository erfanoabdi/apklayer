package apklayer.host;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * The framework's {@code @CriticalNative} methods. ART calls those without JNIEnv and jclass; Robolectric's host build
 * compiles the extra parameters in, the device libraries do not, so the native runtime shim (bridge/native/runtime_shim.c)
 * registers them through an adapter that drops the two arguments. The android-all jar has the annotation stripped, the
 * build's own javac output keeps it: {@code main} (scripts/build-native-arm64.sh) lists "class name+signature" lines from
 * those jars into {@code critical-natives.txt}, which goes into native-runtime.jar; {@code of} reads it at run time.
 */
@DoNotInstrument
public final class CriticalNatives {
    private static final String LIST = "critical-natives.txt";

    private CriticalNatives() {}

    /** Names of the class's critical native methods, "name"+"signature" (called from the shim). */
    public static String[] of(String className) {
        List<String> out = new ArrayList<>();
        try (InputStream in = CriticalNatives.class.getClassLoader().getResourceAsStream(LIST)) {
            if (in == null) return new String[0];
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                int sp = line.indexOf(' ');
                if (sp > 0 && line.substring(0, sp).equals(className)) out.add(line.substring(sp + 1));
            }
        } catch (Exception e) {
            // no list, no critical natives
        }
        return out.toArray(new String[0]);
    }

    /** Generator: {@code CriticalNatives out.txt jar...}. */
    public static void main(String[] args) throws Exception {
        try (PrintWriter w = new PrintWriter(args[0], "UTF-8")) {
            for (int i = 1; i < args.length; i++) {
                try (ZipFile z = new ZipFile(args[i])) {
                    for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                        ZipEntry entry = e.nextElement();
                        if (!entry.getName().endsWith(".class")) continue;
                        try (InputStream in = z.getInputStream(entry)) {
                            scan(new ClassReader(in), w);
                        }
                    }
                }
            }
        }
    }

    private static void scan(ClassReader cr, PrintWriter w) {
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if ((access & Opcodes.ACC_NATIVE) == 0) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (annotation.equals("Ldalvik/annotation/optimization/CriticalNative;"))
                            w.println(cr.getClassName().replace('/', '.') + " " + name + desc);
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}
