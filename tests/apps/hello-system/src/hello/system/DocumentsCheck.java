package hello.system;

import android.content.Context;
import android.content.Intent;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import java.util.List;

/** Storage access as an app gets it: the document pickers (open, open tree, create) have an activity to answer, storage volumes are listed, document uris are told apart. */
final class DocumentsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        for (String action : new String[] {Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT_TREE, Intent.ACTION_CREATE_DOCUMENT}) {
            Intent intent = new Intent(action).setType("*/*");
            if (!action.equals(Intent.ACTION_OPEN_DOCUMENT_TREE)) intent.addCategory(Intent.CATEGORY_OPENABLE);
            Check.require(context.getPackageManager().resolveActivity(intent, 0) != null, "nothing answers " + action);
        }
        List<StorageVolume> volumes = context.getSystemService(StorageManager.class).getStorageVolumes();
        Check.require(!volumes.isEmpty() && volumes.get(0).isPrimary(), "volumes " + volumes);
        Check.require(DocumentsContract.isDocumentUri(context, DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")), "document uri not recognised");
        return volumes.size() + " volumes, primary '" + volumes.get(0).getDescription(context) + "'";
    }
}
