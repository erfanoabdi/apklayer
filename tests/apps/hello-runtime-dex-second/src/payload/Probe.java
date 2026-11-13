package payload;
public final class Probe {
    public static String run() {
        if (!"Dalvik".equals(System.getProperties().getProperty("java.vm.name"))) throw new AssertionError("dynamic properties");
        if (!"16".equals(System.getProperty("hello.dynamic.value"))) throw new AssertionError("app property bag not shared");
        return apklayer.relocated.java.fixture.Token.value();
    }
}
