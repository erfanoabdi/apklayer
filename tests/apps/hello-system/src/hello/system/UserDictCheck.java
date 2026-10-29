package hello.system;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary;

/**
 * UserDictionary: the provider answers. Since Android 11 only the enabled input methods and spell checkers may use it, so an ordinary app's insert is refused (null) and its
 * query is empty; if the insert is accepted the word is found again with its frequency.
 */
final class UserDictCheck implements Check {
    @Override public String run(Context context) throws Exception {
        ContentValues values = new ContentValues();
        values.put(UserDictionary.Words.WORD, "apklayerword");
        values.put(UserDictionary.Words.FREQUENCY, 250);
        Uri inserted = context.getContentResolver().insert(UserDictionary.Words.CONTENT_URI, values);
        try (Cursor c = context.getContentResolver().query(UserDictionary.Words.CONTENT_URI, new String[] {UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY},
                UserDictionary.Words.WORD + "=?", new String[] {"apklayerword"}, null)) {
            Check.require(c != null, "the query gave no cursor");
            if (inserted == null) {
                Check.require(c.getCount() == 0, "the insert was refused but the word is there");
                return "insert refused as for an app that is not an input method, query empty";
            }
            Check.require(c.moveToFirst(), "the insert gave " + inserted + " but the word is not there");
            String found = c.getString(0) + " " + c.getInt(1);
            context.getContentResolver().delete(UserDictionary.Words.CONTENT_URI, UserDictionary.Words.WORD + "=?", new String[] {"apklayerword"});
            return found;
        }
    }
}
