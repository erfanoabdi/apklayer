// android.location.LocationManager -- mirrors AOSP's public surface.
//
// The built-in providers (gps, network, fused) are backed by macOS Location
// Services through com.macapk.loader.LocationBridge (CoreLocation; rung 1 of
// the decision ladder). This class plays the part AOSP splits between
// LocationManagerService and LocationProviderManager
// (services/core/java/com/android/server/location/, android-14.0.0_r1): the
// enabled state, permission enforcement, per-registration interval and
// displacement filtering, maxUpdates / duration, historical delivery, and
// getCurrentLocation's 30 s age and timeout rules. Line references below are
// to those two files.
//
// HOW THE MAC MAPS ONTO ANDROID
//   * Location Services on/off (System Settings) is the location master
//     switch: isLocationEnabled and every built-in provider's enabled state.
//   * macOS's per-app authorization (the TCC prompt) is the runtime permission
//     ACCESS_FINE/COARSE_LOCATION: MacApkPackageManager.checkPermission reads
//     it, and Activity.requestPermissions shows the macOS prompt
//     (SpecialAccessHandler). "Approximate location only" is COARSE without
//     FINE. A pre-M app is granted at install, as on a device; if macOS then
//     refuses it, its fixes are withheld, the way AOSP's app-op denial
//     withholds a legacy app's fixes without revoking the permission.
//   * gps, network and fused are three names for the ONE CoreLocation stream,
//     which is the Mac's whole location stack (Wi-Fi positioning, plus GNSS
//     when the host has a receiver). Each fix carries CoreLocation's real
//     accuracy, so an app sees a 35 m Wi-Fi fix as exactly that. The gps
//     name is kept because a device without it makes requestLocationUpdates
//     ("gps") throw IllegalArgumentException (LocationManagerService:765),
//     and old apps ask for nothing else; ProviderProperties says honestly that
//     no satellite receiver stands behind it, and the GNSS status callbacks
//     below stay silent.
//
// MACAPK_LOCATION_HOST=0 unbinds the host (every built-in provider disabled,
// no prompt ever), the pre-bridge surface. MACAPK_LOCATION_ENFORCE=0 turns the
// SecurityException for a caller without either location permission into a
// silent "provider disabled" answer.
package android.location;

import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.location.provider.ProviderProperties;
import android.os.Looper;
import android.os.SystemClock;
import com.macapk.loader.LocationBridge;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocationManager {
    public static final String GPS_PROVIDER = "gps";
    public static final String NETWORK_PROVIDER = "network";
    public static final String PASSIVE_PROVIDER = "passive";
    public static final String FUSED_PROVIDER = "fused";

    public static final String KEY_LOCATION_CHANGED = "location";
    public static final String KEY_LOCATIONS = "locations";
    public static final String KEY_PROVIDER_ENABLED = "providerEnabled";
    public static final String KEY_PROXIMITY_ENTERING = "entering";
    public static final String KEY_FLUSH_COMPLETE = "flushComplete";
    public static final String KEY_STATUS_CHANGED = "status";
    public static final String EXTRA_PROVIDER_NAME = "android.location.extra.PROVIDER_NAME";
    public static final String EXTRA_PROVIDER_ENABLED = "android.location.extra.PROVIDER_ENABLED";
    public static final String EXTRA_LOCATION_ENABLED = "android.location.extra.LOCATION_ENABLED";

    public static final String PROVIDERS_CHANGED_ACTION = "android.location.PROVIDERS_CHANGED";
    public static final String MODE_CHANGED_ACTION = "android.location.MODE_CHANGED";

    public LocationManager() {}

    // === Test providers =====================================================
    // A "test provider" is a provider whose fixes the APP ITSELF supplies
    // (LocationManager.addTestProvider + setTestProviderLocation). Nothing
    // about it needs hardware, so unlike GPS this is a capability Mac.apk can
    // honour honestly and completely: every coordinate that comes out of it was
    // put in by the caller, and AOSP's contract is that such a Location is
    // identifiable via Location.isMock(), which we set. A test provider added
    // under a built-in name REPLACES that provider, as on a device, so the
    // host stream stops reaching that name until it is removed.
    //
    // ONE deliberate divergence, stated rather than hidden: a real device
    // throws SecurityException unless the caller is the selected "mock location
    // app" in developer settings. Mac.apk has no such setting and runs exactly
    // one app, so the check has no meaning here and the operation is allowed.
    // Refusing instead would break test/mock code for no protective benefit.
    private static final class TestProvider {
        ProviderProperties properties;
        Set<String> extraAttributionTags;
        boolean enabled = true;
        Location location;
    }

    private static final Map<String, TestProvider> sTestProviders = new LinkedHashMap<>();

    /** One requestLocationUpdates / requestSingleUpdate. The mutable fields are
     *  guarded by the registration itself (host fixes arrive on the AppKit
     *  thread, removals come from app threads). */
    private static final class Registration {
        final String provider;
        final LocationListener listener;
        final PendingIntent pendingIntent;
        final Executor executor;
        final LocationRequest request;
        final long expireAtRealtimeMs;
        int delivered;
        Location lastDelivered;
        Registration(String provider, LocationListener listener, PendingIntent pendingIntent,
                Executor executor, LocationRequest request) {
            this.provider = provider;
            this.listener = listener;
            this.pendingIntent = pendingIntent;
            this.executor = executor;
            this.request = request;
            long d = request.getDurationMillis();
            long now = SystemClock.elapsedRealtime();
            this.expireAtRealtimeMs = d >= Long.MAX_VALUE - now ? Long.MAX_VALUE : now + d;
        }
        /** Fed by the host stream: a built-in provider, or passive. */
        boolean host() {
            return PASSIVE_PROVIDER.equals(provider) || isHostProvider(provider);
        }
    }

    private static final CopyOnWriteArrayList<Registration> sRegistrations =
            new CopyOnWriteArrayList<>();

    private static TestProvider testProvider(String provider) {
        synchronized (sTestProviders) {
            return sTestProviders.get(provider);
        }
    }

    private static TestProvider requireTestProvider(String provider) {
        TestProvider tp = testProvider(provider);
        if (tp == null) {
            // AOSP throws IllegalArgumentException for an unknown test provider.
            throw new IllegalArgumentException("provider \"" + provider + "\" is not a test provider");
        }
        return tp;
    }

    public void addTestProvider(String provider, ProviderProperties properties) {
        addTestProvider(provider, properties, Collections.<String>emptySet());
    }

    public void addTestProvider(String provider, ProviderProperties properties,
            Set<String> extraAttributionTags) {
        if (provider == null) throw new IllegalArgumentException("provider == null");
        TestProvider tp = new TestProvider();
        tp.properties = properties;
        tp.extraAttributionTags = new HashSet<>(extraAttributionTags);
        synchronized (sTestProviders) {
            sTestProviders.put(provider, tp);
        }
        updateHostSession();
    }

    /** @deprecated AOSP superseded this by the ProviderProperties form in API 30. */
    @Deprecated
    public void addTestProvider(String provider, boolean requiresNetwork,
            boolean requiresSatellite, boolean requiresCell, boolean hasMonetaryCost,
            boolean supportsAltitude, boolean supportsSpeed, boolean supportsBearing,
            int powerRequirement, int accuracy) {
        // AOSP maps the legacy booleans straight onto ProviderProperties, and maps
        // an out-of-range powerRequirement/accuracy onto the builder's own checks.
        ProviderProperties.Builder b = new ProviderProperties.Builder()
                .setHasNetworkRequirement(requiresNetwork)
                .setHasSatelliteRequirement(requiresSatellite)
                .setHasCellRequirement(requiresCell)
                .setHasMonetaryCost(hasMonetaryCost)
                .setHasAltitudeSupport(supportsAltitude)
                .setHasSpeedSupport(supportsSpeed)
                .setHasBearingSupport(supportsBearing);
        if (powerRequirement >= ProviderProperties.POWER_USAGE_LOW
                && powerRequirement <= ProviderProperties.POWER_USAGE_HIGH) {
            b.setPowerUsage(powerRequirement);
        }
        if (accuracy >= ProviderProperties.ACCURACY_FINE
                && accuracy <= ProviderProperties.ACCURACY_COARSE) {
            b.setAccuracy(accuracy);
        }
        addTestProvider(provider, b.build());
    }

    public void removeTestProvider(String provider) {
        boolean wasEnabled;
        synchronized (sTestProviders) {
            TestProvider tp = sTestProviders.remove(provider);
            wasEnabled = tp != null && tp.enabled;
        }
        if (wasEnabled) {
            dispatchProviderEnabled(provider, false);
        }
        updateHostSession();
    }

    public void setTestProviderLocation(String provider, Location location) {
        if (location == null) throw new IllegalArgumentException("location == null");
        TestProvider tp = requireTestProvider(provider);
        Location copy = new Location(location);
        copy.setProvider(provider);
        // AOSP: a test location is identifiable as a mock to every client.
        copy.setMock(true);
        tp.location = copy;
        dispatchLocation(provider, copy);
    }

    /** @deprecated AOSP deprecated the clear* trio in API 29. */
    @Deprecated
    public void clearTestProviderLocation(String provider) {
        requireTestProvider(provider).location = null;
    }

    public void setTestProviderEnabled(String provider, boolean enabled) {
        TestProvider tp = requireTestProvider(provider);
        if (tp.enabled != enabled) {
            tp.enabled = enabled;
            dispatchProviderEnabled(provider, enabled);
        }
    }

    /** @deprecated see {@link #setTestProviderEnabled}. AOSP restores the default (enabled). */
    @Deprecated
    public void clearTestProviderEnabled(String provider) {
        setTestProviderEnabled(provider, true);
    }

    /**
     * @deprecated Provider status was removed from the platform in API 29; AOSP's
     * implementation of this method is a no-op that only validates the provider.
     */
    @Deprecated
    public void setTestProviderStatus(String provider, int status, android.os.Bundle extras,
            long updateTime) {
        requireTestProvider(provider);
    }

    /** @deprecated see {@link #setTestProviderStatus}. */
    @Deprecated
    public void clearTestProviderStatus(String provider) {
        requireTestProvider(provider);
    }

    /** A test provider's fix: to its own registrations and to passive ones. */
    private static void dispatchLocation(String provider, Location location) {
        for (Registration r : sRegistrations) {
            if (!provider.equals(r.provider) && !PASSIVE_PROVIDER.equals(r.provider)) continue;
            deliver(r, location, location.getProvider());
        }
    }

    private static void dispatchProviderEnabled(String provider, boolean enabled) {
        for (Registration r : sRegistrations) {
            if (!provider.equals(r.provider)) continue;
            if (r.listener != null) {
                r.executor.execute(() -> {
                    try {
                        if (enabled) r.listener.onProviderEnabled(provider);
                        else r.listener.onProviderDisabled(provider);
                    } catch (Throwable ignored) { }
                });
            } else if (r.pendingIntent != null) {
                r.executor.execute(() -> {
                    android.content.Intent i = r.pendingIntent.getIntent();
                    if (i != null) {
                        i.putExtra(EXTRA_PROVIDER_NAME, provider);
                        i.putExtra(EXTRA_PROVIDER_ENABLED, enabled);
                    }
                    try { r.pendingIntent.send(); } catch (PendingIntent.CanceledException e) { }
                });
            }
        }
    }

    // === The host provider (macOS Location Services) ========================

    private static final String[] HOST_PROVIDERS = { GPS_PROVIDER, NETWORK_PROVIDER, FUSED_PROVIDER };

    /** LocationProviderManager:157/160 -- the age at which a last location
     *  stops being "current", and getCurrentLocation's longest wait. */
    private static final long MAX_CURRENT_LOCATION_AGE_MS = 30 * 1000;
    private static final long MAX_GET_CURRENT_LOCATION_TIMEOUT_MS = 30 * 1000;
    /** LocationManager:435 -- requestSingleUpdate's duration. */
    private static final long MAX_SINGLE_LOCATION_TIMEOUT_MS = 30 * 1000;
    /** LocationProviderManager:166/170. */
    private static final long MAX_FASTEST_INTERVAL_JITTER_MS = 30 * 1000;
    private static final float FASTEST_INTERVAL_JITTER_PERCENTAGE = .10f;
    private static final long MIN_REQUEST_DELAY_MS = 30 * 1000;

    private static final boolean ENFORCE =
            !"0".equals(System.getenv("MACAPK_LOCATION_ENFORCE"));

    private static final int PERMISSION_NONE = 0, PERMISSION_COARSE = 1, PERMISSION_FINE = 2;

    private static final Object HOST_LOCK = new Object();
    /** The last fix the host delivered in this process (provider unset). */
    private static Location sHostLast;
    private static boolean sHostRunning;
    private static boolean sHostRunningReduced;
    /** The enabled state last announced to listeners, or null before the first. */
    private static Boolean sHostEnabledAnnounced;

    /** A getCurrentLocation call still waiting for a fresh fix. */
    private static final class CurrentWaiter {
        final String provider;
        final Executor executor;
        final java.util.function.Consumer<Location> consumer;
        final AtomicBoolean done = new AtomicBoolean();
        CurrentWaiter(String provider, Executor executor,
                java.util.function.Consumer<Location> consumer) {
            this.provider = provider;
            this.executor = executor;
            this.consumer = consumer;
        }
    }

    private static final CopyOnWriteArrayList<CurrentWaiter> sWaiters = new CopyOnWriteArrayList<>();

    /** Fires getCurrentLocation timeouts and registration expiry: AOSP's alarms. */
    private static final ScheduledThreadPoolExecutor TIMER;
    static {
        TIMER = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "macapk-location-timer");
            t.setDaemon(true);
            return t;
        });
        TIMER.setRemoveOnCancelPolicy(true);
        LocationBridge.setSink(new LocationBridge.Sink() {
            @Override public void onHostLocation(Location l) { hostLocation(l); }
            @Override public void onHostAuthorization(int status, boolean reduced) {
                hostAuthorization();
            }
        });
    }

    /** A built-in provider fed by the host: bridged, and not replaced by a
     *  test provider of the same name. */
    private static boolean isHostProvider(String provider) {
        if (!GPS_PROVIDER.equals(provider) && !NETWORK_PROVIDER.equals(provider)
                && !FUSED_PROVIDER.equals(provider)) {
            return false;
        }
        return testProvider(provider) == null && LocationBridge.available();
    }

    private static boolean hostEnabled() {
        return LocationBridge.available() && LocationBridge.servicesEnabled();
    }

    /** The caller's location permission level (LocationPermissions.getPermissionLevel). */
    private static int permissionLevel() {
        android.app.Application app = android.app.ActivityThread.currentApplication();
        if (app == null) return PERMISSION_NONE;
        try {
            if (app.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                return PERMISSION_FINE;
            }
            if (app.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                return PERMISSION_COARSE;
            }
        } catch (Throwable ignored) { }
        return PERMISSION_NONE;
    }

    /** LocationPermissions.enforceLocationPermission(uid, level, PERMISSION_COARSE),
     *  message verbatim (LocationPermissions.java:119). Returns whether the
     *  caller may proceed when enforcement is switched off. */
    private static boolean enforceLocationPermission() {
        if (permissionLevel() != PERMISSION_NONE) return true;
        if (ENFORCE) {
            throw new SecurityException("uid " + android.os.Process.myUid() + " does not have "
                    + android.Manifest.permission.ACCESS_COARSE_LOCATION + " or "
                    + android.Manifest.permission.ACCESS_FINE_LOCATION + ".");
        }
        return false;
    }

    /** May a host fix reach this app at all? The master switch is on, the app
     *  holds a location permission, and macOS has not refused it (the app-op). */
    private static boolean hostDeliverable() {
        return hostEnabled() && permissionLevel() != PERMISSION_NONE
                && !LocationBridge.refused();
    }

    /** A host fix, as the named provider reports it. Passive registrations see
     *  the fix under the provider that produced it, which is the fused stack. */
    private static Location named(Location l, String provider) {
        Location c = new Location(l);
        c.setProvider(PASSIVE_PROVIDER.equals(provider) ? FUSED_PROVIDER : provider);
        return c;
    }

    /** The host's newest fix: this process's own, or CoreLocation's cache for
     *  this app, whichever is newer. */
    private static Location hostLast() {
        Location mine;
        synchronized (HOST_LOCK) { mine = sHostLast; }
        if (mine != null || !LocationBridge.authorized()) return mine;
        // Nothing from a session of our own yet. CoreLocation's cache is read
        // through a fresh probe manager, so a getLastKnownLocation polled every
        // frame is held to one read per CACHE_READ_MS.
        long now = SystemClock.elapsedRealtime();
        synchronized (HOST_LOCK) {
            if (sCacheReadAt != 0 && now - sCacheReadAt < CACHE_READ_MS) return sCached;
            sCacheReadAt = now;
        }
        Location cached = null;
        try { cached = LocationBridge.cachedFix(); } catch (Throwable ignored) { }
        synchronized (HOST_LOCK) { sCached = cached; }
        return cached;
    }

    private static final long CACHE_READ_MS = 5000;
    private static long sCacheReadAt;
    private static Location sCached;

    private static void hostLocation(Location l) {
        synchronized (HOST_LOCK) { sHostLast = l; }
        if (!hostDeliverable()) return;
        for (Registration r : sRegistrations) {
            if (r.host()) deliver(r, l, null);
        }
        for (CurrentWaiter w : sWaiters) completeWaiter(w, named(l, w.provider));
    }

    private static void hostAuthorization() {
        boolean enabled = hostEnabled();
        boolean changed;
        synchronized (HOST_LOCK) {
            // The first report is CoreLocation stating the state it started in,
            // not a change; announce only real transitions.
            changed = sHostEnabledAnnounced != null && sHostEnabledAnnounced != enabled;
            sHostEnabledAnnounced = enabled;
        }
        if (changed) {
            for (String p : HOST_PROVIDERS) {
                if (isHostProvider(p)) dispatchProviderEnabled(p, enabled);
            }
            broadcastProvidersChanged(enabled);
        }
        if (!hostDeliverable()) {
            for (CurrentWaiter w : sWaiters) completeWaiter(w, null);
        }
        updateHostSession();
    }

    /** AOSP sends PROVIDERS_CHANGED_ACTION per provider and MODE_CHANGED_ACTION
     *  for the master switch (LocationManagerService.onLocationModeChanged /
     *  LocationProviderManager.onEnabledChanged). Best effort: before the app
     *  exists there is nobody to tell. */
    private static void broadcastProvidersChanged(boolean enabled) {
        android.app.Application app = android.app.ActivityThread.currentApplication();
        if (app == null) return;
        try {
            for (String p : HOST_PROVIDERS) {
                if (!isHostProvider(p)) continue;
                app.sendBroadcast(new android.content.Intent(PROVIDERS_CHANGED_ACTION)
                        .putExtra(EXTRA_PROVIDER_NAME, p)
                        .putExtra(EXTRA_PROVIDER_ENABLED, enabled));
            }
            app.sendBroadcast(new android.content.Intent(MODE_CHANGED_ACTION)
                    .putExtra(EXTRA_LOCATION_ENABLED, enabled));
        } catch (Throwable ignored) { }
    }

    /** Run CoreLocation exactly while something active wants host fixes:
     *  a non-passive host registration or a pending getCurrentLocation. */
    private static void updateHostSession() {
        if (!LocationBridge.available()) return;
        boolean want = false;
        int level = PERMISSION_NONE;
        // Who wants fixes is decided first: asking macOS about authorization
        // starts the Mac.apk Location helper, which an app with nothing
        // registered (a removeUpdates, a test provider) must not do.
        for (Registration r : sRegistrations) {
            if (r.host() && !PASSIVE_PROVIDER.equals(r.provider)) { want = true; break; }
        }
        if (!sWaiters.isEmpty()) want = true;
        if (want) {
            level = permissionLevel();
            if (level == PERMISSION_NONE || !hostEnabled() || LocationBridge.refused()) {
                want = false;
            }
        }
        boolean reduced = level == PERMISSION_COARSE;
        synchronized (HOST_LOCK) {
            if (want && (!sHostRunning || sHostRunningReduced != reduced)) {
                sHostRunning = true;
                sHostRunningReduced = reduced;
                LocationBridge.start(reduced);
            } else if (!want && sHostRunning) {
                sHostRunning = false;
                LocationBridge.stop();
            }
        }
    }

    /**
     * One fix to one registration, through LocationRegistration.acceptLocationChange
     * (LocationProviderManager:868-990): drop it once the registration has
     * expired, when it arrives faster than the minimum update interval (less
     * jitter), or when it has not moved more than the minimum distance; remove
     * the registration after maxUpdates. `asProvider` null means "name it after
     * the registration" (a host fix).
     */
    private static void deliver(Registration r, Location fix, String asProvider) {
        final Location copy;
        boolean remove = false;
        synchronized (r) {
            if (!sRegistrations.contains(r)) return;
            if (SystemClock.elapsedRealtime() >= r.expireAtRealtimeMs) {
                remove = true;
                copy = null;
            } else {
                Location prev = r.lastDelivered;
                if (prev != null) {
                    if (prev.getTime() == fix.getTime()
                            && prev.getLatitude() == fix.getLatitude()
                            && prev.getLongitude() == fix.getLongitude()) {
                        return;   // the same fix again (a cached fix re-reported)
                    }
                    long deltaMs = fix.getElapsedRealtimeMillis() - prev.getElapsedRealtimeMillis();
                    long maxJitterMs = Math.min((long) (FASTEST_INTERVAL_JITTER_PERCENTAGE
                            * r.request.getIntervalMillis()), MAX_FASTEST_INTERVAL_JITTER_MS);
                    if (deltaMs < r.request.getMinUpdateIntervalMillis() - maxJitterMs) return;
                    double minDistance = r.request.getMinUpdateDistanceMeters();
                    if (minDistance > 0.0 && fix.distanceTo(prev) <= minDistance) return;
                }
                copy = asProvider != null ? new Location(fix) : named(fix, r.provider);
                r.lastDelivered = copy;
                if (++r.delivered >= r.request.getMaxUpdates()) remove = true;
            }
        }
        if (remove) {
            sRegistrations.remove(r);
            updateHostSession();
        }
        if (copy == null) return;
        // AOSP copies the location when delivering in-process, since Location
        // is mutable; each listener gets its own.
        final Location out = new Location(copy);
        if (r.listener != null) {
            r.executor.execute(() -> {
                try { r.listener.onLocationChanged(out); } catch (Throwable ignored) { }
            });
        } else if (r.pendingIntent != null) {
            r.executor.execute(() -> {
                android.content.Intent i = r.pendingIntent.getIntent();
                if (i != null) i.putExtra(KEY_LOCATION_CHANGED, out);
                try { r.pendingIntent.send(); } catch (PendingIntent.CanceledException e) { }
            });
        }
    }

    private static void completeWaiter(CurrentWaiter w, Location l) {
        if (!w.done.compareAndSet(false, true)) return;
        sWaiters.remove(w);
        updateHostSession();
        w.executor.execute(() -> { try { w.consumer.accept(l); } catch (Throwable ignored) { } });
    }

    // === Enabled state ======================================================

    /** The location master switch: macOS Location Services. */
    public boolean isLocationEnabled() { return hostEnabled(); }

    public boolean isProviderEnabled(String provider) {
        return providerEnabled(provider);
    }

    private static boolean providerEnabled(String provider) {
        if (provider == null) return false;
        TestProvider tp = testProvider(provider);
        if (tp != null) return tp.enabled;
        // AOSP's passive provider is enabled whenever location is.
        if (isHostProvider(provider) || PASSIVE_PROVIDER.equals(provider)) return hostEnabled();
        return false;
    }

    public Location getLastKnownLocation(String provider) {
        if (provider == null) return null;
        // A test provider's last location is real data the app itself supplied.
        TestProvider tp = testProvider(provider);
        if (tp != null) {
            return (tp.enabled && tp.location != null) ? new Location(tp.location) : null;
        }
        if (!isHostProvider(provider) && !PASSIVE_PROVIDER.equals(provider)) return null;
        if (!LocationBridge.available()) return null;
        if (!enforceLocationPermission()) return null;
        // AOSP returns null for a disabled provider, and the app-op check
        // withholds it from an app macOS has refused.
        if (!hostDeliverable()) return null;
        Location l = hostLast();
        return l != null ? named(l, provider) : null;
    }

    public void getCurrentLocation(String provider,
            android.os.CancellationSignal cancellationSignal,
            Executor executor, java.util.function.Consumer<Location> consumer) {
        getCurrentLocation(provider, new LocationRequest.Builder(0).build(),
                cancellationSignal, executor, consumer);
    }

    /**
     * AOSP getCurrentLocation (LocationManager:1007, LocationProviderManager:1869):
     * a last location younger than 30 s answers at once; otherwise the next fix
     * does, within min(duration, 30 s); a disabled provider, a cancellation or
     * the timeout answer null. Always posted to the executor, never inline.
     */
    public void getCurrentLocation(String provider, LocationRequest locationRequest,
            android.os.CancellationSignal cancellationSignal, Executor executor,
            java.util.function.Consumer<Location> consumer) {
        if (provider == null) throw new IllegalArgumentException("invalid null provider");
        if (locationRequest == null) throw new IllegalArgumentException("invalid null location request");
        if (executor == null) throw new IllegalArgumentException("invalid null executor");
        if (consumer == null) throw new IllegalArgumentException("invalid null consumer");
        if (cancellationSignal != null) cancellationSignal.throwIfCanceled();
        TestProvider tp = testProvider(provider);
        if (tp != null || !isHostProvider(provider)) {
            final Location l = (tp != null && tp.enabled && tp.location != null)
                    ? new Location(tp.location) : null;
            executor.execute(() -> { try { consumer.accept(l); } catch (Throwable ignored) {} });
            return;
        }
        if (!enforceLocationPermission() || !hostDeliverable()) {
            executor.execute(() -> { try { consumer.accept(null); } catch (Throwable ignored) {} });
            return;
        }
        Location last = hostLast();
        if (last != null && last.getElapsedRealtimeAgeMillis() < MAX_CURRENT_LOCATION_AGE_MS) {
            final Location l = named(last, provider);
            executor.execute(() -> { try { consumer.accept(l); } catch (Throwable ignored) {} });
            return;
        }
        final CurrentWaiter w = new CurrentWaiter(provider, executor, consumer);
        sWaiters.add(w);
        long timeout = Math.min(locationRequest.getDurationMillis(),
                MAX_GET_CURRENT_LOCATION_TIMEOUT_MS);
        TIMER.schedule(() -> completeWaiter(w, null), timeout, TimeUnit.MILLISECONDS);
        if (cancellationSignal != null) {
            // A cancelled request delivers nothing at all (GetCurrentLocationTransport
            // drops its consumer on cancel).
            cancellationSignal.setOnCancelListener(() -> {
                if (w.done.compareAndSet(false, true)) {
                    sWaiters.remove(w);
                    updateHostSession();
                }
            });
        }
        updateHostSession();
    }

    // === Providers ==========================================================

    public List<String> getAllProviders() {
        // AOSP lists every known provider regardless of enabled state -- which
        // includes any test provider the app has added.
        List<String> out = new ArrayList<>(Arrays.asList(
            GPS_PROVIDER, NETWORK_PROVIDER, PASSIVE_PROVIDER, FUSED_PROVIDER));
        synchronized (sTestProviders) {
            for (String name : sTestProviders.keySet()) {
                if (!out.contains(name)) out.add(name);
            }
        }
        return out;
    }

    public List<String> getProviders(boolean enabledOnly) {
        return getProviders(null, enabledOnly);
    }

    /** LocationManagerService.getProviders (:679-703): an empty list for a
     *  caller holding neither location permission. Criteria are not matched:
     *  the built-in providers share one source and one set of properties. */
    public List<String> getProviders(Criteria criteria, boolean enabledOnly) {
        if (LocationBridge.available() && permissionLevel() == PERMISSION_NONE) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String p : getAllProviders()) {
            if (!enabledOnly || isProviderEnabled(p)) out.add(p);
        }
        return out;
    }

    /** LocationManagerService.getBestProvider (:706-728), verbatim order. */
    public String getBestProvider(Criteria criteria, boolean enabledOnly) {
        List<String> providers = getProviders(criteria, enabledOnly);
        if (providers.isEmpty()) return null;
        if (providers.contains(FUSED_PROVIDER)) return FUSED_PROVIDER;
        if (providers.contains(GPS_PROVIDER)) return GPS_PROVIDER;
        if (providers.contains(NETWORK_PROVIDER)) return NETWORK_PROVIDER;
        return providers.get(0);
    }

    public LocationProvider getProvider(String provider) {
        if (provider == null) return null;
        return new LocationProvider(provider);
    }

    // === Update requests ====================================================
    //
    // Every overload lands in register(). For a provider that is disabled when
    // the listener registers, AOSP's LocationListener.onProviderDisabled javadoc
    // is the contract: "If a provider is disabled when this listener is
    // registered, this callback will be invoked immediately"
    // (LocationProviderManager:793-797). Delivery is always POSTED (never
    // called inline) so a caller that writes state right after
    // requestLocationUpdates() returns cannot observe the callback nested inside
    // that same call frame -- matching how a real registration crosses the
    // LocationManagerService Binder boundary.

    public void requestLocationUpdates(String provider, long minTimeMs,
            float minDistanceM, LocationListener listener) {
        register(provider, legacyRequest(minTimeMs, minDistanceM), listener, null,
                looperExecutor(Looper.myLooper()));
    }
    public void requestLocationUpdates(String provider, long minTimeMs,
            float minDistanceM, LocationListener listener, Looper looper) {
        register(provider, legacyRequest(minTimeMs, minDistanceM), listener, null,
                looperExecutor(looper));
    }
    public void requestLocationUpdates(String provider, long minTimeMs,
            float minDistanceM, Executor executor, LocationListener listener) {
        register(provider, legacyRequest(minTimeMs, minDistanceM), listener, null,
                executor != null ? executor : looperExecutor(null));
    }
    public void requestLocationUpdates(String provider, long minTimeMs,
            float minDistanceM, PendingIntent pendingIntent) {
        register(provider, legacyRequest(minTimeMs, minDistanceM), null, pendingIntent,
                looperExecutor(null));
    }
    // The Criteria overloads (API 9; deprecated in 31, still public). AOSP android-13.0.0_r1
    // location/java/android/location/LocationManager.java:1266-1374: "Since Android KitKat, Criteria
    // requests will always result in using the FUSED_PROVIDER"; a null criteria is an
    // IllegalArgumentException ("invalid null criteria"); the Looper form wraps `looper == null ? new
    // Handler() : new Handler(looper)` and defers to the Executor form, which defers to the
    // FUSED_PROVIDER request. Mapbox's location provider (Efteling's park map) calls the Looper form
    // and died on NoSuchMethodError.
    public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria,
            LocationListener listener, Looper looper) {
        if (criteria == null) throw new IllegalArgumentException("invalid null criteria");
        requestLocationUpdates(minTimeMs, minDistanceM, criteria,
                looperExecutor(looper != null ? looper : Looper.myLooper()), listener);
    }
    public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria,
            Executor executor, LocationListener listener) {
        if (criteria == null) throw new IllegalArgumentException("invalid null criteria");
        if (executor == null) throw new IllegalArgumentException("invalid null executor");
        register(FUSED_PROVIDER, legacyRequest(minTimeMs, minDistanceM), listener, null, executor);
    }
    public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria,
            PendingIntent pendingIntent) {
        if (criteria == null) throw new IllegalArgumentException("invalid null criteria");
        register(FUSED_PROVIDER, legacyRequest(minTimeMs, minDistanceM), null, pendingIntent,
                looperExecutor(null));
    }
    public void requestLocationUpdates(String provider, LocationRequest locationRequest,
            Executor executor, LocationListener listener) {
        if (locationRequest == null) throw new IllegalArgumentException("invalid null location request");
        register(provider, locationRequest, listener, null,
                executor != null ? executor : looperExecutor(null));
    }
    public void requestLocationUpdates(String provider, LocationRequest locationRequest,
            PendingIntent pendingIntent) {
        if (locationRequest == null) throw new IllegalArgumentException("invalid null location request");
        register(provider, locationRequest, null, pendingIntent, looperExecutor(null));
    }
    // requestSingleUpdate: AOSP builds `new LocationRequest.Builder(0).setMaxUpdates(1)
    // .setDurationMillis(MAX_SINGLE_LOCATION_TIMEOUT_MS)` (LocationManager:1060-1064).
    public void requestSingleUpdate(String provider, LocationListener listener, Looper looper) {
        register(provider, singleRequest(), listener, null, looperExecutor(looper));
    }
    public void requestSingleUpdate(String provider, PendingIntent pendingIntent) {
        register(provider, singleRequest(), null, pendingIntent, looperExecutor(null));
    }
    public void requestSingleUpdate(Criteria criteria, LocationListener listener, Looper looper) {
        if (criteria == null) throw new IllegalArgumentException("invalid null criteria");
        register(FUSED_PROVIDER, singleRequest(), listener, null, looperExecutor(looper));
    }
    public void requestSingleUpdate(Criteria criteria, PendingIntent pendingIntent) {
        if (criteria == null) throw new IllegalArgumentException("invalid null criteria");
        register(FUSED_PROVIDER, singleRequest(), null, pendingIntent, looperExecutor(null));
    }

    /** LocationRequest.createFromDeprecatedProvider: the interval is minTime, and
     *  so is the minimum update interval. */
    private static LocationRequest legacyRequest(long minTimeMs, float minDistanceM) {
        long t = Math.max(0L, minTimeMs);
        return new LocationRequest.Builder(t)
                .setMinUpdateIntervalMillis(t)
                .setMinUpdateDistanceMeters(Math.max(0f, minDistanceM))
                .build();
    }

    private static LocationRequest singleRequest() {
        return new LocationRequest.Builder(0).setMaxUpdates(1)
                .setDurationMillis(MAX_SINGLE_LOCATION_TIMEOUT_MS).build();
    }

    /** Wrap a Looper as an Executor (falls back to the main looper, never
     *  throws) so every listener-taking overload can share one dispatch
     *  path. Mac.apk is deliberately more lenient than AOSP here: a real
     *  device throws when the calling thread has no Looper and none was
     *  supplied, we degrade to the main looper instead of crashing an app
     *  that happened to work by luck. */
    private static Executor looperExecutor(Looper looper) {
        Looper l = (looper != null) ? looper : Looper.getMainLooper();
        android.os.Handler h = new android.os.Handler(l);
        return h::post;
    }

    private static void register(String provider, LocationRequest request,
            LocationListener listener, PendingIntent pendingIntent, Executor executor) {
        if (provider == null || (listener == null && pendingIntent == null)) return;
        boolean host = isHostProvider(provider) || PASSIVE_PROVIDER.equals(provider);
        if (host && LocationBridge.available() && !enforceLocationPermission()) {
            // Enforcement off and no permission: the registration is accepted
            // and hears that the provider is unavailable to it.
            host = false;
        }
        // Registering the same listener or PendingIntent again REPLACES its
        // registration (LocationManagerService keys them by transport).
        for (Registration old : sRegistrations) {
            if ((listener != null && old.listener == listener)
                    || (pendingIntent != null && pendingIntent.equals(old.pendingIntent))) {
                sRegistrations.remove(old);
            }
        }
        Registration r = new Registration(provider, listener, pendingIntent, executor, request);
        sRegistrations.add(r);
        if (r.expireAtRealtimeMs != Long.MAX_VALUE) {
            TIMER.schedule(() -> {
                if (sRegistrations.remove(r)) updateHostSession();
            }, r.expireAtRealtimeMs - SystemClock.elapsedRealtime(), TimeUnit.MILLISECONDS);
        }
        boolean enabled = host ? hostEnabled() && LocationBridge.available()
                               : providerEnabled(provider);
        if (!enabled) {
            notifyDisabled(r);
        } else if (host) {
            synchronized (HOST_LOCK) {
                if (sHostEnabledAnnounced == null) sHostEnabledAnnounced = true;
            }
            deliverHistorical(r);
        }
        updateHostSession();
    }

    /** LocationRegistration.onActive (LocationProviderManager:820-850): a new
     *  registration whose interval exceeds MIN_REQUEST_DELAY_MS gets the last
     *  location if it is younger than that interval. */
    private static void deliverHistorical(Registration r) {
        long maxAgeMs = r.request.getIntervalMillis();
        if (maxAgeMs <= MIN_REQUEST_DELAY_MS || !hostDeliverable()) return;
        Location last = hostLast();
        if (last != null && last.getElapsedRealtimeAgeMillis() < maxAgeMs) {
            deliver(r, last, null);
        }
    }

    private static void notifyDisabled(Registration r) {
        final String provider = r.provider;
        if (r.listener != null) {
            r.executor.execute(() -> {
                try { r.listener.onProviderDisabled(provider); } catch (Throwable ignored) {}
            });
        } else if (r.pendingIntent != null) {
            // AOSP reports provider status on the PendingIntent path via the
            // EXTRA_PROVIDER_NAME / EXTRA_PROVIDER_ENABLED extras. Mutate the
            // PendingIntent's OWN wrapped Intent -- never pass an override Intent
            // to send(Context,int,Intent), which REPLACES the whole Intent
            // including its action, so a receiver's IntentFilter would stop
            // matching.
            r.executor.execute(() -> {
                android.content.Intent i = r.pendingIntent.getIntent();
                if (i != null) {
                    i.putExtra(EXTRA_PROVIDER_NAME, provider);
                    i.putExtra(EXTRA_PROVIDER_ENABLED, false);
                }
                try { r.pendingIntent.send(); } catch (PendingIntent.CanceledException ignored) {}
            });
        }
    }

    public void removeUpdates(LocationListener listener) {
        if (listener == null) return;
        for (Registration r : sRegistrations) {
            if (r.listener == listener) sRegistrations.remove(r);
        }
        updateHostSession();
    }

    public void removeUpdates(PendingIntent pendingIntent) {
        if (pendingIntent == null) return;
        for (Registration r : sRegistrations) {
            if (pendingIntent.equals(r.pendingIntent)) sRegistrations.remove(r);
        }
        updateHostSession();
    }

    public void addProximityAlert(double latitude, double longitude, float radius,
            long expiration, PendingIntent intent) {}
    public void removeProximityAlert(PendingIntent intent) {}

    public boolean sendExtraCommand(String provider, String command, android.os.Bundle extras) {
        return false;
    }
    public void flushGnssBatch() {}

    // GNSS / GPS status API (API 24 GnssStatus.Callback superseded the API 3
    // GpsStatus.Listener). Unlike LocationListener.onProviderDisabled, neither
    // callback type has an AOSP-documented "tell me immediately if there is no
    // GNSS" contract -- GnssStatus updates are purely opportunistic (they fire
    // only while satellites are actually being tracked), so a real device with
    // network-only location and no GPS chip -- or one sitting with no sky view
    // -- also registers successfully and simply never calls back. Accepting the
    // registration and staying silent is the honest behavior here, not a lie.
    // (There used to be a redundant, unreachable set of Object-typed overloads
    // of these same methods above this comment: no app bytecode can target an
    // Object-typed descriptor for a method AOSP declares as GnssStatus.Callback/
    // GpsStatus.Listener, and nothing in this tree called them either -- removed
    // as dead code rather than left to suggest a second, differently-behaved
    // registration path exists.)
    // AOSP declares three overloads (Callback-only, Callback+Handler,
    // Executor+Callback); the Callback-only form was previously missing
    // entirely (only the dead Object-typed stub stood in for it above),
    // which is a real NoSuchMethodError for any caller using it -- delegate
    // to the Handler form the same way AOSP's own no-Handler overload does.
    public boolean registerGnssStatusCallback(android.location.GnssStatus.Callback callback) {
        return registerGnssStatusCallback(callback, null);
    }
    public boolean registerGnssStatusCallback(android.location.GnssStatus.Callback callback, android.os.Handler handler) { return true; }
    public boolean registerGnssStatusCallback(java.util.concurrent.Executor executor, android.location.GnssStatus.Callback callback) { return true; }
    public void unregisterGnssStatusCallback(android.location.GnssStatus.Callback callback) { }
    public boolean addGpsStatusListener(android.location.GpsStatus.Listener listener) { return true; }
    public void removeGpsStatusListener(android.location.GpsStatus.Listener listener) { }
    /** AOSP: "if status is null, a new GpsStatus object is created". Returning
     *  the null straight back NPE'd the legacy idiom
     *  `lm.getGpsStatus(null).getSatellites()`, which is how every pre-API-24 app
     *  reads GPS state -- and four corpus apps still target below 23. An empty
     *  GpsStatus (no satellites) is the honest answer for a Mac. */
    public android.location.GpsStatus getGpsStatus(android.location.GpsStatus status) {
        return status != null ? status : android.location.GpsStatus.createEmpty();
    }
    public String getGnssHardwareModelName() { return null; }
    public int getGnssYearOfHardware() { return 0; }
    public boolean registerGnssMeasurementsCallback(android.location.GnssMeasurementsEvent.Callback callback) { return true; }
    public boolean registerGnssMeasurementsCallback(android.location.GnssMeasurementsEvent.Callback callback, android.os.Handler handler) { return true; }
    public boolean registerGnssMeasurementsCallback(java.util.concurrent.Executor executor, android.location.GnssMeasurementsEvent.Callback callback) { return true; }
    public void unregisterGnssMeasurementsCallback(android.location.GnssMeasurementsEvent.Callback callback) { }
    /** AOSP hasProvider (API 31): whether a named provider exists on the device. */
    public boolean hasProvider(String provider) {
        return GPS_PROVIDER.equals(provider) || NETWORK_PROVIDER.equals(provider)
            || PASSIVE_PROVIDER.equals(provider) || FUSED_PROVIDER.equals(provider)
            || testProvider(provider) != null;
    }

    // === GNSS measurement / navigation-message / antenna / NMEA registration ==
    //
    // Every register* below ACCEPTS the registration and then never calls back.
    // That is not a stub standing in for something better: a Mac has no GNSS
    // chipset, and AOSP's own contract for a chipset that cannot produce raw
    // measurements is exactly this -- registration succeeds and
    // GnssMeasurementsEvent.Callback.onStatusChanged reports STATUS_NOT_SUPPORTED.
    // Many shipped phones behave the same way. Fabricating a satellite fix here
    // would be a lie the app cannot detect; silence is a state it already handles.

    public boolean registerGnssMeasurementsCallback(GnssMeasurementRequest request,
            java.util.concurrent.Executor executor,
            android.location.GnssMeasurementsEvent.Callback callback) {
        return true;
    }

    /** @deprecated AOSP deprecated the no-Handler form in API 30. */
    @Deprecated
    public boolean registerGnssNavigationMessageCallback(
            android.location.GnssNavigationMessage.Callback callback) {
        return registerGnssNavigationMessageCallback(callback, null);
    }

    public boolean registerGnssNavigationMessageCallback(
            android.location.GnssNavigationMessage.Callback callback, android.os.Handler handler) {
        return true;
    }

    public boolean registerGnssNavigationMessageCallback(java.util.concurrent.Executor executor,
            android.location.GnssNavigationMessage.Callback callback) {
        return true;
    }

    public void unregisterGnssNavigationMessageCallback(
            android.location.GnssNavigationMessage.Callback callback) { }

    public boolean registerAntennaInfoListener(java.util.concurrent.Executor executor,
            android.location.GnssAntennaInfo.Listener listener) {
        return true;
    }

    public void unregisterAntennaInfoListener(android.location.GnssAntennaInfo.Listener listener) { }

    /**
     * AOSP returns null when antenna info is unavailable, which is the honest
     * answer for a machine with no GNSS antenna. Note this is NOT an empty list:
     * an empty list would assert "we asked the hardware and it has no antennas".
     */
    public java.util.List<GnssAntennaInfo> getGnssAntennaInfos() { return null; }

    /**
     * A default-built GnssCapabilities: every capability flag clear and no signal
     * types, i.e. "the chipset supports nothing". hasAccumulatedDeltaRange() comes
     * back CAPABILITY_UNKNOWN because with no HAL the answer genuinely is unknown.
     */
    public GnssCapabilities getGnssCapabilities() {
        return new GnssCapabilities.Builder().build();
    }

    /**
     * AOSP returns null when the provider has no properties, and throws
     * IllegalArgumentException only for a provider that does not exist. A test
     * provider reports whatever the app supplied. A host-backed built-in
     * provider describes what actually stands behind it: macOS Location
     * Services, which positions over the network (Wi-Fi), with no satellite or
     * cell requirement, fine accuracy, altitude / speed / bearing when
     * CoreLocation reports them, at low power. Unbridged, the built-in names
     * have no backing implementation, so null -- an honest "no properties".
     */
    public ProviderProperties getProviderProperties(String provider) {
        if (provider == null) throw new IllegalArgumentException("provider == null");
        TestProvider tp = testProvider(provider);
        if (tp != null) return tp.properties;
        if (!hasProvider(provider)) {
            throw new IllegalArgumentException("provider \"" + provider + "\" does not exist");
        }
        if (isHostProvider(provider)) {
            return new ProviderProperties.Builder()
                    .setHasNetworkRequirement(true)
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build();
        }
        return null;
    }

    /**
     * AOSP flushes any batched locations and then invokes
     * LocationListener.onFlushComplete(requestCode). Nothing here batches, so the
     * flush is complete the moment it is asked for -- but the callback still has
     * to fire, because an app that awaits it before proceeding otherwise hangs.
     * Posted, never called inline, for the same reason the update callbacks are.
     */
    public void requestFlush(String provider, LocationListener listener, int requestCode) {
        if (listener == null) throw new IllegalArgumentException("listener == null");
        Executor executor = null;
        for (Registration r : sRegistrations) {
            if (r.listener == listener) { executor = r.executor; break; }
        }
        if (executor == null) executor = looperExecutor(null);
        final Executor e = executor;
        e.execute(() -> {
            try { listener.onFlushComplete(requestCode); } catch (Throwable ignored) { }
        });
    }

    public void requestFlush(String provider, PendingIntent pendingIntent, int requestCode) {
        if (pendingIntent == null) throw new IllegalArgumentException("pendingIntent == null");
        looperExecutor(null).execute(() -> {
            android.content.Intent i = pendingIntent.getIntent();
            if (i != null) i.putExtra(KEY_FLUSH_COMPLETE, requestCode);
            try { pendingIntent.send(); } catch (PendingIntent.CanceledException ignored) { }
        });
    }

    // NMEA. Same reasoning as the GNSS callbacks: registration succeeds, nothing
    // is ever delivered, because there is no GNSS engine emitting sentences.
    /** @deprecated AOSP deprecated the GpsStatus.NmeaListener form in API 24. */
    @Deprecated
    public boolean addNmeaListener(GpsStatus.NmeaListener listener) { return false; }

    /** @deprecated AOSP deprecated the no-Handler form in API 30. */
    @Deprecated
    public boolean addNmeaListener(OnNmeaMessageListener listener) {
        return addNmeaListener(listener, null);
    }

    public boolean addNmeaListener(OnNmeaMessageListener listener, android.os.Handler handler) {
        return true;
    }

    public boolean addNmeaListener(java.util.concurrent.Executor executor,
            OnNmeaMessageListener listener) {
        return true;
    }

    /** @deprecated pairs with the deprecated GpsStatus.NmeaListener registration. */
    @Deprecated
    public void removeNmeaListener(GpsStatus.NmeaListener listener) { }

    public void removeNmeaListener(OnNmeaMessageListener listener) { }

}
