package hello.filedump;

import android.app.Activity;
import android.os.Bundle;

/** What the guest (arm64 code) sees at the paths in files/list.txt: files/out.txt (stat, readlink, dir names, content hash), files/dump/<n> (content <= 256 KB). */
public class Main extends Activity {
    private static native void run(String list, String out, String dump);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("hello");
        String d = getFilesDir().getPath();
        run(d + "/list.txt", d + "/out.txt", d + "/dump");
        System.out.println("[filedump] DONE");
        finish();
    }
}
