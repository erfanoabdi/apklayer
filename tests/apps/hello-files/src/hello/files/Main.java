package hello.files;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** What DocumentsUI sees of the storage provider: its roots, the root directory and its children, listed in the log (the app is a privileged test package, MANAGE_DOCUMENTS). */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try {
                ContentResolver cr = getContentResolver();
                if (System.getProperty("hello.owndata") != null) {  // Android's hard-coded spellings of the app's data dir (java.io and libcore's Os), read back through getFilesDir()
                    String pkg = getPackageName();
                    getFilesDir().mkdirs();
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream("/data/data/" + pkg + "/files/java")) { out.write("java".getBytes()); }
                    java.io.FileDescriptor fd = android.system.Os.open("/data/user/0/" + pkg + "/files/os", android.system.OsConstants.O_CREAT | android.system.OsConstants.O_WRONLY, 0600);
                    android.system.Os.write(fd, "os".getBytes(), 0, 2);
                    android.system.Os.close(fd);
                    String viaJava = new String(java.nio.file.Files.readAllBytes(new java.io.File(getFilesDir(), "java").toPath())), os = new String(java.nio.file.Files.readAllBytes(new java.io.File(getFilesDir(), "os").toPath()));
                    boolean other = new java.io.File("/data/data/hello.reader").exists() || new java.io.File("/data/user/0/hello.reader").exists();
                    System.out.println("[files] owndata java=" + viaJava + " os=" + os + " nio=" + java.nio.file.Files.isDirectory(java.nio.file.Path.of("/data/user/0/" + pkg + "/files")) + " other=" + other + " filesDir=" + getFilesDir());
                    System.out.println("[files] DONE"); finish(); return;
                }
                if (System.getProperty("hello.libcore") != null) { LibCore.print(); finish(); return; }
                if (System.getProperty("hello.scoped") != null) {  // scoped storage: another package's Android/data and Android/obb are not reachable (the runner made hello.scoped.other's files), the app's own are
                    getExternalFilesDir(null).mkdirs();
                    java.io.File mine = new java.io.File(getExternalFilesDir(null), "mine");
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(mine)) { out.write("mine".getBytes()); }
                    String other = System.getProperty("hello.scoped.other");
                    boolean own = new String(java.nio.file.Files.readAllBytes(mine.toPath())).equals("mine");
                    boolean dataHidden = true, obbHidden = true;
                    for (String root : new String[] {"/storage/emulated/0", "/sdcard", "/storage/self/primary", "/data/media/0", "/mnt/user/0/primary", "/mnt/runtime/default/emulated/0", "/mnt/runtime/write/emulated/0", "/storage/emulated/0/./x/.."}) {
                        dataHidden &= !new java.io.File(root + "/Android/data/" + other + "/files/secret").exists() && !new java.io.File(root + "/Android/data/" + other).exists();
                        obbHidden &= !new java.io.File(root + "/Android/obb/" + other + "/secret").exists();
                        try { new java.io.FileInputStream(root + "/Android/data/" + other + "/files/secret").close(); dataHidden = false; } catch (java.io.IOException denied) { /* expected */ }
                    }
                    java.io.File link = new java.io.File(getFilesDir(), "lnk");  // a symlink the app makes itself to another package's directory or a parent of it: Android resolves it and answers as for the real path
                    boolean linkHidden = true;
                    for (String target : new String[] {"/storage/emulated/0/Android/data/" + other, "/storage/emulated/0/Android/data", "/storage/emulated/0/Android", "/proc/self/root/sdcard/Android/data/" + other}) {
                        link.delete();
                        android.system.Os.symlink(target, link.getPath());
                        String tail = target.endsWith(other) ? "/files/secret" : target.endsWith("data") ? "/" + other + "/files/secret" : target.endsWith("Android") ? "/data/" + other + "/files/secret" : "/files/secret";
                        linkHidden &= !new java.io.File(link.getPath() + tail).exists();
                        try { new java.io.FileInputStream(link.getPath() + tail).close(); linkHidden = false; } catch (java.io.IOException denied) { /* expected */ }
                        try { android.system.Os.stat(link.getPath() + tail); linkHidden = false; } catch (android.system.ErrnoException denied) { /* expected */ }
                    }
                    link.delete();
                    System.out.println("[files] scoped link=" + linkHidden);
                    System.out.println("[files] scoped own=" + own + " dataHidden=" + dataHidden + " obbHidden=" + obbHidden);
                    System.out.println("[files] DONE"); finish(); return;
                }
                if (System.getProperty("hello.volumes") != null) { Volumes.print(this); finish(); return; }
                if (System.getProperty("hello.hotplug") != null) { Hotplug.start(this); return; }
                if (System.getProperty("hello.uri") != null) {
                    try (Cursor any = cr.query(Uri.parse(System.getProperty("hello.uri")), null, null, null, null)) { System.out.println("[files] " + System.getProperty("hello.uri") + " -> " + (any == null ? "null" : any.getCount() + " rows, " + any.getClass().getName())); }
                    System.out.println("[files] DONE"); finish(); return;
                }
                String authority = System.getProperty("hello.authority", "com.android.externalstorage.documents");
                try (Cursor roots = cr.query(DocumentsContract.buildRootsUri(authority), null, null, null, null)) {
                    System.out.println("[files] roots: " + roots.getCount());
                    while (roots.moveToNext()) {
                        System.out.println("[files] root " + roots.getString(roots.getColumnIndex("root_id")) + " title=" + roots.getString(roots.getColumnIndex("title"))
                                + " document=" + roots.getString(roots.getColumnIndex("document_id")) + " flags=" + roots.getInt(roots.getColumnIndex("flags")));
                    }
                }
                String doc = System.getProperty("hello.doc", "linux-home:");
                try (Cursor self = cr.query(DocumentsContract.buildDocumentUri(authority, doc), null, null, null, null)) {
                    System.out.println("[files] document " + doc + " rows=" + self.getCount());
                }
                Uri children = DocumentsContract.buildChildDocumentsUri(authority, doc);
                try (Cursor list = cr.query(children, null, null, null, null)) {
                    System.out.println("[files] children of " + doc + ": " + list.getCount());
                    while (list.moveToNext()) {
                        System.out.println("[files] entry " + list.getString(list.getColumnIndex("_display_name")) + " " + list.getString(list.getColumnIndex("mime_type")));
                    }
                }
                String file = System.getProperty("hello.read");
                if (file != null) {
                    Uri uri = DocumentsContract.buildDocumentUri(authority, file);
                    try (java.io.InputStream in = cr.openInputStream(uri)) { byte[] head = in.readNBytes(60); System.out.println("[files] read " + file + ": " + new String(head).replace('\n', ' ')); }
                }
                String view = System.getProperty("hello.view");
                if (view != null) {  // what DocumentsUI does to open a file: ACTION_VIEW with the uri and a read grant
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(DocumentsContract.buildDocumentUri(authority, view), "text/x-apklayer").setClassName("hello.reader", "hello.reader.Main")
                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION));
                    Thread.sleep(15000);
                }
                System.out.println("[files] DONE");
            } catch (Throwable failure) {
                System.out.println("[files] FAIL " + failure);
                failure.printStackTrace(System.out);
            }
            finish();
        }).start();
    }
}
