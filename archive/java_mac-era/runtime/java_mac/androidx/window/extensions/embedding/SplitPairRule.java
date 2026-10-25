package androidx.window.extensions.embedding;

import androidx.window.extensions.core.util.function.Predicate;
public final class SplitPairRule extends SplitRule {
    private final SplitAttributes mDefaultSplitAttributes;
    private SplitPairRule(String tag, SplitAttributes defaultSplitAttributes) { super(tag); mDefaultSplitAttributes = defaultSplitAttributes; }
    public SplitAttributes getDefaultSplitAttributes() { return mDefaultSplitAttributes; }
    public static final class Builder {
        private String mTag;
        private SplitAttributes mDefault = new SplitAttributes.Builder().build();
        public Builder(Predicate<android.util.Pair<android.app.Activity, android.app.Activity>> activitiesPairPredicate,
                       Predicate<android.util.Pair<android.app.Activity, android.content.Intent>> activityIntentPairPredicate,
                       Predicate<android.view.WindowMetrics> parentWindowMetricsPredicate) { }
        public Builder(java.util.function.Predicate<android.util.Pair<android.app.Activity, android.app.Activity>> a,
                       java.util.function.Predicate<android.util.Pair<android.app.Activity, android.content.Intent>> b,
                       java.util.function.Predicate<android.view.WindowMetrics> c) { }
        public Builder setDefaultSplitAttributes(SplitAttributes a) { mDefault = a; return this; }
        public Builder setFinishPrimaryWithSecondary(int f) { return this; }
        public Builder setFinishSecondaryWithPrimary(int f) { return this; }
        public Builder setShouldFinishPrimaryWithSecondary(boolean b) { return this; }
        public Builder setShouldFinishSecondaryWithPrimary(boolean b) { return this; }
        public Builder setLayoutDirection(int d) { return this; }
        public Builder setShouldClearTop(boolean b) { return this; }
        public Builder setSplitRatio(float r) { return this; }
        public Builder setTag(String tag) { mTag = tag; return this; }
        public SplitPairRule build() { return new SplitPairRule(mTag, mDefault); }
    }
}
