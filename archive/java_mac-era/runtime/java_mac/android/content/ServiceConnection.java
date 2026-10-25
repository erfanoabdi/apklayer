package android.content;
import android.os.IBinder;
public interface ServiceConnection {
    void onServiceConnected(ComponentName name, IBinder service);
    void onServiceDisconnected(ComponentName name);

    /** AOSP core/java/android/content/ServiceConnection.java -- both are
     *  empty `default` methods upstream. onBindingDied fires when the binding
     *  to a service died and will not be re-established without a rebind;
     *  onNullBinding when onBind() returned null. Empty defaults, verbatim:
     *  the point is that an implementer may omit them. */
    default void onBindingDied(ComponentName name) {
    }

    default void onNullBinding(ComponentName name) {
    }
}
