package androidx.window.extensions.embedding;

import java.util.Collections;
import java.util.List;
public final class ActivityStack {
    public static final class Token {
        private final android.os.Bundle mBundle;
        public Token(android.os.Bundle bundle) { mBundle = bundle != null ? bundle : new android.os.Bundle(); }
        public android.os.Bundle toBundle() { return mBundle; }
        public static Token createFromBinder(android.os.IBinder binder) { return new Token(new android.os.Bundle()); }
        public static Token readFromBundle(android.os.Bundle bundle) { return new Token(bundle); }
    }
    private final Token mToken = new Token(new android.os.Bundle());
    ActivityStack() {}
    public List<android.app.Activity> getActivities() { return Collections.emptyList(); }
    public boolean isEmpty() { return true; }
    public String getTag() { return null; }
    public Token getActivityStackToken() { return mToken; }
}
