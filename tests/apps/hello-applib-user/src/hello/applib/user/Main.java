package hello.applib.user;

import android.app.Activity;
import android.os.Bundle;

/** Calls the code of the libraries other installed apps provide, as an app built against them does (by reflection here: built without them). */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Object fromStatic = Class.forName("hello.applib.Static").getMethod("greet").invoke(null);
            Object fromSystem = Class.forName("hello.applib.system.Shared").getMethod("greet").invoke(null);
            StringBuilder infos = new StringBuilder();  // ApplicationInfo.sharedLibraryInfos (hidden, the framework's LoadedApk reads it): name:type, in order
            Object list = android.content.pm.ApplicationInfo.class.getField("sharedLibraryInfos").get(getApplicationInfo());
            if (list != null) for (Object info : (java.util.List<?>) list)
                infos.append(((android.content.pm.SharedLibraryInfo) info).getName()).append(':').append(((android.content.pm.SharedLibraryInfo) info).getType()).append(' ');
            System.out.println("[sharedlib] hello.applib.user PASS static=" + fromStatic + " system=" + fromSystem + " infos=" + infos.toString().trim());
        } catch (Throwable failure) {
            System.out.println("[sharedlib] hello.applib.user FAIL " + failure);
            failure.printStackTrace(System.out);
        }
        finish();
    }
}
