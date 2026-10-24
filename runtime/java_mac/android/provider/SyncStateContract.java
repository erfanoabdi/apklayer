package android.provider;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Pair;

/**
 * The contract a sync adapter uses to stash its per-account sync state inside
 * another provider.
 *
 * Every helper below is CLIENT-SIDE: it builds a selection or a
 * ContentProviderOperation and hands it to the provider the caller passed in.
 * Nothing here needs a system service, so it is faithful; whether the target
 * provider exists is the caller's business, and the same null/absent answers
 * AOSP produces come back when it does not.
 *
 * Source: frameworks/base/core/java/android/provider/SyncStateContract.java
 */
public class SyncStateContract {

    /** AOSP declares a public no-arg constructor on this contract class. */
    public SyncStateContract() { }

    public interface Columns extends BaseColumns {
        public static final String ACCOUNT_NAME = "account_name";
        public static final String ACCOUNT_TYPE = "account_type";
        public static final String DATA = "data";
    }

    public static class Constants implements Columns {
        /** AOSP declares a public no-arg constructor. */
        public Constants() { }
        public static final String CONTENT_DIRECTORY = "syncstate";
    }

    public static final class Helpers {

        /** AOSP declares a public no-arg constructor. */
        public Helpers() { }

        private static final String[] DATA_PROJECTION = new String[] { Columns.DATA, Columns._ID };
        private static final String SELECT_BY_ACCOUNT =
                Columns.ACCOUNT_NAME + "=? AND " + Columns.ACCOUNT_TYPE + "=?";

        public static byte[] get(ContentProviderClient provider, Uri uri, Account account)
                throws RemoteException {
            Cursor c = provider.query(uri, DATA_PROJECTION, SELECT_BY_ACCOUNT,
                    new String[] { account.name, account.type }, null);
            if (c == null) return null;
            try {
                if (c.moveToNext()) return c.getBlob(c.getColumnIndexOrThrow(Columns.DATA));
            } finally {
                c.close();
            }
            return null;
        }

        public static Pair<Uri, byte[]> getWithUri(ContentProviderClient provider, Uri uri,
                Account account) throws RemoteException {
            Cursor c = provider.query(uri, DATA_PROJECTION, SELECT_BY_ACCOUNT,
                    new String[] { account.name, account.type }, null);
            if (c == null) return null;
            try {
                if (c.moveToNext()) {
                    long rowId = c.getLong(c.getColumnIndexOrThrow(Columns._ID));
                    byte[] blob = c.getBlob(c.getColumnIndexOrThrow(Columns.DATA));
                    return Pair.create(android.content.ContentUris.withAppendedId(uri, rowId),
                            blob);
                }
            } finally {
                c.close();
            }
            return null;
        }

        public static void set(ContentProviderClient provider, Uri uri, Account account,
                byte[] data) throws RemoteException {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            values.put(Columns.ACCOUNT_NAME, account.name);
            values.put(Columns.ACCOUNT_TYPE, account.type);
            provider.insert(uri, values);
        }

        public static Uri insert(ContentProviderClient provider, Uri uri, Account account,
                byte[] data) throws RemoteException {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            values.put(Columns.ACCOUNT_NAME, account.name);
            values.put(Columns.ACCOUNT_TYPE, account.type);
            return provider.insert(uri, values);
        }

        public static void update(ContentProviderClient provider, Uri uri, byte[] data)
                throws RemoteException {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            provider.update(uri, values, null, null);
        }

        public static ContentProviderOperation newSetOperation(Uri uri, Account account,
                byte[] data) {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            values.put(Columns.ACCOUNT_NAME, account.name);
            values.put(Columns.ACCOUNT_TYPE, account.type);
            return ContentProviderOperation.newInsert(uri).withValues(values).build();
        }

        public static ContentProviderOperation newUpdateOperation(Uri uri, byte[] data) {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            return ContentProviderOperation.newUpdate(uri).withValues(values).build();
        }
    }
}
