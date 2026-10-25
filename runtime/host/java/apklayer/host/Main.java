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
 * <p>Usage: {@code apklayer.host.Main <app.apk> <app-classes.jar>}. The classes jar is the
 * dex2jvm output stamped with {@code @DoNotInstrument}.
 */
public final class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: apklayer.host.Main <app.apk> <app-classes.jar>");
            System.exit(2);
        }
        Path apk = Path.of(args[0]);
        Path classes = Path.of(args[1]);
        // The APK is on the classpath so its resources.arsc can be opened as a resource.
        AndroidSandbox sandbox = SandboxBuilder.newBuilder()
                .addClasspathEntries(List.of(apk, classes))
                .setSdkVersion(Integer.getInteger("apklayer.sdk", 36))
                .build();
        try {
            sandbox.runOnMainThread(() -> {
                try {
                    Class<?> host = sandbox.bootstrappedClass(AppHost.class);
                    Constructor<?> ctor = host.getConstructor(AndroidSandbox.class, Path.class);
                    ((Runnable) ctor.newInstance(sandbox, apk)).run();
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
