package android.webkit;

import android.content.Context;

// android.webkit.WebViewDatabase (AOSP API-1).
// Stores form data and HTTP-auth credentials for the web engine. Abstract in
// AOSP with a Context factory. No engine yet, so everything is empty/no-op but
// the type and methods resolve. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebViewDatabase.java
public abstract class WebViewDatabase {

    public static WebViewDatabase getInstance(Context context) {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        static final WebViewDatabase INSTANCE = new EmptyDatabase();
    }

    @Deprecated public abstract boolean hasUsernamePassword();
    @Deprecated public abstract void clearUsernamePassword();
    public abstract boolean hasHttpAuthUsernamePassword();
    public abstract void clearHttpAuthUsernamePassword();
    public abstract void setHttpAuthUsernamePassword(String host, String realm,
                                                     String username, String password);
    public abstract String[] getHttpAuthUsernamePassword(String host, String realm);
    @Deprecated public abstract boolean hasFormData();
    @Deprecated public abstract void clearFormData();

    private static final class EmptyDatabase extends WebViewDatabase {
        @Override public boolean hasUsernamePassword() { return false; }
        @Override public void clearUsernamePassword() {}
        @Override public boolean hasHttpAuthUsernamePassword() { return false; }
        @Override public void clearHttpAuthUsernamePassword() {}
        @Override public void setHttpAuthUsernamePassword(String host, String realm,
                                                          String username, String password) {}
        @Override public String[] getHttpAuthUsernamePassword(String host, String realm) { return null; }
        @Override public boolean hasFormData() { return false; }
        @Override public void clearFormData() {}
    }
}
