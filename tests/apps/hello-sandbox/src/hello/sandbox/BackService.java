package hello.sandbox;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.util.Arrays;

/** The app's own service the isolated one binds back to (IsolatedService): it answers who called, as PackageManager names that uid. */
public final class BackService extends Service {
    @Override public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                int uid = Binder.getCallingUid();
                reply.writeString("uid=" + uid + " packages=" + Arrays.toString(getPackageManager().getPackagesForUid(uid)) + " name=" + getPackageManager().getNameForUid(uid));
                return true;
            }
        };
    }
}
