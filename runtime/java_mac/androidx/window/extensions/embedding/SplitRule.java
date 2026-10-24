package androidx.window.extensions.embedding;

public abstract class SplitRule extends EmbeddingRule {
    public static final int FINISH_NEVER = 0;
    public static final int FINISH_ALWAYS = 1;
    public static final int FINISH_ADJACENT = 2;
    protected SplitRule(String tag) { super(tag); }
}
