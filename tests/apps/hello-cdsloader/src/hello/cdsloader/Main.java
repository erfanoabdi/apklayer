package hello.cdsloader;

import android.app.Activity;
import android.os.Bundle;
import java.io.InputStream;

/** An app's own ClassLoader subclass (tests/vm/cds-dump.sh): it defines Nest's nested pair from the app's class files, and Inner's nest host check
 *  loads Nest without linking it while resolving Nest's NestMembers entry (the constant pool state that crashed an archive, docs/history/PLAN-2026-11-10.md). */
public class Main extends Activity {
    static class Own extends ClassLoader {
        Own(ClassLoader parent) { super(parent); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("hello.cdsloader.Nest")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c != null) return c;
                try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (in == null) throw new ClassNotFoundException(name);
                    byte[] b = in.readAllBytes();
                    // the app jar's code source: HotSpot archives a custom loader's class only when it names a file it came from
                    return defineClass(name, b, 0, b.length, Main.class.getProtectionDomain());
                } catch (java.io.IOException e) { throw new ClassNotFoundException(name, e); }
            }
        }
    }

    /** Keeps Own and its classes alive until the dump (an unloaded loader's classes are not archived). */
    static Object kept;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Own own = new Own(getClassLoader());
            Class<?> inner = own.loadClass("hello.cdsloader.Nest$Inner");
            Class<?> sibling = own.loadClass("hello.cdsloader.Nest$Sibling");
            Object host = Class.class.getMethod("getNestHost").invoke(inner);  // a JVM API (not Android's): HotSpot's nest host validation
            Object made = sibling.getDeclaredConstructor().newInstance();
            if (host == inner || ((Class<?>) host).getClassLoader() != own) throw new AssertionError("nest host " + host + " is not the loader's own Nest");
            kept = made;
            System.out.println("[cdsloader] PASS host=" + host + " made=" + made);
        } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[cdsloader] FAIL " + failure); }
    }
}
