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
            session = DexConverter.open(dexes);
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
