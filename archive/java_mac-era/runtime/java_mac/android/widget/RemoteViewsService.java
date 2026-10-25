// android.widget.RemoteViewsService -- a Service that supplies a
// RemoteViewsFactory (the data source for a collection app-widget such as a
// ListView/GridView widget). Was Absent. We expose the abstract factory
// contract + binder; no app-widget host drives it locally. AOSP:
//   frameworks/base/core/java/android/widget/RemoteViewsService.java
//   public abstract class RemoteViewsService extends Service
package android.widget;

import android.content.Intent;
import android.os.IBinder;

public abstract class RemoteViewsService extends android.app.Service {

    public interface RemoteViewsFactory {
        void onCreate();
        void onDataSetChanged();
        void onDestroy();
        int getCount();
        RemoteViews getViewAt(int position);
        RemoteViews getLoadingView();
        int getViewTypeCount();
        long getItemId(int position);
        boolean hasStableIds();
    }

    public RemoteViewsService() { super(); }

    @Override public IBinder onBind(Intent intent) {
        // AOSP returns a binder bridging the host to the factory. We have no
        // app-widget host process, so the factory is created but no remote
        // binding occurs.
        onGetViewFactory(intent);
        return null;
    }

    public abstract RemoteViewsFactory onGetViewFactory(Intent intent);
}
