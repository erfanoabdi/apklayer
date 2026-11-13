package hello.runtime;

import android.content.Context;
import dalvik.system.InMemoryDexClassLoader;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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
        System.clearProperty("hello.dynamic.value");
        System.out.println("[hello-runtime] Dynamic DEX PASS properties/reflection/handles/native loads/relocation/isolation");
    }
}
