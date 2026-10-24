package androidx.window.extensions.embedding;

import androidx.window.extensions.core.util.function.Predicate;
public final class SplitPlaceholderRule extends SplitRule {
    private final SplitAttributes mDefaultSplitAttributes;
    private SplitPlaceholderRule(String tag, SplitAttributes d) { super(tag); mDefaultSplitAttributes = d; }
    public SplitAttributes getDefaultSplitAttributes() { return mDefaultSplitAttributes; }
    public static final class Builder {
        private String mTag;
        private SplitAttributes mDefault = new SplitAttributes.Builder().build();
        public Builder(android.content.Intent placeholderIntent,
                       Predicate<android.app.Activity> activityPredicate,
                       Predicate<android.content.Intent> intentPredicate,
                       Predicate<android.view.WindowMetrics> parentWindowMetricsPredicate) { }
        public Builder(android.content.Intent placeholderIntent,
                       java.util.function.Predicate<android.app.Activity> a,
                       java.util.function.Predicate<android.content.Intent> b,
                       java.util.function.Predicate<android.view.WindowMetrics> c) { }
        public Builder setDefaultSplitAttributes(SplitAttributes a) { mDefault = a; return this; }
        public Builder setFinishPrimaryWithPlaceholder(int f) { return this; }
        public Builder setFinishPrimaryWithSecondary(int f) { return this; }
        public Builder setLayoutDirection(int d) { return this; }
        public Builder setSplitRatio(float r) { return this; }
        public Builder setSticky(boolean s) { return this; }
        public Builder setTag(String tag) { mTag = tag; return this; }
        public SplitPlaceholderRule build() { return new SplitPlaceholderRule(mTag, mDefault); }
    }
}
