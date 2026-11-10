package apklayer.host;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import org.robolectric.internal.AndroidSandbox;
import org.robolectric.simulator.SandboxBuilder;

/**
 * apklayer entry point (PLAN archive P1.5): builds a Robolectric sandbox without JUnit, the way
 * Robolectric's own SimulatorMain does, and runs {@link AppHost} inside it.
 *
 * <p>Usage: {@code apklayer.host.Main [--describe <out-dir>] <app.apk> <app-classes.jar>}. The
 * classes jar is the dex2jvm output stamped with {@code @DoNotInstrument}. With {@code --describe}
 * the app is not run: its metadata and launcher icon are written to out-dir (install time).
 */
public final class Main {
    /** The arm64 native runtime jar (bin/apklayer, PLAN archive P7) whose library Robolectric's loader extracts. The app's classes and APK are not the sandbox's:
     *  they get a loader of their own ({@link AppClassLoader}), its resources come from the manifest's APK path. Every process kind shares one sandbox. */
    private static AndroidSandbox sandbox() {
        String nativeJar = System.getProperty("apklayer.nativejar");
        return SandboxBuilder.newBuilder().addClasspathEntries(nativeJar == null ? List.of() : List.of(Path.of(nativeJar)))
                .setSdkVersion(Integer.getInteger("apklayer.sdk", 36)).build();
    }

    /** The app's class loader (its classes, and the APK for its java resources), a child of the sandbox, known inside the sandbox and to its loader's fallback. */
    private static ClassLoader appLoader(AndroidSandbox sandbox, Path apk, Path classes) throws ReflectiveOperationException {
        AppClassLoader loader = new AppClassLoader("app", new Path[] {classes, apk}, sandbox.getRobolectricClassLoader());
        sandbox.bootstrappedClass(AppClassLoader.class).getField("app").set(null, loader);
        Class.forName("org.robolectric.internal.bytecode.ApklayerAppHook").getField("app").set(null, loader);  // a runtime overlay class (runtime/patches)
        return loader;
    }

    /** Android has no module system: app code reflects into any java.* / javax.* class of libcore (App Manager reads SecretKeySpec.key). Every such
     *  package of java.base is opened to the unnamed modules (the app's, the sandbox's) as jvm.args opens java.lang for this call. */
    private static void openJavaBase() {
        Module base = Object.class.getModule();
        try {
            java.lang.reflect.Method open = Module.class.getDeclaredMethod("implAddOpensToAllUnnamed", String.class);
            open.setAccessible(true);
            for (String pkg : base.getPackages()) if (pkg.startsWith("java.") || pkg.startsWith("javax.")) open.invoke(base, pkg);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static void main(String[] args) {
        StartTrace.mark("jvm main");
        openJavaBase();
        if (args.length >= 1 && args[0].equals("--hub")) {  // --hub [<apk> <classes>]: the system process's package it hosts (cli runHub)
            AndroidSandbox sandbox = sandbox();
            Path apk = args.length == 3 ? Path.of(args[1]) : null, classes = args.length == 3 ? Path.of(args[2]) : null;
            try {
                sandbox.runOnMainThread(() -> {
                    try {
                        if (apk != null) Thread.currentThread().setContextClassLoader(appLoader(sandbox, apk, classes));
                        sandbox.bootstrappedClass(HubHost.class).getMethod("run", AndroidSandbox.class, Path.class).invoke(null, sandbox, apk);
                    }
                    catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
                });
            } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
            return;
        }
        boolean spare = args.length == 1 && args[0].equals("--spare");  // the instance's spare: started before its launch is known (AppSpare)
        Path describe = null;
        if (args.length == 4 && args[0].equals("--describe")) {
            describe = Path.of(args[1]);
            args = new String[] {args[2], args[3]};
        }
        if (!spare && args.length != 2) {
            System.err.println("usage: apklayer.host.Main [--describe <out-dir>] <app.apk> <app-classes.jar>");
            System.exit(2);
        }
        String[] files = spare ? null : args;
        Path out = describe;
        if (!spare && !Boolean.getBoolean("apklayer.componenthost") && describe == null) RunDeadline.start();
        AndroidSandbox sandbox = sandbox();
        StartTrace.mark("sandbox built");
        try {
            sandbox.runOnMainThread(() -> {
                try {
                    String[] target = files;
                    if (spare) {
                        Thread.currentThread().setContextClassLoader(sandbox.getRobolectricClassLoader());  // the native runtime's resources (build.prop, ICU) are the sandbox's
                        target = (String[]) sandbox.bootstrappedClass(AppSpare.class).getMethod("awaitTarget", AndroidSandbox.class).invoke(null, sandbox);
                        if (!Boolean.getBoolean("apklayer.componenthost")) RunDeadline.start();  // the launch's runms
                    }
                    // an app's window process, or a package's headless component host (the hub's `apklayer component-host`: -Dapklayer.componenthost)
                    boolean componentHost = Boolean.getBoolean("apklayer.componenthost");
                    Path apk = Path.of(target[0]), classes = Path.of(target[1]);
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
