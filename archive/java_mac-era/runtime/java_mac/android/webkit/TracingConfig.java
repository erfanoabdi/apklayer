package android.webkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// android.webkit.TracingConfig (AOSP API-28).
// Configures WebView devtools tracing. Self-contained value object built via the
// nested Builder. No engine consumes it yet. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/TracingConfig.java
public class TracingConfig {

    public static final int CATEGORIES_NONE = 0;
    public static final int CATEGORIES_ALL = 1 << 0;
    public static final int CATEGORIES_ANDROID_WEBVIEW = 1 << 1;
    public static final int CATEGORIES_WEB_DEVELOPER = 1 << 2;
    public static final int CATEGORIES_INPUT_LATENCY = 1 << 3;
    public static final int CATEGORIES_RENDERING = 1 << 4;
    public static final int CATEGORIES_JAVASCRIPT_AND_RENDERING = 1 << 5;
    public static final int CATEGORIES_FRAME_VIEWER = 1 << 6;

    public static final int RECORD_UNTIL_FULL = 0;
    public static final int RECORD_CONTINUOUSLY = 1;

    private final int predefinedCategories;
    private final List<String> customIncludedCategories;
    private final int tracingMode;

    private TracingConfig(int predefinedCategories, List<String> customIncludedCategories,
                          int tracingMode) {
        this.predefinedCategories = predefinedCategories;
        this.customIncludedCategories = customIncludedCategories;
        this.tracingMode = tracingMode;
    }

    public int getPredefinedCategories() { return predefinedCategories; }

    public List<String> getCustomIncludedCategories() { return customIncludedCategories; }

    public int getTracingMode() { return tracingMode; }

    public static class Builder {
        private int mPredefinedCategories = CATEGORIES_NONE;
        private final List<String> mCustomIncludedCategories = new ArrayList<>();
        private int mTracingMode = RECORD_CONTINUOUSLY;

        public Builder() {}

        public TracingConfig build() {
            return new TracingConfig(mPredefinedCategories, mCustomIncludedCategories, mTracingMode);
        }

        public Builder addCategories(int... categories) {
            for (int category : categories) {
                mPredefinedCategories |= category;
            }
            return this;
        }

        public Builder addCategories(String... categories) {
            mCustomIncludedCategories.addAll(Arrays.asList(categories));
            return this;
        }

        public Builder addCategories(java.util.Collection<String> categories) {
            mCustomIncludedCategories.addAll(categories);
            return this;
        }

        public Builder setTracingMode(int tracingMode) {
            mTracingMode = tracingMode;
            return this;
        }
    }
}
