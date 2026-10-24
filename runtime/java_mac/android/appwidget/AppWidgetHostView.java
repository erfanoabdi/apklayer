// android.appwidget.AppWidgetHostView -- the FrameLayout a HOST (a launcher,
// or an app previewing its own widget) puts a widget's RemoteViews into.
//
// Mac.apk runs ONE app, so there is no home screen and no cross-app widget
// hosting: a launcher APK running here can never display ANOTHER app's widget,
// because the other app is not running. What CAN work, and does work here, is
// the same-process case -- an app inflating its OWN RemoteViews into a host
// view, which is exactly what a widget configuration activity does to show a
// live preview. RemoteViews.apply() is in-process, so that path is real.
//
// Source: frameworks/base core/java/android/appwidget/AppWidgetHostView.java
// (android16-release).
package android.appwidget;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.SizeF;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.Executor;

public class AppWidgetHostView extends FrameLayout {

    // AOSP's three view modes, so a repeated update to the same mode is a
    // no-op rather than a rebuild.
    private static final int VIEW_MODE_NOINIT  = 0;
    private static final int VIEW_MODE_CONTENT = 1;
    private static final int VIEW_MODE_ERROR   = 2;
    private static final int VIEW_MODE_DEFAULT = 3;

    // AOSP core/res/res/values/dimens.xml (android16-release, lines 610-613):
    // default_app_widget_padding_{left,top,right,bottom} are all 8dp. Those are
    // platform-internal dimens this tree does not ship, so the 8dp is applied
    // through TypedValue against the real display metrics -- same number, same
    // density scaling, no internal resource id.
    private static final float DEFAULT_APP_WIDGET_PADDING_DP = 8f;

    private final Context mContext;

    private int mAppWidgetId;
    private AppWidgetProviderInfo mInfo;
    private View mView;
    private int mViewMode = VIEW_MODE_NOINIT;
    private boolean mOnLightBackground;
    private Executor mAsyncExecutor;
    private SparseIntArray mColorResources;
    private RemoteViews mLastInflatedRemoteViews;

    public AppWidgetHostView(Context context) {
        this(context, 0, 0);
    }

    /** AOSP's in/out animation resource ids are accepted and ignored -- its own
     *  constructor stores neither (the fade animation was removed years ago and
     *  the parameters kept for source compatibility). */
    public AppWidgetHostView(Context context, int animationIn, int animationOut) {
        super(context);
        mContext = context;
    }

    /** Set the AppWidget that will be displayed by this view. This method also
     *  adds default padding to widgets, as described in
     *  getDefaultPaddingForWidget(). */
    public void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
        mAppWidgetId = appWidgetId;
        mInfo = info;
        Rect padding = getDefaultPaddingForWidget(mContext, null, null);
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        if (info != null) {
            // AOSP also wraps the label in the "suspended" string when the
            // provider's application is suspended. Nothing can suspend an
            // application on this host, so that branch is unreachable rather
            // than skipped.
            String description = info.loadLabel(
                    mContext != null ? mContext.getPackageManager() : null);
            setContentDescription(description);
        }
    }

    public int getAppWidgetId() {
        return mAppWidgetId;
    }

    public AppWidgetProviderInfo getAppWidgetInfo() {
        return mInfo;
    }

    /** As of ICS, widgets have padding applied by the host rather than baking
     *  it into their own layouts. AOSP reads four platform dimens, all 8dp;
     *  `component` is unused in AOSP too (it delegates to a two-argument
     *  overload that ignores it). */
    public static Rect getDefaultPaddingForWidget(Context context, ComponentName component,
            Rect padding) {
        if (padding == null) {
            padding = new Rect(0, 0, 0, 0);
        } else {
            padding.set(0, 0, 0, 0);
        }
        if (context == null || context.getResources() == null
                || context.getResources().getDisplayMetrics() == null) {
            return padding;
        }
        int p = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                DEFAULT_APP_WIDGET_PADDING_DP,
                context.getResources().getDisplayMetrics());
        padding.set(p, p, p, p);
        return padding;
    }

    /** Provide guidance about the size of this widget to the AppWidgetManager.
     *  @deprecated at API 31 in favour of the List&lt;SizeF&gt; overload. */
    @Deprecated
    public void updateAppWidgetSize(Bundle newOptions, int minWidth, int minHeight,
            int maxWidth, int maxHeight) {
        if (newOptions == null) {
            newOptions = new Bundle();
        }
        newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth);
        newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight);
        newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidth);
        newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeight);
        updateAppWidgetOptions(newOptions);
    }

    /** AOSP derives min/max from the supplied size list and records the list
     *  itself under OPTION_APPWIDGET_SIZES. */
    public void updateAppWidgetSize(Bundle newOptions, List<SizeF> sizes) {
        if (newOptions == null) {
            newOptions = new Bundle();
        }
        if (sizes != null && !sizes.isEmpty()) {
            float minWidth = Float.MAX_VALUE, minHeight = Float.MAX_VALUE;
            float maxWidth = 0, maxHeight = 0;
            for (int i = 0; i < sizes.size(); i++) {
                SizeF size = sizes.get(i);
                if (size == null) continue;
                minWidth = Math.min(minWidth, size.getWidth());
                minHeight = Math.min(minHeight, size.getHeight());
                maxWidth = Math.max(maxWidth, size.getWidth());
                maxHeight = Math.max(maxHeight, size.getHeight());
            }
            if (minWidth != Float.MAX_VALUE) {
                newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (int) minWidth);
                newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (int) minHeight);
                newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, (int) maxWidth);
                newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, (int) maxHeight);
            }
        }
        updateAppWidgetOptions(newOptions);
    }

    /** AOSP forwards to AppWidgetManager, which round-trips the options through
     *  the system. There is no system here, so the manager stores them itself --
     *  see AppWidgetManager.updateAppWidgetOptions. */
    public void updateAppWidgetOptions(Bundle options) {
        AppWidgetManager.getInstance(mContext).updateAppWidgetOptions(mAppWidgetId, options);
    }

    /** Process a set of RemoteViews coming in as an update from the AppWidget
     *  provider. Will animate into these new views as needed.
     *
     *  AOSP's applyRemoteViews() additionally recycles the existing view when
     *  the new RemoteViews is compatible, dispatches to an async executor, and
     *  resolves against the PROVIDER's context. Recycling is an optimisation;
     *  the async path only applies when setExecutor() was called; and the
     *  provider's context IS this context on a single-app host. So the
     *  observable result -- the inflated content, the error view on failure,
     *  the default view for a null update -- is the same. */
    public void updateAppWidget(RemoteViews remoteViews) {
        mLastInflatedRemoteViews = remoteViews;
        View content = null;
        Exception exception = null;

        if (remoteViews == null) {
            if (mViewMode == VIEW_MODE_DEFAULT) {
                return;
            }
            content = getDefaultView();
            mViewMode = VIEW_MODE_DEFAULT;
        } else {
            try {
                content = remoteViews.apply(mContext, this);
            } catch (RuntimeException e) {
                exception = e;
            }
            mViewMode = VIEW_MODE_CONTENT;
        }
        applyContent(content, exception);
    }

    /** AOSP applyContent(): fall back to the error view, lay the content out
     *  centered, and swap it in for whatever was there. */
    private void applyContent(View content, Exception exception) {
        if (content == null) {
            if (mViewMode == VIEW_MODE_ERROR) {
                return;
            }
            content = getErrorView();
            mViewMode = VIEW_MODE_ERROR;
        }
        prepareView(content);
        addView(content);
        if (mView != content) {
            removeView(mView);
            mView = content;
        }
    }

    /** Prepare the given view to be shown. This might include adjusting layout
     *  parameters. */
    protected void prepareView(View view) {
        FrameLayout.LayoutParams requested = null;
        if (view.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            requested = (FrameLayout.LayoutParams) view.getLayoutParams();
        }
        if (requested == null) {
            requested = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
        }
        requested.gravity = Gravity.CENTER;
        view.setLayoutParams(requested);
    }

    /** Inflate and return the default layout requested by AppWidget provider.
     *
     *  AOSP inflates it from the PROVIDER package's context with a class
     *  filter; on a single-app host the provider package is this package, so
     *  the plain inflater over this context resolves the identical layout. */
    protected View getDefaultView() {
        View defaultView = null;
        try {
            if (mInfo != null) {
                int layoutId = mInfo.initialLayout;
                Bundle options =
                        AppWidgetManager.getInstance(mContext).getAppWidgetOptions(mAppWidgetId);
                if (options != null
                        && options.containsKey(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY)) {
                    int category = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY);
                    if (category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) {
                        int kgLayoutId = mInfo.initialKeyguardLayout;
                        layoutId = kgLayoutId == 0 ? layoutId : kgLayoutId;
                    }
                }
                if (layoutId != 0) {
                    defaultView = LayoutInflater.from(mContext).inflate(layoutId, this, false);
                }
            }
        } catch (RuntimeException e) {
            defaultView = null;
        }
        if (defaultView == null) {
            defaultView = getErrorView();
        }
        return defaultView;
    }

    /** Inflate and return a view that represents an error state.
     *
     *  AOSP's text comes from com.android.internal.R.string
     *  .gadget_host_error_inflating, which this tree does not ship; the string
     *  AOSP's own values/strings.xml gives that id ("Problem loading widget")
     *  is used directly, with AOSP's 50%-black background. */
    protected View getErrorView() {
        TextView tv = new TextView(mContext);
        tv.setText("Problem loading widget");
        tv.setBackgroundColor(Color.argb(127, 0, 0, 0));
        return tv;
    }

    /** Sets whether the widget is being displayed on a light/white background
     *  and should be inverted. */
    public void setOnLightBackground(boolean onLightBackground) {
        mOnLightBackground = onLightBackground;
    }

    /** Set the executor that will be used to inflate a RemoteViews update
     *  asynchronously. AOSP cancels any in-flight inflation first; nothing is
     *  ever in flight here because updateAppWidget() inflates synchronously
     *  (see its comment), so the executor is recorded and not used -- which is
     *  what a null executor means in AOSP too. */
    public void setExecutor(Executor executor) {
        mAsyncExecutor = executor;
    }

    /** Set the dynamic color overrides applied to this widget's RemoteViews.
     *
     *  AOSP builds a RemoteViews.ColorResources from the mapping and reapplies
     *  the last RemoteViews through it. RemoteViews in this tree has no
     *  color-resource override path, so the mapping is stored and the last
     *  RemoteViews is reapplied -- the reapply is the observable half, and it
     *  happens; the recolouring does not, and cannot until RemoteViews carries
     *  the override (a different package, not this lane). */
    public void setColorResources(SparseIntArray colorMapping) {
        if (colorMapping == null) {
            return;
        }
        mColorResources = colorMapping;
        mViewMode = VIEW_MODE_NOINIT;
        reapplyLastRemoteViews();
    }

    /** Reset the dynamic color overrides to the default values. */
    public void resetColorResources() {
        if (mColorResources != null) {
            mColorResources = null;
            mViewMode = VIEW_MODE_NOINIT;
            reapplyLastRemoteViews();
        }
    }

    private void reapplyLastRemoteViews() {
        if (mLastInflatedRemoteViews != null) {
            updateAppWidget(mLastInflatedRemoteViews);
        }
    }
}
