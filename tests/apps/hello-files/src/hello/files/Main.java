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
