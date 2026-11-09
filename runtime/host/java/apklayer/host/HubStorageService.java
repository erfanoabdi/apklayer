package apklayer.host;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.storage.StorageVolume;
import android.service.storage.ExternalStorageService;
import android.service.storage.IExternalStorageService;

/**
 * StorageSessionController.notifyVolumeStateChanged: a volume's new state told to MediaProvider's ExternalStorageService
 * (ExternalStorageServiceImpl.onVolumeStateChanged: a mounted volume is attached and scanned, REASON_MOUNTED; an unmounted or removed one detached).
 * The system keeps that service bound on a device; here it is bound for the one call and let go when it answered (no process stays for it).
 */
final class HubStorageService {
    private static final HubDirectory.Peer SYSTEM = new HubDirectory.Peer(-1, 1000, "android", null, null, true, 1000, null, "system");

    private HubStorageService() {}

    static void notify(HubDirectory directory, StorageVolume volume) {
        android.content.pm.ProviderInfo media = directory.packages().provider(android.provider.MediaStore.AUTHORITY);
        if (media == null) return;
        HubServiceBindings bindings = directory.bindings();
        IServiceConnection[] self = new IServiceConnection[1];
        self[0] = new IServiceConnection.Stub() {
            private boolean called;
            @Override public void connected(ComponentName name, IBinder service, android.app.IBinderSession session, boolean dead) {
                synchronized (this) { if (service == null || called) return; called = true; }
                try {
                    IExternalStorageService.Stub.asInterface(service).notifyVolumeStateChanged(volume.getId(), volume,
                            new RemoteCallback(result -> bindings.unbind(SYSTEM, self[0].asBinder())));
                } catch (RemoteException gone) { bindings.unbind(SYSTEM, self[0].asBinder()); }
            }
        };
        Intent intent = new Intent(ExternalStorageService.SERVICE_INTERFACE).setPackage(media.packageName);
        if (!bindings.bind(SYSTEM, intent, self[0], Context.BIND_AUTO_CREATE | Context.BIND_NOT_VISIBLE, null))
            System.out.println("[apklayer-hub] volumes: no ExternalStorageService in " + media.packageName);
    }
}
