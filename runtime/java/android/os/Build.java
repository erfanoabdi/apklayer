package android.os;

// apklayer patch: minimal Build stand-in (upstream Build.java pulls in a large graph).
// Only constants referenced by our current seed set are provided.
public class Build {
    public static final String MODEL = "apklayer";
    public static final String MANUFACTURER = "apklayer";
    public static final String PRODUCT = "apklayer";
    public static final String BRAND = "apklayer";
    public static final String DEVICE = "apklayer";
    public static final String TYPE = "user";
    public static final String ID = "apklayer";
    public static final boolean DEBUG = false;
    public static final boolean IS_DEBUGGABLE = false;
    public static final boolean IS_USER_BUILD = true;

    public static class VERSION {
        public static final int SDK_INT = 36;
        public static final int PREVIEW_SDK_INT = 0;
        public static final String RELEASE = "16";
        public static final String INCREMENTAL = "apklayer";
        public static final String CODENAME = "REL";
        public static final int MAJOR_VERSION = 16;
        public static final int MINOR_VERSION = 0;
    }

    public static class VERSION_CODES {
        public static final int KITKAT = 19;
        public static final int LOLLIPOP = 21;
        public static final int M = 23;
        public static final int N = 24;
        public static final int O = 26;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int TIRAMISU = 33;
        public static final int UPSIDE_DOWN_CAKE = 34;
        public static final int VANS = 35;
        public static final int VANILLA_ICE_CREAM = 36;
    }
}
