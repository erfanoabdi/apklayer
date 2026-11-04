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
        log("other " + probe(System.getProperty("hello.other")));
        log("home " + probe(System.getProperty("hello.home")));
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

    private static String osUid() {
        try {
            for (String line : Files.readAllLines(new File("/proc/self/status").toPath())) if (line.startsWith("Uid:")) return line.split("\\s+")[2];
        } catch (Exception e) { return "?"; }
        return "?";
    }

    private static void log(String line) { System.out.println("[sandbox] " + line); }
}
