package hello.runtime;

import android.app.Activity;
import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Defining-loader invariants; these cannot be satisfied by only rewriting getClassLoader(). */
public final class LoaderChecks {
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); }
    public static void run(Activity activity) throws Throwable {
        ClassLoader loader = Main.class.getClassLoader();
        check(loader instanceof PathClassLoader, "app defining loader is PathClassLoader");
        check(loader.getClass().getName().equals("dalvik.system.PathClassLoader"), "concrete Android loader type");
        // Hidden libcore API: Android always returns true; HotSpot requires registration.
        check(Boolean.TRUE.equals(ClassLoader.class.getMethod("isRegisteredAsParallelCapable").invoke(loader)),
                "parallel-capable Android loader");
        check(loader == activity.getClassLoader(), "context uses defining loader");
        check(loader == Thread.currentThread().getContextClassLoader(), "thread uses defining loader");
        check(loader == (ClassLoader) Class.class.getMethod("getClassLoader").invoke(Main.class), "reflective defining loader");
        check(Class.forName(Main.class.getName()) == Main.class, "caller-sensitive Class.forName");
        check(Class.forName(Main.class.getName(), false, loader) == Main.class, "explicit Class.forName");
        check(loader.loadClass(Main.class.getName()) == Main.class, "loadClass agrees");
        java.lang.reflect.Method find = BaseDexClassLoader.class.getDeclaredMethod("findClass", String.class);
        find.setAccessible(true);
        check(find.invoke(loader, Main.class.getName()) == Main.class, "protected findClass agrees");
        try {
            find.invoke(loader, "hello.runtime.MissingClass");
            throw new AssertionError("protected findClass accepted missing class");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof ClassNotFoundException, "protected findClass failure type");
        }
        check(loader.loadClass("[Lhello.runtime.Main;") == Main[].class, "array class lookup");
        check(Main[].class.getClassLoader() == loader, "array defining loader");
        check((BaseDexClassLoader) loader == loader, "BaseDex cast preserves loader identity");
        ClassLoader unrelated = new ClassLoader(loader) {};
        try { BaseDexClassLoader wrong = (BaseDexClassLoader) unrelated; throw new AssertionError("unrelated loader cast accepted: " + wrong); }
        catch (ClassCastException expected) { }
        try (InputStream in = Main.class.getResourceAsStream("/assets/loader-marker.txt")) {
            check(in != null, "APK resource from defining loader");
            byte[] bytes = new byte[128];
            int size = in.read(bytes);
            check(new String(bytes, 0, size, StandardCharsets.UTF_8).trim().equals("hello-runtime loader resource"), "APK resource contents");
        }
        check(loader.getResources("assets/loader-marker.txt").hasMoreElements(), "resource enumeration");
        int count = 8;
        Thread[] threads = new Thread[count];
        Class<?>[] classes = new Class<?>[count];
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        for (int i = 0; i < count; i++) {
            final int slot = i;
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    check(Thread.currentThread().getContextClassLoader() == loader, "inherited context loader");
                    classes[slot] = Class.forName("hello.runtime.LoaderChecks$Parallel", false, loader);
                } catch (Throwable failure) { error.compareAndSet(null, failure); }
            }, "loader-check-" + i);
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) { thread.join(5000); check(!thread.isAlive(), "parallel loader progress"); }
        if (error.get() != null) throw error.get();
        for (Class<?> cls : classes) check(cls == classes[0] && cls.getClassLoader() == loader, "one class definition under concurrency");
        System.out.println("[hello-runtime] Defining PathClassLoader PASS (boot parent remains host)");
    }
    private static final class Parallel { }
}
