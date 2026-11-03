package apklayer.tools;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.robolectric.interceptors.AndroidInterceptors;
import org.robolectric.internal.bytecode.ClassDetails;
import org.robolectric.internal.bytecode.ClassInstrumentor;
import org.robolectric.internal.bytecode.ClassNodeProvider;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;
import org.robolectric.internal.bytecode.Interceptors;
import org.robolectric.simulator.SandboxBuilder;
import org.robolectric.util.inject.Injector;

/**
 * Instruments the framework jar ahead of time, so the app JVMs' sandbox loader finds every class already instrumented (Robolectric skips classes
 * that carry its ShadowedObject marker) instead of rewriting ~3600 of them with ASM on every start. Robolectric's own preinstrumented/JarInstrumentor,
 * except that the configuration is the one the app JVM's sandbox uses (the simulator SandboxBuilder's, with the same interceptors), so the bytes
 * equal what the sandbox loader would produce; a class the instrumentor rejects stays as it is (the loader then instruments it, as before).
 *
 * <p>Usage (tools/build instrument): java -cp native-runtime.jar:host-classes:sim-classes:Robolectric_all.jar apklayer.tools.PreInstrument <in.jar> <out.jar>
 */
public final class PreInstrument {
    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        Method build = SandboxBuilder.class.getDeclaredMethod("buildInstrumentationConfiguration", Interceptors.class);
        build.setAccessible(true);
        InstrumentationConfiguration config = (InstrumentationConfiguration) build.invoke(SandboxBuilder.newBuilder(), new Interceptors(AndroidInterceptors.all()));
        ClassInstrumentor instrumentor = new Injector.Builder().build().getInstance(ClassInstrumentor.class);
        Path out = Path.of(args[1]), partial = Path.of(args[1] + ".partial");
        int instrumented = 0, kept = 0;
        try (JarFile in = new JarFile(args[0]);
             JarOutputStream jar = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(partial), 1 << 20))) {
            ClassNodeProvider provider = new ClassNodeProvider() {
                @Override
                protected byte[] getClassBytes(String name) throws ClassNotFoundException { return classBytes(in, name); }
            };
            for (Enumeration<JarEntry> entries = in.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                byte[] bytes;
                try (InputStream data = in.getInputStream(entry)) { bytes = data.readAllBytes(); }
                if (name.endsWith(".class")) {
                    ClassDetails details = new ClassDetails(bytes);
                    if (config.shouldInstrument(details)) {
                        try {
                            bytes = instrumentor.instrument(details, config, provider);
                            instrumented++;
                        } catch (RuntimeException rejected) {
                            System.err.println("PreInstrument: left " + name + " to the sandbox loader: " + rejected);
                            kept++;
                        }
                    }
                }
                JarEntry copy = new JarEntry(name);
                copy.setTime(entry.getTime());
                jar.putNextEntry(copy);
                jar.write(bytes);
            }
        }
        Files.move(partial, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        System.out.printf("PreInstrument: %d classes instrumented, %d left as they are, %.1f s -> %s%n", instrumented, kept, (System.nanoTime() - t0) / 1e9, out);
    }

    /** A class of the jar, else of the JDK (the hierarchy the instrumentor computes frames against). */
    private static byte[] classBytes(JarFile jar, String className) throws ClassNotFoundException {
        String file = className.replace('.', '/') + ".class";
        ZipEntry entry = jar.getEntry(file);
        try (InputStream data = entry != null ? jar.getInputStream(entry) : ClassLoader.getSystemResourceAsStream(file)) {
            if (data == null) throw new ClassNotFoundException(className);
            return data.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException(className, e);
        }
    }
}
