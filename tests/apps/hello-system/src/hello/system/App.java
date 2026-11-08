package hello.system;

import android.app.Application;
import android.os.Build;

/** Build as an app's static initialiser reads it: before Application.onCreate (and attachBaseContext), in every process of the app. */
public class App extends Application {
    static final String STATIC_FINGERPRINT = Build.FINGERPRINT, STATIC_TAGS = Build.TAGS, STATIC_TYPE = Build.TYPE, STATIC_MODEL = Build.MODEL;
    static volatile Boolean postSawActivity;  // LaunchOrderCheck: whether Main.onCreate had run when this process's onCreate post ran

    @Override public void onCreate() {
        super.onCreate();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> postSawActivity = Main.created);
    }
}
