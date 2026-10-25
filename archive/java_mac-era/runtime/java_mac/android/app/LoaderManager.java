// android.app.LoaderManager -- the platform (pre-androidx) data-loader
// coordinator returned by Activity.getLoaderManager() / Fragment
// .getLoaderManager(). Deprecated in API-28 in favor of
// androidx.loader.app.LoaderManager, but legacy apps still use it.
// AOSP API-11+.
//
// AOSP ref: frameworks/base/core/java/android/app/LoaderManager.java
// (abstract) + LoaderManagerImpl. Mac.apk drives loaders synchronously:
// initLoader/restartLoader create the Loader via the callbacks, start it,
// and -- because our Loaders are typically CursorLoaders that load on
// the calling thread -- deliver onLoadFinished immediately. This makes a
// list-backed legacy Activity populate rather than stay empty.
package android.app;

import android.content.Loader;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

public class LoaderManager {

    /** App-supplied factory + result sink for one loader id. */
    public interface LoaderCallbacks<D> {
        Loader<D> onCreateLoader(int id, Bundle args);
        void onLoadFinished(Loader<D> loader, D data);
        void onLoaderReset(Loader<D> loader);
    }

    private final Map<Integer, Loader<?>> mLoaders = new HashMap<>();

    /** AOSP initLoader: reuse an existing loader for `id`, else create +
     *  start one via the callbacks. */
    @SuppressWarnings("unchecked")
    public <D> Loader<D> initLoader(int id, Bundle args, LoaderCallbacks<D> callback) {
        Loader<?> existing = mLoaders.get(id);
        if (existing != null) return (Loader<D>) existing;
        return createAndStart(id, args, callback);
    }

    /** AOSP restartLoader: discard any existing loader for `id`, then
     *  create + start a fresh one. */
    public <D> Loader<D> restartLoader(int id, Bundle args, LoaderCallbacks<D> callback) {
        destroyLoader(id);
        return createAndStart(id, args, callback);
    }

    @SuppressWarnings("unchecked")
    private <D> Loader<D> createAndStart(int id, Bundle args, LoaderCallbacks<D> callback) {
        if (callback == null) return null;
        Loader<D> loader = callback.onCreateLoader(id, args);
        if (loader == null) return null;
        mLoaders.put(id, loader);
        // Register a listener that forwards to the callback, then start.
        loader.registerListener(id, (Loader.OnLoadCompleteListener<D>) (ldr, data) -> {
            try { callback.onLoadFinished(ldr, data); } catch (Throwable ignored) {}
        });
        try { loader.startLoading(); } catch (Throwable ignored) {}
        return loader;
    }

    public void destroyLoader(int id) {
        Loader<?> loader = mLoaders.remove(id);
        if (loader != null) {
            try { loader.stopLoading(); loader.reset(); } catch (Throwable ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    public <D> Loader<D> getLoader(int id) { return (Loader<D>) mLoaders.get(id); }

    public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {}

    @Deprecated public static void enableDebugLogging(boolean enabled) {}
}
