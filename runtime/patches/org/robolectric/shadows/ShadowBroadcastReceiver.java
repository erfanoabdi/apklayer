package org.robolectric.shadows;

import static org.robolectric.util.reflector.Reflector.reflector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.atomic.AtomicBoolean;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;

/**
 * Robolectric's BroadcastReceiver stand-in (abortBroadcast only sets a flag it reads itself). apklayer patch: with the instance hub (-Dapklayer.instancehub=true)
 * abortBroadcast is Android's own, so the abort reaches the PendingResult and with it the hub's ordered queue.
 */
@Implements(BroadcastReceiver.class)
public class ShadowBroadcastReceiver {
  @RealObject BroadcastReceiver receiver;

  private AtomicBoolean abort = new AtomicBoolean(false);
  private boolean wentAsync = false;
  private BroadcastReceiver.PendingResult originalPendingResult;

  @Implementation
  protected void abortBroadcast() {
    ReflectionHelpers.callInstanceMethod(receiver, Shadow.directMethodName("android.content.BroadcastReceiver", "abortBroadcast"));
  }

  @Implementation
  protected void onReceive(Context context, Intent intent) {
    if (abort == null || !abort.get()) {
      receiver.onReceive(context, intent);
    }
  }

  public void onReceive(Context context, Intent intent, AtomicBoolean abort) {
    this.abort = abort;
    onReceive(context, intent);
    if (receiver.getPendingResult() != null) {
      receiver.getPendingResult().finish();
    }
  }

  @Implementation
  protected BroadcastReceiver.PendingResult goAsync() {
    originalPendingResult = receiver.getPendingResult();
    wentAsync = true;
    return reflector(BroadcastReceiverReflector.class, receiver).goAsync();
  }

  public boolean wentAsync() {
    return wentAsync;
  }

  public boolean isBroadcastAborted() {
    return abort.get();
  }

  public BroadcastReceiver.PendingResult getOriginalPendingResult() {
    return wentAsync ? originalPendingResult : receiver.getPendingResult();
  }

  @ForType(BroadcastReceiver.class)
  interface BroadcastReceiverReflector {
    @Direct
    BroadcastReceiver.PendingResult goAsync();
  }
}
