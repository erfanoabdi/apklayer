// android.app.admin.DeviceAdminService -- the Service a device/profile owner
// declares so the system keeps its process bound and alive (API 26).
//   AOSP: frameworks/base/core/java/android/app/admin/DeviceAdminService.java
//         (android14-release). onBind() is final and returns the binder of an
//         IDeviceAdminService stub, and IDeviceAdminService.aidl declares NO
//         methods ("So far, we have no methods in this class." -- AOSP). So the
//         faithful binder is simply a Binder that answers the
//         "android.app.admin.IDeviceAdminService" descriptor, which is what
//         this returns; there is no transaction it could be asked to serve.
// Nothing on Mac.apk binds it (there is no DevicePolicyManagerService); an app
// that binds its own gets the same binder a device would hand back.
// Census + method: docs/fwgap-new-platform-classes_2026-09-26.md.
package android.app.admin;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public class DeviceAdminService extends Service {
    /** IDeviceAdminService's AIDL descriptor (android.app.admin.IDeviceAdminService). */
    private static final String DESCRIPTOR = "android.app.admin.IDeviceAdminService";

    private final IDeviceAdminServiceImpl mImpl;

    public DeviceAdminService() {
        mImpl = new IDeviceAdminServiceImpl();
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mImpl.asBinder();
    }

    /** The empty IDeviceAdminService.Stub: a Binder registered under the descriptor. */
    private static final class IDeviceAdminServiceImpl extends Binder implements IInterface {
        IDeviceAdminServiceImpl() {
            attachInterface(this, DESCRIPTOR);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
