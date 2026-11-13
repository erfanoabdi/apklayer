package hello.runtime;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.function.Function;

public final class Main extends Activity {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static void equal(Object expected, Object actual, String label) {
        check(java.util.Objects.equals(expected, actual), label + ": expected " + expected + ", got " + actual);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        setContentView(text);
        try {
            properties();
            ArtRuntimeChecks.run();
            LoaderChecks.run(this);
            text.setText("Runtime identity PASS");
            System.out.println("[hello-runtime] Runtime identity PASS");
        } catch (Throwable failure) {
            failure.printStackTrace();
            text.setText(failure.toString());
            System.out.println("[hello-runtime] Runtime identity FAIL " + failure);
        }
    }
    private static void properties() throws Throwable {
        if (Boolean.getBoolean("hello.runtime.flag")) {
            check(Boolean.getBoolean("java.net.preferIPv4Stack"), "application java.net flag preserved");
            equal("yes", System.getProperty("javax.runtime.identity"), "application javax flag preserved");
            equal("yes", System.getProperty("user.runtime.identity"), "application user flag preserved");
        }
        equal("Dalvik", System.getProperty("java.vm.name"), "VM name");
        equal("2.1.0", System.getProperty("java.vm.version"), "VM version");
        equal("0", System.getProperty("java.version"), "Java version");
        equal("0.9", System.getProperty("java.runtime.version"), "Runtime version");
        equal("Android Runtime", System.getProperty("java.runtime.name"), "Runtime name");
        equal("/apex/com.android.art", System.getProperty("java.home"), "Java home");
        equal("Dalvik Core Library", System.getProperty("java.specification.name"), "Core library");
        equal(null, System.getProperty("java.vm.info"), "No HotSpot VM info");
        equal(null, System.getProperty("jdk.debug"), "No JDK debug");
        Properties p = System.getProperties();
        check(p.stringPropertyNames().contains("java.vm.version"), "fixed defaults enumerate");
        for (String key : p.stringPropertyNames()) {
            check(!key.startsWith("apklayer.") && !key.startsWith("robolectric.")
                    && !key.startsWith("jdk.") && !key.startsWith("sun."), "backend key " + key);
            System.out.println("[hello-runtime] property " + key + "=" + p.getProperty(key));
        }
        equal("Dalvik", System.setProperty("java.vm.name", "HotSpot"), "fixed write returns prior");
        equal(null, System.clearProperty("java.vm.name"), "fixed removal returns null");
        equal(null, p.compute("java.vm.name", (key, value) -> { throw new AssertionError("fixed compute called"); }), "fixed compute");
        equal("Dalvik", p.computeIfAbsent("java.vm.name", key -> { throw new AssertionError("fixed absent called"); }), "fixed absent");
        equal("Dalvik", p.computeIfPresent("java.vm.name", (key, value) -> "wrong"), "fixed present");
        equal("Dalvik", p.merge("java.vm.name", "wrong", (a, b) -> "wrong"), "fixed merge");
        equal("Dalvik", p.replace("java.vm.name", "wrong"), "fixed replace");
        equal(false, p.replace("java.vm.name", "Dalvik", "wrong"), "fixed conditional replace");
        p.putAll(java.util.Collections.singletonMap("java.vm.name", "wrong"));
        p.clear();
        equal("Dalvik", p.getProperty("java.vm.name"), "clear retains default");
        equal(null, System.setProperty("test.mutable", "0x10"), "custom set");
        equal(Integer.valueOf(16), Integer.getInteger("test.mutable"), "integer helper");
        equal(Long.valueOf(16), Long.getLong("test.mutable", -1L), "long helper");
        System.setProperty("test.mutable", "true");
        check(Boolean.getBoolean("test.mutable"), "boolean helper");
        equal("true", System.clearProperty("test.mutable"), "custom clear");
        equal(Integer.valueOf(7), Integer.getInteger("", 7), "integer invalid name");
        equal(Long.valueOf(7), Long.getLong(null, Long.valueOf(7)), "long null name");
        check(!Boolean.getBoolean(null), "boolean null name");
        Method get = System.class.getMethod("getProperty", String.class);
        equal("2.1.0", get.invoke(null, "java.vm.version"), "reflection");
        try { get.invoke(null, ""); throw new AssertionError("reflection missing exception"); }
        catch (InvocationTargetException expected) { check(expected.getCause() instanceof IllegalArgumentException, "reflection cause"); }
        MethodHandle handle = MethodHandles.lookup().findStatic(System.class, "getProperty", MethodType.methodType(String.class, String.class));
        equal("2.1.0", (String) handle.invokeExact("java.vm.version"), "findStatic handle");
        handle = MethodHandles.lookup().unreflect(get);
        equal("2.1.0", (String) handle.invokeExact("java.vm.version"), "unreflect handle");
        Function<String, String> reference = System::getProperty;
        equal("2.1.0", reference.apply("java.vm.version"), "method reference");
        try { System.getProperty(null); throw new AssertionError("null key accepted"); }
        catch (NullPointerException expected) { }
        try { System.getProperty(""); throw new AssertionError("empty key accepted"); }
        catch (IllegalArgumentException expected) { }
        Properties replacement = new Properties();
        replacement.setProperty("java.vm.name", "wrong");
        replacement.setProperty("test.copied", "yes");
        System.setProperties(replacement);
        replacement.setProperty("test.copied", "no");
        equal("yes", System.getProperty("test.copied"), "setProperties copies argument");
        equal("Dalvik", System.getProperty("java.vm.name"), "replacement preserves fixed");
        System.setProperties(null);
        equal(null, System.getProperty("test.copied"), "null properties resets custom");
        equal("Dalvik", System.getProperty("java.vm.name"), "null preserves fixed");
        equal("", System.getProperty("user.home"), "Android home default");
        System.out.println("[hello-runtime] Java properties PASS");
    }
}
