// dalvik.system.InMemoryDexClassLoader -- loads classes from one or more DEX
// images held in ByteBuffers (no file path). Used by app-protection / DRM
// "packers", dynamic-feature frameworks, and a growing set of obfuscation
// tools that decrypt a DEX into memory at runtime and hand the ByteBuffer
// straight to ART. Added in API 26.
//
// AOSP (libcore/dalvik/src/main/java/dalvik/system/InMemoryDexClassLoader.java):
//   public InMemoryDexClassLoader(ByteBuffer[] dexBuffers, String librarySearchPath, ClassLoader parent)
//   public InMemoryDexClassLoader(ByteBuffer[] dexBuffers, ClassLoader parent)   // delegates with null searchPath
//   public InMemoryDexClassLoader(ByteBuffer dexBuffer, ClassLoader parent)      // single-buffer convenience
// extends BaseDexClassLoader.
//
// ART maps the buffers as in-memory dex files. Mac.apk has no DEX interpreter,
// so we run com.macapk.dex (the same DEX->JVM-bytecode converter used for the
// primary app dex) over all the buffers together, in memory, and serve the
// resulting jar through the BaseDexClassLoader URL chain. That jar is
// EPHEMERAL: opened by this loader and then unlinked, so nothing is left on
// disk (MacApkDexConverter, docs/dynamic-dex-cache_2026-09-24.md). The
// buffers' positions are preserved (the converter duplicates before reading),
// matching the on-device contract that the caller may reuse them.
package dalvik.system;

import java.nio.ByteBuffer;

public final class InMemoryDexClassLoader extends BaseDexClassLoader {

    /** AOSP primary ctor. librarySearchPath is advisory here (we have no
     *  Android ld.so path); the buffers are converted to bytecode jars. */
    public InMemoryDexClassLoader(ByteBuffer[] dexBuffers,
                                  String librarySearchPath, ClassLoader parent) {
        super(MacApkDexConverter.convertBuffersToJarUrls(requireNonEmpty(dexBuffers)),
              parent);
        // Open the converted jar, then unlink it: the loader reads through its
        // fd from here on, and no file outlives the process. ART never
        // persists an in-memory dex's code either (VdexFile::WriteToDisk
        // writes has_dex_section=false for it).
        macapkOpenNow();
    }

    /** AOSP 2-arg array ctor: identical with a null library search path. */
    public InMemoryDexClassLoader(ByteBuffer[] dexBuffers, ClassLoader parent) {
        this(dexBuffers, null, parent);
    }

    /** AOSP single-buffer convenience ctor (the common packer shape). */
    public InMemoryDexClassLoader(ByteBuffer dexBuffer, ClassLoader parent) {
        this(new ByteBuffer[] { dexBuffer }, null, parent);
    }

    private static ByteBuffer[] requireNonEmpty(ByteBuffer[] bufs) {
        if (bufs == null || bufs.length == 0) {
            // AOSP throws NPE / produces an empty loader; mirror by yielding an
            // empty array so the loader simply finds no classes (parent-first
            // delegation still works for inherited classes).
            return new ByteBuffer[0];
        }
        return bufs;
    }
}
