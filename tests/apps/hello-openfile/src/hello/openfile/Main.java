package hello.openfile;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** What OpenVPN for Android does with an .ovpn: a VIEW with a Uri, its type and display name, the content read through the resolver; printed for tests/vm/open-files.py. */
public class Main extends Activity {
    private TextView view;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        view = new TextView(this);
        view.setTextSize(20);
        setContentView(view);
        show("onCreate", getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        show("onNewIntent", intent);
    }

    private void show(String where, Intent i) {
        String text = where + " action=" + i.getAction();
        Uri uri = i.getData();
        if (uri != null) {
            String name = null;
            long size = -1;
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) { name = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)); size = c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE)); }
            }
            String content;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                in.transferTo(out);
                content = out.toString("UTF-8").replace('\n', '|');
            } catch (Exception e) { content = "ERROR " + e; }
            text += " uri=" + uri + " type=" + i.getType() + " resolvedType=" + getContentResolver().getType(uri) + " name=" + name + " size=" + size
                    + " grant=" + ((i.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) + " content=" + content;
        }
        System.out.println("[hello-openfile] " + text);
        view.setText(text);
    }
}
