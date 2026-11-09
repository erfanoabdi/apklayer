package hello.actprocess;

import android.app.Application;

/** Created in every process of the app, as on Android: Remote checks its own process got one. */
public class App extends Application {
    static String createdIn;

    @Override public void onCreate() {
        super.onCreate();
        createdIn = getProcessName();
    }
}
