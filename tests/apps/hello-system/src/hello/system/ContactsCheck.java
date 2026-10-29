package hello.system;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import java.util.ArrayList;

/** ContactsProvider: a contact with a name and a number goes in as a batch, is found again through the phone lookup and the data table, and is removed. */
final class ContactsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI).withValue(RawContacts.ACCOUNT_TYPE, null).withValue(RawContacts.ACCOUNT_NAME, null).build());
        ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME, "Apklayer Check").build());
        ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, "+15551230000").withValue(Phone.TYPE, Phone.TYPE_MOBILE).build());
        Uri raw = resolver.applyBatch(ContactsContract.AUTHORITY, ops)[0].uri;
        Check.require(raw != null, "no raw contact uri");
        long rawId = ContentUris.parseId(raw);
        String found;
        try (Cursor c = resolver.query(Phone.CONTENT_URI, new String[] {Phone.DISPLAY_NAME, Phone.NUMBER}, Phone.NUMBER + "=?", new String[] {"+15551230000"}, null)) {
            Check.require(c != null && c.moveToFirst(), "the number is not in the phone table");
            found = c.getString(0) + " " + c.getString(1);
        }
        Uri lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode("+15551230000"));
        try (Cursor c = resolver.query(lookup, new String[] {ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            Check.require(c != null && c.moveToFirst() && "Apklayer Check".equals(c.getString(0)), "phone lookup does not find the contact");
        }
        Uri syncRaw = RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
        int deleted = resolver.delete(syncRaw, RawContacts._ID + "=?", new String[] {String.valueOf(rawId)});
        Check.require(deleted == 1, "delete removed " + deleted);
        return found;
    }
}
