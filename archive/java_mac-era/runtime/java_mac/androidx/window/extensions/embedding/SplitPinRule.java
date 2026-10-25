package androidx.window.extensions.embedding;

import androidx.window.extensions.core.util.function.Predicate;
public final class SplitPinRule extends SplitRule {
    private SplitPinRule(String tag) { super(tag); }
    public static final class Builder {
        private String mTag;
        public Builder(SplitAttributes defaultSplitAttributes,
                       Predicate<android.view.WindowMetrics> parentWindowMetricsPredicate) { }
        public Builder setSticky(boolean sticky) { return this; }
        public Builder setTag(String tag) { mTag = tag; return this; }
        public SplitPinRule build() { return new SplitPinRule(mTag); }
    }
}
