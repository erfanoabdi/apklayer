package androidx.window.extensions.embedding;

/** Base for activity-embedding rules. Mac.apk does not embed activities, so a
 *  rule is inert (held but never applied). */
public abstract class EmbeddingRule {
    private final String mTag;
    protected EmbeddingRule(String tag) { mTag = tag; }
    public String getTag() { return mTag; }
}
