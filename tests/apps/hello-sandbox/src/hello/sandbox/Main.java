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
        log("gpu " + openable(System.getProperty("hello.gpu")));  // a GPU node the app's uid may not open: through apklayerd's broker
        log("private " + probe(System.getProperty("hello.private")));  // with home-access: a 0600 file in a 0700 folder of the home, through the broker
        log("privdir " + probe(System.getProperty("hello.privdir")));
        String privdir = System.getProperty("hello.privdir");  // the broker's other operations in that 0700 folder (with home-access)
        if (privdir != null) {
            File made = new File(privdir, "made"), w = new File(privdir, "w.txt"), r = new File(privdir, "r.txt");
            boolean wrote;
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(w)) { out.write(1); wrote = true; } catch (Exception e) { wrote = false; }
            log("change mkdir=" + made.mkdir() + " write=" + wrote + " rename=" + w.renameTo(r) + " delete=" + r.delete() + " rmdir=" + made.delete()
                + " access=" + new File(privdir, "p.txt").canRead());
            String more;  // chmod, symlink and link too
            try {
                java.nio.file.Path p = new File(privdir, "p.txt").toPath(), sym = new File(privdir, "sym").toPath(), hard = new File(privdir, "hard").toPath();
                Files.setPosixFilePermissions(p, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                Files.createSymbolicLink(sym, p); Files.createLink(hard, p);
                more = "chmod=ok symlink=" + Files.isSymbolicLink(sym) + " link=" + Files.exists(hard);
                Files.delete(sym); Files.delete(hard);
            } catch (Exception e) { more = "failed " + e; }
            log("change2 " + more);
        }
        try {
            Files.writeString(new File(System.getProperty("hello.write", "/nonexistent/x")).toPath(), "written");
            log("write ok");
        } catch (Exception e) { log("write denied " + e.getClass().getSimpleName()); }
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

    private static String openable(String path) {
        if (path == null || path.isEmpty()) return "none";
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(path, "rw")) { return "opened"; }
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
