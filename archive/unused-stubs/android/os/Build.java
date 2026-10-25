package android.os;
// Outer-classpath placeholder so reflection (conscrypt etc.) can read SDK_INT without
// touching the instrumented android-all copy. The sandbox uses the real Build.
public final class Build {
    public static final class VERSION {
        public static final int SDK_INT = 27;
        public static final String RELEASE = "8.1.0";
        public static final String CODENAME = "OMR1";
        private VERSION() {}
    }
    public static final String MODEL = "apklayer";
    public static final String MANUFACTURER = "apklayer";
    private Build() {}
}
