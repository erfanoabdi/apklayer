package payload;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class Probe {
    private static void eq(Object want, Object got) {
        if (!java.util.Objects.equals(want, got)) throw new AssertionError(want + " != " + got);
    }
    public static String run() throws Throwable {
        eq("Dalvik", System.getProperty("java.vm.name"));
        eq("2.1.0", System.class.getMethod("getProperty", String.class).invoke(null, "java.vm.version"));
        MethodHandle get = MethodHandles.lookup().findStatic(System.class, "getProperty",
                MethodType.methodType(String.class, String.class));
        eq("0.9", (String) get.invokeExact("java.runtime.version"));
        java.util.function.Function<String, String> reference = System::getProperty;
        eq("0", reference.apply("java.version"));
        System.setProperty("hello.dynamic.value", "16");
        eq(Integer.valueOf(16), Integer.getInteger("hello.dynamic.value"));
        eq(null, System.getProperty("apklayer.native"));
        eq("override", new OwnContext().startService(null).getClassName());
        eq("parent", new ParentContext().startService(null).getClassName());
        System.loadLibrary("log");
        Runtime.getRuntime().loadLibrary("log");
        eq("apklayer.relocated.java.fixture.Token", java.fixture.Token.class.getName());
        return java.fixture.Token.value();
    }
}
