// android.appwidget.AppWidgetManager -- the home-screen-widget service. Apps with
// a Glance/RemoteViews widget (Jetchat) reference AppWidgetManager.getInstance()
// at startup / in their widget receiver; a missing class threw NoClassDefFoundError
// (154x -> blank). Mac.apk has no home screen, so there are no placed widgets:
// every query honestly returns "no widgets" (empty id arrays), matching a device
// where the user never added the widget. v1.0.429.
//
// That honesty is DECLARED, not just implied: MacApkPackageManager's
// SYSTEM_FEATURES set does not contain "android.software.app_widgets", so an app
// that checks hasSystemFeature(FEATURE_APP_WIDGETS) first and an app that just
// calls these methods learn the same thing. AppWidgetHost's class comment traces
// the same reasoning through AOSP's own sService==null branch.
//
// Source: frameworks/base core/java/android/appwidget/AppWidgetManager.java
// (android16-release)
package android.appwidget;

import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

public class AppWidgetManager {
    public static final String ACTION_APPWIDGET_UPDATE   = "android.appwidget.action.APPWIDGET_UPDATE";
    public static final String ACTION_APPWIDGET_DELETED  = "android.appwidget.action.APPWIDGET_DELETED";
    public static final String ACTION_APPWIDGET_ENABLED  = "android.appwidget.action.APPWIDGET_ENABLED";
    public static final String ACTION_APPWIDGET_DISABLED = "android.appwidget.action.APPWIDGET_DISABLED";
    public static final String ACTION_APPWIDGET_RESTORED = "android.appwidget.action.APPWIDGET_RESTORED";
    public static final String ACTION_APPWIDGET_HOST_RESTORED =
            "android.appwidget.action.APPWIDGET_HOST_RESTORED";
    public static final String ACTION_APPWIDGET_OPTIONS_CHANGED =
            "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS";
    public static final String ACTION_APPWIDGET_BIND = "android.appwidget.action.APPWIDGET_BIND";
    public static final String ACTION_APPWIDGET_CONFIGURE =
            "android.appwidget.action.APPWIDGET_CONFIGURE";
    public static final String ACTION_APPWIDGET_PICK = "android.appwidget.action.APPWIDGET_PICK";

    public static final String EXTRA_APPWIDGET_ID  = "appWidgetId";
    public static final String EXTRA_APPWIDGET_IDS = "appWidgetIds";
    public static final String EXTRA_APPWIDGET_OLD_IDS = "appWidgetOldIds";
    public static final String EXTRA_APPWIDGET_OPTIONS  = "appWidgetOptions";
    public static final String EXTRA_APPWIDGET_PREVIEW  = "appWidgetPreview";
    public static final String EXTRA_APPWIDGET_PROVIDER = "appWidgetProvider";
    public static final String EXTRA_APPWIDGET_PROVIDER_PROFILE = "appWidgetProviderProfile";
    public static final String EXTRA_CUSTOM_EXTRAS = "customExtras";
    public static final String EXTRA_CUSTOM_INFO   = "customInfo";
    public static final String EXTRA_HOST_ID       = "hostId";

    public static final String EVENT_CATEGORY_APPWIDGET = "android.appwidget";
    public static final String EVENT_TYPE_WIDGET_INTERACTION = "widget_interaction";
    public static final String EXTRA_EVENT_CLICKED_VIEWS =
            "android.appwidget.extra.EVENT_CLICKED_VIEWS";
    public static final String EXTRA_EVENT_DURATION_MS =
            "android.appwidget.extra.EVENT_DURATION_MS";
    public static final String EXTRA_EVENT_POSITION_RECT =
            "android.appwidget.extra.EVENT_POSITION_RECT";
    public static final String EXTRA_EVENT_SCROLLED_VIEWS =
            "android.appwidget.extra.EVENT_SCROLLED_VIEWS";

    public static final int    INVALID_APPWIDGET_ID = 0;

    public static final String META_DATA_APPWIDGET_PROVIDER = "android.appwidget.provider";

    public static final String OPTION_APPWIDGET_MIN_WIDTH  = "appWidgetMinWidth";
    public static final String OPTION_APPWIDGET_MIN_HEIGHT = "appWidgetMinHeight";
    public static final String OPTION_APPWIDGET_MAX_WIDTH  = "appWidgetMaxWidth";
    public static final String OPTION_APPWIDGET_MAX_HEIGHT = "appWidgetMaxHeight";
    public static final String OPTION_APPWIDGET_HOST_CATEGORY = "appWidgetCategory";
    public static final String OPTION_APPWIDGET_RESTORE_COMPLETED = "appWidgetRestoreCompleted";
    public static final String OPTION_APPWIDGET_SIZES = "appWidgetSizes";

    private static final AppWidgetManager INSTANCE = new AppWidgetManager();
    public static AppWidgetManager getInstance(Context context) { return INSTANCE; }

    /** AOSP round-trips a widget's options through the system, and
     *  getAppWidgetOptions() reads back whatever was last written. There is no
     *  system here, but the round-trip is host-local bookkeeping rather than a
     *  fabricated widget, so it is kept honestly in this map: an app that calls
     *  updateAppWidgetOptions() and then getAppWidgetOptions() sees its own
     *  values, which is what AppWidgetHostView.getDefaultView() relies on when
     *  it reads OPTION_APPWIDGET_HOST_CATEGORY. It does NOT make a widget
     *  exist -- getAppWidgetIds() is still empty and getAppWidgetInfo() is
     *  still null. */
    private final java.util.HashMap<Integer, android.os.Bundle> mOptions =
            new java.util.HashMap<>();

    // No placed widgets in our env -> empty id sets, no-op updates.
    public int[] getAppWidgetIds(ComponentName provider) { return new int[0]; }
    public void updateAppWidget(int appWidgetId, RemoteViews views) {}
    public void updateAppWidget(int[] appWidgetIds, RemoteViews views) {}
    public void updateAppWidget(ComponentName provider, RemoteViews views) {}
    public void partiallyUpdateAppWidget(int appWidgetId, RemoteViews views) {}
    public void partiallyUpdateAppWidget(int[] appWidgetIds, RemoteViews views) {}
    public void notifyAppWidgetViewDataChanged(int[] appWidgetIds, int viewId) {}
    public void notifyAppWidgetViewDataChanged(int appWidgetId, int viewId) {}
    public AppWidgetProviderInfo getAppWidgetInfo(int appWidgetId) { return null; }

    public synchronized android.os.Bundle getAppWidgetOptions(int appWidgetId) {
        android.os.Bundle b = mOptions.get(appWidgetId);
        // AOSP never hands back null here (it returns Bundle.EMPTY on a failed
        // lookup), and callers dereference it without a guard.
        return b != null ? b : new android.os.Bundle();
    }

    public synchronized void updateAppWidgetOptions(int appWidgetId, android.os.Bundle options) {
        if (options == null) {
            mOptions.remove(appWidgetId);
        } else {
            mOptions.put(appWidgetId, options);
        }
    }

    public java.util.List<AppWidgetProviderInfo> getInstalledProviders() {
        return new java.util.ArrayList<>();
    }
    public java.util.List<AppWidgetProviderInfo> getInstalledProvidersForPackage(
            String packageName, android.os.UserHandle profile) {
        return new java.util.ArrayList<>();
    }

    /** AOSP returns every provider installed for the given profile. There is no
     *  home screen here and therefore no provider is ever installed as far as
     *  the widget layer is concerned, so the list is empty -- the same answer
     *  the two sibling queries above already give. @NonNull in the SDK, so an
     *  empty list rather than null. */
    public java.util.List<AppWidgetProviderInfo> getInstalledProvidersForProfile(
            android.os.UserHandle profile) {
        return new java.util.ArrayList<>();
    }

    public boolean isRequestPinAppWidgetSupported() { return false; }
    public boolean requestPinAppWidget(ComponentName provider, android.os.Bundle extras,
            android.app.PendingIntent successCallback) { return false; }

    /** AOSP: bind the widget id to a provider, succeeding only when the caller
     *  is allowed to bind without user confirmation (i.e. it is the default
     *  host). Nothing can be a widget host here, so binding always fails --
     *  which is also what an ordinary app gets on a real device. */
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, ComponentName provider) {
        return false;
    }

    /** Same, with provider options supplied at bind time. */
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, ComponentName provider,
            android.os.Bundle options) {
        return false;
    }

    /** Same, for a provider belonging to another profile. */
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, android.os.UserHandle user,
            ComponentName provider, android.os.Bundle options) {
        return false;
    }

    /** AOSP: replace a provider's AppWidgetProviderInfo with the one named by
     *  the metadata key, so a provider can swap its own definition at runtime.
     *  With no widget service there is no stored info to replace; AOSP's own
     *  behaviour in that case is likewise to change nothing observable, since
     *  getAppWidgetInfo() would still answer null. */
    public void updateAppWidgetProviderInfo(ComponentName provider, String metaDataKey) {}

    public boolean setWidgetPreview(android.content.ComponentName provider, int widgetCategories,
            android.widget.RemoteViews preview) { return false; }
    public android.widget.RemoteViews getWidgetPreview(android.content.ComponentName provider,
            android.os.UserHandle profile, int widgetCategory) { return null; }

    /** AOSP removes a previously-published generated preview for the given
     *  categories. setWidgetPreview() above cannot succeed here, so there is
     *  never a stored preview to remove. */
    public void removeWidgetPreview(android.content.ComponentName provider,
            int widgetCategories) {}
}
