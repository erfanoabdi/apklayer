package hello.system;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class NullAuthenticator extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
}
