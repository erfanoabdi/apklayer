package android.webkit;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.view.View;

// android.webkit.WebChromeClient (AOSP API-1+).
// The app overrides these to handle JS dialogs, progress, titles, file pickers,
// permissions, and new windows. With no web engine yet none are invoked, but
// the full signature surface (esp. onShowFileChooser + FileChooserParams) must
// exist so app subclasses compile/resolve. Defaults mirror AOSP. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebChromeClient.java
public class WebChromeClient {

    public void onProgressChanged(WebView view, int newProgress) {}
    public void onReceivedTitle(WebView view, String title) {}
    public void onReceivedIcon(WebView view, Bitmap icon) {}
    public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed) {}

    // ----- JS dialogs -----
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) { return false; }
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) { return false; }
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                              JsPromptResult result) { return false; }
    public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
        return false;
    }

    // ----- console -----
    @Deprecated
    public void onConsoleMessage(String message, int lineNumber, String sourceID) {}
    /** AOSP: the default forwards to the deprecated 3-argument form, "for
     *  backwards compatibility", and reports the message as unhandled. */
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        onConsoleMessage(consoleMessage.message(), consoleMessage.lineNumber(),
                         consoleMessage.sourceId());
        return false;
    }

    // ----- custom (fullscreen) view -----
    public void onShowCustomView(View view, CustomViewCallback callback) {}
    @Deprecated
    public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {}
    public void onHideCustomView() {}
    public Bitmap getDefaultVideoPoster() { return null; }
    public View getVideoLoadingProgressView() { return null; }

    // ----- multi-window -----
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                  Message resultMsg) { return false; }
    public void onRequestFocus(WebView view) {}
    public void onCloseWindow(WebView window) {}

    // ----- geolocation / permissions -----
    public void onGeolocationPermissionsShowPrompt(String origin,
                                                   GeolocationPermissions.Callback callback) {
        // AOSP default does nothing (the page's request simply never resolves
        // until the app overrides this); mirror that.
    }
    public void onGeolocationPermissionsHidePrompt() {}
    public void onPermissionRequest(PermissionRequest request) {
        if (request != null) request.deny();
    }
    public void onPermissionRequestCanceled(PermissionRequest request) {}

    // ----- misc (AOSP core/java/android/webkit/WebChromeClient.java) -----
    // getVisitedHistory: AOSP's own default body is empty (does not invoke the
    // callback) -- the app is expected to override it and answer from its own
    // history store; leaving it unanswered until overridden is the real
    // AOSP-observable default, so we mirror that rather than inventing an
    // eager empty-array callback.
    public void getVisitedHistory(ValueCallback<String[]> callback) {}

    // onExceededDatabaseQuota: @Deprecated (tied to the removed Web SQL
    // Database API, "no longer called"), but AOSP's default body genuinely
    // echoes the current quota back through quotaUpdater, i.e. declines the
    // increase. Mirror that exact body for any app override that still
    // super()s into it.
    @Deprecated
    public void onExceededDatabaseQuota(String url, String databaseIdentifier,
                                        long quota, long estimatedDatabaseSize,
                                        long totalQuota, WebStorage.QuotaUpdater quotaUpdater) {
        quotaUpdater.updateQuota(quota);
    }

    // onJsTimeout: @Deprecated ("no longer supported and will not be
    // invoked" -- V8 has no such callback), AOSP's default answer is to
    // interrupt the offending script.
    @Deprecated
    public boolean onJsTimeout() { return true; }

    // ----- file chooser (P1: <input type=file>) -----
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams) {
        // AOSP default returns false -> the framework cancels the request by
        // invoking the callback with null. We do the same so the page does not
        // hang waiting on a selection.
        return false;
    }

    // ----- nested types -----
    public interface CustomViewCallback {
        void onCustomViewHidden();
    }

    // Describes a file-picker request from <input type=file>. Abstract in AOSP;
    // the engine subclasses it. We provide the constants + the static helpers
    // (parseResult / createIntent) which are concrete in AOSP.
    public static abstract class FileChooserParams {
        public static final int MODE_OPEN = 0;
        public static final int MODE_OPEN_MULTIPLE = 1;
        public static final int MODE_OPEN_FOLDER = 2; // reserved by AOSP
        public static final int MODE_SAVE = 3;

        /** File System Access: the page asked for READ access only. */
        public static final int PERMISSION_MODE_READ = 0;
        /** File System Access: the page asked for READ+WRITE access. */
        public static final int PERMISSION_MODE_READ_WRITE = 1;

        // Parses the picker Activity result into the Uri[] the page expects.
        public static Uri[] parseResult(int resultCode, Intent data) {
            // android.app.Activity.RESULT_OK == -1
            if (resultCode != -1 || data == null) {
                return null;
            }
            Uri uri = data.getData();
            return uri == null ? null : new Uri[]{uri};
        }

        public abstract int getMode();
        public abstract String[] getAcceptTypes();
        public abstract boolean isCaptureEnabled();
        public abstract CharSequence getTitle();
        public abstract String getFilenameHint();
        public abstract Intent createIntent();

        /**
         * Which access the page requested for the chosen file/folder
         * (API 36, File System Access API).
         *
         * AOSP declares this CONCRETE, not abstract, and its base
         * implementation returns PERMISSION_MODE_READ -- adding an abstract
         * method to a public abstract class would have broken every existing
         * subclass, so upstream shipped a default. Mirrored here for the same
         * reason: app code and Chromium-style engines both subclass this.
         *
         * READ is also the honest answer for a request that arrived without a
         * mode: the safer of the two, and the one AOSP picks.
         */
        public int getPermissionMode() { return PERMISSION_MODE_READ; }
    }
}
