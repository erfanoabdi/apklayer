// android.app.SearchManager
//
// AOSP's system search-dialog/global-search service. Apps fetch it via
// getSystemService(SEARCH_SERVICE) to launch the search UI or read a target
// activity's searchable metadata. Mac.apk has no global search transport and no
// search dialog, so the UI entry points record the request and the
// searchable-metadata lookups answer the way a device with no global-search
// provider installed does -- null / empty rather than an exception, so an app
// that probes `getSearchableInfo(component) == null` takes its "not searchable"
// path instead of NPEing on a null service.
//
// AOSP ref: frameworks/base/core/java/android/app/SearchManager.java
// (android16-release). AOSP declares
// `implements DialogInterface.OnCancelListener, DialogInterface.OnDismissListener`
// -- load-bearing because the class is handed to Dialog.setOnCancelListener as
// those types, so an `instanceof` or a cast in app code must succeed.
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

public class SearchManager
        implements DialogInterface.OnCancelListener, DialogInterface.OnDismissListener {

    // ---- AOSP constants (values copied verbatim from AOSP SearchManager) ----

    /** AOSP MENU_KEY: "the search key is 's'." */
    public final static char MENU_KEY = 's';
    /** AOSP MENU_KEYCODE: KeyEvent.KEYCODE_S. */
    public final static int MENU_KEYCODE = android.view.KeyEvent.KEYCODE_S;

    public static final String QUERY = "query";
    public static final String USER_QUERY = "user_query";
    public static final String APP_DATA = "app_data";
    /** AOSP SEARCH_MODE: @hide in AOSP but long-standing; kept for parity with
     *  apps that spell the literal. */
    public static final String SEARCH_MODE = "search_mode";
    public static final String ACTION_KEY = "action_key";
    public static final String EXTRA_DATA_KEY = "intent_extra_data_key";
    public static final String EXTRA_SELECT_QUERY = "select_query";
    public static final String EXTRA_NEW_SEARCH = "new_search";
    public static final String EXTRA_WEB_SEARCH_PENDINGINTENT = "web_search_pendingintent";
    public static final String CURSOR_EXTRA_KEY_IN_PROGRESS = "in_progress";
    public static final String ACTION_MSG = "action_msg";
    public static final int FLAG_QUERY_REFINEMENT = 1 << 0;

    public static final String SUGGEST_URI_PATH_QUERY = "search_suggest_query";
    public static final String SUGGEST_MIME_TYPE =
            "vnd.android.cursor.dir/vnd.android.search.suggest";
    public static final String SUGGEST_URI_PATH_SHORTCUT = "search_suggest_shortcut";
    public static final String SHORTCUT_MIME_TYPE =
            "vnd.android.cursor.item/vnd.android.search.suggest";

    public static final String SUGGEST_COLUMN_FORMAT = "suggest_format";
    public static final String SUGGEST_COLUMN_TEXT_1 = "suggest_text_1";
    public static final String SUGGEST_COLUMN_TEXT_2 = "suggest_text_2";
    public static final String SUGGEST_COLUMN_TEXT_2_URL = "suggest_text_2_url";
    public static final String SUGGEST_COLUMN_ICON_1 = "suggest_icon_1";
    public static final String SUGGEST_COLUMN_ICON_2 = "suggest_icon_2";
    public static final String SUGGEST_COLUMN_RESULT_CARD_IMAGE = "suggest_result_card_image";
    public static final String SUGGEST_COLUMN_INTENT_ACTION = "suggest_intent_action";
    public static final String SUGGEST_COLUMN_INTENT_DATA = "suggest_intent_data";
    public static final String SUGGEST_COLUMN_INTENT_EXTRA_DATA = "suggest_intent_extra_data";
    public static final String SUGGEST_COLUMN_INTENT_DATA_ID = "suggest_intent_data_id";
    public static final String SUGGEST_COLUMN_QUERY = "suggest_intent_query";
    public static final String SUGGEST_COLUMN_SHORTCUT_ID = "suggest_shortcut_id";
    public static final String SUGGEST_COLUMN_SPINNER_WHILE_REFRESHING =
            "suggest_spinner_while_refreshing";
    public static final String SUGGEST_COLUMN_CONTENT_TYPE = "suggest_content_type";
    public static final String SUGGEST_COLUMN_IS_LIVE = "suggest_is_live";
    public static final String SUGGEST_COLUMN_VIDEO_WIDTH = "suggest_video_width";
    public static final String SUGGEST_COLUMN_VIDEO_HEIGHT = "suggest_video_height";
    public static final String SUGGEST_COLUMN_AUDIO_CHANNEL_CONFIG =
            "suggest_audio_channel_config";
    public static final String SUGGEST_COLUMN_PURCHASE_PRICE = "suggest_purchase_price";
    public static final String SUGGEST_COLUMN_RENTAL_PRICE = "suggest_rental_price";
    public static final String SUGGEST_COLUMN_RATING_STYLE = "suggest_rating_style";
    public static final String SUGGEST_COLUMN_RATING_SCORE = "suggest_rating_score";
    public static final String SUGGEST_COLUMN_PRODUCTION_YEAR = "suggest_production_year";
    public static final String SUGGEST_COLUMN_DURATION = "suggest_duration";
    public static final String SUGGEST_COLUMN_FLAGS = "suggest_flags";
    public static final String SUGGEST_COLUMN_LAST_ACCESS_HINT = "suggest_last_access_hint";
    public static final String SUGGEST_NEVER_MAKE_SHORTCUT = "_-1";
    public static final String SUGGEST_PARAMETER_LIMIT = "limit";

    public static final String INTENT_ACTION_GLOBAL_SEARCH =
            "android.search.action.GLOBAL_SEARCH";
    public static final String INTENT_ACTION_SEARCH_SETTINGS =
            "android.search.action.SEARCH_SETTINGS";
    public static final String INTENT_ACTION_WEB_SEARCH_SETTINGS =
            "android.search.action.WEB_SEARCH_SETTINGS";
    public static final String INTENT_ACTION_SEARCHABLES_CHANGED =
            "android.search.action.SEARCHABLES_CHANGED";
    public static final String INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED =
            "android.search.action.GLOBAL_SEARCH_ACTIVITY_CHANGED";
    public static final String INTENT_ACTION_SEARCH_SETTINGS_CHANGED =
            "android.search.action.SETTINGS_CHANGED";

    public interface OnDismissListener { void onDismiss(); }
    public interface OnCancelListener { void onCancel(); }

    private OnDismissListener mDismissListener;
    private OnCancelListener mCancelListener;

    public SearchManager() {}
    public SearchManager(Context context) {}

    /** AOSP startSearch(...): shows the system search dialog over the calling
     *  activity. There is no search dialog on Mac.apk, so nothing is displayed
     *  -- the same outcome as
     *  Configuration.UI_MODE_TYPE_TELEVISION, where AOSP documents that this
     *  method is not supported and does nothing. */
    public void startSearch(String initialQuery, boolean selectInitialQuery,
                            ComponentName launchActivity, Bundle appSearchData,
                            boolean globalSearch) {}

    /** AOSP triggerSearch(String, ComponentName, Bundle): "Similar to
     *  startSearch, but actually fires off the search query after invoking the
     *  search dialog." AOSP's FIRST act is to reject an empty query and return
     *  without starting anything -- keep that, it is the observable half -- then
     *  it calls startSearch followed by mSearchDialog.launchQuerySearch(). We
     *  have no dialog to launch. */
    public void triggerSearch(String query, ComponentName launchActivity,
                              Bundle appSearchData) {
        if (query == null || query.trim().length() == 0) return;
        startSearch(query, false, launchActivity, appSearchData, false);
    }

    public void stopSearch() {}
    public void setOnDismissListener(OnDismissListener listener) { mDismissListener = listener; }
    public void setOnCancelListener(OnCancelListener listener) { mCancelListener = listener; }

    /** AOSP onCancel(DialogInterface): forwards to the registered
     *  OnCancelListener. AOSP marks it "@deprecated obsolete internal
     *  implementation detail", but it is public API and is why SearchManager
     *  implements DialogInterface.OnCancelListener. @deprecated matches AOSP. */
    @Deprecated
    public void onCancel(DialogInterface dialog) {
        if (mCancelListener != null) mCancelListener.onCancel();
    }

    /** AOSP onDismiss(DialogInterface): forwards to the registered
     *  OnDismissListener. @deprecated matches AOSP. */
    @Deprecated
    public void onDismiss(DialogInterface dialog) {
        if (mDismissListener != null) mDismissListener.onDismiss();
    }

    /** AOSP getSearchableInfo(): the searchable metadata for an activity, or
     *  null if it declares none. We have no searchable.xml parsing, so null. */
    public android.app.SearchableInfo getSearchableInfo(ComponentName componentName) {
        return null;
    }

    /** AOSP getSearchablesInGlobalSearch(): "a list containing searchable
     *  information for all searchable activities that have the
     *  android:includeInGlobalSearch attribute set". With no searchable-metadata
     *  index the list is empty -- AOSP returns an empty list, never null, so a
     *  caller iterating it does not NPE. */
    public java.util.List<android.app.SearchableInfo> getSearchablesInGlobalSearch() {
        return new java.util.ArrayList<android.app.SearchableInfo>();
    }

    /** AOSP getGlobalSearchActivity(): "the name of the global search
     *  activity", which is null on a device where no global-search provider is
     *  installed. Mac.apk installs one app and no search provider. */
    public ComponentName getGlobalSearchActivity() { return null; }

    public android.content.Intent getAssistIntent(boolean inclContext) { return null; }
}
