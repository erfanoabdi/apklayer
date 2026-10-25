package dalvik.system;

import java.lang.reflect.Array;

// apklayer patch: minimal VMRuntime for ArrayUtils.newUnpaddedArray et al.
public final class VMRuntime {
    private static final VMRuntime THE_ONE = new VMRuntime();
    public static VMRuntime getRuntime() { return THE_ONE; }
    public Object newUnpaddedArray(Class<?> clazz, int minLen) { return Array.newInstance(clazz, minLen); }
    public Object newNonMovableArray(Class<?> clazz, int len) { return Array.newInstance(clazz, len); }
    public boolean is64Bit() { return true; }
    public static boolean is64BitInstructionSet(String instructionSet) { return true; }
    public void notifyStartup() {}
    public void notifyNativeBrk() {}
    public void gc() { System.gc(); }
    public long heapSize() { return Runtime.getRuntime().totalMemory(); }
    public long heapAllocated() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }
    public void preFork() {}
    public void postFork(String isZygote) {}
    public void preZygoteFork() {}
    public void postZygoteFork() {}
}
