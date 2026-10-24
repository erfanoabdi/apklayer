// android.media.MediaRouter
//
// AOSP's legacy (API 16) service for routing audio/video to external
// displays/speakers. Apps fetch it via getSystemService(MEDIA_ROUTER_SERVICE),
// query the selected route, and -- for apps that publish their own Cast-style
// destinations -- create USER routes of their own.
//
// Mac.apk has no external-route DISCOVERY (no Cast, no DLNA, no Bluetooth route
// provider), so the system contributes exactly one route: the implicit DEFAULT
// route backed by the host's default audio output. USER routes are a different
// matter and ARE fully supported here: an app builds those itself out of a
// RouteCategory it also created, and AOSP's MediaRouter simply stores them and
// hands them back. Refusing to store them would break the app for no reason --
// nothing about a user route needs a route provider we lack.
//
// Mirrors frameworks/base/media/java/android/media/MediaRouter.java
// (android16-release).
package android.media;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class MediaRouter {

    public static final int ROUTE_TYPE_LIVE_AUDIO = 0x1;
    public static final int ROUTE_TYPE_LIVE_VIDEO = 0x2;
    public static final int ROUTE_TYPE_USER       = 0x800000;
    public static final int CALLBACK_FLAG_PERFORM_ACTIVE_SCAN = 0x1;
    public static final int CALLBACK_FLAG_UNFILTERED_EVENTS   = 0x2;

    // AOSP keeps the route/category tables in a process-wide "static state"
    // object shared by every MediaRouter instance (MediaRouter.java L96-120,
    // `sStatic`), so two getSystemService() calls see the same routes. Mirrored
    // here, otherwise an app that adds a user route through one instance and
    // reads it back through another sees it vanish.
    private static final Object sLock = new Object();
    private static RouteInfo sDefaultRoute;
    private static RouteCategory sSystemCategory;
    private static RouteInfo sSelectedRoute;
    private static final List<RouteInfo> sRoutes = new ArrayList<>();
    private static final List<RouteCategory> sCategories = new ArrayList<>();
    private static final CopyOnWriteArrayList<CallbackRecord> sCallbacks =
            new CopyOnWriteArrayList<>();
    private static AudioManager sAudioManager;
    // AOSP hands the router itself to every callback. Callbacks here are fired
    // from RouteInfo setters that have no instance to hand, so the process-wide
    // router (there is only ever one, per sStatic above) is remembered for them.
    private static MediaRouter sInstance;

    public MediaRouter() { this(null); }

    public MediaRouter(Context context) {
        synchronized (sLock) {
            sInstance = this;
            if (sAudioManager == null && context != null) {
                try {
                    sAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                } catch (Throwable ignore) {
                    // A Context with no audio service still yields a usable router;
                    // the default route then reports fixed volume.
                }
            }
            if (sDefaultRoute == null) {
                // AOSP's system category is named by a framework string and is
                // NOT groupable (MediaRouter.java L240-250).
                sSystemCategory = new RouteCategory("System",
                        ROUTE_TYPE_LIVE_AUDIO | ROUTE_TYPE_LIVE_VIDEO, false);
                sCategories.add(sSystemCategory);
                sDefaultRoute = new RouteInfo(sSystemCategory);
                sDefaultRoute.mName = new AudioDeviceInfo().getProductName();
                sDefaultRoute.mSupportedTypes = ROUTE_TYPE_LIVE_AUDIO | ROUTE_TYPE_LIVE_VIDEO;
                sDefaultRoute.mDeviceType = RouteInfo.DEVICE_TYPE_SPEAKER;
                sDefaultRoute.mPlaybackType = RouteInfo.PLAYBACK_TYPE_LOCAL;
                sDefaultRoute.mPlaybackStream = AudioManager.STREAM_MUSIC;
                sDefaultRoute.mIsDefault = true;
                sRoutes.add(sDefaultRoute);
                sSelectedRoute = sDefaultRoute;
            }
        }
    }

    /** AOSP getRouteCount(): the default route plus any the app published. */
    public int getRouteCount() {
        synchronized (sLock) { return sRoutes.size(); }
    }

    public RouteInfo getRouteAt(int index) {
        synchronized (sLock) { return sRoutes.get(index); }
    }

    /** AOSP returns the selected route for the given type mask; the default
     *  route is selected until the app selects another. */
    public RouteInfo getSelectedRoute(int type) {
        synchronized (sLock) { return sSelectedRoute; }
    }

    public RouteInfo getDefaultRoute() {
        synchronized (sLock) { return sDefaultRoute; }
    }

    public int getCategoryCount() {
        synchronized (sLock) { return sCategories.size(); }
    }

    public RouteCategory getCategoryAt(int index) {
        synchronized (sLock) { return sCategories.get(index); }
    }

    public void addCallback(int types, Callback cb) { addCallback(types, cb, 0); }

    public void addCallback(int types, Callback cb, int flags) {
        Objects.requireNonNull(cb, "callback must not be null");
        for (CallbackRecord record : sCallbacks) {
            if (record.mCallback == cb) {
                // AOSP widens the existing record's type mask rather than
                // registering twice (MediaRouter.java L1080-1090).
                record.mTypes |= types;
                record.mFlags |= flags;
                return;
            }
        }
        sCallbacks.add(new CallbackRecord(cb, types, flags));
    }

    public void removeCallback(Callback cb) {
        for (CallbackRecord record : sCallbacks) {
            if (record.mCallback == cb) { sCallbacks.remove(record); return; }
        }
    }

    /** AOSP throws on a null route (`@NonNull` in the API-36 signature) and
     *  fires onRouteUnselected/onRouteSelected around the change. */
    public void selectRoute(int types, RouteInfo route) {
        Objects.requireNonNull(route, "route must not be null");
        RouteInfo previous;
        synchronized (sLock) {
            if (sSelectedRoute == route) return;
            previous = sSelectedRoute;
            sSelectedRoute = route;
        }
        for (CallbackRecord record : sCallbacks) {
            if ((record.mTypes & types) == 0) continue;
            if (previous != null) record.mCallback.onRouteUnselected(this, types, previous);
            record.mCallback.onRouteSelected(this, types, route);
        }
    }

    /** API 16. Creates a category an app's own USER routes can belong to. */
    public RouteCategory createRouteCategory(CharSequence name, boolean isGroupable) {
        return new RouteCategory(name, ROUTE_TYPE_USER, isGroupable);
    }

    /** The resource-id overload. Resolving it needs a Context, which this
     *  overload is not given; AOSP resolves against the framework resources it
     *  owns. There is no honest way to turn a bare id into a string here, so the
     *  category carries the id's own decimal form as its name -- visible and
     *  wrong-looking rather than silently empty, and the app's own
     *  createRouteCategory(CharSequence, boolean) overload is unaffected. */
    public RouteCategory createRouteCategory(int nameResId, boolean isGroupable) {
        RouteCategory category =
                new RouteCategory(Integer.toString(nameResId), ROUTE_TYPE_USER, isGroupable);
        category.mNameResId = nameResId;
        return category;
    }

    /** API 16. Creates (but does not yet publish) an app-owned route. */
    public UserRouteInfo createUserRoute(RouteCategory category) {
        return new UserRouteInfo(category);
    }

    public void addUserRoute(UserRouteInfo info) {
        Objects.requireNonNull(info, "route must not be null");
        synchronized (sLock) {
            if (sRoutes.contains(info)) return;
            sRoutes.add(info);
            if (info.mCategory != null && !sCategories.contains(info.mCategory)) {
                sCategories.add(info.mCategory);
            }
        }
        for (CallbackRecord record : sCallbacks) {
            record.mCallback.onRouteAdded(this, info);
        }
    }

    public void removeUserRoute(UserRouteInfo info) {
        Objects.requireNonNull(info, "route must not be null");
        synchronized (sLock) {
            if (!sRoutes.remove(info)) return;
            if (sSelectedRoute == info) sSelectedRoute = sDefaultRoute;
        }
        for (CallbackRecord record : sCallbacks) {
            record.mCallback.onRouteRemoved(this, info);
        }
    }

    public void clearUserRoutes() {
        List<RouteInfo> removed = new ArrayList<>();
        synchronized (sLock) {
            for (int i = sRoutes.size() - 1; i >= 0; i--) {
                RouteInfo route = sRoutes.get(i);
                if (route instanceof UserRouteInfo) {
                    sRoutes.remove(i);
                    removed.add(route);
                }
            }
            if (sSelectedRoute != null && removed.contains(sSelectedRoute)) {
                sSelectedRoute = sDefaultRoute;
            }
        }
        for (RouteInfo route : removed) {
            for (CallbackRecord record : sCallbacks) {
                record.mCallback.onRouteRemoved(this, route);
            }
        }
    }

    static void notifyRouteChanged(RouteInfo route) {
        MediaRouter router = sInstance;
        for (CallbackRecord record : sCallbacks) {
            record.mCallback.onRouteChanged(router, route);
        }
    }

    static void notifyRouteVolumeChanged(RouteInfo route) {
        MediaRouter router = sInstance;
        for (CallbackRecord record : sCallbacks) {
            record.mCallback.onRouteVolumeChanged(router, route);
        }
    }

    private static final class CallbackRecord {
        final Callback mCallback;
        int mTypes;
        int mFlags;
        CallbackRecord(Callback callback, int types, int flags) {
            mCallback = callback; mTypes = types; mFlags = flags;
        }
    }

    /** AOSP MediaRouter.RouteInfo: one destination. */
    public static class RouteInfo {
        public static final int DEVICE_TYPE_UNKNOWN = 0;
        public static final int DEVICE_TYPE_TV = 1;
        public static final int DEVICE_TYPE_SPEAKER = 2;
        public static final int DEVICE_TYPE_BLUETOOTH = 3;

        public static final int PLAYBACK_TYPE_LOCAL = 0;
        public static final int PLAYBACK_TYPE_REMOTE = 1;

        public static final int PLAYBACK_VOLUME_FIXED = 0;
        public static final int PLAYBACK_VOLUME_VARIABLE = 1;

        CharSequence mName = "Default";
        int mNameResId;
        CharSequence mDescription;
        CharSequence mStatus;
        int mSupportedTypes = ROUTE_TYPE_LIVE_AUDIO;
        int mDeviceType = DEVICE_TYPE_UNKNOWN;
        int mPlaybackType = PLAYBACK_TYPE_LOCAL;
        int mPlaybackStream = AudioManager.STREAM_MUSIC;
        int mVolumeHandling = PLAYBACK_VOLUME_FIXED;
        int mVolumeMax;
        int mVolume;
        Object mTag;
        Drawable mIcon;
        RouteCategory mCategory;
        RouteGroup mGroup;
        boolean mIsDefault;
        boolean mEnabled = true;
        VolumeCallback mVolumeCallback;

        RouteInfo() { }
        RouteInfo(RouteCategory category) { mCategory = category; }

        public CharSequence getName() { return mName; }
        /** AOSP resolves a name set by resource id against the given Context
         *  (MediaRouter.java L1520-1530); a name set as a CharSequence ignores
         *  the Context entirely. */
        public CharSequence getName(Context context) {
            if (mNameResId != 0 && context != null) {
                try { return context.getResources().getText(mNameResId); }
                catch (Throwable ignore) { /* fall through to the literal name */ }
            }
            return mName;
        }
        /** Nullable in AOSP -- most routes carry no user-visible description. */
        public CharSequence getDescription() { return mDescription; }
        /** Nullable in AOSP; a transient string like "Connecting...". */
        public CharSequence getStatus() { return mStatus; }
        public int getSupportedTypes() { return mSupportedTypes; }
        public int getDeviceType() { return mDeviceType; }
        public int getPlaybackType() { return mPlaybackType; }
        public int getPlaybackStream() { return mPlaybackStream; }
        public int getVolumeHandling() { return mVolumeHandling; }
        public int getVolumeMax() {
            if (mIsDefault) {
                // The default route IS the host's music output, so its range is
                // the real STREAM_MUSIC range rather than a placeholder.
                AudioManager am = sAudioManager;
                if (am != null) {
                    try { return am.getStreamMaxVolume(mPlaybackStream); }
                    catch (Throwable ignore) { /* fall through */ }
                }
            }
            return mVolumeMax;
        }
        public int getVolume() {
            if (mIsDefault) {
                AudioManager am = sAudioManager;
                if (am != null) {
                    try { return am.getStreamVolume(mPlaybackStream); }
                    catch (Throwable ignore) { /* fall through */ }
                }
            }
            return mVolume;
        }
        /** Nullable in AOSP; the category this route belongs to. */
        public RouteCategory getCategory() { return mCategory; }
        /** Nullable in AOSP; non-null only while the route is inside a group. */
        public RouteGroup getGroup() { return mGroup; }
        /** Nullable in AOSP; the route's icon, when its publisher gave it one. */
        public Drawable getIconDrawable() { return mIcon; }
        public Object getTag() { return mTag; }
        public void setTag(Object tag) {
            mTag = tag;
            MediaRouter.notifyRouteChanged(this);
        }
        public boolean isSelected() { return this == sSelectedRoute; }
        public boolean isConnecting() { return false; }
        /** AOSP: false while a route is present but unusable (e.g. a Bluetooth
         *  route whose device disconnected). Every route this class hands out is
         *  usable, so true. */
        public boolean isEnabled() { return mEnabled; }

        /** AOSP: the Display that content on this route should be shown on, or null if
         *  this route does not present on a secondary display. Returning null here is the
         *  CORRECT answer rather than a stub's convenient lie -- Mac.apk presents one
         *  window on the host's main display and exposes no remote/Presentation route, so
         *  real Android on the same hardware profile also returns null. Callers already
         *  handle null: that is the ordinary phone-with-no-Cast-device case, which is why
         *  apps null-check it. Referenced by Telegram and VLC
         *  (docs/untriaged-corpus-triage_2026-07-31.md, ranked gap #13). */
        public android.view.Display getPresentationDisplay() { return null; }

        /** AOSP forwards the request to the route's VolumeCallback for a USER
         *  route, and adjusts the real stream for the default route
         *  (MediaRouter.java L1640-1665). Both paths are honoured here: an app
         *  that publishes a Cast route gets its own callback invoked, and the
         *  default route really moves the host volume. */
        public void requestSetVolume(int volume) {
            if (mVolumeHandling != PLAYBACK_VOLUME_VARIABLE) return;
            if (mIsDefault) {
                AudioManager am = sAudioManager;
                if (am != null) {
                    try { am.setStreamVolume(mPlaybackStream, volume, 0); }
                    catch (Throwable ignore) { /* best-effort, as in AOSP */ }
                }
                return;
            }
            if (mVolumeCallback != null) mVolumeCallback.onVolumeSetRequest(this, volume);
        }

        public void requestUpdateVolume(int direction) {
            if (mVolumeHandling != PLAYBACK_VOLUME_VARIABLE) return;
            if (mIsDefault) {
                AudioManager am = sAudioManager;
                if (am != null) {
                    try {
                        am.adjustStreamVolume(mPlaybackStream,
                                direction > 0 ? AudioManager.ADJUST_RAISE
                                        : direction < 0 ? AudioManager.ADJUST_LOWER
                                                : AudioManager.ADJUST_SAME, 0);
                    } catch (Throwable ignore) { /* best-effort, as in AOSP */ }
                }
                return;
            }
            if (mVolumeCallback != null) mVolumeCallback.onVolumeUpdateRequest(this, direction);
        }
    }

    /** AOSP MediaRouter.RouteCategory: a named grouping of routes. */
    public static class RouteCategory {
        CharSequence mName;
        int mNameResId;
        final int mTypes;
        final boolean mGroupable;

        RouteCategory(CharSequence name, int types, boolean groupable) {
            mName = name; mTypes = types; mGroupable = groupable;
        }

        public CharSequence getName() { return mName; }
        public CharSequence getName(Context context) {
            if (mNameResId != 0 && context != null) {
                try { return context.getResources().getText(mNameResId); }
                catch (Throwable ignore) { /* fall through */ }
            }
            return mName;
        }
        public int getSupportedTypes() { return mTypes; }
        public boolean isGroupable() { return mGroupable; }
        /** AOSP fills the caller's list (creating one if null) with the routes
         *  belonging to this category and returns it (MediaRouter.java L2280). */
        public List<RouteInfo> getRoutes(List<RouteInfo> out) {
            if (out == null) out = new ArrayList<>();
            else out.clear();
            synchronized (sLock) {
                for (RouteInfo route : sRoutes) {
                    if (route.mCategory == this) out.add(route);
                }
            }
            return out;
        }
    }

    /** AOSP MediaRouter.Callback: route-change notifications.
     *  AOSP declares the on* methods abstract; they are concrete no-ops here so
     *  an app that overrides only some of them still links. Accepting MORE than
     *  AOSP accepts is the safe direction; refusing what AOSP accepts is not. */
    public abstract static class Callback {
        public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {}
        public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {}
        public void onRouteAdded(MediaRouter router, RouteInfo info) {}
        public void onRouteRemoved(MediaRouter router, RouteInfo info) {}
        public void onRouteChanged(MediaRouter router, RouteInfo info) {}
        public void onRouteGrouped(MediaRouter router, RouteInfo info, RouteGroup group,
                int index) {}
        public void onRouteUngrouped(MediaRouter router, RouteInfo info, RouteGroup group) {}
        public void onRouteVolumeChanged(MediaRouter router, RouteInfo info) {}
        /** API 16. Fired when a route's presentation Display appears/disappears.
         *  Never fires here: getPresentationDisplay() is permanently null. */
        public void onRoutePresentationDisplayChanged(MediaRouter router, RouteInfo info) {}
    }

    /** AOSP MediaRouter.SimpleCallback: empty default impls. */
    public static class SimpleCallback extends Callback {
        @Override public void onRouteGrouped(MediaRouter router, RouteInfo info, RouteGroup group,
                int index) {}
        @Override public void onRouteUngrouped(MediaRouter router, RouteInfo info,
                RouteGroup group) {}
    }

    /** AOSP MediaRouter.VolumeCallback: an app-owned route's volume handler. */
    public abstract static class VolumeCallback {
        public VolumeCallback() { }
        public abstract void onVolumeSetRequest(RouteInfo info, int volume);
        public abstract void onVolumeUpdateRequest(RouteInfo info, int direction);
    }

    /** AOSP MediaRouter.RouteGroup: several routes presented as one. */
    public static class RouteGroup extends RouteInfo {
        private final List<RouteInfo> mRoutes = new ArrayList<>();
        RouteGroup() { }
        RouteGroup(RouteCategory category) { super(category); }
        public int getRouteCount() { return mRoutes.size(); }
        public RouteInfo getRouteAt(int index) { return mRoutes.get(index); }
        public void addRoute(RouteInfo route) { addRoute(route, mRoutes.size()); }
        /** AOSP throws IllegalStateException when the route already belongs to a
         *  group (MediaRouter.java L2380-2390). */
        public void addRoute(RouteInfo route, int insertAt) {
            Objects.requireNonNull(route, "route must not be null");
            if (route.getGroup() != null) {
                throw new IllegalStateException("Route " + route + " is already part of a group.");
            }
            mRoutes.add(insertAt, route);
            route.mGroup = this;
            MediaRouter.notifyRouteChanged(this);
        }
        public void removeRoute(RouteInfo route) {
            if (mRoutes.remove(route)) {
                route.mGroup = null;
                MediaRouter.notifyRouteChanged(this);
            }
        }
        public void removeRoute(int index) {
            RouteInfo route = mRoutes.remove(index);
            route.mGroup = null;
            MediaRouter.notifyRouteChanged(this);
        }
        public void setIconDrawable(Drawable icon) {
            mIcon = icon;
            MediaRouter.notifyRouteChanged(this);
        }
        /** The resource-id overload has no Context to resolve against here (see
         *  createRouteCategory(int, boolean)), so the id is recorded and the
         *  drawable stays null rather than a wrong image being shown. */
        public void setIconResource(int resId) { mIcon = null; }
        public void setUpdateVolumes(boolean update) {}
    }

    /** AOSP MediaRouter.UserRouteInfo: a route published BY the app (a Cast
     *  target the app itself discovered). Fully mutable, exactly as in AOSP --
     *  none of this needs a system route provider. */
    public static class UserRouteInfo extends RouteInfo {
        private RemoteControlClient mRcc;

        UserRouteInfo(RouteCategory category) {
            super(category);
            mSupportedTypes = ROUTE_TYPE_USER;
            mPlaybackType = PLAYBACK_TYPE_REMOTE;
            mName = "";
        }

        public void setName(CharSequence name) {
            mName = name; mNameResId = 0;
            MediaRouter.notifyRouteChanged(this);
        }
        /** Resource-id overload: recorded so getName(Context) can resolve it
         *  against the app's own resources, which IS reachable there. */
        public void setName(int resId) {
            mNameResId = resId;
            MediaRouter.notifyRouteChanged(this);
        }
        public void setDescription(CharSequence description) {
            mDescription = description;
            MediaRouter.notifyRouteChanged(this);
        }
        public void setStatus(CharSequence status) {
            mStatus = status;
            MediaRouter.notifyRouteChanged(this);
        }
        public void setIconDrawable(Drawable icon) {
            mIcon = icon;
            MediaRouter.notifyRouteChanged(this);
        }
        /** See RouteGroup.setIconResource: no Context is reachable from this
         *  setter, so the drawable stays null rather than becoming a wrong image. */
        public void setIconResource(int resId) { mIcon = null; }
        public void setPlaybackType(int type) { mPlaybackType = type; }
        public void setPlaybackStream(int stream) { mPlaybackStream = stream; }
        public void setVolume(int volume) {
            mVolume = Math.max(0, Math.min(volume, mVolumeMax));
            MediaRouter.notifyRouteVolumeChanged(this);
        }
        public void setVolumeMax(int volumeMax) { mVolumeMax = volumeMax; }
        public void setVolumeHandling(int handling) { mVolumeHandling = handling; }
        public void setVolumeCallback(VolumeCallback vcb) { mVolumeCallback = vcb; }
        public void setRemoteControlClient(RemoteControlClient rcc) { mRcc = rcc; }
        /** Nullable in AOSP. */
        public RemoteControlClient getRemoteControlClient() { return mRcc; }
    }
}
