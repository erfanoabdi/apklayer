package apklayer.host;

import io.github.kksimp.dex2jvm.DexConverter;
import dalvik.system.BaseDexClassLoader;
import java.nio.ByteBuffer;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * apklayer patch: InMemoryDexClassLoader gets classes. Native libraries embed Java helpers as DEX (AGDK's Swappy:
 * com.google.androidgamesdk.SwappyDisplayManager) and load them from a ByteBuffer; the JVM has no DexFile, so the dex is converted with
 * dex2jvm when the loader is created and classes are defined from the result on demand. Other lookups go to the original findClass.
 */
@Implements(value = BaseDexClassLoader.class, callThroughByDefault = true)
public class ShadowHostBaseDexClassLoader {
    @RealObject private BaseDexClassLoader real;
    private DexConverter.Session session;
    private java.util.Set<String> refused = java.util.Set.of();

    /**
     * This JVM runs without bytecode verification (cli/run.cpp: every installed app was verified at install), so classes converted here, which
     * no install saw, go through HotSpot's verifier in a JVM of their own first (VerifyInstall --classes, ~1-2 s; only apps with in-memory DEX):
     * what it refuses is never defined. The verifier's view: the app's own classes, the framework jars, this JVM's class path.
     */
    private static java.util.Set<String> verify(DexConverter.Session session) throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("apklayer-dexverify");
        try {
            java.nio.file.Path jar = dir.resolve("classes.jar"), log = dir.resolve("verify.log");
            try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
                for (var e : session.convertAll().entrySet()) {
                    out.putNextEntry(new java.util.zip.ZipEntry(e.getKey() + ".class"));
                    out.write(e.getValue());
                }
            }
            StringBuilder path = new StringBuilder();
            if (AppClassLoader.app instanceof java.net.URLClassLoader app)
                for (java.net.URL u : app.getURLs()) path.append(java.nio.file.Path.of(u.toURI())).append(':');
            java.io.File[] framework = new java.io.File(System.getProperty("robolectric.dependency.dir", "")).listFiles((d, n) -> n.endsWith(".jar"));
            if (framework != null) for (java.io.File f : framework) path.append(f).append(':');
            path.append(System.getProperty("java.class.path"));
            ProcessBuilder pb = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-Xlog:class+init=info:file=" + log,
                    "-Dapklayer.verify.classpath=" + path, "-Dapklayer.verify.log=" + log, "-cp", System.getProperty("java.class.path"),
                    "apklayer.tools.VerifyInstall", "--classes", jar.toString());
            pb.environment().remove("LD_PRELOAD");
            pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT);
            int status = pb.start().waitFor();
            if (status == 0) return java.util.Set.of();
            if (status != 3) throw new IllegalStateException("the verifier exited with " + status);
            java.util.Set<String> refused = new java.util.HashSet<>();
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(jar + ".refused"))) refused.add(line.split("\t", 2)[0]);
            return refused;
        } finally {
            try (var files = java.nio.file.Files.list(dir)) { for (java.nio.file.Path f : files.toList()) java.nio.file.Files.delete(f); }
            java.nio.file.Files.delete(dir);
        }
    }

    @Implementation
    protected void __constructor__(ByteBuffer[] dexFiles, String librarySearchPath, ClassLoader parent) {
        Shadow.invokeConstructor(BaseDexClassLoader.class, real,
                ClassParameter.from(ByteBuffer[].class, dexFiles),
                ClassParameter.from(String.class, librarySearchPath),
                ClassParameter.from(ClassLoader.class, parent));
        try {
            byte[][] dexes = new byte[dexFiles.length][];
            for (int i = 0; i < dexFiles.length; i++) {
                ByteBuffer b = dexFiles[i].duplicate();
                dexes[i] = new byte[b.remaining()];
                b.get(dexes[i]);
            }
            DexConverter.Session opened = DexConverter.open(dexes);
            if (Boolean.getBoolean("apklayer.unverified")) refused = verify(opened);  // a failed check leaves the loader without these classes
            session = opened;
        } catch (Throwable t) {
            System.err.println("[apklayer] in-memory dex not converted: " + t);
        }
    }

    @Implementation
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (session != null) {
            String internal = name.replace('.', '/');
            synchronized (this) {
                Class<?> loaded = ReflectionHelpers.callInstanceMethod(real, "findLoadedClass",
                        ClassParameter.from(String.class, name.replace('/', '.')));
                if (loaded != null) return loaded;
                if (refused.contains(internal)) throw new VerifyError("[apklayer] " + name + " failed bytecode verification (in-memory dex)");
                byte[] bytes = session.classBytes(internal);
                if (bytes != null) {
                    return ReflectionHelpers.callInstanceMethod(real, "defineClass",
                            ClassParameter.from(String.class, name.replace('/', '.')),
                            ClassParameter.from(byte[].class, bytes), ClassParameter.from(int.class, 0),
                            ClassParameter.from(int.class, bytes.length));
                }
            }
        }
        try { return Shadow.directlyOn(real, BaseDexClassLoader.class, "findClass", ClassParameter.from(String.class, name)); }
        catch (RuntimeException wrapped) {  // directlyOn wraps the checked exception; parent-last loaders catch ClassNotFoundException itself (Aurora Store)
            Throwable cause = wrapped.getCause();
            if (cause instanceof java.lang.reflect.InvocationTargetException invoked) cause = invoked.getCause();
            if (cause instanceof ClassNotFoundException missing) throw missing;
            throw wrapped;
        }
    }
}
