// android.widget.ShareActionProvider -- an ActionProvider that exposes a
// share action in a menu, sharing via a held Intent. Was Absent. We keep the
// share-intent + history-file surface; presenting the share target list has no
// backend. AOSP:
//   frameworks/base/core/java/android/widget/ShareActionProvider.java
//   public class ShareActionProvider extends ActionProvider
package android.widget;

import android.content.Context;
import android.content.Intent;
import android.view.ActionProvider;

public class ShareActionProvider extends ActionProvider {
    public static final String DEFAULT_SHARE_HISTORY_FILE_NAME =
            "share_history.xml";

    public interface OnShareTargetSelectedListener {
        boolean onShareTargetSelected(ShareActionProvider source, Intent intent);
    }

    private final Context mContext;
    private String mShareHistoryFileName = DEFAULT_SHARE_HISTORY_FILE_NAME;
    private Intent mShareIntent;
    private OnShareTargetSelectedListener mOnShareTargetSelectedListener;

    public ShareActionProvider(Context context) {
        super();
        mContext = context;
    }

    public void setOnShareTargetSelectedListener(OnShareTargetSelectedListener listener) {
        mOnShareTargetSelectedListener = listener;
    }

    public void setShareIntent(Intent shareIntent) { mShareIntent = shareIntent; }

    public void setShareHistoryFileName(String shareHistoryFile) {
        mShareHistoryFileName = shareHistoryFile;
    }
}
