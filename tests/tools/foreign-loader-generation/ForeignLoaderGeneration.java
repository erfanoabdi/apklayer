import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.robolectric.internal.bytecode.ClassInstrumentor;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;
import org.robolectric.internal.bytecode.ResourceProvider;
import org.robolectric.internal.bytecode.SandboxClassLoader;

public class ForeignLoaderGeneration {
    private static void check(boolean pass, String what) {
        if (!pass) throw new AssertionError(what);
    }
    private static void put(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        var method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }
    private static void checkGeneration(ClassLoader loader, int expected) throws Exception {
        Class<?> type = loader.loadClass("localfixture.Target");
        check(type.getClassLoader() == loader, "Target must be defined by the foreign loader");
        check(type.getMethod("generation").invoke(null).equals(expected), "Wrong class generation: " + expected);
    }

    private static void checkResource(URL resource, String expected) throws Exception {
        check(resource != null, "Missing fixture resource");
        try (InputStream stream = resource.openStream()) {
            check(new String(stream.readAllBytes(), StandardCharsets.UTF_8).equals(expected),
                    "Wrong resource generation: " + expected);
        }
    }

    private static void publish(Path marker) throws Exception {
        Files.writeString(marker, "uid=10001\n");
        // Make publication ordering independent of the filesystem's timestamp resolution.
        Path directory = marker.getParent();
        FileTime baseTime = Files.getLastModifiedTime(directory.resolve("base.apk"));
        FileTime jarTime = Files.getLastModifiedTime(directory.resolve("classes.jar"));
        Files.setLastModifiedTime(marker, baseTime.compareTo(jarTime) >= 0 ? baseTime : jarTime);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path target = Files.createDirectory(root.resolve("fixture.target"));
        Files.createFile(target.resolve("base.apk"));
        Files.move(root.resolve("1.jar"), target.resolve("classes.jar"));
        Path marker = target.resolve("info.properties");
        publish(marker);
        ClassLoader host = ForeignLoaderGeneration.class.getClassLoader();
        var config = InstrumentationConfiguration.newBuilder().doNotAcquirePackage("org.robolectric.").build();
        try (var sandbox = new SandboxClassLoader(host, config, new ResourceProvider() {
            public URL getResource(String name) { return host.getResource(name); }
            public InputStream getResourceAsStream(String name) { return host.getResourceAsStream(name); }
            public void close() {}
        }, new ClassInstrumentor())) {
            Class<?> shadowType = sandbox.loadClass("apklayer.host.ShadowHostLoadedApk");
            Class<?> loadedType = sandbox.loadClass("android.app.LoadedApk");
            Class<?> infoType = sandbox.loadClass("android.content.pm.ApplicationInfo");
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            // Populate only the package metadata used by the loader, without booting Android.
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            Object loaded = unsafe.allocateInstance(loadedType);
            Object info = unsafe.allocateInstance(infoType);
            put(loadedType, loaded, "mApplicationInfo", info);
            put(loadedType, loaded, "mPackageName", "fixture.target");
            put(loadedType, loaded, "mAppDir", root.resolve("fixture.target/base.apk").toString());
            put(loadedType, loaded, "mIncludeCode", true);
            put(infoType, info, "flags", 4);
            Object shadow = shadowType.getConstructor().newInstance();
            put(shadowType, shadow, "loaded", loaded);
            try (var index = new URLClassLoader(new URL[] {
                    root.resolve("fixture.caller/classes.jar").toUri().toURL(),
                    root.resolve("fixture.caller/base.apk").toUri().toURL()})) {
                put(sandbox.loadClass("apklayer.host.AppClassLoader"), null, "index", index);
                var lookup = method(shadowType, "classLoaderForContext", int.class);
                var refresh = method(shadowType, "createOrUpdateClassLoaderLocked", List.class);
                ClassLoader original = (ClassLoader) lookup.invoke(shadow, 1);
                URL originalResource = original.getResource("target-resource.txt");
                check(originalResource != null, "Original loader did not find its resource");

                // Publish the replacement by rename, as the installer does. Leave the original
                // class unloaded and the original resource stream unopened until after replacement.
                Files.move(root.resolve("2.jar"), target.resolve("classes.jar"),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                publish(marker);
                refresh.invoke(shadow, List.of());
                ClassLoader replacement = (ClassLoader) lookup.invoke(shadow, 1);
                check(replacement != original, "Refresh reused the original loader");
                checkGeneration(replacement, 2);
                checkResource(replacement.getResource("target-resource.txt"), "2");

                checkGeneration(original, 1);
                checkResource(originalResource, "1");
                checkResource(original.getResource("target-resource.txt"), "1");
            }
        }
        System.out.println("Foreign loader generation check passed");
    }
}
