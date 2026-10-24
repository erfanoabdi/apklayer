package androidx.window.extensions.embedding;

import androidx.window.extensions.core.util.function.Predicate;
public final class ActivityRule extends EmbeddingRule {
    private final boolean mShouldAlwaysExpand;
    private ActivityRule(String tag, boolean shouldAlwaysExpand) { super(tag); mShouldAlwaysExpand = shouldAlwaysExpand; }
    public boolean shouldAlwaysExpand() { return mShouldAlwaysExpand; }
    public static final class Builder {
        private String mTag;
        private boolean mShouldAlwaysExpand;
        public Builder(Predicate<android.app.Activity> activityPredicate,
                       Predicate<android.content.Intent> intentPredicate) { }
        public Builder(java.util.function.Predicate<android.app.Activity> activityPredicate,
                       java.util.function.Predicate<android.content.Intent> intentPredicate) { }
        public Builder setShouldAlwaysExpand(boolean shouldAlwaysExpand) { mShouldAlwaysExpand = shouldAlwaysExpand; return this; }
        public Builder setTag(String tag) { mTag = tag; return this; }
        public ActivityRule build() { return new ActivityRule(mTag, mShouldAlwaysExpand); }
    }
}
