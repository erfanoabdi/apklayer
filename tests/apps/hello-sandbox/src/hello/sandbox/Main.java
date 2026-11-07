package hello.sandbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import java.io.File;
import java.nio.file.Files;

/**
 * What the per-app sandbox lets this app see (tests/vm/sandbox.sh): another app's data dir (-Dhello.other), a file of the user's home (-Dhello.home), its own
 * files, then a file picked in the document picker and one beside it that was not picked (-Dhello.notpicked).
 */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        log("uid app=" + Process.myUid() + " os=" + osUid());
        log("intent " + getIntent().getAction() + " extra=" + getIntent().getStringExtra("hello.extra"));  // the launch's intent file, when it had one
        log("groups " + status("Groups:"));
        log("other " + probe(System.getProperty("hello.other")));
        log("home " + probe(System.getProperty("hello.home")));
        log("keystore " + probe("/data/misc/keystore/persistent.sqlite"));  // keystore2's database: the system's, not in an app's /data
        log("webview " + probe("/data/local/tmp/webview-command-line"));  // what Android's /data does show an app
        try {
            Files.writeString(new File(getFilesDir(), "own.txt").toPath(), "mine");
            log("own " + probe(new File(getFilesDir(), "own.txt").getPath()));
        } catch (Exception e) { log("own failed " + e); }
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7);
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        log("picked " + (data == null || data.getData() == null ? "none" : probe(data.getData().getPath())));
        log("notpicked " + probe(System.getProperty("hello.notpicked")));
        log("DONE");
    }

    private static String probe(String path) {
        if (path == null) return "unset";
        File f = new File(path);
        if (f.isDirectory()) { String[] names = f.list(); return names == null ? "denied" : "listed " + names.length; }
        try { return "read " + Files.readString(f.toPath()).trim(); }
        catch (Exception e) { return "denied " + e.getClass().getSimpleName(); }
    }

    private static String osUid() { return status("Uid:").split("\\s+")[1]; }

    private static String status(String key) {
        try {
            for (String line : Files.readAllLines(new File("/proc/self/status").toPath())) if (line.startsWith(key)) return line.substring(key.length()).trim();
        } catch (Exception e) { return "? ?"; }
        return "? ?";
    }

    private static void log(String line) { System.out.println("[sandbox] " + line); }
}
