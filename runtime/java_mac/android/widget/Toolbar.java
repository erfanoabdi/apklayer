// android.widget.Toolbar -- the framework action-bar-as-a-view (API 21).
//
// PORTED 2026-09-26 (MACAPK_PLATFORM_TOOLBAR, default ON) from AOSP android14
// frameworks/base core/java/android/widget/Toolbar.java; line numbers cited below
// are from that file. What was here before was a non-painting stub: title,
// subtitle and navigation icon were STORED but never added as child views,
// setTitle(int) / setNavigationIcon(int) / setLogo(int) were empty, getMenu()
// returned null, and the class had no onMeasure / onLayout of its own, so a
// wrap_content Toolbar took ViewGroup's "fill the spec" answer.
//
// Measured victim, Mastodon (org.joinmastodon.android 2.13.1): its AppKit uses the
// PLATFORM Toolbar for every screen (g0.c field `k:Landroid/widget/Toolbar;`).
//   - g0.c.O() does setTitle(dummy), then scans the Toolbar's CHILDREN for a
//     TextView whose text is the dummy, to capture the title view it styles
//     (marquee, fading edge). With no children every screen had an empty bar,
//     where the emulator draws "<- Welcome back".
//   - g0.c.Q(), reached from onViewCreated whenever the fragment has an options
//     menu, does `toolbar.getMenu().clear()` UNGUARDED: an NPE on every AppKit
//     fragment with a menu (timeline, profile, ...).
// docs/fleet-widget-apps-mastodon-fossify-pianoli_2026-09-26.md M3.
//
// What the port brings: AOSP's lazily-created system children (title / subtitle
// TextViews, navigation ImageButton, logo ImageView, collapse ImageButton,
// ActionMenuView), the constructor's styled-attribute read through the toolbar's
// default style (?android:toolbarStyle), and AOSP's onMeasure / onLayout verbatim
// in structure (collapsing margins, content insets, title margins, custom views
// by gravity). The menu half lives in ActionMenuView.
//
// MACAPK_PLATFORM_TOOLBAR=0 restores the pre-port stub on every path: the same
// constructors' super calls, the store-only setters, getMenu() == null, and no
// onMeasure / onLayout / generateDefaultLayoutParams override.
//
// DEVIATIONS, deliberate:
//  - navigationButtonStyle is a PRIVATE framework attr (attrs.xml:9707, absent
//    from public-final.xml), and our platform style table keeps only named public
//    attrs (FrameworkAttrIds), so it cannot be read through obtainStyledAttributes.
//    Only the two framework toolbar styles set it (styles.xml:1288 Widget.Toolbar ->
//    Widget.Toolbar.Button.Navigation; styles_material.xml:900 Widget.Material.
//    Toolbar -> Widget.Material.Toolbar.Button.Navigation), and an app cannot
//    (private attrs are not addressable from app resources), so the value is
//    DERIVED from which of those two styles the toolbar's default style descends
//    from. A style descending from neither gets 0, which is also AOSP's answer.
//  - expanded action views (MenuItem.expandActionView inside the bar) are not
//    run; hasExpandedActionView() answers AOSP's no-presenter "false".
//  - saved-instance state (the open overflow / expanded item) is not restored.
package android.widget;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public class Toolbar extends ViewGroup {
    private static final String TAG = "Mac.apk.Toolbar";

    /** The labelled bisect gate for the whole port (Toolbar + ActionMenuView). */
    static final boolean MACAPK_PLATFORM_TOOLBAR =
        !"0".equals(System.getenv("MACAPK_PLATFORM_TOOLBAR"));

    /** android.R.attr.toolbarStyle -- AOSP Toolbar(Context, AttributeSet) passes
     *  com.android.internal.R.attr.toolbarStyle, which IS the public attr. */
    private static final int ATTR_TOOLBAR_STYLE = 0x010104aa;

    // R.styleable.Toolbar, public members only (see the navigationButtonStyle
    // deviation above), ascending id order as aapt emits a styleable. Ids are
    // android.R.attr values (public-final.xml); debug/w5fragments_gate.sh checks
    // every one against com.macapk.loader.FrameworkAttrIds by name.
    private static final int[] STYLEABLE_TOOLBAR = {
        0x010100af, // gravity
        0x010101e1, // title
        0x010102be, // logo
        0x010102d1, // subtitle
        0x0101042e, // titleTextAppearance
        0x0101042f, // subtitleTextAppearance
        0x01010453, // contentInsetStart
        0x01010454, // contentInsetEnd
        0x01010455, // contentInsetLeft
        0x01010456, // contentInsetRight
        0x010104a9, // popupTheme
        0x010104c0, // navigationIcon
        0x010104c1, // navigationContentDescription
        0x010104d0, // collapseContentDescription
        0x010104e3, // titleTextColor
        0x010104e4, // subtitleTextColor
        0x010104e9, // logoDescription
        0x010104f8, // titleMargin
        0x010104f9, // titleMarginStart
        0x010104fa, // titleMarginEnd
        0x010104fb, // titleMarginTop
        0x010104fc, // titleMarginBottom
        0x010104fd, // maxButtonHeight
        0x010104fe, // buttonGravity
        0x010104ff, // collapseIcon
        0x01010522, // contentInsetStartWithNavigation
        0x01010523, // contentInsetEndWithActions
    };
    private static final int T_GRAVITY = 0, T_TITLE = 1, T_LOGO = 2, T_SUBTITLE = 3,
        T_TITLE_TA = 4, T_SUBTITLE_TA = 5, T_INSET_START = 6, T_INSET_END = 7,
        T_INSET_LEFT = 8, T_INSET_RIGHT = 9, T_POPUP_THEME = 10, T_NAV_ICON = 11,
        T_NAV_DESC = 12, T_COLLAPSE_DESC = 13, T_TITLE_COLOR = 14, T_SUBTITLE_COLOR = 15,
        T_LOGO_DESC = 16, T_TITLE_MARGIN = 17, T_TITLE_MARGIN_START = 18,
        T_TITLE_MARGIN_END = 19, T_TITLE_MARGIN_TOP = 20, T_TITLE_MARGIN_BOTTOM = 21,
        T_MAX_BUTTON_HEIGHT = 22, T_BUTTON_GRAVITY = 23, T_COLLAPSE_ICON = 24,
        T_INSET_START_NAV = 25, T_INSET_END_ACTIONS = 26;

    /** Package-visible for debug/w5fragments_gate.sh's id check. */
    static int[] macapkStyleableToolbar() { return STYLEABLE_TOOLBAR.clone(); }

    // ---- state shared by both paths ----
    private CharSequence title;      // AOSP mTitleText
    private CharSequence subtitle;   // AOSP mSubtitleText
    private int titleMarginStart;
    private int titleMarginTop;
    private int titleMarginEnd;
    private int titleMarginBottom;

    // ---- pre-port stub state (MACAPK_PLATFORM_TOOLBAR=0 only) ----
    private Drawable navigationIcon;
    private Drawable logo;
    private View.OnClickListener navigationOnClickListener;

    // ---- the port (AOSP :140-221) ----
    private TextView mTitleTextView;
    private TextView mSubtitleTextView;
    private ImageButton mNavButtonView;
    private ImageView mLogoView;
    private Drawable mCollapseIcon;
    private CharSequence mCollapseDescription;
    private ImageButton mCollapseButtonView;
    private ActionMenuView mMenuView;
    private Context mPopupContext;
    private int mTitleTextAppearance;
    private int mSubtitleTextAppearance;
    private int mNavButtonStyle;
    private int mButtonGravity = Gravity.TOP;
    private int mMaxButtonHeight = -1;
    private int mGravity = Gravity.START | Gravity.CENTER_VERTICAL;
    private int mTitleTextColor;
    private int mSubtitleTextColor;
    private boolean mEatingTouch;
    private final ArrayList<View> mTempViews = new ArrayList<View>();
    private final ArrayList<View> mHiddenViews = new ArrayList<>();
    private final int[] mTempMargins = new int[2];
    private OnMenuItemClickListener mOnMenuItemClickListener;
    private final ActionMenuView.OnMenuItemClickListener mMenuViewItemClickListener =
            new ActionMenuView.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (mOnMenuItemClickListener != null) {
                        return mOnMenuItemClickListener.onMenuItemClick(item);
                    }
                    return false;
                }
            };

    // ---- constructors (AOSP :223-344) ----
    // All four funnel into ViewGroup's 4-arg constructor. With the gate OFF the
    // arguments are exactly what the stub passed (defStyleAttr for the 3/4-arg
    // forms, 0 otherwise, never a defStyleRes): View(ctx, null, 0, 0) runs the
    // same code as View(ctx) and View(ctx, attrs), because View's 4-arg body only
    // reads the default-style cascade when defStyleAttr or defStyleRes is nonzero.
    public Toolbar(Context context) { this(context, null); }
    public Toolbar(Context context, AttributeSet attrs) {
        this(context, attrs, MACAPK_PLATFORM_TOOLBAR ? ATTR_TOOLBAR_STYLE : 0);
    }
    public Toolbar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public Toolbar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, MACAPK_PLATFORM_TOOLBAR ? defStyleRes : 0);
        if (MACAPK_PLATFORM_TOOLBAR) macapkInitFromStyle(context, attrs, defStyleAttr, defStyleRes);
    }

    /** The body of AOSP's 4-arg constructor (:235-343). Every read is guarded:
     *  a throwing TypedArray read inside a constructor takes the whole inflate
     *  down, which on a device cannot happen for these well-formed attrs. */
    private void macapkInitFromStyle(Context context, AttributeSet attrs,
                                     int defStyleAttr, int defStyleRes) {
        mPopupContext = context;
        TypedArray a;
        try {
            a = context.obtainStyledAttributes(attrs, STYLEABLE_TOOLBAR, defStyleAttr, defStyleRes);
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.w(TAG, "obtainStyledAttributes(Toolbar) threw " + t
                + " -- the toolbar keeps AOSP's field defaults");
            return;
        }
        try {
            mTitleTextAppearance = a.getResourceId(T_TITLE_TA, 0);
            mSubtitleTextAppearance = a.getResourceId(T_SUBTITLE_TA, 0);
            mNavButtonStyle = macapkNavButtonStyle(context, defStyleAttr, defStyleRes);
            mGravity = a.getInteger(T_GRAVITY, mGravity);
            mButtonGravity = a.getInteger(T_BUTTON_GRAVITY, Gravity.TOP);
            titleMarginStart = titleMarginEnd = titleMarginTop = titleMarginBottom =
                    a.getDimensionPixelOffset(T_TITLE_MARGIN, 0);
            final int marginStart = a.getDimensionPixelOffset(T_TITLE_MARGIN_START, -1);
            if (marginStart >= 0) titleMarginStart = marginStart;
            final int marginEnd = a.getDimensionPixelOffset(T_TITLE_MARGIN_END, -1);
            if (marginEnd >= 0) titleMarginEnd = marginEnd;
            final int marginTop = a.getDimensionPixelOffset(T_TITLE_MARGIN_TOP, -1);
            if (marginTop >= 0) titleMarginTop = marginTop;
            final int marginBottom = a.getDimensionPixelOffset(T_TITLE_MARGIN_BOTTOM, -1);
            if (marginBottom >= 0) titleMarginBottom = marginBottom;
            mMaxButtonHeight = a.getDimensionPixelSize(T_MAX_BUTTON_HEIGHT, -1);

            final int contentInsetStart =
                    a.getDimensionPixelOffset(T_INSET_START, RtlSpacingHelper.UNDEFINED);
            final int contentInsetEnd =
                    a.getDimensionPixelOffset(T_INSET_END, RtlSpacingHelper.UNDEFINED);
            final int contentInsetLeft = a.getDimensionPixelSize(T_INSET_LEFT, 0);
            final int contentInsetRight = a.getDimensionPixelSize(T_INSET_RIGHT, 0);
            mContentInsets.setAbsolute(contentInsetLeft, contentInsetRight);
            if (contentInsetStart != RtlSpacingHelper.UNDEFINED
                    || contentInsetEnd != RtlSpacingHelper.UNDEFINED) {
                mContentInsets.setRelative(contentInsetStart, contentInsetEnd);
            }
            mContentInsetStartWithNavigation = a.getDimensionPixelOffset(
                    T_INSET_START_NAV, RtlSpacingHelper.UNDEFINED);
            mContentInsetEndWithActions = a.getDimensionPixelOffset(
                    T_INSET_END_ACTIONS, RtlSpacingHelper.UNDEFINED);

            try { mCollapseIcon = a.getDrawable(T_COLLAPSE_ICON); } catch (Throwable ignored) {}
            mCollapseDescription = a.getText(T_COLLAPSE_DESC);

            final CharSequence t = a.getText(T_TITLE);
            if (!TextUtils.isEmpty(t)) setTitle(t);
            final CharSequence st = a.getText(T_SUBTITLE);
            if (!TextUtils.isEmpty(st)) setSubtitle(st);

            // "Set the default context, since setPopupTheme() may be a no-op."
            mPopupContext = getContext();
            setPopupTheme(a.getResourceId(T_POPUP_THEME, 0));

            Drawable navIcon = null;
            try { navIcon = a.getDrawable(T_NAV_ICON); } catch (Throwable ignored) {}
            if (navIcon != null) setNavigationIcon(navIcon);
            final CharSequence navDesc = a.getText(T_NAV_DESC);
            if (!TextUtils.isEmpty(navDesc)) setNavigationContentDescription(navDesc);

            Drawable logoD = null;
            try { logoD = a.getDrawable(T_LOGO); } catch (Throwable ignored) {}
            if (logoD != null) setLogo(logoD);
            final CharSequence logoDesc = a.getText(T_LOGO_DESC);
            if (!TextUtils.isEmpty(logoDesc)) setLogoDescription(logoDesc);

            if (a.hasValue(T_TITLE_COLOR)) {
                setTitleTextColor(a.getColor(T_TITLE_COLOR, 0xffffffff));
            }
            if (a.hasValue(T_SUBTITLE_COLOR)) {
                setSubtitleTextColor(a.getColor(T_SUBTITLE_COLOR, 0xffffffff));
            }
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.w(TAG, "Toolbar styled-attribute read threw " + t);
        } finally {
            try { a.recycle(); } catch (Throwable ignored) {}
        }
    }

    /** See the navigationButtonStyle DEVIATION in the header. Walks the
     *  toolbar's default style (defStyleRes, else the theme's value of
     *  defStyleAttr) up its parent chain and answers the navigation-button
     *  style the first FRAMEWORK toolbar style on that chain names. */
    private static int macapkNavButtonStyle(Context context, int defStyleAttr, int defStyleRes) {
        try {
            int styleId = defStyleRes;
            if (styleId == 0 && defStyleAttr != 0) {
                android.util.TypedValue tv = new android.util.TypedValue();
                if (context.getTheme().resolveAttribute(defStyleAttr, tv, true)) {
                    styleId = tv.resourceId != 0 ? tv.resourceId : tv.data;
                }
            }
            if (styleId == 0) return 0;
            android.content.res.Resources res = context.getResources();
            String name = res.getResourceEntryName(styleId);
            // Package 0x01 is the platform; a parent reference names its own
            // package ("@android:style/..."), so later hops can tell by prefix.
            boolean framework = (styleId >>> 24) == 0x01;
            Object ri = android.content.res.Resources.macapkGetResourceIndex();
            for (int hop = 0; name != null && hop < 32; hop++) {
                String n = name;
                if (n.startsWith("@")) n = n.substring(1);
                if (n.startsWith("android:")) { n = n.substring(8); framework = true; }
                if (n.startsWith("style/")) n = n.substring(6);
                if (framework && "Widget.Material.Toolbar".equals(n)) {
                    return res.getIdentifier("Widget.Material.Toolbar.Button.Navigation",
                                             "style", "android");
                }
                if (framework && "Widget.Toolbar".equals(n)) {
                    return res.getIdentifier("Widget.Toolbar.Button.Navigation",
                                             "style", "android");
                }
                if (!(ri instanceof com.macapk.loader.ResourceIndex)) return 0;
                // The app table answers a bare name; "@android:style/" makes
                // ResourceIndex.getStyleByName skip to the platform table.
                com.macapk.loader.ResourceIndex.StyleDef sd =
                    ((com.macapk.loader.ResourceIndex) ri).getStyleByName(
                        framework ? "@android:style/" + n : n);
                String parent;
                if (sd == null) {
                    // A style with no explicit parent inherits by its dotted
                    // name ("Toolbar.Custom" -> "Toolbar"), the aapt rule.
                    int dot = n.lastIndexOf('.');
                    parent = dot > 0 ? n.substring(0, dot) : null;
                } else {
                    parent = sd.parent;
                    if (parent == null) {
                        int dot = n.lastIndexOf('.');
                        parent = dot > 0 ? n.substring(0, dot) : null;
                    } else if (parent.isEmpty()) {
                        parent = null;   // parent="" suppresses the dotted inference
                    } else {
                        framework = parent.contains("android:");
                    }
                }
                name = parent;
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /** AOSP :373-383. The popup context is what the overflow popup inflates in. */
    public void setPopupTheme(int resId) {
        if (!MACAPK_PLATFORM_TOOLBAR) { if (mPopupTheme != resId) mPopupTheme = resId; return; }
        if (mPopupTheme != resId) {
            mPopupTheme = resId;
            if (resId == 0) {
                mPopupContext = getContext();
            } else {
                mPopupContext = new android.view.ContextThemeWrapper(getContext(), resId);
            }
        }
    }
    public int getPopupTheme() { return mPopupTheme; }

    // ---- title / subtitle (AOSP :749-900) ----

    public CharSequence getTitle() { return title; }
    public void setTitle(int resId) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        setTitle(getContext().getText(resId));
    }
    public void setTitle(CharSequence title) {
        if (!MACAPK_PLATFORM_TOOLBAR) { this.title = title; return; }
        if (!TextUtils.isEmpty(title)) {
            if (mTitleTextView == null) {
                final Context context = getContext();
                mTitleTextView = new TextView(context);
                mTitleTextView.setSingleLine();
                mTitleTextView.setEllipsize(TextUtils.TruncateAt.END);
                if (mTitleTextAppearance != 0) {
                    mTitleTextView.setTextAppearance(mTitleTextAppearance);
                }
                if (mTitleTextColor != 0) {
                    mTitleTextView.setTextColor(mTitleTextColor);
                }
            }
            if (!isChildOrHidden(mTitleTextView)) {
                addSystemView(mTitleTextView, true);
            }
        } else if (mTitleTextView != null && isChildOrHidden(mTitleTextView)) {
            removeView(mTitleTextView);
            mHiddenViews.remove(mTitleTextView);
        }
        if (mTitleTextView != null) {
            mTitleTextView.setText(title);
        }
        this.title = title;
    }

    public CharSequence getSubtitle() { return subtitle; }
    public void setSubtitle(int resId) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        setSubtitle(getContext().getText(resId));
    }
    public void setSubtitle(CharSequence subtitle) {
        if (!MACAPK_PLATFORM_TOOLBAR) { this.subtitle = subtitle; return; }
        if (!TextUtils.isEmpty(subtitle)) {
            if (mSubtitleTextView == null) {
                final Context context = getContext();
                mSubtitleTextView = new TextView(context);
                mSubtitleTextView.setSingleLine();
                mSubtitleTextView.setEllipsize(TextUtils.TruncateAt.END);
                if (mSubtitleTextAppearance != 0) {
                    mSubtitleTextView.setTextAppearance(mSubtitleTextAppearance);
                }
                if (mSubtitleTextColor != 0) {
                    mSubtitleTextView.setTextColor(mSubtitleTextColor);
                }
            }
            if (!isChildOrHidden(mSubtitleTextView)) {
                addSystemView(mSubtitleTextView, true);
            }
        } else if (mSubtitleTextView != null && isChildOrHidden(mSubtitleTextView)) {
            removeView(mSubtitleTextView);
            mHiddenViews.remove(mSubtitleTextView);
        }
        if (mSubtitleTextView != null) {
            mSubtitleTextView.setText(subtitle);
        }
        this.subtitle = subtitle;
    }

    public void setTitleTextAppearance(Context context, int resId) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        mTitleTextAppearance = resId;
        if (mTitleTextView != null) mTitleTextView.setTextAppearance(resId);
    }
    public void setSubtitleTextAppearance(Context context, int resId) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        mSubtitleTextAppearance = resId;
        if (mSubtitleTextView != null) mSubtitleTextView.setTextAppearance(resId);
    }
    public void setTitleTextColor(int color) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        mTitleTextColor = color;
        if (mTitleTextView != null) mTitleTextView.setTextColor(color);
    }
    public void setSubtitleTextColor(int color) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        mSubtitleTextColor = color;
        if (mSubtitleTextView != null) mSubtitleTextView.setTextColor(color);
    }

    // ---- navigation button (AOSP :905-1030) ----

    public void setNavigationIcon(Drawable icon) {
        if (!MACAPK_PLATFORM_TOOLBAR) { this.navigationIcon = icon; return; }
        if (icon != null) {
            ensureNavButtonView();
            if (!isChildOrHidden(mNavButtonView)) {
                addSystemView(mNavButtonView, true);
            }
        } else if (mNavButtonView != null && isChildOrHidden(mNavButtonView)) {
            removeView(mNavButtonView);
            mHiddenViews.remove(mNavButtonView);
        }
        if (mNavButtonView != null) {
            mNavButtonView.setImageDrawable(icon);
        }
    }
    public void setNavigationIcon(int resId) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        setNavigationIcon(getContext().getDrawable(resId));
    }
    public Drawable getNavigationIcon() {
        if (!MACAPK_PLATFORM_TOOLBAR) return navigationIcon;
        return mNavButtonView != null ? mNavButtonView.getDrawable() : null;
    }
    // Pre-port: AOSP hangs the description on the nav BUTTON view, which it
    // lazily creates only for a non-empty description; with no button view the
    // getter answers null. The stub modelled the button with mHasNavButtonView.
    public void setNavigationContentDescription(CharSequence description) {
        if (!MACAPK_PLATFORM_TOOLBAR) {
            if (description != null && description.length() > 0) mHasNavButtonView = true;
            if (mHasNavButtonView) mNavigationContentDescription = description;
            return;
        }
        if (!TextUtils.isEmpty(description)) ensureNavButtonView();
        if (mNavButtonView != null) mNavButtonView.setContentDescription(description);
    }
    public void setNavigationContentDescription(int resId) {
        setNavigationContentDescription(resId != 0 ? getContext().getText(resId) : null);
    }
    /** AOSP :912. */
    public CharSequence getNavigationContentDescription() {
        if (!MACAPK_PLATFORM_TOOLBAR) {
            return mHasNavButtonView ? mNavigationContentDescription : null;
        }
        return mNavButtonView != null ? mNavButtonView.getContentDescription() : null;
    }
    public void setNavigationOnClickListener(View.OnClickListener listener) {
        if (!MACAPK_PLATFORM_TOOLBAR) { this.navigationOnClickListener = listener; return; }
        ensureNavButtonView();
        mNavButtonView.setOnClickListener(listener);
    }

    // ---- logo (AOSP :523-719) ----

    public void setLogo(Drawable drawable) {
        if (!MACAPK_PLATFORM_TOOLBAR) { this.logo = drawable; return; }
        if (drawable != null) {
            ensureLogoView();
            if (!isChildOrHidden(mLogoView)) {
                addSystemView(mLogoView, true);
            }
        } else if (mLogoView != null && isChildOrHidden(mLogoView)) {
            removeView(mLogoView);
            mHiddenViews.remove(mLogoView);
        }
        if (mLogoView != null) {
            mLogoView.setImageDrawable(drawable);
        }
    }
    public void setLogo(int resId) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        setLogo(getContext().getDrawable(resId));
    }
    public Drawable getLogo() {
        if (!MACAPK_PLATFORM_TOOLBAR) return logo;
        return mLogoView != null ? mLogoView.getDrawable() : null;
    }

    private void ensureLogoView() {
        if (mLogoView == null) mLogoView = new ImageView(getContext());
    }

    // AOSP Toolbar.java:1237/:1300 -- these two feed the RtlSpacingHelper that
    // every getContentInset*/getCurrentContentInset* accessor reads.
    public void setContentInsetsRelative(int contentInsetStart, int contentInsetEnd) {
        mContentInsets.setRelative(contentInsetStart, contentInsetEnd);
    }
    public void setContentInsetsAbsolute(int contentInsetLeft, int contentInsetRight) {
        mContentInsets.setAbsolute(contentInsetLeft, contentInsetRight);
    }
    /** AOSP :1376. */
    public void setContentInsetStartWithNavigation(int insetStartWithNavigation) {
        if (insetStartWithNavigation < 0) {
            insetStartWithNavigation = RtlSpacingHelper.UNDEFINED;
        }
        if (insetStartWithNavigation != mContentInsetStartWithNavigation) {
            mContentInsetStartWithNavigation = insetStartWithNavigation;
            if (getNavigationIcon() != null) requestLayout();
        }
    }

    // AOSP Toolbar title-margin family (:407-505): store + requestLayout.
    public void setTitleMargin(int start, int top, int end, int bottom) {
        titleMarginStart = start;
        titleMarginTop = top;
        titleMarginEnd = end;
        titleMarginBottom = bottom;
        requestLayout();
    }
    public int getTitleMarginStart() { return titleMarginStart; }
    public void setTitleMarginStart(int margin) { titleMarginStart = margin; requestLayout(); }
    public int getTitleMarginTop() { return titleMarginTop; }
    public void setTitleMarginTop(int margin) { titleMarginTop = margin; requestLayout(); }
    public int getTitleMarginEnd() { return titleMarginEnd; }
    public void setTitleMarginEnd(int margin) { titleMarginEnd = margin; requestLayout(); }
    public int getTitleMarginBottom() { return titleMarginBottom; }
    public void setTitleMarginBottom(int margin) { titleMarginBottom = margin; requestLayout(); }

    // ---- menu (AOSP :1134-1215) ----

    /** AOSP :1134-1137 -- "ensureMenu(); return mMenuView.getMenu();" never null.
     *  The pre-port stub answered null, and Mastodon's AppKit dereferences it
     *  unguarded (g0.c.Q: getMenu().clear()). */
    public Menu getMenu() {
        if (!MACAPK_PLATFORM_TOOLBAR) return null;
        ensureMenu();
        return mMenuView.getMenu();
    }
    public void inflateMenu(int resId) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        getMenuInflater().inflate(resId, getMenu());
    }
    public MenuInflater getMenuInflater() { return new MenuInflater(getContext()); }
    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        mOnMenuItemClickListener = listener;
    }
    public void dismissPopupMenus() {
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        if (mMenuView != null) mMenuView.dismissPopupMenus();
    }

    private void ensureMenu() {
        ensureMenuView();
        if (mMenuView.peekMenu() == null) {
            // "Initialize a new menu for the first time." The expanded-action-view
            // presenter AOSP also installs here is not ported (header).
            mMenuView.getMenu();
        }
    }

    /** AOSP :1173-1184. */
    private void ensureMenuView() {
        if (mMenuView == null) {
            mMenuView = new ActionMenuView(getContext());
            mMenuView.setPopupTheme(mPopupTheme);
            mMenuView.setOnMenuItemClickListener(mMenuViewItemClickListener);
            final LayoutParams lp = macapkDefaultLp();
            lp.gravity = Gravity.END | (mButtonGravity & Gravity.VERTICAL_GRAVITY_MASK);
            mMenuView.setLayoutParams(lp);
            addSystemView(mMenuView, false);
        }
    }

    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }

    /** AOSP :1494-1501. */
    private void ensureNavButtonView() {
        if (mNavButtonView == null) {
            mNavButtonView = new ImageButton(getContext(), null, 0, mNavButtonStyle);
            final LayoutParams lp = macapkDefaultLp();
            lp.gravity = Gravity.START | (mButtonGravity & Gravity.VERTICAL_GRAVITY_MASK);
            mNavButtonView.setLayoutParams(lp);
        }
    }

    /** AOSP :1503-1519. */
    private void ensureCollapseButtonView() {
        if (mCollapseButtonView == null) {
            mCollapseButtonView = new ImageButton(getContext(), null, 0, mNavButtonStyle);
            mCollapseButtonView.setImageDrawable(mCollapseIcon);
            mCollapseButtonView.setContentDescription(mCollapseDescription);
            final LayoutParams lp = macapkDefaultLp();
            lp.gravity = Gravity.START | (mButtonGravity & Gravity.VERTICAL_GRAVITY_MASK);
            lp.mViewType = LayoutParams.EXPANDED;
            mCollapseButtonView.setLayoutParams(lp);
            mCollapseButtonView.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { collapseActionView(); }
            });
        }
    }

    /** AOSP :1521-1539. `instanceof` in place of checkLayoutParams() so that
     *  MACAPK_CHECK_LAYOUT_PARAMS=0 (which makes checkLayoutParams answer true
     *  for anything) cannot turn the cast below into a ClassCastException. */
    private void addSystemView(View v, boolean allowHide) {
        final ViewGroup.LayoutParams vlp = v.getLayoutParams();
        final LayoutParams lp;
        if (vlp == null) {
            lp = macapkDefaultLp();
        } else if (!(vlp instanceof LayoutParams)) {
            lp = generateLayoutParams(vlp);
        } else {
            lp = (LayoutParams) vlp;
        }
        lp.mViewType = LayoutParams.SYSTEM;
        // No expanded action view is ever showing (header), so AOSP's
        // "allowHide && mExpandedActionView != null" branch cannot be taken.
        addView(v, lp);
    }

    /** AOSP :2252-2254. */
    private boolean isChildOrHidden(View child) {
        return child.getParent() == this || mHiddenViews.contains(child);
    }

    /** AOSP :2208-2211 generateDefaultLayoutParams, as the concrete type. */
    private LayoutParams macapkDefaultLp() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    /** AOSP :2208-2211.
     *
     *  DECLARED TYPE, deliberately ViewGroup.LayoutParams, as is
     *  generateLayoutParams(AttributeSet) below. The SDK declares both
     *  covariantly (android-34 android.jar: `protected Toolbar$LayoutParams
     *  generateDefaultLayoutParams()` and `public Toolbar$LayoutParams
     *  generateLayoutParams(AttributeSet)`), and a Toolbar-typed call compiled
     *  against the SDK names that descriptor. But a covariant declaration cannot
     *  hand back the base class's bare LayoutParams, and that bare LP is what
     *  MACAPK_PLATFORM_TOOLBAR=0 must return to BE the pre-port path:
     *  LayoutInflater.buildLayoutParams treats a bare ViewGroup.LayoutParams as
     *  "not meaningful" and builds its own, while any Toolbar.LayoutParams is kept
     *  and has its margins and layout_gravity filled, so a converted value would
     *  change what the gate-off arm lays out. Measured blast radius of leaving the
     *  covariant descriptors absent: debug/apishape_census.py over every container
     *  in ../Apks, 2026-09-26, 0 references to either (or to the ActionMenuView
     *  pair). Same call, same evidence, as LinearLayout.generateLayoutParams
     *  (the linearlp lane). Narrow both to Toolbar.LayoutParams when this gate
     *  retires; the lane gate's api rows list them as KNOWN OPEN until then. They
     *  were absent before the port too, so nothing that linked then fails now. */
    @Override
    public ViewGroup.LayoutParams generateDefaultLayoutParams() {
        if (!MACAPK_PLATFORM_TOOLBAR) return super.generateDefaultLayoutParams();
        return macapkDefaultLp();
    }

    /** AOSP :2190-2193. Declared type: see generateDefaultLayoutParams. */
    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        if (!MACAPK_PLATFORM_TOOLBAR) return super.generateLayoutParams(attrs);
        return new LayoutParams(getContext(), attrs);
    }

    /** AOSP :1584-1609: "Toolbars always eat touch events". */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!MACAPK_PLATFORM_TOOLBAR) return super.onTouchEvent(ev);
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) mEatingTouch = false;
        if (!mEatingTouch) {
            final boolean handled = super.onTouchEvent(ev);
            if (action == MotionEvent.ACTION_DOWN && !handled) mEatingTouch = true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mEatingTouch = false;
        }
        return true;
    }

    /** AOSP :508-512. */
    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        if (!MACAPK_PLATFORM_TOOLBAR) return;
        mContentInsets.setDirection(layoutDirection == LAYOUT_DIRECTION_RTL);
    }

    // ---- measure / layout (AOSP :1627-2188) ----

    /** AOSP converts a foreign LayoutParams when it is SET (onSetLayoutParams,
     *  :1612-1625). Our View.setLayoutParams has no parent hook, so do the same
     *  conversion at the one place that would otherwise cast it. */
    private void macapkConvertChildLayoutParams() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (!(lp instanceof LayoutParams)) {
                child.setLayoutParams(lp == null ? macapkDefaultLp() : generateLayoutParams(lp));
            }
        }
    }

    /** A Toolbar.LayoutParams built from XML has ActionBar.LayoutParams' -1
     *  default gravity; our inflater records android:layout_gravity on
     *  LayoutParams.macapkGravity (LayoutInflater.applyLayoutAttribute). AOSP's
     *  default for "no layout_gravity" is Gravity.NO_GRAVITY. */
    private static int gravityOf(LayoutParams lp) {
        int g = lp.gravity;
        if (g == -1 || g == Gravity.NO_GRAVITY) g = lp.macapkGravity;
        return g < 0 ? Gravity.NO_GRAVITY : g;
    }

    private static LayoutParams tlp(View v) { return (LayoutParams) v.getLayoutParams(); }

    private void measureChildConstrained(View child, int parentWidthSpec, int widthUsed,
            int parentHeightSpec, int heightUsed, int heightConstraint) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        int childWidthSpec = getChildMeasureSpec(parentWidthSpec,
                getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin
                        + widthUsed, lp.width);
        int childHeightSpec = getChildMeasureSpec(parentHeightSpec,
                getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin
                        + heightUsed, lp.height);
        final int childHeightMode = MeasureSpec.getMode(childHeightSpec);
        if (childHeightMode != MeasureSpec.EXACTLY && heightConstraint >= 0) {
            final int size = childHeightMode != MeasureSpec.UNSPECIFIED
                    ? Math.min(MeasureSpec.getSize(childHeightSpec), heightConstraint)
                    : heightConstraint;
            childHeightSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    private int measureChildCollapseMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed, int[] collapsingMargins) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final int leftDiff = lp.leftMargin - collapsingMargins[0];
        final int rightDiff = lp.rightMargin - collapsingMargins[1];
        final int leftMargin = Math.max(0, leftDiff);
        final int rightMargin = Math.max(0, rightDiff);
        final int hMargins = leftMargin + rightMargin;
        collapsingMargins[0] = Math.max(0, -leftDiff);
        collapsingMargins[1] = Math.max(0, -rightDiff);
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight() + hMargins + widthUsed, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin
                        + heightUsed, lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        return child.getMeasuredWidth() + hMargins;
    }

    /** AOSP :1677-1689 -- mCollapsible is a hidden setter, false by default. */
    private boolean shouldCollapse() { return false; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!MACAPK_PLATFORM_TOOLBAR) { super.onMeasure(widthMeasureSpec, heightMeasureSpec); return; }
        macapkConvertChildLayoutParams();
        int width = 0;
        int height = 0;
        int childState = 0;
        final int[] collapsingMargins = mTempMargins;
        final int marginStartIndex;
        final int marginEndIndex;
        if (isLayoutRtl()) {
            marginStartIndex = 1;
            marginEndIndex = 0;
        } else {
            marginStartIndex = 0;
            marginEndIndex = 1;
        }

        // System views measure first.
        int navWidth = 0;
        if (shouldLayout(mNavButtonView)) {
            measureChildConstrained(mNavButtonView, widthMeasureSpec, width, heightMeasureSpec, 0,
                    mMaxButtonHeight);
            navWidth = mNavButtonView.getMeasuredWidth() + getHorizontalMargins(mNavButtonView);
            height = Math.max(height, mNavButtonView.getMeasuredHeight()
                    + getVerticalMargins(mNavButtonView));
            childState = combineMeasuredStates(childState, mNavButtonView.getMeasuredState());
        }
        if (shouldLayout(mCollapseButtonView)) {
            measureChildConstrained(mCollapseButtonView, widthMeasureSpec, width,
                    heightMeasureSpec, 0, mMaxButtonHeight);
            navWidth = mCollapseButtonView.getMeasuredWidth()
                    + getHorizontalMargins(mCollapseButtonView);
            height = Math.max(height, mCollapseButtonView.getMeasuredHeight()
                    + getVerticalMargins(mCollapseButtonView));
            childState = combineMeasuredStates(childState, mCollapseButtonView.getMeasuredState());
        }
        final int contentInsetStart = getCurrentContentInsetStart();
        width += Math.max(contentInsetStart, navWidth);
        collapsingMargins[marginStartIndex] = Math.max(0, contentInsetStart - navWidth);

        int menuWidth = 0;
        if (shouldLayout(mMenuView)) {
            measureChildConstrained(mMenuView, widthMeasureSpec, width, heightMeasureSpec, 0,
                    mMaxButtonHeight);
            menuWidth = mMenuView.getMeasuredWidth() + getHorizontalMargins(mMenuView);
            height = Math.max(height, mMenuView.getMeasuredHeight()
                    + getVerticalMargins(mMenuView));
            childState = combineMeasuredStates(childState, mMenuView.getMeasuredState());
        }
        final int contentInsetEnd = getCurrentContentInsetEnd();
        width += Math.max(contentInsetEnd, menuWidth);
        collapsingMargins[marginEndIndex] = Math.max(0, contentInsetEnd - menuWidth);

        if (shouldLayout(mLogoView)) {
            width += measureChildCollapseMargins(mLogoView, widthMeasureSpec, width,
                    heightMeasureSpec, 0, collapsingMargins);
            height = Math.max(height, mLogoView.getMeasuredHeight()
                    + getVerticalMargins(mLogoView));
            childState = combineMeasuredStates(childState, mLogoView.getMeasuredState());
        }

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = tlp(child);
            if (lp.mViewType != LayoutParams.CUSTOM || !shouldLayout(child)) {
                // We already got all system views above. Skip them and GONE views.
                continue;
            }
            width += measureChildCollapseMargins(child, widthMeasureSpec, width,
                    heightMeasureSpec, 0, collapsingMargins);
            height = Math.max(height, child.getMeasuredHeight() + getVerticalMargins(child));
            childState = combineMeasuredStates(childState, child.getMeasuredState());
        }

        int titleWidth = 0;
        int titleHeight = 0;
        final int titleVertMargins = titleMarginTop + titleMarginBottom;
        final int titleHorizMargins = titleMarginStart + titleMarginEnd;
        if (shouldLayout(mTitleTextView)) {
            titleWidth = measureChildCollapseMargins(mTitleTextView, widthMeasureSpec,
                    width + titleHorizMargins, heightMeasureSpec, titleVertMargins,
                    collapsingMargins);
            titleWidth = mTitleTextView.getMeasuredWidth() + getHorizontalMargins(mTitleTextView);
            titleHeight = mTitleTextView.getMeasuredHeight() + getVerticalMargins(mTitleTextView);
            childState = combineMeasuredStates(childState, mTitleTextView.getMeasuredState());
        }
        if (shouldLayout(mSubtitleTextView)) {
            titleWidth = Math.max(titleWidth, measureChildCollapseMargins(mSubtitleTextView,
                    widthMeasureSpec, width + titleHorizMargins,
                    heightMeasureSpec, titleHeight + titleVertMargins,
                    collapsingMargins));
            titleHeight += mSubtitleTextView.getMeasuredHeight()
                    + getVerticalMargins(mSubtitleTextView);
            childState = combineMeasuredStates(childState, mSubtitleTextView.getMeasuredState());
        }

        width += titleWidth;
        height = Math.max(height, titleHeight);

        // Measurement already took padding into account for available space for the
        // children, add it in for the final size.
        width += getPaddingLeft() + getPaddingRight();
        height += getPaddingTop() + getPaddingBottom();

        final int measuredWidth = resolveSizeAndState(
                Math.max(width, getSuggestedMinimumWidth()),
                widthMeasureSpec, childState & MEASURED_STATE_MASK);
        final int measuredHeight = resolveSizeAndState(
                Math.max(height, getSuggestedMinimumHeight()),
                heightMeasureSpec, childState << MEASURED_HEIGHT_STATE_SHIFT);

        setMeasuredDimension(measuredWidth, shouldCollapse() ? 0 : measuredHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!MACAPK_PLATFORM_TOOLBAR) { super.onLayout(changed, l, t, r, b); return; }
        macapkConvertChildLayoutParams();
        final boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final int width = getWidth();
        final int height = getHeight();
        final int paddingLeft = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();
        int left = paddingLeft;
        int right = width - paddingRight;

        final int[] collapsingMargins = mTempMargins;
        collapsingMargins[0] = collapsingMargins[1] = 0;

        // Align views within the minimum toolbar height, if set.
        final int minHeight = getMinimumHeight();
        final int alignmentHeight = minHeight >= 0 ? Math.min(minHeight, b - t) : 0;

        if (shouldLayout(mNavButtonView)) {
            if (isRtl) {
                right = layoutChildRight(mNavButtonView, right, collapsingMargins, alignmentHeight);
            } else {
                left = layoutChildLeft(mNavButtonView, left, collapsingMargins, alignmentHeight);
            }
        }
        if (shouldLayout(mCollapseButtonView)) {
            if (isRtl) {
                right = layoutChildRight(mCollapseButtonView, right, collapsingMargins,
                        alignmentHeight);
            } else {
                left = layoutChildLeft(mCollapseButtonView, left, collapsingMargins,
                        alignmentHeight);
            }
        }
        if (shouldLayout(mMenuView)) {
            if (isRtl) {
                left = layoutChildLeft(mMenuView, left, collapsingMargins, alignmentHeight);
            } else {
                right = layoutChildRight(mMenuView, right, collapsingMargins, alignmentHeight);
            }
        }

        final int contentInsetLeft = getCurrentContentInsetLeft();
        final int contentInsetRight = getCurrentContentInsetRight();
        collapsingMargins[0] = Math.max(0, contentInsetLeft - left);
        collapsingMargins[1] = Math.max(0, contentInsetRight - (width - paddingRight - right));
        left = Math.max(left, contentInsetLeft);
        right = Math.min(right, width - paddingRight - contentInsetRight);

        if (shouldLayout(mLogoView)) {
            if (isRtl) {
                right = layoutChildRight(mLogoView, right, collapsingMargins, alignmentHeight);
            } else {
                left = layoutChildLeft(mLogoView, left, collapsingMargins, alignmentHeight);
            }
        }

        final boolean layoutTitle = shouldLayout(mTitleTextView);
        final boolean layoutSubtitle = shouldLayout(mSubtitleTextView);
        int titleHeight = 0;
        if (layoutTitle) {
            final LayoutParams lp = tlp(mTitleTextView);
            titleHeight += lp.topMargin + mTitleTextView.getMeasuredHeight() + lp.bottomMargin;
        }
        if (layoutSubtitle) {
            final LayoutParams lp = tlp(mSubtitleTextView);
            titleHeight += lp.topMargin + mSubtitleTextView.getMeasuredHeight() + lp.bottomMargin;
        }

        if (layoutTitle || layoutSubtitle) {
            int titleTop;
            final View topChild = layoutTitle ? mTitleTextView : mSubtitleTextView;
            final View bottomChild = layoutSubtitle ? mSubtitleTextView : mTitleTextView;
            final LayoutParams toplp = tlp(topChild);
            final LayoutParams bottomlp = tlp(bottomChild);
            final boolean titleHasWidth = layoutTitle && mTitleTextView.getMeasuredWidth() > 0
                    || layoutSubtitle && mSubtitleTextView.getMeasuredWidth() > 0;

            switch (mGravity & Gravity.VERTICAL_GRAVITY_MASK) {
                case Gravity.TOP:
                    titleTop = getPaddingTop() + toplp.topMargin + titleMarginTop;
                    break;
                default:
                case Gravity.CENTER_VERTICAL:
                    final int space = height - paddingTop - paddingBottom;
                    int spaceAbove = (space - titleHeight) / 2;
                    if (spaceAbove < toplp.topMargin + titleMarginTop) {
                        spaceAbove = toplp.topMargin + titleMarginTop;
                    } else {
                        final int spaceBelow = height - paddingBottom - titleHeight
                                - spaceAbove - paddingTop;
                        if (spaceBelow < toplp.bottomMargin + titleMarginBottom) {
                            spaceAbove = Math.max(0, spaceAbove
                                    - (bottomlp.bottomMargin + titleMarginBottom - spaceBelow));
                        }
                    }
                    titleTop = paddingTop + spaceAbove;
                    break;
                case Gravity.BOTTOM:
                    titleTop = height - paddingBottom - bottomlp.bottomMargin - titleMarginBottom
                            - titleHeight;
                    break;
            }
            if (isRtl) {
                final int rd = (titleHasWidth ? titleMarginStart : 0) - collapsingMargins[1];
                right -= Math.max(0, rd);
                collapsingMargins[1] = Math.max(0, -rd);
                int titleRight = right;
                int subtitleRight = right;

                if (layoutTitle) {
                    final LayoutParams lp = tlp(mTitleTextView);
                    final int titleLeft = titleRight - mTitleTextView.getMeasuredWidth();
                    final int titleBottom = titleTop + mTitleTextView.getMeasuredHeight();
                    mTitleTextView.layout(titleLeft, titleTop, titleRight, titleBottom);
                    titleRight = titleLeft - titleMarginEnd;
                    titleTop = titleBottom + lp.bottomMargin;
                }
                if (layoutSubtitle) {
                    final LayoutParams lp = tlp(mSubtitleTextView);
                    titleTop += lp.topMargin;
                    final int subtitleLeft = subtitleRight - mSubtitleTextView.getMeasuredWidth();
                    final int subtitleBottom = titleTop + mSubtitleTextView.getMeasuredHeight();
                    mSubtitleTextView.layout(subtitleLeft, titleTop, subtitleRight, subtitleBottom);
                    subtitleRight = subtitleRight - titleMarginEnd;
                    titleTop = subtitleBottom + lp.bottomMargin;
                }
                if (titleHasWidth) {
                    right = Math.min(titleRight, subtitleRight);
                }
            } else {
                final int ld = (titleHasWidth ? titleMarginStart : 0) - collapsingMargins[0];
                left += Math.max(0, ld);
                collapsingMargins[0] = Math.max(0, -ld);
                int titleLeft = left;
                int subtitleLeft = left;

                if (layoutTitle) {
                    final LayoutParams lp = tlp(mTitleTextView);
                    final int titleRight = titleLeft + mTitleTextView.getMeasuredWidth();
                    final int titleBottom = titleTop + mTitleTextView.getMeasuredHeight();
                    mTitleTextView.layout(titleLeft, titleTop, titleRight, titleBottom);
                    titleLeft = titleRight + titleMarginEnd;
                    titleTop = titleBottom + lp.bottomMargin;
                }
                if (layoutSubtitle) {
                    final LayoutParams lp = tlp(mSubtitleTextView);
                    titleTop += lp.topMargin;
                    final int subtitleRight = subtitleLeft + mSubtitleTextView.getMeasuredWidth();
                    final int subtitleBottom = titleTop + mSubtitleTextView.getMeasuredHeight();
                    mSubtitleTextView.layout(subtitleLeft, titleTop, subtitleRight, subtitleBottom);
                    subtitleLeft = subtitleRight + titleMarginEnd;
                    titleTop = subtitleBottom + lp.bottomMargin;
                }
                if (titleHasWidth) {
                    left = Math.max(titleLeft, subtitleLeft);
                }
            }
        }

        // Get all remaining children sorted for layout. This is all prepared
        // such that absolute layout direction can be used below.
        addCustomViewsWithGravity(mTempViews, Gravity.LEFT);
        final int leftViewsCount = mTempViews.size();
        for (int i = 0; i < leftViewsCount; i++) {
            left = layoutChildLeft(mTempViews.get(i), left, collapsingMargins, alignmentHeight);
        }

        addCustomViewsWithGravity(mTempViews, Gravity.RIGHT);
        final int rightViewsCount = mTempViews.size();
        for (int i = 0; i < rightViewsCount; i++) {
            right = layoutChildRight(mTempViews.get(i), right, collapsingMargins, alignmentHeight);
        }

        // Centered views try to center with respect to the whole bar, but views
        // pinned to the left or right can push the mass of centered views to one
        // side or the other.
        addCustomViewsWithGravity(mTempViews, Gravity.CENTER_HORIZONTAL);
        final int centerViewsWidth = getViewListMeasuredWidth(mTempViews, collapsingMargins);
        final int parentCenter = paddingLeft + (width - paddingLeft - paddingRight) / 2;
        final int halfCenterViewsWidth = centerViewsWidth / 2;
        int centerLeft = parentCenter - halfCenterViewsWidth;
        final int centerRight = centerLeft + centerViewsWidth;
        if (centerLeft < left) {
            centerLeft = left;
        } else if (centerRight > right) {
            centerLeft -= centerRight - right;
        }

        final int centerViewsCount = mTempViews.size();
        for (int i = 0; i < centerViewsCount; i++) {
            centerLeft = layoutChildLeft(mTempViews.get(i), centerLeft, collapsingMargins,
                    alignmentHeight);
        }

        mTempViews.clear();
    }

    private int getViewListMeasuredWidth(List<View> views, int[] collapsingMargins) {
        int collapseLeft = collapsingMargins[0];
        int collapseRight = collapsingMargins[1];
        int width = 0;
        final int count = views.size();
        for (int i = 0; i < count; i++) {
            final View v = views.get(i);
            final LayoutParams lp = tlp(v);
            final int l = lp.leftMargin - collapseLeft;
            final int r = lp.rightMargin - collapseRight;
            final int leftMargin = Math.max(0, l);
            final int rightMargin = Math.max(0, r);
            collapseLeft = Math.max(0, -l);
            collapseRight = Math.max(0, -r);
            width += leftMargin + v.getMeasuredWidth() + rightMargin;
        }
        return width;
    }

    private int layoutChildLeft(View child, int left, int[] collapsingMargins,
            int alignmentHeight) {
        final LayoutParams lp = tlp(child);
        final int l = lp.leftMargin - collapsingMargins[0];
        left += Math.max(0, l);
        collapsingMargins[0] = Math.max(0, -l);
        final int top = getChildTop(child, alignmentHeight);
        final int childWidth = child.getMeasuredWidth();
        child.layout(left, top, left + childWidth, top + child.getMeasuredHeight());
        left += childWidth + lp.rightMargin;
        return left;
    }

    private int layoutChildRight(View child, int right, int[] collapsingMargins,
            int alignmentHeight) {
        final LayoutParams lp = tlp(child);
        final int r = lp.rightMargin - collapsingMargins[1];
        right -= Math.max(0, r);
        collapsingMargins[1] = Math.max(0, -r);
        final int top = getChildTop(child, alignmentHeight);
        final int childWidth = child.getMeasuredWidth();
        child.layout(right - childWidth, top, right, top + child.getMeasuredHeight());
        right -= childWidth + lp.leftMargin;
        return right;
    }

    private int getChildTop(View child, int alignmentHeight) {
        final LayoutParams lp = tlp(child);
        final int childHeight = child.getMeasuredHeight();
        final int alignmentOffset = alignmentHeight > 0 ? (childHeight - alignmentHeight) / 2 : 0;
        switch (getChildVerticalGravity(gravityOf(lp))) {
            case Gravity.TOP:
                return getPaddingTop() - alignmentOffset;
            case Gravity.BOTTOM:
                return getHeight() - getPaddingBottom() - childHeight
                        - lp.bottomMargin - alignmentOffset;
            default:
            case Gravity.CENTER_VERTICAL:
                final int paddingTop = getPaddingTop();
                final int paddingBottom = getPaddingBottom();
                final int height = getHeight();
                final int space = height - paddingTop - paddingBottom;
                int spaceAbove = (space - childHeight) / 2;
                if (spaceAbove < lp.topMargin) {
                    spaceAbove = lp.topMargin;
                } else {
                    final int spaceBelow = height - paddingBottom - childHeight
                            - spaceAbove - paddingTop;
                    if (spaceBelow < lp.bottomMargin) {
                        spaceAbove = Math.max(0, spaceAbove - (lp.bottomMargin - spaceBelow));
                    }
                }
                return paddingTop + spaceAbove;
        }
    }

    private int getChildVerticalGravity(int gravity) {
        final int vgrav = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        switch (vgrav) {
            case Gravity.TOP:
            case Gravity.BOTTOM:
            case Gravity.CENTER_VERTICAL:
                return vgrav;
            default:
                return mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        }
    }

    private void addCustomViewsWithGravity(List<View> views, int gravity) {
        final boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final int childCount = getChildCount();
        final int absGrav = Gravity.getAbsoluteGravity(gravity, getLayoutDirection());
        views.clear();
        if (isRtl) {
            for (int i = childCount - 1; i >= 0; i--) {
                final View child = getChildAt(i);
                final LayoutParams lp = tlp(child);
                if (lp.mViewType == LayoutParams.CUSTOM && shouldLayout(child)
                        && getChildHorizontalGravity(gravityOf(lp)) == absGrav) {
                    views.add(child);
                }
            }
        } else {
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = tlp(child);
                if (lp.mViewType == LayoutParams.CUSTOM && shouldLayout(child)
                        && getChildHorizontalGravity(gravityOf(lp)) == absGrav) {
                    views.add(child);
                }
            }
        }
    }

    private int getChildHorizontalGravity(int gravity) {
        final int ld = getLayoutDirection();
        final int absGrav = Gravity.getAbsoluteGravity(gravity, ld);
        final int hGrav = absGrav & Gravity.HORIZONTAL_GRAVITY_MASK;
        switch (hGrav) {
            case Gravity.LEFT:
            case Gravity.RIGHT:
            case Gravity.CENTER_HORIZONTAL:
                return hGrav;
            default:
                return ld == LAYOUT_DIRECTION_RTL ? Gravity.RIGHT : Gravity.LEFT;
        }
    }

    private boolean shouldLayout(View view) {
        return view != null && view.getParent() == this && view.getVisibility() != GONE;
    }

    private int getHorizontalMargins(View v) {
        final MarginLayoutParams mlp = (MarginLayoutParams) v.getLayoutParams();
        return mlp.getMarginStart() + mlp.getMarginEnd();
    }

    private int getVerticalMargins(View v) {
        final MarginLayoutParams mlp = (MarginLayoutParams) v.getLayoutParams();
        return mlp.topMargin + mlp.bottomMargin;
    }

    // AOSP frameworks/base core/java/android/widget/Toolbar.java (android15-release,
    // ~line 2319): `public static class LayoutParams extends ActionBar.LayoutParams`.
    // This was `extends ViewGroup.MarginLayoutParams` -- one hop too shallow in the
    // real hierarchy (ActionBar.LayoutParams itself extends MarginLayoutParams and
    // adds the `gravity` field), found by debug/framework_lint.py's class-shape diff
    // against android.jar. The wrong superclass meant `instanceof ActionBar.LayoutParams`
    // was false for a Toolbar.LayoutParams and this class carried its OWN shadow
    // `gravity` field instead of ActionBar.LayoutParams's -- two different fields
    // named the same thing, readable/writable independently depending which static
    // type a caller held, silently diverging. Also restores the constructors AOSP
    // provides that this class was missing entirely (ActionBar.LayoutParams-typed
    // source, MarginLayoutParams-typed source, gravity-only) and the (width,height)
    // default gravity AOSP sets (CENTER_VERTICAL|START, not NO_GRAVITY).
    /** v1.0.1643: AOSP checkLayoutParams, so addView converts a foreign
     *  LayoutParams into this container's own kind (ViewGroup.addView ->
     *  generateLayoutParams). Gate MACAPK_CHECK_LAYOUT_PARAMS=0 restores the old
     *  accept-anything answer for an A/B. */
    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (!MACAPK_CHECK_LP) return true;
        return p instanceof Toolbar.LayoutParams;
    }
    private static final boolean MACAPK_CHECK_LP =
        !"0".equals(System.getenv("MACAPK_CHECK_LAYOUT_PARAMS"));

    public static class LayoutParams extends ActionBar.LayoutParams {
        // AOSP :2320-2324: which of the Toolbar's own children this is. The
        // measure / layout passes treat SYSTEM children (title, nav, menu, ...)
        // by name and lay out only CUSTOM ones by gravity.
        static final int CUSTOM = 0;
        static final int SYSTEM = 1;
        static final int EXPANDED = 2;
        int mViewType = CUSTOM;

        public LayoutParams(Context c, AttributeSet attrs) { super(c, attrs); }
        public LayoutParams(int width, int height) {
            super(width, height);
            this.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        }
        public LayoutParams(int width, int height, int gravity) { super(width, height); this.gravity = gravity; }
        public LayoutParams(int gravity) { this(WRAP_CONTENT, MATCH_PARENT, gravity); }
        public LayoutParams(LayoutParams source) {
            super(source);
            mViewType = source.mViewType;   // AOSP :2345-2348
        }
        public LayoutParams(ActionBar.LayoutParams source) { super(source); }
        // ActionBar.LayoutParams has no MarginLayoutParams-typed constructor (same
        // gap AOSP's own Toolbar works around -- see the upstream comment "ActionBar.
        // LayoutParams doesn't have a MarginLayoutParams constructor. Fake it here and
        // copy over the relevant data."), so copy the margin fields by hand here too.
        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
            this.leftMargin = source.leftMargin;
            this.topMargin = source.topMargin;
            this.rightMargin = source.rightMargin;
            this.bottomMargin = source.bottomMargin;
        }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }
    }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.Toolbar had 31 public
    // members missing -- the whole content-inset accessor family, the collapse
    // button, the overflow menu, the popup theme, the logo description, and
    // generateLayoutParams). Bodies mirror
    // frameworks/base/core/java/android/widget/Toolbar.java (android16-release);
    // cited line numbers are from that file.
    //
    // With MACAPK_PLATFORM_TOOLBAR=0 the pre-port stub answers apply: AOSP's
    // lazily-created child views do not exist, and where a getter's AOSP answer is
    // "ask that child view" a boolean stands in for "has the child been created
    // yet", because that boolean decides AOSP's null-vs-value answer.
    // ==================================================================

    private final RtlSpacingHelper mContentInsets = new RtlSpacingHelper();
    private int mContentInsetStartWithNavigation = RtlSpacingHelper.UNDEFINED;
    private int mContentInsetEndWithActions = RtlSpacingHelper.UNDEFINED;

    private boolean mHasNavButtonView;
    private CharSequence mNavigationContentDescription;
    private boolean mHasLogoView;
    private CharSequence mLogoDescription;
    private boolean mHasCollapseButtonView;
    private Drawable mCollapseButtonDrawable;
    private CharSequence mCollapseContentDescription;
    private Drawable mOverflowIcon;
    private int mPopupTheme;

    // ---- content insets -----------------------------------------------
    /** AOSP :1254 / :1275 / :1319 / :1340. */
    public int getContentInsetStart() { return mContentInsets.getStart(); }
    public int getContentInsetEnd()   { return mContentInsets.getEnd(); }
    public int getContentInsetLeft()  { return mContentInsets.getLeft(); }
    public int getContentInsetRight() { return mContentInsets.getRight(); }

    /** AOSP :1357 / :1401 -- fall back to the plain inset when unset. */
    public int getContentInsetStartWithNavigation() {
        return mContentInsetStartWithNavigation != RtlSpacingHelper.UNDEFINED
                ? mContentInsetStartWithNavigation : getContentInsetStart();
    }
    public int getContentInsetEndWithActions() {
        return mContentInsetEndWithActions != RtlSpacingHelper.UNDEFINED
                ? mContentInsetEndWithActions : getContentInsetEnd();
    }
    /** AOSP :1419. */
    public void setContentInsetEndWithActions(int insetEndWithActions) {
        if (insetEndWithActions < 0) insetEndWithActions = RtlSpacingHelper.UNDEFINED;
        if (insetEndWithActions != mContentInsetEndWithActions) {
            mContentInsetEndWithActions = insetEndWithActions;
            if (getNavigationIcon() != null) requestLayout();
        }
    }

    /** AOSP :1439 / :1453 / :1473 / :1488. getCurrentContentInsetEnd asks
     *  whether the action menu has visible items (AOSP :1454-1461). */
    public int getCurrentContentInsetStart() {
        return getNavigationIcon() != null
                ? Math.max(getContentInsetStart(), Math.max(mContentInsetStartWithNavigation, 0))
                : getContentInsetStart();
    }
    public int getCurrentContentInsetEnd() {
        if (!MACAPK_PLATFORM_TOOLBAR) return getContentInsetEnd();
        boolean hasActions = false;
        if (mMenuView != null) {
            final Menu mb = mMenuView.peekMenu();
            hasActions = mb != null && mb.hasVisibleItems();
        }
        return hasActions
                ? Math.max(getContentInsetEnd(), Math.max(mContentInsetEndWithActions, 0))
                : getContentInsetEnd();
    }
    public int getCurrentContentInsetLeft() {
        return isLayoutRtl() ? getCurrentContentInsetEnd() : getCurrentContentInsetStart();
    }
    public int getCurrentContentInsetRight() {
        return isLayoutRtl() ? getCurrentContentInsetStart() : getCurrentContentInsetEnd();
    }

    // ---- logo description ---------------------------------------------
    /** AOSP :686 / :674 / :701. */
    public void setLogoDescription(CharSequence description) {
        if (!MACAPK_PLATFORM_TOOLBAR) {
            if (description != null && description.length() > 0) mHasLogoView = true;
            if (mHasLogoView) mLogoDescription = description;
            return;
        }
        if (!TextUtils.isEmpty(description)) ensureLogoView();
        if (mLogoView != null) mLogoView.setContentDescription(description);
    }
    public void setLogoDescription(int resId) { setLogoDescription(getContext().getText(resId)); }
    public CharSequence getLogoDescription() {
        if (!MACAPK_PLATFORM_TOOLBAR) return mHasLogoView ? mLogoDescription : null;
        return mLogoView != null ? mLogoView.getContentDescription() : null;
    }

    // ---- collapse button ----------------------------------------------
    /** AOSP :1117 / :1103 / :1089. AOSP's null case resets the button's image
     *  to the styled mCollapseIcon. */
    public void setCollapseIcon(Drawable icon) {
        if (!MACAPK_PLATFORM_TOOLBAR) {
            if (icon != null) {
                mHasCollapseButtonView = true;
                mCollapseButtonDrawable = icon;
            } else if (mHasCollapseButtonView) {
                mCollapseButtonDrawable = null;
            }
            return;
        }
        if (icon != null) {
            ensureCollapseButtonView();
            mCollapseButtonView.setImageDrawable(icon);
        } else if (mCollapseButtonView != null) {
            mCollapseButtonView.setImageDrawable(mCollapseIcon);
        }
    }
    public void setCollapseIcon(int resId) { setCollapseIcon(getContext().getDrawable(resId)); }
    public Drawable getCollapseIcon() {
        if (!MACAPK_PLATFORM_TOOLBAR) return mHasCollapseButtonView ? mCollapseButtonDrawable : null;
        return mCollapseButtonView != null ? mCollapseButtonView.getDrawable() : null;
    }
    /** AOSP :1043 and its two setters just below it. */
    public void setCollapseContentDescription(CharSequence description) {
        if (!MACAPK_PLATFORM_TOOLBAR) {
            if (description != null && description.length() > 0) mHasCollapseButtonView = true;
            if (mHasCollapseButtonView) mCollapseContentDescription = description;
            return;
        }
        if (!TextUtils.isEmpty(description)) ensureCollapseButtonView();
        if (mCollapseButtonView != null) mCollapseButtonView.setContentDescription(description);
    }
    public void setCollapseContentDescription(int resId) {
        setCollapseContentDescription(resId != 0 ? getContext().getText(resId) : null);
    }
    public CharSequence getCollapseContentDescription() {
        if (!MACAPK_PLATFORM_TOOLBAR) {
            return mHasCollapseButtonView ? mCollapseContentDescription : null;
        }
        return mCollapseButtonView != null ? mCollapseButtonView.getContentDescription() : null;
    }

    // ---- expanded action view -----------------------------------------
    /** AOSP :721 / :735. Both answer off mExpandedMenuPresenter, which needs a
     *  real MenuItemImpl expansion we do not run (header); AOSP's own null path
     *  is "false" and "do nothing". */
    public boolean hasExpandedActionView() { return false; }
    public void collapseActionView() { }

    // ---- overflow menu -------------------------------------------------
    /** AOSP :538 / :552 / :561 -- each is `mMenuView != null && ...`. */
    public boolean isOverflowMenuShowing() {
        if (!MACAPK_PLATFORM_TOOLBAR) return false;
        return mMenuView != null && mMenuView.isOverflowMenuShowing();
    }
    public boolean showOverflowMenu() {
        if (!MACAPK_PLATFORM_TOOLBAR) return false;
        return mMenuView != null && mMenuView.showOverflowMenu();
    }
    public boolean hideOverflowMenu() {
        if (!MACAPK_PLATFORM_TOOLBAR) return false;
        return mMenuView != null && mMenuView.hideOverflowMenu();
    }

    /** AOSP :1144 / :1155 -- "ensureMenu(); mMenuView.setOverflowIcon(icon)". The
     *  pre-port stub kept the icon on the Toolbar itself. */
    public void setOverflowIcon(Drawable icon) {
        if (!MACAPK_PLATFORM_TOOLBAR) { mOverflowIcon = icon; return; }
        ensureMenu();
        mMenuView.setOverflowIcon(icon);
    }
    public Drawable getOverflowIcon() {
        if (!MACAPK_PLATFORM_TOOLBAR) return mOverflowIcon;
        ensureMenu();
        return mMenuView.getOverflowIcon();
    }

    // ---- layout params --------------------------------------------------
    /** AOSP Toolbar.generateLayoutParams(ViewGroup.LayoutParams) (:2195-2206) --
     *  preserve the richest source type available, so gravity
     *  (ActionBar.LayoutParams) and margins (MarginLayoutParams) survive the
     *  conversion instead of being dropped by the generic copy constructor.
     *  Overrides ViewGroup.generateLayoutParams(LayoutParams), which ViewGroup
     *  now declares and addView calls when checkLayoutParams rejects a foreign
     *  LayoutParams; the covariant return matches the SDK descriptor
     *  (Toolbar$LayoutParams) and javac bridges the ViewGroup one. */
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (p instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) p);
        } else if (p instanceof ActionBar.LayoutParams) {
            return new LayoutParams((ActionBar.LayoutParams) p);
        } else if (p instanceof ViewGroup.MarginLayoutParams) {
            return new LayoutParams((ViewGroup.MarginLayoutParams) p);
        }
        return new LayoutParams(p);
    }
}
