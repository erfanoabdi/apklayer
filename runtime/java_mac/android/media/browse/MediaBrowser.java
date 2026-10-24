// android.media.browse.MediaBrowser -- client for browsing a MediaBrowserService's
// content tree. AOSP frameworks/base media/java/android/media/browse/MediaBrowser.java.
//
// Mac.apk has no MediaBrowserService binding mechanism (no Binder IPC), so a
// connect() attempt fails cleanly via onConnectionFailed and subscribe()/getItem
// report no children/null item. The class links so apps that construct a browser
// (Android Auto / Wear media clients) run without a NoSuchMethodError; there is
// simply no service to browse -- honest for a single-process host.
package android.media.browse;

public final class MediaBrowser {
    public static final String EXTRA_PAGE = "android.media.browse.extra.PAGE";
    public static final String EXTRA_PAGE_SIZE = "android.media.browse.extra.PAGE_SIZE";

    private final ConnectionCallback mConnectionCallback;

    public MediaBrowser(android.content.Context context, android.content.ComponentName serviceComponent,
                        ConnectionCallback callback, android.os.Bundle rootHints) {
        mConnectionCallback = callback;
    }

    public void connect() {
        // No service to bind; report failure so the client's reconnect/fallback
        // path runs rather than hanging on a never-arriving onConnected.
        if (mConnectionCallback != null) {
            try { mConnectionCallback.onConnectionFailed(); } catch (Throwable ignore) {}
        }
    }
    public void disconnect() {}
    public boolean isConnected() { return false; }
    public android.content.ComponentName getServiceComponent() { return null; }
    public String getRoot() { return ""; }
    public android.os.Bundle getExtras() { return null; }
    public android.media.session.MediaSession.Token getSessionToken() { return null; }

    public void subscribe(String parentId, SubscriptionCallback callback) {
        if (callback != null) {
            try { callback.onError(parentId); } catch (Throwable ignore) {}
        }
    }
    public void subscribe(String parentId, android.os.Bundle options, SubscriptionCallback callback) {
        subscribe(parentId, callback);
    }
    public void unsubscribe(String parentId) {}
    public void unsubscribe(String parentId, SubscriptionCallback callback) {}
    public void getItem(String mediaId, ItemCallback cb) {
        if (cb != null) {
            try { cb.onError(mediaId); } catch (Throwable ignore) {}
        }
    }

    // AOSP's ConnectionCallback is concrete (frameworks/base
    // android11-release media/java/android/media/browse/MediaBrowser.java:866,
    // "public static class ConnectionCallback") -- unlike its siblings
    // SubscriptionCallback/ItemCallback just below, which really are abstract
    // in AOSP too (:889, :950) and so are NOT divergent. All three methods
    // here were already no-op bodies, not abstract declarations, so dropping
    // the class-level `abstract` invents nothing. type-identity audit
    // (docs/type-identity-audit_2026-08-12.md) flagged this as harmful: an
    // app (or R8, vertical-merging an empty anonymous override) can emit
    // `new MediaBrowser$ConnectionCallback` directly, which threw
    // InstantiationError against our abstract class but succeeds on AOSP.
    public static class ConnectionCallback {
        public ConnectionCallback() {}
        public void onConnected() {}
        public void onConnectionSuspended() {}
        public void onConnectionFailed() {}
    }
    public abstract static class SubscriptionCallback {
        public SubscriptionCallback() {}
        public void onChildrenLoaded(String parentId, java.util.List<MediaItem> children) {}
        public void onChildrenLoaded(String parentId, java.util.List<MediaItem> children, android.os.Bundle options) {}
        public void onError(String parentId) {}
        public void onError(String parentId, android.os.Bundle options) {}
    }
    public abstract static class ItemCallback {
        public ItemCallback() {}
        public void onItemLoaded(MediaItem item) {}
        public void onError(String mediaId) {}
    }

    public static class MediaItem implements android.os.Parcelable {
        public static final int FLAG_BROWSABLE = 1 << 0;
        public static final int FLAG_PLAYABLE = 1 << 1;
        private final int mFlags;
        private final android.media.MediaDescription mDescription;
        public MediaItem(android.media.MediaDescription description, int flags) {
            mDescription = description;
            mFlags = flags;
        }
        public int getFlags() { return mFlags; }
        public boolean isBrowsable() { return (mFlags & FLAG_BROWSABLE) != 0; }
        public boolean isPlayable() { return (mFlags & FLAG_PLAYABLE) != 0; }
        public android.media.MediaDescription getDescription() { return mDescription; }
        public String getMediaId() {
            return mDescription != null ? mDescription.getMediaId() : null;
        }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeInt(mFlags);
            if (mDescription != null) mDescription.writeToParcel(dest, flags);
        }
        public static final android.os.Parcelable.Creator<MediaItem> CREATOR =
            new android.os.Parcelable.Creator<MediaItem>() {
                @Override public MediaItem createFromParcel(android.os.Parcel in) {
                    int flags = in.readInt();
                    return new MediaItem(android.media.MediaDescription.CREATOR.createFromParcel(in), flags);
                }
                @Override public MediaItem[] newArray(int size) { return new MediaItem[size]; }
            };
    }
}
