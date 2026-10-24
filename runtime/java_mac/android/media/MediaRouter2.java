package android.media;

import android.content.Context;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** The modern (API 30) media-routing client. Mac.apk plays audio/video through
 *  the host's own default output and hosts no route providers (no Cast, no DLNA,
 *  no Bluetooth route service), so the only route that exists is the built-in
 *  speaker -- exactly what a real Android device with no route providers
 *  installed reports. Discovery therefore never surfaces anything beyond it and
 *  transfer callbacks never fire, which is faithful rather than empty: AOSP's
 *  local router likewise starts with only the system route.
 *  Source: frameworks/base/media/java/android/media/MediaRouter2.java
 *  (android16-release). */
public final class MediaRouter2 {

    /** AOSP's system MediaRoute2Provider names its default route "DEFAULT_ROUTE"
     *  (services/core/java/com/android/server/media/SystemMediaRoute2Provider.java). */
    private static final String SYSTEM_ROUTE_ID = "DEFAULT_ROUTE";
    private static final String SYSTEM_SESSION_ID = "SYSTEM_SESSION";

    private static MediaRouter2 sInstance;

    private final String mClientPackageName;
    private final AudioManager mAudioManager;
    private final MediaRoute2Info mSystemRoute;
    private final RoutingController mSystemController;

    private final CopyOnWriteArrayList<RouteCallbackRecord> mRouteCallbackRecords =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<TransferCallbackRecord> mTransferCallbackRecords =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ControllerCallbackRecord> mControllerCallbackRecords =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListingPreferenceCallbackRecord>
            mListingPreferenceCallbackRecords = new CopyOnWriteArrayList<>();

    private volatile RouteListingPreference mRouteListingPreference;
    private volatile OnGetControllerHintsListener mOnGetControllerHintsListener;
    private int mNextScanTokenId = 1;

    private MediaRouter2(Context context) {
        String pkg = "";
        try {
            if (context != null && context.getPackageName() != null) {
                pkg = context.getPackageName();
            }
        } catch (Throwable ignore) {
            // A Context that cannot name its own package is still usable here; the
            // client package name only labels the routing session.
        }
        mClientPackageName = pkg;
        AudioManager am = null;
        try {
            if (context != null) {
                am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            }
        } catch (Throwable ignore) {
            // A Context with no audio service still yields a usable router; the
            // route then reports FIXED volume, which is the honest answer.
        }
        mAudioManager = am;
        mSystemRoute = buildSystemRoute(am);
        mSystemController = new RoutingController(buildSystemSession(mSystemRoute, pkg));
    }

    /**
     * The one route this host really has: the machine's default audio output.
     * Built from AudioDeviceInfo (which already describes the CoreAudio default
     * sink) rather than from invented values, and given the two features AOSP's
     * system route carries, so an app whose RouteDiscoveryPreference asks for
     * LIVE_AUDIO/LIVE_VIDEO finds it and an app asking for REMOTE_PLAYBACK
     * correctly finds nothing.
     */
    private static MediaRoute2Info buildSystemRoute(AudioManager am) {
        AudioDeviceInfo device = new AudioDeviceInfo();
        MediaRoute2Info.Builder b =
                new MediaRoute2Info.Builder(SYSTEM_ROUTE_ID, device.getProductName())
                        .addFeature(MediaRoute2Info.FEATURE_LIVE_AUDIO)
                        .addFeature(MediaRoute2Info.FEATURE_LIVE_VIDEO)
                        .setType(device.getType())
                        .setSupportedRoutingTypes(MediaRoute2Info.FLAG_ROUTING_TYPE_SYSTEM_AUDIO
                                | MediaRoute2Info.FLAG_ROUTING_TYPE_SYSTEM_VIDEO)
                        .setConnectionState(MediaRoute2Info.CONNECTION_STATE_CONNECTED);
        b.mIsSystem = true;
        // Volume is REAL host state, read through AudioManager (which is itself
        // backed by the host mixer) rather than reported as a constant. If no
        // AudioManager is reachable -- a Context-less caller, or a service that
        // has not been created -- the honest fallback is FIXED volume, i.e. "this
        // route's volume is not app-adjustable", not a made-up range.
        try {
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                if (max > 0) {
                    b.setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE);
                    b.setVolumeMax(max);
                    b.setVolume(am.getStreamVolume(AudioManager.STREAM_MUSIC));
                }
            }
        } catch (Throwable ignore) {
            // Leave the AOSP Builder default: PLAYBACK_VOLUME_FIXED, max 0.
        }
        return b.build();
    }

    private static RoutingSessionInfo buildSystemSession(MediaRoute2Info route, String pkg) {
        return new RoutingSessionInfo.Builder(SYSTEM_SESSION_ID, pkg)
                .addSelectedRoute(route.getId())
                .setVolumeHandling(route.getVolumeHandling())
                .setVolumeMax(route.getVolumeMax())
                .setVolume(route.getVolume())
                .build();
    }

    /** AOSP holds one per-process instance keyed on the application context
     *  (MediaRouter2.java L231-239). */
    public static synchronized MediaRouter2 getInstance(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        if (sInstance == null) sInstance = new MediaRouter2(context);
        return sInstance;
    }

    /**
     * The privileged "proxy" router: an app holding MEDIA_ROUTING_CONTROL or
     * MEDIA_CONTENT_CONTROL observes ANOTHER package's routing. API 35.
     *
     * Mac.apk runs exactly one app per process and has no other package's
     * routing to proxy, so the returned instance observes the same (only)
     * session this process has. AOSP invokes onInstanceInvalidatedListener when
     * the proxied package stops being visible; here it never does, because the
     * one package in the process never goes away while the process lives --
     * so the listener is retained and simply never fires, rather than being
     * invoked with a false invalidation.
     */
    public static synchronized MediaRouter2 getInstance(Context context, String clientPackageName,
            Executor executor, Runnable onInstanceInvalidatedListener) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(onInstanceInvalidatedListener,
                "onInstanceInvalidatedListener must not be null");
        // AOSP: "clientPackageName must not be null or empty" (L424).
        if (clientPackageName == null || clientPackageName.isEmpty()) {
            throw new IllegalArgumentException("clientPackageName must not be null or empty");
        }
        return getInstance(context);
    }

    public RoutingController getSystemController() { return mSystemController; }

    /**
     * AOSP returns the routes filtered by the union of every registered
     * RouteDiscoveryPreference: a route survives only if it has one of the
     * preferred features (LocalMediaRouter2Impl.filterRoutesWithIndividualPreference,
     * MediaRouter2.java L3881-3895). The same rule is applied here, so an app
     * that has registered nothing sees nothing (as on Android) and an app asking
     * for LIVE_AUDIO sees the built-in speaker.
     */
    public List<MediaRoute2Info> getRoutes() {
        for (RouteCallbackRecord record : mRouteCallbackRecords) {
            if (hasAnyFeature(mSystemRoute, record.mPreference.getPreferredFeatures())) {
                return Collections.singletonList(mSystemRoute);
            }
        }
        return Collections.emptyList();
    }

    private static boolean hasAnyFeature(MediaRoute2Info route, List<String> features) {
        for (String feature : features) {
            if (route.getFeatures().contains(feature)) return true;
        }
        return false;
    }

    public void registerRouteCallback(Executor executor, RouteCallback routeCallback,
            RouteDiscoveryPreference preference) {
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(routeCallback, "callback must not be null");
        Objects.requireNonNull(preference, "preference must not be null");
        RouteCallbackRecord record = new RouteCallbackRecord(executor, routeCallback, preference);
        // AOSP ignores a duplicate registration with a warning (L1000-1010).
        for (RouteCallbackRecord existing : mRouteCallbackRecords) {
            if (existing.mRouteCallback == routeCallback) return;
        }
        mRouteCallbackRecords.add(record);
        // AOSP dispatches the current filtered route set to a newly registered
        // callback; doing the same here is what lets an app discover the
        // built-in speaker without waiting for a change that never comes.
        final List<MediaRoute2Info> routes = getRoutes();
        executor.execute(() -> routeCallback.onRoutesUpdated(routes));
    }

    public void unregisterRouteCallback(RouteCallback routeCallback) {
        Objects.requireNonNull(routeCallback, "callback must not be null");
        for (RouteCallbackRecord record : mRouteCallbackRecords) {
            if (record.mRouteCallback == routeCallback) {
                mRouteCallbackRecords.remove(record);
                return;
            }
        }
    }

    public void registerTransferCallback(Executor executor, TransferCallback callback) {
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        for (TransferCallbackRecord record : mTransferCallbackRecords) {
            if (record.mCallback == callback) return;
        }
        mTransferCallbackRecords.add(new TransferCallbackRecord(executor, callback));
    }

    public void unregisterTransferCallback(TransferCallback callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        for (TransferCallbackRecord record : mTransferCallbackRecords) {
            if (record.mCallback == callback) {
                mTransferCallbackRecords.remove(record);
                return;
            }
        }
    }

    public void registerControllerCallback(Executor executor, ControllerCallback callback) {
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        for (ControllerCallbackRecord record : mControllerCallbackRecords) {
            if (record.mCallback == callback) return;
        }
        mControllerCallbackRecords.add(new ControllerCallbackRecord(executor, callback));
    }

    public void unregisterControllerCallback(ControllerCallback callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        for (ControllerCallbackRecord record : mControllerCallbackRecords) {
            if (record.mCallback == callback) {
                mControllerCallbackRecords.remove(record);
                return;
            }
        }
    }

    /** API 34. The app's preferred output-switcher listing. Stored and read back
     *  (see RouteListingPreference); Mac.apk has no output switcher to show it in. */
    public void setRouteListingPreference(RouteListingPreference routeListingPreference) {
        mRouteListingPreference = routeListingPreference;
        for (ListingPreferenceCallbackRecord record : mListingPreferenceCallbackRecords) {
            record.mExecutor.execute(() -> record.mCallback.accept(routeListingPreference));
        }
    }

    public RouteListingPreference getRouteListingPreference() { return mRouteListingPreference; }

    /** API 35. AOSP dispatches the CURRENT preference to a freshly registered
     *  callback (MediaRouter2.java L756-790), so the app never has to guess. */
    public void registerRouteListingPreferenceUpdatedCallback(Executor executor,
            Consumer<RouteListingPreference> routeListingPreferenceCallback) {
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(routeListingPreferenceCallback, "callback must not be null");
        for (ListingPreferenceCallbackRecord record : mListingPreferenceCallbackRecords) {
            if (record.mCallback == routeListingPreferenceCallback) return;
        }
        mListingPreferenceCallbackRecords.add(
                new ListingPreferenceCallbackRecord(executor, routeListingPreferenceCallback));
        final RouteListingPreference current = mRouteListingPreference;
        executor.execute(() -> routeListingPreferenceCallback.accept(current));
    }

    public void unregisterRouteListingPreferenceUpdatedCallback(
            Consumer<RouteListingPreference> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        for (ListingPreferenceCallbackRecord record : mListingPreferenceCallbackRecords) {
            if (record.mCallback == callback) {
                mListingPreferenceCallbackRecords.remove(record);
                return;
            }
        }
    }

    /** API 34. Asks System UI to show the output switcher.
     *  AOSP's own local implementation returns whatever the router service
     *  answers, and returns FALSE when the service is unreachable
     *  (MediaRouter2.java L3782-3791). Mac.apk has no System UI and no output
     *  switcher, so nothing was shown and false is the truthful answer -- an app
     *  that checks the return value falls back to its own in-app picker, which is
     *  the behaviour we want. */
    public boolean showSystemOutputSwitcher() { return false; }

    /** API 35. AOSP registers a scan request that keeps discovery alive with the
     *  screen off, and the token must be handed back to cancelScanRequest.
     *  Discovery here has nothing to scan for (no route providers), so the token
     *  is real and cancellable but no scanning is started. */
    public ScanToken requestScan(ScanRequest scanRequest) {
        Objects.requireNonNull(scanRequest, "scanRequest must not be null");
        synchronized (this) {
            return new ScanToken(mNextScanTokenId++);
        }
    }

    public void cancelScanRequest(ScanToken token) {
        Objects.requireNonNull(token, "token must not be null");
    }

    /** API 30. Supplies per-session control hints when a controller is created.
     *  No non-system controller is ever created here, so the listener is stored
     *  and never consulted -- as on a device with no route providers. */
    public void setOnGetControllerHintsListener(OnGetControllerHintsListener listener) {
        mOnGetControllerHintsListener = listener;
    }

    /**
     * AOSP's local router notifies onTransferFailure for a route it does not
     * know (MediaRouter2.java L3808-3820). The only known route here is the
     * built-in speaker, which the system session already holds, so a transfer to
     * it moves nothing and a transfer to anything else fails -- which is the
     * same answer a device with no route providers gives.
     */
    public void transferTo(MediaRoute2Info route) {
        Objects.requireNonNull(route, "route must not be null");
        if (!SYSTEM_ROUTE_ID.equals(route.getId())) {
            notifyTransferFailure(route);
        }
    }

    private void notifyTransferFailure(MediaRoute2Info route) {
        for (TransferCallbackRecord record : mTransferCallbackRecords) {
            record.mExecutor.execute(() -> record.mCallback.onTransferFailure(route));
        }
    }

    /** AOSP: "Stops the current media routing. If the system controller controls
     *  the media routing, this method is a no-op." (MediaRouter2.java L1057-1063).
     *  The system controller always controls routing here. */
    public void stop() { }

    /** AOSP: null when no controller has that id (MediaRouter2.java L1149-1158). */
    public RoutingController getController(String id) {
        Objects.requireNonNull(id, "id must not be null");
        for (RoutingController controller : getControllers()) {
            if (id.equals(controller.getId())) return controller;
        }
        return null;
    }

    /** AOSP: "The list returned here will never be empty. The first element in
     *  the list is always the system controller." (MediaRouter2.java L1160-1168). */
    public List<RoutingController> getControllers() {
        return Collections.singletonList(mSystemController);
    }

    /** API 35, privileged. AOSP's LOCAL router throws UnsupportedOperationException
     *  here -- "setRouteVolume is only supported by proxy routers"
     *  (MediaRouter2.java L3856-3861) -- and every instance this class hands out is
     *  a local one, so throwing is the faithful answer rather than silently
     *  pretending the volume moved. Apps use RoutingController.setVolume instead. */
    public void setRouteVolume(MediaRoute2Info route, int volume) {
        Objects.requireNonNull(route, "route must not be null");
        throw new UnsupportedOperationException(
                "setRouteVolume is only supported by proxy routers. See javadoc.");
    }

    /** Notified when the set of available routes changes. */
    public static class RouteCallback {
        public RouteCallback() { }
        /** @deprecated in AOSP since API 34, superseded by onRoutesUpdated. */
        @Deprecated public void onRoutesAdded(List<MediaRoute2Info> routes) { }
        /** @deprecated in AOSP since API 34, superseded by onRoutesUpdated. */
        @Deprecated public void onRoutesRemoved(List<MediaRoute2Info> routes) { }
        /** @deprecated in AOSP since API 34, superseded by onRoutesUpdated. */
        @Deprecated public void onRoutesChanged(List<MediaRoute2Info> routes) { }
        public void onRoutesUpdated(List<MediaRoute2Info> routes) { }
    }

    /** Notified when a routing controller changes (never, here). */
    public static class ControllerCallback {
        public ControllerCallback() { }
        public void onControllerUpdated(RoutingController controller) { }
    }

    /** Notified of the result of {@link #transferTo(MediaRoute2Info)}. */
    public static class TransferCallback {
        public TransferCallback() { }
        public void onTransfer(RoutingController oldController, RoutingController newController) { }
        public void onTransferFailure(MediaRoute2Info requestedRoute) { }
        public void onStop(RoutingController controller) { }
    }

    /** Supplies app-defined hints when a routing controller is created. */
    public interface OnGetControllerHintsListener {
        Bundle onGetControllerHints(MediaRoute2Info route);
    }

    /** Opaque handle for an active scan request. API 35. */
    public static final class ScanToken {
        private final int mId;
        ScanToken(int id) { mId = id; }
        int getId() { return mId; }
    }

    /** Parameters for {@link #requestScan(ScanRequest)}. API 35. */
    public static final class ScanRequest {
        private final boolean mIsScreenOffScan;
        private ScanRequest(boolean isScreenOffScan) { mIsScreenOffScan = isScreenOffScan; }
        public boolean isScreenOffScan() { return mIsScreenOffScan; }

        public static final class Builder {
            boolean mIsScreenOffScan;
            public Builder() { }
            public Builder setScreenOffScan(boolean isScreenOffScan) {
                mIsScreenOffScan = isScreenOffScan; return this;
            }
            public ScanRequest build() { return new ScanRequest(mIsScreenOffScan); }
        }
    }

    /** Controls one routing session; the system controller drives local output. */
    // AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
    // mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
    // app compiled against real Android that subclasses this fails to resolve
    // against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
    public class RoutingController {
        private final RoutingSessionInfo mSession;

        RoutingController(RoutingSessionInfo session) { mSession = session; }

        public String getId() { return mSession.getId(); }
        public RoutingSessionInfo getRoutingSessionInfo() { return mSession; }
        /** AOSP maps the session's route ids back to MediaRoute2Info through the
         *  router's full (unfiltered) route table. */
        public List<MediaRoute2Info> getSelectedRoutes() {
            return getRoutesWithIds(mSession.getSelectedRoutes());
        }
        public List<MediaRoute2Info> getSelectableRoutes() {
            return getRoutesWithIds(mSession.getSelectableRoutes());
        }
        public List<MediaRoute2Info> getDeselectableRoutes() {
            return getRoutesWithIds(mSession.getDeselectableRoutes());
        }
        public List<MediaRoute2Info> getTransferableRoutes() {
            return getRoutesWithIds(mSession.getTransferableRoutes());
        }
        public Bundle getControlHints() { return mSession.getControlHints(); }
        public int getVolumeHandling() { return mSession.getVolumeHandling(); }
        public int getVolumeMax() { return mSession.getVolumeMax(); }
        public int getVolume() { return mSession.getVolume(); }
        public boolean wasTransferInitiatedBySelf() { return false; }

        /** AOSP's SystemRoutingController overrides this to a constant false --
         *  "SystemRoutingController will never be released" (MediaRouter2.java
         *  L2354-2358). The only controller here IS the system controller. */
        public boolean isReleased() { return false; }

        /** Likewise a no-op: AOSP's SystemRoutingController.releaseInternal does
         *  nothing (MediaRouter2.java L2366-2369). */
        public void release() { }

        /** AOSP IGNORES a route that is already selected or not selectable, with
         *  a log line and no exception (MediaRouter2.java L2094-2113). Nothing is
         *  selectable in a single-route system session, so every call is ignored --
         *  which is that same documented path, not a stub. */
        public void selectRoute(MediaRoute2Info route) {
            Objects.requireNonNull(route, "route must not be null");
        }

        /** Same contract as selectRoute: AOSP ignores a route that is not
         *  currently selected or not deselectable (MediaRouter2.java L2130-2150). */
        public void deselectRoute(MediaRoute2Info route) {
            Objects.requireNonNull(route, "route must not be null");
        }

        /** AOSP ignores the call on a FIXED-volume session or an out-of-range
         *  value (MediaRouter2.java L2224-2233). When the host DOES expose an
         *  adjustable output the request is honoured by moving the real
         *  STREAM_MUSIC volume, because that is what this session's volume is. */
        public void setVolume(int volume) {
            if (getVolumeHandling() == MediaRoute2Info.PLAYBACK_VOLUME_FIXED) return;
            if (volume < 0 || volume > getVolumeMax()) return;
            try {
                if (mAudioManager != null) {
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
                }
            } catch (Throwable ignore) {
                // No reachable AudioManager: the request cannot be honoured, and
                // AOSP's own path here is also best-effort (the service call is
                // logged and swallowed on RemoteException).
            }
        }

        private List<MediaRoute2Info> getRoutesWithIds(List<String> ids) {
            List<MediaRoute2Info> out = new ArrayList<>();
            for (String id : ids) {
                if (SYSTEM_ROUTE_ID.equals(id)) out.add(mSystemRoute);
            }
            return Collections.unmodifiableList(out);
        }
    }

    private static final class RouteCallbackRecord {
        final Executor mExecutor;
        final RouteCallback mRouteCallback;
        final RouteDiscoveryPreference mPreference;
        RouteCallbackRecord(Executor e, RouteCallback c, RouteDiscoveryPreference p) {
            mExecutor = e; mRouteCallback = c; mPreference = p;
        }
    }

    private static final class TransferCallbackRecord {
        final Executor mExecutor;
        final TransferCallback mCallback;
        TransferCallbackRecord(Executor e, TransferCallback c) { mExecutor = e; mCallback = c; }
    }

    private static final class ControllerCallbackRecord {
        final Executor mExecutor;
        final ControllerCallback mCallback;
        ControllerCallbackRecord(Executor e, ControllerCallback c) { mExecutor = e; mCallback = c; }
    }

    private static final class ListingPreferenceCallbackRecord {
        final Executor mExecutor;
        final Consumer<RouteListingPreference> mCallback;
        ListingPreferenceCallbackRecord(Executor e, Consumer<RouteListingPreference> c) {
            mExecutor = e; mCallback = c;
        }
    }
}
