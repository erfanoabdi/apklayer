package hello.cpdenied;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;

/** Talks to the providers of hello.cpprovider (another app: lazily started, real Binder). The scenario comes from -Dhello.cp=... */
public class Main extends Activity {
    static final Uri ITEMS = Uri.parse("content://hello.cpprovider.items/items");
    static final Uri OPEN = Uri.parse("content://hello.cpprovider.open/x");
    static final Uri PRIVATE = Uri.parse("content://hello.cpprovider.private/x");

    private static void require(boolean yes, String detail) { if (!yes) throw new AssertionError(detail); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("cp client");
        setContentView(view);
        new Thread(() -> {
            try { run(System.getProperty("hello.cp", "basic")); }
            catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[cpc] FAIL " + failure); }
        }, "cp-client").start();
    }

    private static String names(ContentResolver resolver, Uri uri) {
        StringBuilder out = new StringBuilder();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            require(cursor != null, "query returned null for " + uri);
            while (cursor.moveToNext()) out.append(cursor.getString(cursor.getColumnCount() - 1)).append(',');
        }
        return out.toString();
    }

    private void run(String scenario) throws Exception {
        ContentResolver resolver = getContentResolver();
        System.out.println("[cpc] start pid=" + Process.myPid() + " scenario=" + scenario);
        switch (scenario) {
            case "basic" -> {
                // the provider's app is not running: the hub starts it
                require("open,".equals(names(resolver, OPEN)), "open provider rows");
                require("alpha,beta,".equals(names(resolver, ITEMS)), "items rows " + names(resolver, ITEMS));
                ContentValues values = new ContentValues();
                values.put("name", "gamma");
                Uri inserted = resolver.insert(ITEMS, values);
                require(inserted != null && inserted.toString().endsWith("/3"), "insert " + inserted);
                require("alpha,beta,gamma,".equals(names(resolver, ITEMS)), "after insert " + names(resolver, ITEMS));
                values.put("name", "n");
                require(resolver.update(ITEMS, values, null, null) == 3, "update count");
                require("n0,n1,n2,".equals(names(resolver, ITEMS)), "after update " + names(resolver, ITEMS));
                require(resolver.delete(ITEMS, null, null) == 3, "delete count");
                require("".equals(names(resolver, ITEMS)), "after delete");
                require("vnd.android.cursor.dir/vnd.hello.item".equals(resolver.getType(ITEMS)), "getType");
                Bundle extras = new Bundle();
                extras.putString("note", "hello");
                Bundle out = resolver.call(ITEMS, "ping", "arg", extras);
                require(out != null && "ping:arg:hello".equals(out.getString("echo")), "call echo");
                require(out.getInt("pid") != Process.myPid(), "the provider is another process");
                require(out.getInt("callerPid") == Process.myPid(), "the provider sees this process as the caller: " + out.getInt("callerPid"));
                try { resolver.query(PRIVATE, null, null, null, null); require(false, "a private provider answered"); }
                catch (SecurityException expected) { System.out.println("[cpc] private provider refused: " + expected.getMessage()); }
                System.out.println("[cpc] provider pid=" + out.getInt("pid"));
                System.out.println("[cpc] PASS basic: query/insert/update/delete/getType/call, identities, private provider refused");
            }
            case "denied" -> {
                require("open,".equals(names(resolver, OPEN)), "open provider is open to everyone");
                try { names(resolver, ITEMS); require(false, "read without the permission was allowed"); }
                catch (SecurityException expected) { System.out.println("[cpc] read refused: " + expected.getMessage()); }
                ContentValues values = new ContentValues();
                values.put("name", "x");
                try { resolver.insert(ITEMS, values); require(false, "write without the permission was allowed"); }
                catch (SecurityException expected) { System.out.println("[cpc] write refused: " + expected.getMessage()); }
                System.out.println("[cpc] PASS denied: read and write permissions enforced by the provider");
            }
            case "grant" -> {
                try { names(resolver, ITEMS); require(false, "read without the permission was allowed"); } catch (SecurityException expected) { }
                resolver.call(ITEMS, "grant", getPackageName(), null);
                require("alpha,beta,".equals(names(resolver, ITEMS)) || names(resolver, ITEMS).startsWith("alpha"), "read after grant");
                ContentValues values = new ContentValues();
                values.put("name", "x");
                try { resolver.insert(ITEMS, values); require(false, "a read grant allowed a write"); } catch (SecurityException expected) { }
                resolver.call(ITEMS, "revoke", null, null);
                try { names(resolver, ITEMS); require(false, "read after revoke was allowed"); } catch (SecurityException expected) { }
                System.out.println("[cpc] PASS grant: grantUriPermission by the provider's app opens read (not write), revoke closes it");
            }
            case "death" -> {
                Bundle first = resolver.call(ITEMS, "ping", "a", null);
                int pid = first.getInt("pid");
                System.out.println("[cpc] provider pid=" + pid);
                System.out.println("[cpc] KILL " + pid);
                Thread.sleep(2000);  // the driver kills the provider's process now
                Bundle second = resolver.call(ITEMS, "ping", "b", null);
                require(second.getInt("pid") != pid, "the provider was not restarted");
                require("alpha,beta,".equals(names(resolver, ITEMS)), "restarted provider rows");
                System.out.println("[cpc] PASS death: provider restarted by the hub, new pid=" + second.getInt("pid"));
            }
            default -> System.out.println("[cpc] unknown scenario");
        }
    }
}
