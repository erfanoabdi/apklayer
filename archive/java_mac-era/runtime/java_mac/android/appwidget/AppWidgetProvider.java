package android.appwidget;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
/** Base for an app-widget provider (a BroadcastReceiver dispatching widget
 *  lifecycle to onUpdate/onDeleted/etc). Mac.apk has no home screen, so a widget
 *  is never placed and these never fire, but the app's provider subclass must
 *  compile. Source: frameworks/base/core/java/android/appwidget/AppWidgetProvider.java */
public class AppWidgetProvider extends BroadcastReceiver {
    public AppWidgetProvider() { }
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                int[] ids = extras.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                if (ids != null && ids.length > 0) {
                    onUpdate(context, AppWidgetManager.getInstance(context), ids);
                }
            }
        }
    }
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) { }
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle newOptions) { }
    public void onDeleted(Context context, int[] appWidgetIds) { }
    public void onEnabled(Context context) { }
    public void onDisabled(Context context) { }
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) { }
}
