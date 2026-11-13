package payload;

/** Bundled duplicate without the parent APK's override: its metadata must not win. */
public final class ParentContext extends android.content.ContextWrapper {
    public ParentContext() { super(null); }
}
