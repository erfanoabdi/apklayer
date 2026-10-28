package org.robolectric.shadows;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Bundle;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;

/**
 * Robolectric's stand-in for BroadcastReceiver.PendingResult: finish() only completes a future. apklayer patch: with the instance hub (-Dapklayer.instancehub=true)
 * the real finish() runs too, so Android's own code tells the (hub-backed) activity manager that the receiver is done and passes the ordered result on.
 */
@Implements(BroadcastReceiver.PendingResult.class)
public final class ShadowBroadcastPendingResult {
  @RealObject BroadcastReceiver.PendingResult pendingResult;

  private final SettableFuture<BroadcastReceiver.PendingResult> finished = SettableFuture.create();

  static BroadcastReceiver.PendingResult create(
      int resultCode, String resultData, Bundle resultExtras, boolean ordered) {
    try {
      return new BroadcastReceiver.PendingResult(
          resultCode, resultData, resultExtras, 0 /* TYPE_REGISTERED */, ordered, false, null, 0, 0);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static BroadcastReceiver.PendingResult createSticky(Intent intent) {
    try {
      return new BroadcastReceiver.PendingResult(
          0, intent.getDataString(), intent.getExtras(), 0 /* TYPE_REGISTERED */, false, true, null, 0, 0);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Implementation
  protected void finish() {
    org.robolectric.util.ReflectionHelpers.callInstanceMethod(
        pendingResult, Shadow.directMethodName("android.content.BroadcastReceiver$PendingResult", "finish"));
    finished.set(pendingResult);
  }

  public ListenableFuture<BroadcastReceiver.PendingResult> getFuture() {
    return finished;
  }
}
