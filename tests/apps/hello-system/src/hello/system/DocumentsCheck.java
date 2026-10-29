package hello.system;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

/** Storage access: the external storage documents provider lists its roots and the children of the primary root; ACTION_OPEN_DOCUMENT has an activity to answer it. */
final class DocumentsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        String authority = "com.android.externalstorage.documents";
        StringBuilder roots = new StringBuilder();
        try (Cursor c = context.getContentResolver().query(DocumentsContract.buildRootsUri(authority), null, null, null, null)) {
            Check.require(c != null && c.getCount() > 0, "no roots");
            while (c.moveToNext()) roots.append(c.getString(c.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_TITLE))).append(';');
        }
        Uri children = DocumentsContract.buildChildDocumentsUri(authority, "primary:");
        int count;
        try (Cursor c = context.getContentResolver().query(children, new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            Check.require(c != null, "no children cursor");
            count = c.getCount();
        }
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        Check.require(context.getPackageManager().resolveActivity(open, 0) != null, "nothing answers ACTION_OPEN_DOCUMENT");
        return "roots " + roots + " primary children " + count;
    }
}
