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
    /** The APK and its converted classes, plus the arm64 native runtime jar (bin/apklayer, PLAN P7) whose library Robolectric's loader extracts. */
    private static List<Path> sandboxJars(Path apk, Path classes) {
        String nativeJar = System.getProperty("apklayer.nativejar");
        return nativeJar == null ? List.of(apk, classes) : List.of(apk, classes, Path.of(nativeJar));
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
                .addClasspathEntries(sandboxJars(apk, classes))
                .setSdkVersion(Integer.getInteger("apklayer.sdk", 36))
                .build();
        try {
            sandbox.runOnMainThread(() -> {
                try {
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
