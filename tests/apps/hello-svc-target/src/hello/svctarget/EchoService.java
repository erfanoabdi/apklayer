package hello.svctarget;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/** A second service, so one connection can be bound to two (scenario "filters" of hello-svc-caller). */
public class EchoService extends Service {
    @Override public IBinder onBind(Intent intent) { return new Binder(); }
}
