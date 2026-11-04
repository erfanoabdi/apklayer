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

/** The tree's own providers, reached through the hub like on a device: settings, contacts, user dictionary. -Dhello.sys=settings|rawsettings|androidid|contacts|dictionary */
public final class Main extends Activity {
    private static void require(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // dangerous permissions are asked for at run time, as on a device (the desktop grants what is asked)
        requestPermissions(new String[] {"android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS", "android.permission.READ_USER_DICTIONARY",
                "android.permission.WRITE_USER_DICTIONARY"}, 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        new Thread(() -> {
            String what = System.getProperty("hello.sys", "settings");
            try {
                ContentResolver cr = getContentResolver();
                switch (what) {
                    case "settings" -> {
                        // as an app without system privileges: a public System key written (WRITE_SETTINGS), Global and Secure read only
                        require(Settings.System.canWrite(this), "WRITE_SETTINGS not granted");
                        int timeout = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 60000);
                        require(Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, timeout + 1000), "Settings.System put refused");
                        require(Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, -1) == timeout + 1000, "Settings.System round trip");
                        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, timeout);
                        require(Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, -1) == 0, "Settings.Global airplane_mode_on");
                        require(Settings.Secure.getString(cr, Settings.Secure.ANDROID_ID) != null, "Settings.Secure.ANDROID_ID");
                    }
                    case "androidid" -> {  // sysprov.sh compares two launches with `apklayer stop` between, right after this write (a pending save at stop)
                        int timeout = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 60000), next = timeout >= 600000 ? 60000 : timeout + 1000;
                        System.out.println("[sys] read android_id " + Settings.Secure.getString(cr, Settings.Secure.ANDROID_ID) + " screen_off_timeout " + timeout);
                        require(Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, next), "Settings.System put refused");
                        System.out.println("[sys] wrote screen_off_timeout " + next);
                    }
                    case "rawsettings" -> {
                        Bundle extras = new Bundle();
                        extras.putString("value", "7");
                        Bundle put = cr.call(Uri.parse("content://settings"), "PUT_system", "apklayer_raw", extras);
                        System.out.println("[sys] raw put -> " + put);
                        Bundle got = cr.call(Uri.parse("content://settings"), "GET_system", "apklayer_raw", null);
                        System.out.println("[sys] raw get -> " + got);
                        require(got != null && "7".equals(got.getString("value")), "raw settings call");
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
                        ContentValues word = new ContentValues();
                        word.put(UserDictionary.Words.WORD, "apklayerword");
                        word.put(UserDictionary.Words.FREQUENCY, 250);
                        word.put(UserDictionary.Words.LOCALE, "en_US");
                        word.put(UserDictionary.Words.APP_ID, 0);
                        Uri inserted = cr.insert(UserDictionary.Words.CONTENT_URI, word);
                        System.out.println("[sys] word inserted at " + inserted);
                        // as on Android 11+: only the enabled input methods and spell checkers may use it, so this app's insert is refused and its query empty
                        require(inserted == null, "an app that is not an input method inserted a word");
                        try (Cursor c = cr.query(UserDictionary.Words.CONTENT_URI, new String[] {UserDictionary.Words.WORD}, null, null, null)) {
                            System.out.println("[sys] words: " + (c == null ? -1 : c.getCount()));
                            require(c != null && c.getCount() == 0, "words visible to an app that is not an input method");
                        }
                    }
                    default -> throw new AssertionError("unknown scenario " + what);
                }
                System.out.println("[sys] PASS " + what);
            } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[sys] FAIL " + what + ": " + failure); }
        }).start();
    }
}
