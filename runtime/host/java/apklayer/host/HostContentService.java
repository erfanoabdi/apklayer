package apklayer.host;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.IContentService;

/**
 * The "content" service (system_server's ContentService) for the code that calls it directly instead of through ContentResolver: ContactsProvider asks
 * getIsSyncable of every account it sees (ContentResolver.getContentService() was null, so a visible account killed its worker and every later call waited
 * for it). The sync settings are Robolectric's (ShadowContentResolver's static store); everything else answers the AIDL defaults.
 */
final class HostContentService extends IContentService.Default {
    static void install() {
        org.robolectric.shadows.ShadowServiceManager.addBinderService(ContentResolver.CONTENT_SERVICE_NAME, IContentService.class, new HostContentService());
    }

    @Override public int getIsSyncable(Account account, String authority) { return ContentResolver.getIsSyncable(account, authority); }
    @Override public int getIsSyncableAsUser(Account account, String authority, int userId) { return ContentResolver.getIsSyncable(account, authority); }
    @Override public void setIsSyncable(Account account, String authority, int syncable) { ContentResolver.setIsSyncable(account, authority, syncable); }
    @Override public boolean getSyncAutomatically(Account account, String authority) { return ContentResolver.getSyncAutomatically(account, authority); }
    @Override public boolean getMasterSyncAutomatically() { return ContentResolver.getMasterSyncAutomatically(); }
}
