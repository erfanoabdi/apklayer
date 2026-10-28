package hello.sysprov;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.UserDictionary;

/** The tree's own providers, reached through the hub like on a device: settings, contacts, user dictionary. -Dhello.sys=settings|contacts|dictionary */
public final class Main extends Activity {
    private static void require(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            String what = System.getProperty("hello.sys", "settings");
            try {
                ContentResolver cr = getContentResolver();
                switch (what) {
                    case "settings" -> {
                        Settings.System.putInt(cr, "apklayer_test", 42);
                        require(Settings.System.getInt(cr, "apklayer_test", -1) == 42, "Settings.System round trip");
                        Settings.Global.putString(cr, "apklayer_global", "yes");
                        require("yes".equals(Settings.Global.getString(cr, "apklayer_global")), "Settings.Global round trip");
                        require(Settings.Secure.getString(cr, Settings.Secure.ANDROID_ID) != null, "Settings.Secure.ANDROID_ID");
                    }
                    case "contacts" -> {
                        ContentValues raw = new ContentValues();
                        Uri rawUri = cr.insert(ContactsContract.RawContacts.CONTENT_URI, raw);
                        require(rawUri != null, "raw contact insert");
                        ContentValues name = new ContentValues();
                        name.put(ContactsContract.Data.RAW_CONTACT_ID, android.content.ContentUris.parseId(rawUri));
                        name.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                        name.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Ada Lovelace");
                        require(cr.insert(ContactsContract.Data.CONTENT_URI, name) != null, "name insert");
                        try (Cursor c = cr.query(ContactsContract.Contacts.CONTENT_URI, new String[] {ContactsContract.Contacts.DISPLAY_NAME}, null, null, null)) {
                            boolean found = false;
                            while (c != null && c.moveToNext()) found |= "Ada Lovelace".equals(c.getString(0));
                            require(found, "contact found");
                        }
                    }
                    case "dictionary" -> {
                        UserDictionary.Words.addWord(this, "apklayerword", 250, null, null);
                        try (Cursor c = cr.query(UserDictionary.Words.CONTENT_URI, new String[] {UserDictionary.Words.WORD}, null, null, null)) {
                            boolean found = false;
                            while (c != null && c.moveToNext()) found |= "apklayerword".equals(c.getString(0));
                            require(found, "word found");
                        }
                    }
                    default -> throw new AssertionError("unknown scenario " + what);
                }
                System.out.println("[sys] PASS " + what);
            } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[sys] FAIL " + what + ": " + failure); }
        }).start();
    }
}
