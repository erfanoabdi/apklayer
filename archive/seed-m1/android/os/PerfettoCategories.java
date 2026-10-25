package android.os;

// apklayer patch: generated Perfetto categories stand-in; always disabled.
public final class PerfettoCategories {
    public static final class Category {
        public boolean isEnabled() { return false; }
    }
    public static void registerCategories() {}
    public static void reset() {}
    public static final Category MQ_CATEGORY = new Category();
    public static final Category TRACE_CATEGORY = new Category();
}
