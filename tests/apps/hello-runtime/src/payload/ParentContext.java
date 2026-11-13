package payload;

/** The parent APK's real class wins over the deliberately different embedded DEX copy. */
public final class ParentContext extends android.content.ContextWrapper {
    public ParentContext() { super(null); }
    @Override public android.content.ComponentName startService(android.content.Intent intent) {
        return new android.content.ComponentName("fixture", "parent");
    }
}
