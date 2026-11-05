package org.robolectric.shadows;

import java.util.function.BiConsumer;

/**
 * apklayer patch: whoever serves the location providers learns when a provider's first request comes and its last goes
 * (ShadowLocationManager, patches/robolectric 0018): the instance hub's GNSS HAL client runs only while some app has a gps request
 * (apklayer.host.HostLocation). The listener is called with the provider's lock held: it must not block.
 */
public final class LocationRequests {
  private LocationRequests() {}

  /** {provider, whether it now has a request}. */
  public static volatile BiConsumer<String, Boolean> listener;

  static void changed(String provider, boolean active) {
    BiConsumer<String, Boolean> l = listener;
    if (l != null) l.accept(provider, active);
  }
}
