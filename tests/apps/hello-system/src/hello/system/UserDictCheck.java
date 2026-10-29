package hello.system;

import android.content.Context;
import android.database.Cursor;
import android.provider.UserDictionary;
import java.util.Locale;

/** UserDictionary: a word added through UserDictionary.Words.addWord is found again with its frequency and shortcut. */
final class UserDictCheck implements Check {
    @Override public String run(Context context) throws Exception {
        UserDictionary.Words.addWord(context, "apklayerword", 250, "apkw", Locale.US);
        try (Cursor c = context.getContentResolver().query(UserDictionary.Words.CONTENT_URI, new String[] {UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY, UserDictionary.Words.SHORTCUT},
                UserDictionary.Words.WORD + "=?", new String[] {"apklayerword"}, null)) {
            Check.require(c != null && c.moveToFirst(), "the word is not in the dictionary");
            return c.getString(0) + " " + c.getInt(1) + " " + c.getString(2);
        } finally {
            context.getContentResolver().delete(UserDictionary.Words.CONTENT_URI, UserDictionary.Words.WORD + "=?", new String[] {"apklayerword"});
        }
    }
}
