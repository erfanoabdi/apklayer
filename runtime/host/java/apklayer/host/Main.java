package apklayer.host;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import org.robolectric.internal.AndroidSandbox;
import org.robolectric.simulator.SandboxBuilder;

/**
 * apklayer entry point (PLAN P1.5): builds a Robolectric sandbox without JUnit, the way
 * Robolectric's own SimulatorMain does, and runs {@link AppHost} inside it.
 *
 * <p>Usage: {@code apklayer.host.Main [--describe <out-dir>] <app.apk> <app-classes.jar>}. The
 * classes jar is the dex2jvm output stamped with {@code @DoNotInstrument}. With {@code --describe}
 * the app is not run: its metadata and launcher icon are written to out-dir (install time).
 */
public final class Main {
    /** The APK (its resources.arsc), plus the arm64 native runtime jar (bin/apklayer, PLAN P7) whose library Robolectric's loader extracts. The app's classes are
     *  not the sandbox's: they get a loader of their own ({@link AppClassLoader}). */
    private static List<Path> sandboxJars(Path apk) {
        String nativeJar = System.getProperty("apklayer.nativejar");
        return nativeJar == null ? List.of(apk) : List.of(apk, Path.of(nativeJar));
    }

    /** The app's class loader (its classes, and the APK for its java resources), a child of the sandbox, known inside the sandbox and to its loader's fallback. */
    private static ClassLoader appLoader(AndroidSandbox sandbox, Path apk, Path classes) throws ReflectiveOperationException {
        AppClassLoader loader = new AppClassLoader("app", new Path[] {classes, apk}, sandbox.getRobolectricClassLoader());
        sandbox.bootstrappedClass(AppClassLoader.class).getField("app").set(null, loader);
        Class.forName("org.robolectric.internal.bytecode.ApklayerAppHook").getField("app").set(null, loader);  // a runtime overlay class (runtime/patches)
        return loader;
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--hub")) {
            String nativeJar = System.getProperty("apklayer.nativejar");
            AndroidSandbox sandbox = SandboxBuilder.newBuilder()
                    .addClasspathEntries(nativeJar == null ? List.of() : List.of(Path.of(nativeJar)))
                    .setSdkVersion(Integer.getInteger("apklayer.sdk", 36)).build();
            try {
                sandbox.runOnMainThread(() -> {
                    try { sandbox.bootstrappedClass(HubHost.class).getMethod("run", AndroidSandbox.class).invoke(null, sandbox); }
                    catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
                });
            } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
            return;
        }
        boolean componentHost = args.length == 3 && args[0].equals("--component-host");
        if (componentHost) { args = new String[]{args[1], args[2]}; System.setProperty("apklayer.componenthost", "true"); }
        Path describe = null;
        if (args.length == 4 && args[0].equals("--describe")) {
            describe = Path.of(args[1]);
            args = new String[] {args[2], args[3]};
        }
        if (args.length != 2) {
            System.err.println("usage: apklayer.host.Main [--describe <out-dir>] <app.apk> <app-classes.jar>");
            System.exit(2);
        }
        Path apk = Path.of(args[0]);
        Path classes = Path.of(args[1]);
        Path out = describe;
        // The APK is on the classpath so its resources.arsc can be opened as a resource.
        AndroidSandbox sandbox = SandboxBuilder.newBuilder()
                .addClasspathEntries(sandboxJars(apk))
                .setSdkVersion(Integer.getInteger("apklayer.sdk", 36))
                .build();
        try {
            sandbox.runOnMainThread(() -> {
                try {
                    Thread.currentThread().setContextClassLoader(appLoader(sandbox, apk, classes));
                    Class<?> host = sandbox.bootstrappedClass(componentHost ? ComponentHost.class : AppHost.class);
                    Constructor<?> ctor = host.getConstructor(AndroidSandbox.class, Path.class, Path.class);
                    ((Runnable) ctor.newInstance(sandbox, apk, out)).run();
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private Main() {}
}
