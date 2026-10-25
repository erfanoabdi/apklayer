// android.appwidget.AppWidgetHost -- the object a launcher (or any app that
// wants to display widgets) uses to allocate widget ids and build host views.
//
// ⚠ WHY EVERY METHOD BELOW LOOKS LIKE A NO-OP, AND WHY THAT IS AOSP-CORRECT
// RATHER THAN A STUB:
//
// AOSP's AppWidgetHost binds to IAppWidgetService in a private static
// bindService(), and that bind is CONDITIONAL:
//
//     PackageManager packageManager = context.getPackageManager();
//     if (!packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)
//             && !context.getResources().getBoolean(R.bool.config_enableAppWidgetService)) {
//         return;                      // sService stays null
//     }
//
// -- frameworks/base core/java/android/appwidget/AppWidgetHost.java
// (android16-release). Every public method then begins `if (sService == null)
// return <nothing>;`, because a device without the app-widget feature is a
// supported configuration that AOSP handles explicitly.
//
// Mac.apk is exactly that device: MacApkPackageManager's SYSTEM_FEATURES set
// does not contain "android.software.app_widgets" (it cannot honestly, since a
// single-app host has no home screen and nothing to place a widget on), so
// hasSystemFeature(FEATURE_APP_WIDGETS) is false. So the bodies here are not
// invented degradations -- they are AOSP's own sService==null branch, method
// for method, and they keep the two answers CONSISTENT: an app that checks the
// feature first and an app that just calls the API both learn the same thing.
//
// Source: frameworks/base core/java/android/appwidget/AppWidgetHost.java
// (android16-release).
package android.appwidget;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

public class AppWidgetHost {

    // Retained because AOSP retains them and every method below would need
    // them the moment a widget service exists to talk to; nothing reads them
    // while sService is null, which is AOSP's state too.
    private final Context mContext;
    private final int mHostId;

    public AppWidgetHost(Context context, int hostId) {
        mContext = context;
        mHostId = hostId;
    }

    /** @hide -- the host id this object was constructed with. */
    int getHostId() { return mHostId; }

    /** @hide -- the context this host was constructed with. */
    Context getHostContext() { return mContext; }

    /** Start receiving onAppWidgetChanged calls for your AppWidgets.
     *  AOSP with no service: return immediately, so no callbacks arrive. */
    public void startListening() { }

    /** Stop receiving onAppWidgetChanged calls for your AppWidgets. */
    public void stopListening() { }

    /** Get a appWidgetId for a host in the calling process.
     *  AOSP with no service: `return -1;` -- deliberately NOT
     *  INVALID_APPWIDGET_ID (0), because -1 signals "could not allocate" while
     *  0 is a valid-looking sentinel a caller may store. */
    public int allocateAppWidgetId() {
        return -1;
    }

    /** Starts an app widget provider's configure activity for result.
     *  AOSP with no service: return without starting anything, rather than
     *  throwing ActivityNotFoundException. */
    public final void startAppWidgetConfigureActivityForResult(Activity activity,
            int appWidgetId, int intentFlags, int requestCode, Bundle options) { }

    /** Gets a list of all the appWidgetIds that are bound to the current host. */
    public int[] getAppWidgetIds() {
        return new int[0];
    }

    /** Stop listening to changes for this AppWidget. */
    public void deleteAppWidgetId(int appWidgetId) { }

    /** Remove all records about this host from the AppWidget manager. */
    public void deleteHost() { }

    /** Remove all records about all hosts for your package. */
    public static void deleteAllHosts() { }

    /** Create the AppWidgetHostView for the given widget.
     *  AOSP with no service: `return null;` BEFORE constructing the view -- a
     *  host view with no service behind it would never receive an update, so
     *  AOSP does not hand one back. */
    public final AppWidgetHostView createView(Context context, int appWidgetId,
            AppWidgetProviderInfo appWidget) {
        return null;
    }

    /** Called to create the AppWidgetHostView. Override to return a custom
     *  subclass if needed. Not gated on the service in AOSP -- createView()
     *  above is -- so a subclass that calls this directly still gets a view. */
    protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
            AppWidgetProviderInfo appWidget) {
        return new AppWidgetHostView(context);
    }

    /** Called when the AppWidget provider for a AppWidget has been upgraded to
     *  a new apk. AOSP pushes the new info at the registered listener; nothing
     *  can register one here, so the default body is the same reachable
     *  no-listener path. */
    protected void onProviderChanged(int appWidgetId, AppWidgetProviderInfo appWidget) { }

    /** Called when the set of available widgets changes (ever) i.e. widget
     *  hosts should call AppWidgetManager#getInstalledProviders to get the new
     *  list of installed providers and update any UI that may contain a
     *  reference to the removed components. AOSP's body is empty. */
    protected void onProvidersChanged() { }

    /** Called when the app widget is removed for appWidgetId. AOSP's body is
     *  empty. */
    public void onAppWidgetRemoved(int appWidgetId) { }

    /** Clear the list of Views that have been created by this AppWidgetHost.
     *  AOSP clears its listener map; nothing can be added to one here, so there
     *  is nothing to clear -- the same state AOSP is in before the first
     *  createView(). */
    protected void clearViews() { }
}
