package apklayer.host;

import io.github.kksimp.dex2jvm.DexConverter;
import io.github.kksimp.dex2jvm.Main;
import apklayer.tools.DenyInstrument;
import dalvik.system.BaseDexClassLoader;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * apklayer patch: DEX-backed loaders get classes. InMemoryDexClassLoader carries embedded helper DEX (AGDK's Swappy); GMS loads Cronet through
 * a path-based DelegateLastClassLoader. The JVM has no usable DexFile, so DEX is converted with dex2jvm when either loader is created and classes
 * are defined from the result on demand. Ordinary lookups still go to the original findClass.
 */
@Implements(value = BaseDexClassLoader.class, callThroughByDefault = true)
public class ShadowHostBaseDexClassLoader {
    @RealObject private BaseDexClassLoader real;
    private DexConverter.Session session;
    private DenyInstrument transformer;

    @Implementation
    protected void __constructor__(ByteBuffer[] dexFiles, String librarySearchPath, ClassLoader parent) {
        Shadow.invokeConstructor(BaseDexClassLoader.class, real,
                ClassParameter.from(ByteBuffer[].class, dexFiles),
                ClassParameter.from(String.class, librarySearchPath),
                ClassParameter.from(ClassLoader.class, parent));
        try {
            List<byte[]> dexes = new ArrayList<>();
            for (int i = 0; i < dexFiles.length; i++) {
                ByteBuffer b = dexFiles[i].duplicate();
                byte[] dex = new byte[b.remaining()];
                b.get(dex);
                dexes.add(dex);
            }
            index(dexes, parent);
        } catch (Throwable t) {
            System.err.println("[apklayer] in-memory dex not converted: " + t);
        }
    }

    @Implementation
    protected void __constructor__(String dexPath, File optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        Shadow.invokeConstructor(BaseDexClassLoader.class, real,
                ClassParameter.from(String.class, dexPath), ClassParameter.from(File.class, optimizedDirectory),
                ClassParameter.from(String.class, librarySearchPath), ClassParameter.from(ClassLoader.class, parent));
        pathDex(dexPath, parent);
    }

    @Implementation
    protected void __constructor__(String dexPath, String librarySearchPath, ClassLoader parent) {
        Shadow.invokeConstructor(BaseDexClassLoader.class, real,
                ClassParameter.from(String.class, dexPath), ClassParameter.from(String.class, librarySearchPath),
                ClassParameter.from(ClassLoader.class, parent));
        pathDex(dexPath, parent);
    }

    @Implementation
    protected void __constructor__(String dexPath, String librarySearchPath, ClassLoader parent,
            ClassLoader[] sharedLibraryLoaders, ClassLoader[] sharedLibraryLoadersAfter) {
        Shadow.invokeConstructor(BaseDexClassLoader.class, real,
                ClassParameter.from(String.class, dexPath), ClassParameter.from(String.class, librarySearchPath),
                ClassParameter.from(ClassLoader.class, parent), ClassParameter.from(ClassLoader[].class, sharedLibraryLoaders),
                ClassParameter.from(ClassLoader[].class, sharedLibraryLoadersAfter));
        pathDex(dexPath, parent);
    }

    /** Only secondary DEX loaders need this bridge; the app's main PathClassLoader has its own complete converted index. */
    private void pathDex(String dexPath, ClassLoader parent) {
        if (dexPath == null || dexPath.isEmpty()) return;
        String kind = real.getClass().getName();
        if (!kind.equals("dalvik.system.DexClassLoader") && !kind.equals("dalvik.system.DelegateLastClassLoader")) return;
        try {
            List<byte[]> dexes = new ArrayList<>();
            for (String path : dexPath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (path.isEmpty()) continue;
                Path file = hostPath(path);
                if (!java.nio.file.Files.isRegularFile(file)) continue;
                dexes.addAll(Main.readDexes(file));
            }
            if (!dexes.isEmpty()) index(dexes, parent);
        } catch (Throwable t) {
            System.err.println("[apklayer] dynamic dex not converted (" + kind + "): " + t);
        }
    }

    /** Context and package-manager APIs expose Android paths; the converter reads the corresponding installed host file. */
    private static Path hostPath(String shown) {
        Path direct = Path.of(shown);
        if (java.nio.file.Files.isRegularFile(direct)) return direct;
        String apps = System.getProperty("apklayer.apps"), pkg = System.getProperty("apklayer.package");
        if (apps == null || pkg == null) return direct;
        String codeDir = "/data/app/~~Zl3q8XvKc0bYtqN2dHkP5Q==/" + pkg + "-Zl3q8XvKc0bYtqN2dHkP5Q==/";
        if (shown.startsWith(codeDir)) {
            Path root = Path.of(apps, pkg).toAbsolutePath().normalize();
            Path mapped = root.resolve(shown.substring(codeDir.length())).normalize();
            return mapped.startsWith(root) ? mapped : direct;
        }
        String data = System.getProperty("apklayer.data");
        if (data == null) return direct;
        String[][] roots = {{"/data/user/0/" + pkg + "/", "app"}, {"/data/data/" + pkg + "/", "app"},
                {"/data/user_de/0/" + pkg + "/", "device"}};
        for (String[] root : roots) if (shown.startsWith(root[0])) {
            Path hostRoot = Path.of(data, root[1]).toAbsolutePath().normalize();
            Path mapped = hostRoot.resolve(shown.substring(root[0].length())).normalize();
            return mapped.startsWith(hostRoot) ? mapped : direct;
        }
        return direct;
    }

    private void index(List<byte[]> dexes, ClassLoader parent) {
        if (dexes.isEmpty()) return;
        DexConverter.Session opened = DexConverter.open(dexes.toArray(new byte[0][]));
        DenyInstrument processing = new DenyInstrument(parent, true);
        for (var file : opened.dexFiles()) {
            for (var cls : file.classes()) {
                java.util.Set<String> methods = new java.util.HashSet<>();
                for (var method : cls.methods()) methods.add(method.name() + method.descriptor());
                processing.addClass(cls.name(), cls.superclassName(), methods);
            }
        }
        transformer = processing;
        session = opened;
    }

    @Implementation
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (session != null) {
            String internal = name.replace('.', '/');
            synchronized (this) {
                Class<?> loaded = ReflectionHelpers.callInstanceMethod(real, "findLoadedClass",
                        ClassParameter.from(String.class, name.replace('/', '.')));
                if (loaded != null) return loaded;
                String source = transformer.sourceName(internal);
                // Original java.* names cannot be defined by a non-boot JVM loader; converted
                // references already request the relocated name, just like installed jars.
                if (!transformer.mappedName(source).equals(internal)) throw new ClassNotFoundException(name);
                byte[] bytes = session.classBytes(source);
                if (bytes != null) {
                    bytes = transformer.transform(bytes);
                    return ReflectionHelpers.callInstanceMethod(real, "defineClass",
                            ClassParameter.from(String.class, transformer.mappedName(source).replace('/', '.')),
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
