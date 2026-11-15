package hello.runtime;

import android.content.Context;
import dalvik.system.DelegateLastClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DynamicDexChecks {
    private static InMemoryDexClassLoader loader(Context context, String fixture) throws Exception {
        ArrayList<ByteBuffer> dexes = new ArrayList<>();
        try (ZipInputStream apk = new ZipInputStream(context.getAssets().open(fixture + ".apk"))) {
            for (ZipEntry entry; (entry = apk.getNextEntry()) != null;) {
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] block = new byte[8192];
                for (int n; (n = apk.read(block)) != -1;) bytes.write(block, 0, n);
                byte[] data = bytes.toByteArray();
                // A slice with a nonzero original buffer position also checks input handling.
                ByteBuffer buffer = ByteBuffer.allocateDirect(data.length + 8);
                buffer.position(8);
                buffer.put(data).flip().position(8);
                dexes.add(buffer);
            }
        }
        if (dexes.isEmpty()) throw new AssertionError("fixture has no DEX: " + fixture);
        return new InMemoryDexClassLoader(dexes.toArray(new ByteBuffer[0]), context.getClassLoader());
    }
    public static void run(Context context) throws Exception {
        // Index both before loading either: relocation and method metadata must not leak.
        ClassLoader first = loader(context, "hello-runtime-dex");
        ClassLoader second = loader(context, "hello-runtime-dex-second");
        Class<?> a = first.loadClass("payload.Probe"), b = second.loadClass("payload.Probe");
        if (a == b || a.getClassLoader() != first || b.getClassLoader() != second)
            throw new AssertionError("dynamic defining loaders mixed");
        if (!"first".equals(a.getMethod("run").invoke(null))) throw new AssertionError("first DEX");
        if (!"second".equals(b.getMethod("run").invoke(null))) throw new AssertionError("second DEX");
        String token = "apklayer.relocated.java.fixture.Token";
        if (first.loadClass(token) == second.loadClass(token)) throw new AssertionError("relocation leaked between DEX sessions");

        // GMS loads the Cronet Dynamite split with a path-based DelegateLastClassLoader. The JVM has no
        // DexFile for that path, so apklayer must convert and define its DEX through this loader too.
        File apk = File.createTempFile("runtime-dex-", ".apk", context.getCacheDir());
        try {
            try (var input = context.getAssets().open("hello-runtime-dex.apk")) {
                Files.copy(input, apk.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            for (ClassLoader dynamic : new ClassLoader[] {
                    new DexClassLoader(apk.getPath(), null, null, context.getClassLoader()),
                    new DelegateLastClassLoader(apk.getPath(), null, context.getClassLoader())}) {
                Class<?> loaded = dynamic.loadClass("payload.Probe");
                if (loaded.getClassLoader() != dynamic || !"first".equals(loaded.getMethod("run").invoke(null)))
                    throw new AssertionError("path DEX not defined by " + dynamic.getClass().getSimpleName());
            }
        } finally {
            if (!apk.delete()) throw new AssertionError("could not remove path DEX fixture");
        }
        // ApplicationInfo uses Android's /data/app spelling. A Dynamite module loader receives the same kind of path for an installed split.
        ClassLoader installedPath = new DelegateLastClassLoader(context.getApplicationInfo().sourceDir, null, context.getClassLoader());
        Class<?> installedClass = installedPath.loadClass(DynamicDexChecks.class.getName());
        if (installedClass == DynamicDexChecks.class || installedClass.getClassLoader() != installedPath)
            throw new AssertionError("Android-visible APK path was not mapped and loaded by its DelegateLastClassLoader");
        System.clearProperty("hello.dynamic.value");
        System.out.println("[hello-runtime] Dynamic DEX PASS in-memory/path loaders, properties/reflection/handles/native loads/relocation/isolation");
    }
}
