package payload;
public final class OwnContext extends android.content.ContextWrapper {
    public OwnContext() { super(null); }
    @Override public android.content.ComponentName startService(android.content.Intent intent) {
        return new android.content.ComponentName("fixture", "override");
    }
}
