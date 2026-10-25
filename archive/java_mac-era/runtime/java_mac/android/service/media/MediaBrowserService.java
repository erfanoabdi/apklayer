package android.service.media;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import java.util.List;

/**
 * Base for a media-browser service (Auto/Assistant/Wear browse trees). Apps
 * extend it and override onGetRoot/onLoadChildren. Mac.apk has no external
 * browse client, so the service runs its lifecycle but nothing queries it --
 * the same as a device with no connected browser. The type + lifecycle hooks
 * must exist for the app's subclass to compile and instantiate.
 *
 * Source: frameworks/base/media/java/android/service/media/MediaBrowserService.java
 */
public abstract class MediaBrowserService extends Service {

    public static final String SERVICE_INTERFACE = "android.media.browse.MediaBrowserService";
    public static final String KEY_MEDIA_ITEM = "media_item";
    public static final String KEY_SEARCH_RESULTS = "search_results";

    private android.media.session.MediaSession.Token mToken;
    private Bundle mRootHints;

    @Override public void onCreate() { super.onCreate(); }

    @Override public IBinder onBind(Intent intent) { return null; }

    public abstract BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints);

    public abstract void onLoadChildren(String parentId, Result<List<android.media.browse.MediaBrowser.MediaItem>> result);

    public void onLoadChildren(String parentId,
            Result<List<android.media.browse.MediaBrowser.MediaItem>> result, Bundle options) {
        onLoadChildren(parentId, result);
    }

    public void onLoadItem(String itemId,
            Result<android.media.browse.MediaBrowser.MediaItem> result) {
        result.sendResult(null);
    }

    public void setSessionToken(android.media.session.MediaSession.Token token) { mToken = token; }
    public android.media.session.MediaSession.Token getSessionToken() { return mToken; }

    public final Bundle getBrowserRootHints() { return mRootHints; }

    public final android.media.session.MediaSessionManager.RemoteUserInfo getCurrentBrowserInfo() {
        return null;
    }

    public void notifyChildrenChanged(String parentId) { }
    public void notifyChildrenChanged(String parentId, Bundle options) { }

    /** The root of a browse tree returned from onGetRoot. */
    public static final class BrowserRoot {
        public static final String EXTRA_RECENT = "android.service.media.extra.RECENT";
        public static final String EXTRA_OFFLINE = "android.service.media.extra.OFFLINE";
        public static final String EXTRA_SUGGESTED = "android.service.media.extra.SUGGESTED";

        private final String mRootId;
        private final Bundle mExtras;

        public BrowserRoot(String rootId, Bundle extras) {
            if (rootId == null) {
                throw new IllegalArgumentException("The root id in BrowserRoot cannot be null.");
            }
            mRootId = rootId;
            mExtras = extras;
        }
        public String getRootId() { return mRootId; }
        public Bundle getExtras() { return mExtras; }
    }

    /** Async result the service fills in from onLoadChildren/onLoadItem. */
    public static class Result<T> {
        private boolean mDetachCalled;
        private boolean mSendResultCalled;

        Result() { }

        public void sendResult(T result) { mSendResultCalled = true; }
        public void detach() { mDetachCalled = true; }
        public void sendError(Bundle extras) { }
        boolean isDone() { return mDetachCalled || mSendResultCalled; }
    }
}
