// android.app.AlertDialog -- Material/AppCompat-style alert. Per AOSP
// source at https://cs.android.com/.../app/AlertDialog.java, backed there
// by com.android.internal.app.AlertController.
//
// AOSP's Builder holds an AlertController.AlertParams P; every setter
// stores into P and create() does
//     final AlertDialog dialog = new AlertDialog(P.mContext, 0, false);
//     P.apply(dialog.mAlert);
// so the accumulated title / message / icon / buttons / list all reach the
// dialog's controller. Our Builder used to be pure `return this;` with a
// create() that threw the whole thing away, which meant the standard
// idiom -- the way essentially every real-world AlertDialog is built --
// produced an object that knew nothing about itself: no title, no message,
// no button text, and no way for a click to ever reach the app's
// OnClickListener. getButton() hard-returned null, so even the very common
// `dialog.show(); dialog.getButton(BUTTON_POSITIVE).setEnabled(false);`
// NPE'd.
//
// Now the Builder accumulates state, create() transfers it, and onCreate()
// installs a real content view (AOSP: AlertController.installContent) with
// the title/message/list/buttons as actual Views, so getButton() hands back
// a live Button and a click on it runs listener.onClick(dialog, which) and
// then dismisses, exactly like AlertController's ButtonHandler.
package android.app;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
public class AlertDialog extends Dialog implements DialogInterface {
    public static final int THEME_DEVICE_DEFAULT_DARK = 4;
    public static final int THEME_DEVICE_DEFAULT_LIGHT = 5;
    public static final int THEME_HOLO_DARK = 2;
    public static final int THEME_HOLO_LIGHT = 3;
    public static final int THEME_TRADITIONAL = 1;

    /** MACAPK_ALERT_DIALOG_IDS (default ON; =0 builds the pre-change id-less
     *  tree, for bisecting). The views we build carry the ids AOSP's layouts
     *  give them -- alert_dialog.xml / alert_dialog_material.xml and their
     *  includes: @id/parentPanel, title_template, icon, alertTitle, contentPanel,
     *  scrollView, message, customPanel, custom, buttonPanel, button1..3 and the
     *  spacers -- because app code reaches into a platform AlertDialog by id.
     *  AlertController itself does (`mTitleView = (TextView)
     *  mWindow.findViewById(R.id.alertTitle)`, android14 AlertController.java
     *  :707), and so do SDKs: AppLovin's consent dialog (com.applovin.impl.z0,
     *  in 10 corpus apps) runs, from its OnShowListener,
     *  getIdentifier("android:id/alertTitle") -> findViewById -> (TextView)
     *  .getCurrentTextColor(). With no ids that NPE'd, Dialog.show swallowed it,
     *  and the title never got its link colour, movement method or min height
     *  (docs/fleet-games-smashhit-gd-meltdown-jumplite_2026-09-26.md, G1).
     *  Public ids are the frozen literals below; internal ones (alertTitle,
     *  parentPanel, ...) come from the same framework table the app queries
     *  (getIdentifier(name, "id", "android")), so the two agree by
     *  construction, and a view is left without an id when that answers 0. */
    static final boolean MACAPK_ALERT_DIALOG_IDS =
        !"0".equals(System.getenv("MACAPK_ALERT_DIALOG_IDS"));

    /** MACAPK_ALERT_DIALOG_TITLE (default ON; =0 builds the title as the
     *  pre-change plain single-line TextView). The title is a
     *  com.android.internal.widget.DialogTitle, as in both AOSP alert layouts,
     *  so a title that does not fit on one line wraps to two at
     *  textAppearanceMedium instead of ending in "..." (G3). */
    /** BISECT GATE (2026-09-26, default ON): the Material title is sans-serif-medium, not fake bold. */
    static final boolean MACAPK_ALERT_TITLE_MEDIUM =
        !"0".equals(System.getenv("MACAPK_ALERT_TITLE_MEDIUM"));
    /** BISECT GATE (2026-09-26, default ON): dialog buttons carry the borderless mask's 12dp x 10dp padding. */
    static final boolean MACAPK_ALERT_BUTTON_MASK_PADDING =
        !"0".equals(System.getenv("MACAPK_ALERT_BUTTON_MASK_PADDING"));
    static final boolean MACAPK_ALERT_DIALOG_TITLE =
        !"0".equals(System.getenv("MACAPK_ALERT_DIALOG_TITLE"));

    /** MACAPK_ALERT_DIALOG_TRADITIONAL (default ON; =0 always builds the
     *  Material tree, the pre-change behaviour). AlertController picks its
     *  layout from the dialog theme's alertDialogStyle (android14
     *  AlertController.java:212-216, verbatim):
     *
     *      final TypedArray a = context.obtainStyledAttributes(null,
     *              R.styleable.AlertDialog, R.attr.alertDialogStyle, 0);
     *      mAlertDialogLayout = a.getResourceId(
     *              R.styleable.AlertDialog_layout, R.layout.alert_dialog);
     *
     *  The root Theme binds alertDialogStyle = @style/AlertDialog, which sets
     *  the popup_* backgrounds and NO layout (styles.xml:56-70), so every
     *  Theme / Theme.NoTitleBar / Theme.Light app gets the traditional
     *  R.layout.alert_dialog (dark title and message panels, 22sp / 18sp text,
     *  a grey button bar, a single button centred at half width). Material,
     *  Holo and DeviceDefault bind their own layouts. We built the Material tree
     *  for everyone. Measured victim: Geometry Dash Meltdown, a
     *  Theme.NoTitleBar.Fullscreen activity whose alertDialogTheme is
     *  Theme.Dialog.Alert (G2). Needs MACAPK_DIALOG_THEME_WRAPPER, since the
     *  style is read from the dialog theme the wrapper installs; with that off
     *  the whole dialog-theme stack is back at its pre-change behaviour.
     *
     *  ⚠ ALSO NEEDS THE 9-PATCH LATTICE (MACAPK_TRADITIONAL_LATTICE_READY). The
     *  traditional panels ARE 9-patches (popup_top/center/bottom_dark|bright,
     *  btn_default), and a NinePatch.draw that blits the whole bitmap scaled
     *  smears their fixed borders across the panel: popup_top_dark (101px,
     *  28px translucent shadow columns each side) drawn 1000px wide keeps
     *  alpha 0x0e at x=100 where the stretched body is 0xe6, so about a quarter
     *  of each side of every panel is nearly transparent, with white 18sp text
     *  over the game (measured by debug/w5alertdialog_gate.sh's ninepatch row).
     *  That is worse than the Material tree it replaces, and the reviewer's
     *  census puts it on 141 theme rows across ~45 corpus apps, hcr181 (a
     *  protected sweep row) and all 10 AppLovin games among them. So the
     *  traditional tree is built only when android.graphics.NinePatch carries
     *  the lattice draw (debug/w5alertdialog_verify/ninepatch-lattice.patch)
     *  AND its gate MACAPK_NINEPATCH_LATTICE is on; otherwise every dialog keeps
     *  the Material tree, exactly as with this gate at 0. The dependency is read
     *  reflectively because NinePatch is another lane's file and its gate field
     *  is package-private to android.graphics. Consequence for bisecting:
     *  MACAPK_NINEPATCH_LATTICE=0 also puts dialogs back on the Material tree
     *  (the pre-change state of both); the lattice's own effect on a panel is
     *  isolated by the gate's ninepatch row, which draws the 9-patch directly. */
    static final boolean MACAPK_ALERT_DIALOG_TRADITIONAL =
        !"0".equals(System.getenv("MACAPK_ALERT_DIALOG_TRADITIONAL"));

    /** Whether the running NinePatch honours its stretch zones (see above):
     *  its MACAPK_NINEPATCH_LATTICE field exists and is true. False when the
     *  field is absent (the lattice patch has not landed) or unreadable. */
    static final boolean MACAPK_TRADITIONAL_LATTICE_READY = macapkLatticeReady();

    private static boolean macapkLatticeReady() {
        try {
            java.lang.reflect.Field f = android.graphics.NinePatch.class
                    .getDeclaredField("MACAPK_NINEPATCH_LATTICE");
            f.setAccessible(true);
            return f.getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** MACAPK_ALERT_DIALOG_ICON_ATTR (default ON; =0 restores the pre-change
     *  setIconAttribute, which stored the ATTRIBUTE id as the icon's resource
     *  id and resolved nothing). android14 AlertDialog.java:426-430, verbatim:
     *
     *      public void setIconAttribute(@AttrRes int attrId) {
     *          TypedValue out = new TypedValue();
     *          mContext.getTheme().resolveAttribute(attrId, out, true);
     *          mAlert.setIcon(out.resourceId);
     *      }
     *
     *  (Builder.setIconAttribute :612-617 and AlertParams.apply :1123-1125 do
     *  the same against the builder's / dialog's theme.) The Material tree never
     *  read the stored id, so the old value was harmless there, but the
     *  traditional tree's setupTitle does (`if (mIconId != 0)`), and
     *  Resources.getDrawable(0x01010355, alertDialogIcon's attr id) answered an
     *  unrelated 24x24 vector (measured, 2026-09-26 review). With it on, the
     *  Material tree also shows the theme's icon (ic_dialog_alert_material
     *  under Theme.Material), as a device does. */
    static final boolean MACAPK_ALERT_DIALOG_ICON_ATTR =
        !"0".equals(System.getenv("MACAPK_ALERT_DIALOG_ICON_ATTR"));

    /** android.R.id / android.R.attr values (public-final.xml; frozen, so safe
     *  as literals -- see Dialog's ATTR_* note). */
    private static final int ID_ICON    = 0x01020006;
    private static final int ID_MESSAGE = 0x0102000b;
    private static final int ID_BUTTON1 = 0x01020019;
    private static final int ID_BUTTON2 = 0x0102001a;
    private static final int ID_BUTTON3 = 0x0102001b;
    private static final int ID_CUSTOM  = 0x0102002b;
    static final int ATTR_ALERT_DIALOG_STYLE = 0x0101005d;
    private static final int ATTR_LAYOUT = 0x010100f2;
    private static final int ATTR_TEXT_APPEARANCE_LARGE  = 0x01010040;
    private static final int ATTR_TEXT_APPEARANCE_MEDIUM = 0x01010041;
    private static final int ATTR_WINDOW_BACKGROUND = 0x01010054;
    private static final int ATTR_BUTTON_BAR_STYLE = 0x0101032e;
    private static final int ATTR_BUTTON_BAR_POSITIVE_BUTTON_STYLE = 0x01010489;
    private static final int ATTR_BUTTON_BAR_NEUTRAL_BUTTON_STYLE  = 0x0101048a;
    private static final int ATTR_BUTTON_BAR_NEGATIVE_BUTTON_STYLE = 0x0101048b;

    // Constructors: android14 core/java/android/app/AlertDialog.java :144-230,
    // verbatim in behaviour (AOSP declares the first two `protected`; ours stay
    // public, which no caller compiled against the SDK can tell apart):
    //
    //     protected AlertDialog(Context context) { this(context, 0); }
    //     protected AlertDialog(Context context, boolean cancelable, OnCancelListener l) {
    //         this(context, 0); setCancelable(cancelable); setOnCancelListener(l); }
    //     protected AlertDialog(Context context, @StyleRes int themeResId) {
    //         this(context, themeResId, true); }
    //     AlertDialog(Context context, @StyleRes int themeResId, boolean createContextThemeWrapper) {
    //         super(context, createContextThemeWrapper ? resolveDialogTheme(context, themeResId) : 0,
    //                 createContextThemeWrapper);
    //
    // i.e. an AlertDialog runs in ?android:alertDialogTheme (not dialogTheme),
    // or in the THEME_* legacy constant's theme. We ignored themeResId and
    // passed the caller's Context straight through, so the dialog read the
    // activity theme. Under MACAPK_DIALOG_THEME_WRAPPER=0 the 3-arg form hands
    // Dialog createContextThemeWrapper=false, which stores the caller's Context
    // unchanged -- the pre-change behaviour of every overload.
    public AlertDialog(Context c) { this(c, 0); }
    public AlertDialog(Context c, int themeResId) { this(c, themeResId, true); }
    AlertDialog(Context c, int themeResId, boolean createContextThemeWrapper) {
        super(c, (MACAPK_DIALOG_THEME_WRAPPER && createContextThemeWrapper)
                        ? resolveDialogTheme(c, themeResId) : 0,
              MACAPK_DIALOG_THEME_WRAPPER && createContextThemeWrapper);
    }
    protected AlertDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        this(context, 0);
        setCancelable(cancelable);
        setOnCancelListener(cancelListener);
    }

    /** android14 AlertDialog.resolveDialogTheme (:211-230), verbatim in
     *  behaviour: the five legacy THEME_* constants name framework themes, a
     *  real resource id (ResourceId.isValid: package byte and type byte both
     *  non-zero) is used as given, and anything else resolves
     *  ?android:alertDialogTheme on the caller's theme. AOSP names the legacy
     *  themes by their internal R.style ids; our framework table is looked up
     *  by NAME because internal ids are not frozen across API levels. */
    static int resolveDialogTheme(Context context, int themeResId) {
        final String legacy;
        switch (themeResId) {
            case THEME_TRADITIONAL:          legacy = "Theme.Dialog.Alert"; break;
            case THEME_HOLO_DARK:            legacy = "Theme.Holo.Dialog.Alert"; break;
            case THEME_HOLO_LIGHT:           legacy = "Theme.Holo.Light.Dialog.Alert"; break;
            case THEME_DEVICE_DEFAULT_DARK:  legacy = "Theme.DeviceDefault.Dialog.Alert"; break;
            case THEME_DEVICE_DEFAULT_LIGHT: legacy = "Theme.DeviceDefault.Light.Dialog.Alert"; break;
            default:                         legacy = null;
        }
        if (legacy != null) {
            try {
                android.content.res.Resources r = context == null ? null : context.getResources();
                return r == null ? 0 : r.getIdentifier(legacy, "style", "android");
            } catch (Throwable t) {
                return 0;
            }
        }
        // ResourceId.isValid: `id != -1 && (id & 0xff000000) != 0 && (id & 0x00ff0000) != 0`.
        if (themeResId != -1 && (themeResId & 0xff000000) != 0
                && (themeResId & 0x00ff0000) != 0) return themeResId;
        return context == null ? 0 : macapkResolveThemeAttr(context, ATTR_ALERT_DIALOG_THEME);
    }

    private CharSequence mMessage;
    private android.graphics.drawable.Drawable mIcon;
    private int mIconId;
    private android.view.View mView;
    private CharSequence mTitle;
    private android.view.View mCustomTitleView;

    // AlertController's three button slots: text + the listener a click on
    // that slot must reach.
    private CharSequence mButtonPositiveText, mButtonNegativeText, mButtonNeutralText;
    private OnClickListener mButtonPositiveListener, mButtonNegativeListener, mButtonNeutralListener;
    private android.widget.Button mButtonPositive, mButtonNegative, mButtonNeutral;

    // List content (setItems / setSingleChoiceItems / setMultiChoiceItems /
    // setAdapter all funnel into an adapter plus a click listener).
    private android.widget.ListAdapter mAdapter;
    private OnClickListener mListListener;
    private OnMultiChoiceClickListener mMultiChoiceListener;
    private boolean mIsSingleChoice;
    private boolean mIsMultiChoice;
    private int mCheckedItem = -1;
    private boolean[] mCheckedItems;
    /** AOSP AlertController's params carry an OnItemSelectedListener that is
     *  installed on the list view; see Builder.setOnItemSelectedListener. */
    private android.widget.AdapterView.OnItemSelectedListener mOnItemSelectedListener;
    private android.widget.ListView mListView;

    private boolean mContentInstalled;
    private android.widget.TextView mTitleView;
    private android.widget.TextView mMessageView;
    /** AOSP alert_dialog_material.xml's @id/contentPanel: the message (or the
     *  list that replaces it). Kept so a post-show setMessage() can reveal it. */
    private android.view.View mContentPanel;
    /** AOSP AlertController.mViewSpacing*: setView(View, l, t, r, b) pads the
     *  @id/custom frame instead of the view. */
    private boolean mViewSpacingSpecified;
    private int mViewSpacingLeft, mViewSpacingTop, mViewSpacingRight, mViewSpacingBottom;

    public void setMessage(CharSequence message) {
        this.mMessage = message;
        if (mMessageView != null) {
            mMessageView.setText(message);
            mMessageView.setVisibility(message == null ? View.GONE : View.VISIBLE);
            if (mContentPanel != null) {
                mContentPanel.setVisibility(message == null ? View.GONE : View.VISIBLE);
            }
        }
    }
    public CharSequence getMessage() { return mMessage; }
    public void setIcon(int resId) {
        this.mIconId = resId;
        this.mIcon = resolveDrawable(resId);
        mMacapkIconIdIsAttr = false;
    }
    public void setIcon(android.graphics.drawable.Drawable icon) { this.mIcon = icon; }
    public android.graphics.drawable.Drawable getIcon() { return mIcon; }
    /** AOSP resolves the icon ATTRIBUTE against the dialog's theme and sets the
     *  resource it names (see MACAPK_ALERT_DIALOG_ICON_ATTR). */
    public void setIconAttribute(int attrId) {
        if (!MACAPK_ALERT_DIALOG_ICON_ATTR) {
            // Pre-change: the attribute id stored as if it were a drawable id.
            // The flag only keeps the traditional tree (which reads mIconId,
            // where the pre-change Material tree never did) from drawing the
            // unrelated resource that id happens to name.
            this.mIconId = attrId;
            mMacapkIconIdIsAttr = true;
            return;
        }
        android.util.TypedValue out = new android.util.TypedValue();
        try {
            if (mContext != null && mContext.getTheme() != null) {
                mContext.getTheme().resolveAttribute(attrId, out, true);
            }
        } catch (Throwable t) {
            out.resourceId = 0;
        }
        setIcon(out.resourceId);
    }
    /** Set by the pre-change setIconAttribute (MACAPK_ALERT_DIALOG_ICON_ATTR=0):
     *  mIconId then holds an attribute id, not a drawable. */
    private boolean mMacapkIconIdIsAttr;
    public void setView(View view) { this.mView = view; mViewSpacingSpecified = false; }
    public void setView(View view, int viewSpacingLeft, int viewSpacingTop, int viewSpacingRight, int viewSpacingBottom) {
        this.mView = view;
        mViewSpacingSpecified = true;
        mViewSpacingLeft = viewSpacingLeft;   mViewSpacingTop = viewSpacingTop;
        mViewSpacingRight = viewSpacingRight; mViewSpacingBottom = viewSpacingBottom;
    }
    public void setCustomTitle(View view) { this.mCustomTitleView = view; }

    /**
     * AOSP: "this method can only be used after the dialog has been created",
     * i.e. the Button objects come into existence when the content is
     * installed (show() -> onCreate()), and null before that.
     */
    public android.widget.Button getButton(int whichButton) {
        switch (whichButton) {
            case BUTTON_POSITIVE: return mButtonPositive;
            case BUTTON_NEGATIVE: return mButtonNegative;
            case BUTTON_NEUTRAL:  return mButtonNeutral;
            default: return null;
        }
    }
    public android.widget.ListView getListView() { return mListView; }
    @Override public void setTitle(CharSequence title) {
        super.setTitle(title);
        this.mTitle = title;
        if (mTitleView != null) {
            mTitleView.setText(title);
            mTitleView.setVisibility(title == null ? View.GONE : View.VISIBLE);
        }
    }
    /** AOSP stores this in AlertController.mForceInverseBackground, which only
     *  the traditional layout's panel backgrounds read (setBackground: a custom
     *  panel is bright instead of dark). The Material tree ignores it, as
     *  AOSP's Material layout does. */
    public void setInverseBackgroundForced(boolean forceInverseBackground) {
        mForceInverseBackground = forceInverseBackground;
    }
    private boolean mForceInverseBackground;

    public static final int BUTTON_POSITIVE = -1;
    public static final int BUTTON_NEGATIVE = -2;
    public static final int BUTTON_NEUTRAL = -3;
    @Deprecated public static final int BUTTON1 = BUTTON_POSITIVE;
    @Deprecated public static final int BUTTON2 = BUTTON_NEGATIVE;
    @Deprecated public static final int BUTTON3 = BUTTON_NEUTRAL;

    public void setButton(int whichButton, CharSequence text, OnClickListener listener) {
        switch (whichButton) {
            case BUTTON_POSITIVE: mButtonPositiveText = text; mButtonPositiveListener = listener; break;
            case BUTTON_NEGATIVE: mButtonNegativeText = text; mButtonNegativeListener = listener; break;
            case BUTTON_NEUTRAL:  mButtonNeutralText  = text; mButtonNeutralListener  = listener; break;
            default: return;
        }
        android.widget.Button b = getButton(whichButton);
        if (b != null) {
            b.setText(text);
            b.setVisibility(macapkNotEmpty(text) ? View.VISIBLE : View.GONE);
        }
    }
    // AOSP's Message-based overload posts the caller's Message to its target
    // on click instead of invoking a listener. Same slot, same text.
    public void setButton(int whichButton, CharSequence text, final android.os.Message msg) {
        setButton(whichButton, text, msg == null ? null : new OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                android.os.Message.obtain(msg).sendToTarget();
            }
        });
    }
    // Deprecated pre-API-17 overloads (no whichButton -> implicitly the
    // positive/primary button). Old apps (HCR 1.8.1) still call these.
    public void setButton(CharSequence text, OnClickListener listener) {
        setButton(BUTTON_POSITIVE, text, listener);
    }
    public void setButton(CharSequence text, android.os.Message msg) {
        setButton(BUTTON_POSITIVE, text, msg);
    }
    public void setButton2(CharSequence text, OnClickListener listener) {
        setButton(BUTTON_NEGATIVE, text, listener);
    }
    public void setButton3(CharSequence text, OnClickListener listener) {
        setButton(BUTTON_NEUTRAL, text, listener);
    }
    public void setButton(int whichButton, CharSequence text, android.graphics.drawable.Drawable icon, OnClickListener listener) {
        setButton(whichButton, text, listener);
    }

    // ------------------------------------------------------------------
    // Content installation (AOSP AlertController.installContent()).
    // ------------------------------------------------------------------
    @Override protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installContent();
    }

    /** AOSP builds this tree from layout/alert_dialog_material.xml, which
     *  <include>s alert_dialog_title_material.xml and
     *  alert_dialog_button_bar_material.xml. We ship no framework resource
     *  table, so it is constructed in code and every dimension below is quoted
     *  from the file it came from (aosp-mirror/platform_frameworks_base@main):
     *
     *    dialog_padding_material      24dp  dimens_material.xml:127  (= ?dialogPreferredPadding,
     *                                                                  bound themes_material.xml:190/:563)
     *    dialog_padding_top_material  18dp  dimens_material.xml:128
     *    dialog_title_divider_material 8dp  dimens_material.xml:129
     *    text_size_title_material     20sp  dimens_material.xml:76   (title)
     *    text_size_subhead_material   16sp  dimens_material.xml:77   (message)
     *    text_size_button_material    14sp  dimens_material.xml:85
     *    alert_dialog_button_bar_width  64dp  dimens.xml:479
     *    alert_dialog_button_bar_height 48dip dimens.xml:481
     *    icon 32x32dp, marginEnd 8dp        alert_dialog_title_material.xml
     *    button bar paddingStart/End 12dp, paddingTop/Bottom 4dp
     *
     *  WHY THIS REPLACED A FLAT LINEAR LAYOUT. The old tree stacked title,
     *  body and buttons with NO paddings and NO type scale, so every dialog
     *  rendered its title at the TextView default (14sp -- identical to the
     *  body) with ink starting 2px from the panel edge. Measured on newpipe:
     *  the emulator's panel is 703x575 and ours was 723x263. The height is
     *  almost entirely these constants, not a measure-chain bug -- 18dp title
     *  pad (36) + 20sp title (~49) + 8dp divider (16) + 9 message lines x 38px
     *  (342) + button bar (4dp + 48dip + 4dp = 112) reconstructs 577.
     *
     *  ⚠ NO ScrollView, DELIBERATELY. AOSP wraps the message in one, but its
     *  height only behaves because com.android.internal.widget.AlertDialogLayout
     *  .onMeasure measures the title and button panels FIRST and hands the
     *  content panel the remainder. We do not have that yet, so a ScrollView
     *  dropped into a plain vertical LinearLayout would take all remaining
     *  height and push the buttons off the bottom -- strictly worse than this.
     *  Port AlertDialogLayout.onMeasure, then re-add it.
     *
     *  ⚠ FONT WEIGHT IS UNREACHABLE. AOSP's title and button text are
     *  "sans-serif-medium" (weight 500). TextView.setTypeface(Typeface) is a
     *  no-op in this tree, so the closest reachable approximation of "heavier
     *  than body" is the fake-bold the draw Paint already applies for
     *  Typeface.BOLD. It is heavier than medium; the alternative is regular,
     *  which would leave the title distinguishable from the message by size
     *  alone. */

    /** Resolve a theme attribute through THIS dialog's own Context, falling back
     *  to the process-wide anchor.
     *
     *  AOSP has no application-theme tier: ContextThemeWrapper owns one
     *  Resources.Theme and AssetManager2's Theme::GetAttribute chases attribute
     *  indirection inside THAT theme's merged table only. We had a single
     *  process-wide ResourceIndex.applicationThemeName, written once at boot
     *  from the MANIFEST theme and never updated by Activity.setTheme(), so a
     *  dialog read colours through whatever the manifest named.
     *
     *  That is wrong for every app whose manifest theme is a SPLASH theme.
     *  Measured on newpipe, whose manifest names OpeningTheme:
     *
     *      OpeningTheme      colorAccent -> @android:color/transparent  #00000000
     *      DarkTheme.YouTube colorAccent -> @color/dark_youtube_accent  #ffffffff
     *
     *  #00000000 is non-null, so the fallback below never fired and makeButton
     *  painted all three buttons fully transparent while the bar kept its 48dp
     *  minimum height -- an empty button bar. The app's real theme was one
     *  cascade entry away: applyStyle(force=true) puts DarkTheme.YouTube at the
     *  HEAD of appliedStyles, ahead of OpeningTheme.
     *
     *  MONOTONE BY CONSTRUCTION: Theme.resolveAttrColor walks the cascade and,
     *  on a miss, terminally calls the very same resolveApplicationThemeColor
     *  the static path calls. An absent or empty cascade therefore returns
     *  byte-identically to the old code. This can only ADD a resolution.
     *  (Deliberately NOT guarded with `if (r != fallback)`: resolveAttrColor
     *  already falls back internally, and that guard would misread "resolved to
     *  exactly the fallback value" as "unresolved".) */
    private int macapkThemeAttrColor(String attr, int fallback) {
        try {
            android.content.res.Resources.Theme th =
                    (mContext != null) ? mContext.getTheme() : null;
            if (th != null) {
                int v = th.resolveAttrColor(attr, fallback);
                if (System.getenv("MACAPK_TRACE_WINDOW_FLAGS") != null) {
                    int stat = android.content.res.Resources.macapkThemeColor(attr, fallback);
                    com.macapk.loader.NativeLog.i("Mac.apk.AlertDialog",
                        "attr=" + attr + " viaContext=#" + Integer.toHexString(v)
                        + " viaGlobal=#" + Integer.toHexString(stat)
                        + " ctx=" + mContext.getClass().getName()
                        + (v != stat ? "  <== STAGE-A CHANGED IT" : ""));
                }
                return v;
            }
        } catch (Throwable ignored) {}
        return android.content.res.Resources.macapkThemeColor(attr, fallback);
    }

    private void installContent() {
        if (mContentInstalled) return;
        mContentInstalled = true;
        if (macapkUsesTraditionalLayout()) {
            macapkInstallTraditionalContent();
            return;
        }
        mMacapkTree = "material";

        final int PAD      = macapkDp(24f);   // ?dialogPreferredPadding
        final int PAD_TOP  = macapkDp(18f);   // dialog_padding_top_material
        final int DIVIDER  = macapkDp(8f);    // dialog_title_divider_material
        // contentPanel / customPanel both carry android:minHeight="48dp"
        // (alert_dialog_material.xml:32 / :72).
        final int PANEL_MINH = macapkDp(48f);
        final int BTN_MINW = macapkDp(64f);
        final int BTN_MINH = macapkDp(48f);
        final int BAR_PADH = macapkDp(12f);
        final int BAR_PADV = macapkDp(4f);
        final int ICON     = macapkDp(32f);

        // Colours come from the APP's own theme cascade, so nothing here knows
        // which app it is. Fallbacks are paired with the panel's own white
        // fallback in Dialog.macapkDialogBackground so text stays legible even
        // when the cascade fails outright.
        final int colText   = macapkThemeAttrColor("textColorPrimary", 0xDE000000);
        final int colAccent = macapkThemeAttrColor("colorAccent", 0xFF008577);

        final boolean hasTitle   = mCustomTitleView != null
                                || (mTitle != null && mTitle.length() > 0);
        final boolean hasButtons = macapkNotEmpty(mButtonPositiveText)
                                || macapkNotEmpty(mButtonNegativeText)
                                || macapkNotEmpty(mButtonNeutralText);
        // AlertController.setupContent: "if (mMessage != null) { ...message... }
        // else { ... if (mListView != null) { the list REPLACES the ScrollView }
        // else { contentPanel.setVisibility(GONE) } }" -- a message, even an
        // empty one, WINS over a list, so the test is on null exactly as AOSP's.
        final boolean showMessage = mMessage != null;
        final boolean showList    = !showMessage && mAdapter != null;
        // @id/customPanel is a SIBLING of @id/contentPanel in
        // alert_dialog_material.xml and setupCustomContent runs independently of
        // setupContent, so a custom view shows IN ADDITION to the message/list.
        // We used to pick exactly one of view/list/message, which dropped the
        // message of every setMessage()+setView() dialog -- measured on Among
        // Us, whose Unity "gles-api-check" warning (libunity 0x6fbaec) sets title,
        // message, a CheckBox view and two buttons: the message never rendered.
        final boolean hasCustom   = mView != null;

        android.widget.LinearLayout root = new android.widget.LinearLayout(mContext);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.START | android.view.Gravity.TOP);
        // AOSP ids (MACAPK_ALERT_DIALOG_IDS), after alert_dialog_material.xml and
        // its includes. This tree has no separate @id/topPanel wrapper or
        // @id/scrollView (see the ScrollView note above), so those two ids have
        // no view to go on; every other view AOSP names is named here.
        macapkSetAospId(root, 0, "parentPanel");
        // The floating window's width (95% / 65% of the screen) is no longer set
        // here. It used to be `getWindow().setLayout(round(fraction *
        // DisplayMetrics.widthPixels), WRAP_CONTENT)`, which (a) baked a pixel
        // width that goes stale on a window resize -- the Among Us dialog got
        // 2184px on an 808px surface and its buttons were laid out off screen --
        // and (b) clobbered any getWindow().setLayout() the app made before
        // show(). It is now applied at MEASURE time by the dialog decor's own
        // onMeasure (Dialog.DialogWindow.ensureDecor), from the box the window is
        // actually laid out in -- AOSP DecorView.onMeasure's timing. On the
        // fleet's 808px portrait surface that is 767, set as a DecorView-style
        // MINIMUM after the config_prefDialogWidth pre-pass (v1.0.1648,
        // Dialog.DialogWindow.ensureDecor); the old fixed-WIDTH rule survives
        // only as Dialog.macapkFloatingWidthPx under MACAPK_DIALOG_WIDTH_AOSP=0.

        // ---------------- topPanel ----------------
        if (mCustomTitleView != null) {
            // AlertController.setupTitle: a custom title replaces the whole
            // template rather than being added beside it.
            root.addView(mCustomTitleView, matchWrap());
        } else if (hasTitle) {
            android.widget.LinearLayout titleTemplate =
                    new android.widget.LinearLayout(mContext);
            titleTemplate.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            titleTemplate.setGravity(
                    android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            titleTemplate.setPadding(PAD, PAD_TOP, PAD, 0);
            macapkSetAospId(titleTemplate, 0, "title_template");
            if (mIcon != null) {
                android.widget.ImageView iconView = new android.widget.ImageView(mContext);
                iconView.setImageDrawable(mIcon);
                iconView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                android.widget.LinearLayout.LayoutParams ilp =
                        new android.widget.LinearLayout.LayoutParams(ICON, ICON);
                ilp.rightMargin = macapkDp(8f);   // layout_marginEnd; we lay out LTR
                macapkSetAospId(iconView, ID_ICON, null);
                titleTemplate.addView(iconView, ilp);
            }
            mTitleView = macapkNewTitleView();
            macapkSetAospId(mTitleView, 0, "alertTitle");
            mTitleView.setText(mTitle);
            mTitleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f);
            mTitleView.setTextColor(colText);
            mTitleView.setSingleLine(true);
            mTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (MACAPK_ALERT_TITLE_MEDIUM) {
                // BISECT GATE MACAPK_ALERT_TITLE_MEDIUM (2026-09-26, default ON;
                // docs/fleet-newpipe-vlc-residual_2026-09-26.md). The title's
                // appearance is TextAppearance.Material.DialogWindowTitle ->
                // TextAppearance.Material.Title: fontFamily
                // @string/font_family_title_material = "sans-serif-medium"
                // (weight 500), textStyle normal. The fake-bold below predates
                // family/weight support in this tree (the "FONT WEIGHT IS
                // UNREACHABLE" note is stale: Roboto-Medium ships in
                // runtime/fonts and TextView resolves family names). NewPipe's
                // "Keep Android Open" title rendered 343 px wide and heavy against
                // the emulator's 337 px medium. =0 restores the fake bold.
                mTitleView.setMacapkFontFamily("sans-serif-medium");
            } else {
                mTitleView.setMacapkTextStyle(android.graphics.Typeface.BOLD);
            }
            titleTemplate.addView(mTitleView, matchWrap());
            root.addView(titleTemplate, matchWrap());
        }
        // titleDividerNoCustom (8dp). AlertController.setupView shows it only
        // when there is a top panel, NO custom panel, and a message or a list:
        // "if (mMessage != null || mListView != null || hasCustomPanel) { if
        // (!hasCustomPanel) divider = titleDividerNoCustom; if (divider == null)
        // divider = titleDivider; }" -- and the Material title layout has no
        // @id/titleDivider, so with a custom panel there is no divider at all.
        if (hasTitle && !hasCustom && (showMessage || mAdapter != null)) {
            android.widget.Space divider = macapkSpace();
            macapkSetAospId(divider, 0, "titleDividerNoCustom");
            root.addView(divider, matchFixed(DIVIDER));
        }

        // ---------------- contentPanel: the message, or the list replacing it --
        // AlertParams.createListView runs whenever items/an adapter were set, so
        // getListView() answers even when the message wins and the list is never
        // attached (AOSP keeps dialog.mListView in that case too).
        if (mAdapter != null) mListView = macapkCreateListView(hasTitle, hasButtons, colText);
        if (showList) {
            android.widget.FrameLayout contentPanel = new android.widget.FrameLayout(mContext);
            contentPanel.setMinimumHeight(PANEL_MINH);
            macapkSetAospId(contentPanel, 0, "contentPanel");
            contentPanel.addView(mListView, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            mContentPanel = contentPanel;
            // AlertDialogLayout.tryOnMeasure measures the top and button panels
            // FIRST and gives this panel only what remains. A 0-height, weight-1
            // child of a vertical LinearLayout gets exactly that remainder when
            // the list overflows and its own wrapped height when it fits. Without
            // it a list took the whole AT_MOST height and the button bar was
            // measured at 0px -- buttons clipped, dialog undismissable.
            root.addView(contentPanel, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            // Always constructed so a post-show setMessage() still lands (that
            // setter guards on mMessageView != null). AlertController takes the
            // contentPanel GONE when there is no message; same effect.
            android.widget.LinearLayout contentPanel = new android.widget.LinearLayout(mContext);
            contentPanel.setOrientation(android.widget.LinearLayout.VERTICAL);
            contentPanel.setMinimumHeight(PANEL_MINH);
            macapkSetAospId(contentPanel, 0, "contentPanel");
            mMessageView = new android.widget.TextView(mContext);
            mMessageView.setText(mMessage);
            mMessageView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
            mMessageView.setTextColor(colText);
            mMessageView.setPadding(PAD, 0, PAD, 0);
            mMessageView.setVisibility(showMessage ? View.VISIBLE : View.GONE);
            macapkSetAospId(mMessageView, ID_MESSAGE, null);
            // textSpacerNoTitle / textSpacerNoButtons live INSIDE the scroll
            // content in AOSP, so they count toward the 48dp panel minimum.
            if (!hasTitle && showMessage) {
                android.widget.Space s = macapkSpace();
                macapkSetAospId(s, 0, "textSpacerNoTitle");
                contentPanel.addView(s, matchFixed(PAD_TOP));
            }
            contentPanel.addView(mMessageView, matchWrap());
            if (!hasButtons && showMessage) {
                android.widget.Space s = macapkSpace();
                macapkSetAospId(s, 0, "textSpacerNoButtons");
                contentPanel.addView(s, matchFixed(PAD_TOP));
            }
            contentPanel.setVisibility(showMessage ? View.VISIBLE : View.GONE);
            mContentPanel = contentPanel;
            root.addView(contentPanel, matchWrap());
        }

        // ---------------- customPanel > @id/custom > the app's view -------------
        // AlertController.setupCustomContent: `custom.addView(customView, new
        // LayoutParams(MATCH_PARENT, MATCH_PARENT))`, the view spacing (if any)
        // padding @id/custom, the panel GONE when there is no view.
        if (hasCustom) {
            android.widget.FrameLayout customPanel = new android.widget.FrameLayout(mContext);
            customPanel.setMinimumHeight(PANEL_MINH);
            macapkSetAospId(customPanel, 0, "customPanel");
            android.widget.FrameLayout custom = new android.widget.FrameLayout(mContext);
            macapkSetAospId(custom, ID_CUSTOM, null);
            if (mViewSpacingSpecified) {
                custom.setPadding(mViewSpacingLeft, mViewSpacingTop,
                                  mViewSpacingRight, mViewSpacingBottom);
            }
            custom.addView(mView, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            customPanel.addView(custom, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(customPanel, matchWrap());
        }

        // ---------------- buttonPanel ----------------
        android.widget.LinearLayout bar = new android.widget.LinearLayout(mContext);
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.BOTTOM);
        bar.setPadding(BAR_PADH, BAR_PADV, BAR_PADH, BAR_PADV);
        macapkSetAospId(bar, 0, "buttonPanel");
        mButtonNeutral  = makeButton(BUTTON_NEUTRAL,  mButtonNeutralText,  colAccent, BTN_MINW, BTN_MINH);
        mButtonNegative = makeButton(BUTTON_NEGATIVE, mButtonNegativeText, colAccent, BTN_MINW, BTN_MINH);
        mButtonPositive = makeButton(BUTTON_POSITIVE, mButtonPositiveText, colAccent, BTN_MINW, BTN_MINH);
        macapkSetAospId(mButtonPositive, ID_BUTTON1, null);
        macapkSetAospId(mButtonNegative, ID_BUTTON2, null);
        macapkSetAospId(mButtonNeutral,  ID_BUTTON3, null);
        // ORDER IS AOSP'S: button3 (neutral) at the far left, then a weighted
        // INVISIBLE spacer, then negative and positive hard right. That spacer
        // is the whole reason neutral separates from the other two -- without
        // it the three labels butt together, which is exactly what newpipe
        // rendered ("SolutionDetailsOK"). INVISIBLE not GONE is load-bearing:
        // LinearLayout skips only GONE children.
        bar.addView(mButtonNeutral, wrapWrap());
        android.widget.Space spacer = new android.widget.Space(mContext);
        spacer.setVisibility(View.INVISIBLE);
        macapkSetAospId(spacer, 0, "spacer");
        bar.addView(spacer, new android.widget.LinearLayout.LayoutParams(0, 0, 1f));
        bar.addView(mButtonNegative, wrapWrap());
        bar.addView(mButtonPositive, wrapWrap());
        // AlertController.setupButtons takes the whole panel GONE when no
        // button carries text.
        bar.setVisibility(hasButtons ? View.VISIBLE : View.GONE);
        root.addView(bar, matchWrap());

        setContentView(root);
    }

    private static boolean macapkNotEmpty(CharSequence s) { return s != null && s.length() > 0; }

    /** Give `v` the AOSP id (MACAPK_ALERT_DIALOG_IDS): `publicId` when the id is
     *  public (frozen), else the framework table's answer for `internalName`.
     *  No id at all when the table has none -- an invented one could collide. */
    private void macapkSetAospId(View v, int publicId, String internalName) {
        if (!MACAPK_ALERT_DIALOG_IDS || v == null) return;
        int id = publicId;
        if (id == 0 && internalName != null) id = macapkFrameworkId(internalName, "id");
        if (id != 0) v.setId(id);
    }

    /** getIdentifier(name, type, "android"): the platform table, exactly what an
     *  app's getIdentifier("android:id/alertTitle", null, null) reads. */
    private int macapkFrameworkId(String name, String type) {
        try {
            android.content.res.Resources r = mContext == null ? null : mContext.getResources();
            return r == null ? 0 : r.getIdentifier(name, type, "android");
        } catch (Throwable t) {
            return 0;
        }
    }

    /** The title view: a DialogTitle (MACAPK_ALERT_DIALOG_TITLE), as both AOSP
     *  alert layouts declare it, else the pre-change plain TextView. */
    private android.widget.TextView macapkNewTitleView() {
        return MACAPK_ALERT_DIALOG_TITLE
                ? new com.android.internal.widget.DialogTitle(mContext)
                : new android.widget.TextView(mContext);
    }

    /** Whether AlertController would inflate R.layout.alert_dialog for this
     *  dialog: its theme's alertDialogStyle binds no layout (AOSP's default
     *  argument to getResourceId), or binds that very layout. False -- the
     *  Material tree, as before -- when the gate or the theme wrapper is off,
     *  when NinePatch cannot draw the panels yet (MACAPK_TRADITIONAL_LATTICE_
     *  READY, see the gate's header), and when the theme cannot answer
     *  alertDialogStyle at all (no resource
     *  table, a bare Context): that is an unmodelled theme, not a traditional
     *  one. Every platform theme defines alertDialogStyle (the root Theme binds
     *  it at themes.xml:216), so a real theme always answers. */
    private boolean macapkUsesTraditionalLayout() {
        if (!MACAPK_ALERT_DIALOG_TRADITIONAL || !MACAPK_TRADITIONAL_LATTICE_READY
                || !MACAPK_DIALOG_THEME_WRAPPER || mContext == null) {
            return false;
        }
        if (macapkResolveThemeAttr(mContext, ATTR_ALERT_DIALOG_STYLE) == 0) return false;
        android.content.res.TypedArray a = null;
        try {
            a = mContext.obtainStyledAttributes(null, new int[] { ATTR_LAYOUT },
                    ATTR_ALERT_DIALOG_STYLE, 0);
            final int layout = a.getResourceId(0, 0);
            if (layout == 0) return true;
            final int traditional = macapkFrameworkId("alert_dialog", "layout");
            return traditional != 0 && layout == traditional;
        } catch (Throwable t) {
            return false;
        } finally {
            if (a != null) try { a.recycle(); } catch (Throwable ignored) { }
        }
    }

    /** What the traditional tree last built, for the offline gate
     *  (debug/w5alertdialog_gate.sh reads these reflectively) and for a post-show
     *  lookup: "traditional" or "material". */
    String mMacapkTree;

    /** AlertController.installContent() with R.layout.alert_dialog -- the
     *  traditional alert, built in code for the same reason the Material tree
     *  is (see installContent's header): our inflater does not yet give a
     *  framework layout's `@+id/` views their framework ids (measured
     *  2026-09-26: inflating android:layout/alert_dialog named its parentPanel
     *  with the APP's 0x7f id and its spacers with hashed ids), and
     *  com.android.internal.widget classes only exist here as of DialogTitle.
     *
     *  Every attribute below is android14-release core/res/res/layout/
     *  alert_dialog.xml, element by element, and the panel logic is
     *  AlertController's setupView / setupCustomContent / setupContent /
     *  setupButtons / centerButton / setupTitle / setBackground (:530-980),
     *  in that order. The styles are read from the dialog theme
     *  (Theme.Dialog.Alert for a root-Theme app): textAppearanceLarge /
     *  textAppearanceMedium for the title and message (22sp / 18sp,
     *  textColorPrimary = primary_text_dark), buttonBarStyle for the bar,
     *  buttonBar{Positive,Neutral,Negative}ButtonStyle for the buttons (-> Widget
     *  .Button: btn_default, 14sp primary_text_light, no caps), and the
     *  alertDialogStyle's fullDark / topDark / centerDark / bottomMedium ... for
     *  the panel 9-patches. The window background is the theme's
     *  windowBackground, @color/transparent for Theme.Dialog.Alert ("sets the
     *  background to empty so it can do two-tone backgrounds", themes.xml:784)
     *  -- not the Material panel Dialog paints by default.
     *
     *  NOT PORTED (listed): the ViewStub / custom-panel override resolution
     *  (resolvePanel -- a custom view carrying its own topPanel / contentPanel /
     *  buttonPanel), FLAG_ALT_FOCUSABLE_IM for a custom view without a text
     *  field, setCloseOnTouchOutsideIfNotSet when there are no buttons, the
     *  focus requests, scroll indicators, and the traditional list-row layouts
     *  (select_dialog_item.xml: rows reuse the Material ones, in the inverse
     *  text colour the bright list panel needs). alertDialogCenterButtons is a
     *  PRIVATE attribute our theme cannot resolve; the root Theme binds it
     *  true (themes.xml:217), and every theme whose alertDialogStyle binds no
     *  layout inherits that root, so it is taken as true here. */
    private void macapkInstallTraditionalContent() {
        mMacapkTree = "traditional";
        final Context c = mContext;
        final int LP_MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        final int LP_WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

        // <LinearLayout @+id/parentPanel vertical paddingTop=9dip paddingBottom=3dip
        //  paddingStart=3dip paddingEnd=1dip>. We lay out LTR, so start=left.
        android.widget.LinearLayout parentPanel = new android.widget.LinearLayout(c);
        parentPanel.setOrientation(android.widget.LinearLayout.VERTICAL);
        parentPanel.setPadding(macapkDp(3f), macapkDp(9f), macapkDp(1f), macapkDp(3f));
        macapkSetAospId(parentPanel, 0, "parentPanel");

        // <LinearLayout @+id/topPanel match/wrap minHeight=54dip vertical>
        android.widget.LinearLayout topPanel = new android.widget.LinearLayout(c);
        topPanel.setOrientation(android.widget.LinearLayout.VERTICAL);
        topPanel.setMinimumHeight(macapkDp(54f));
        macapkSetAospId(topPanel, 0, "topPanel");
        // <LinearLayout @+id/title_template match/wrap horizontal
        //  gravity=center_vertical margins top 6dip bottom 9dip start/end 10dip>
        android.widget.LinearLayout titleTemplate = new android.widget.LinearLayout(c);
        titleTemplate.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        titleTemplate.setGravity(android.view.Gravity.CENTER_VERTICAL);
        macapkSetAospId(titleTemplate, 0, "title_template");
        android.widget.LinearLayout.LayoutParams ttlp =
                new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_WRAP);
        ttlp.topMargin = macapkDp(6f);
        ttlp.bottomMargin = macapkDp(9f);
        ttlp.leftMargin = macapkDp(10f);
        ttlp.rightMargin = macapkDp(10f);
        // <ImageView @+id/icon wrap/wrap layout_gravity=top paddingTop=6dip
        //  paddingEnd=10dip src=@drawable/ic_dialog_info>
        android.widget.ImageView icon = new android.widget.ImageView(c);
        icon.setPadding(0, macapkDp(6f), macapkDp(10f), 0);
        icon.setImageDrawable(macapkFrameworkDrawable("ic_dialog_info"));
        macapkSetAospId(icon, ID_ICON, null);
        android.widget.LinearLayout.LayoutParams ilp =
                new android.widget.LinearLayout.LayoutParams(LP_WRAP, LP_WRAP);
        ilp.gravity = android.view.Gravity.TOP;
        titleTemplate.addView(icon, ilp);
        // <DialogTitle @+id/alertTitle style=?android:attr/textAppearanceLarge
        //  singleLine ellipsize=end match/wrap textAlignment=viewStart>
        mTitleView = macapkNewTitleView();
        macapkApplyTextAppearance(mTitleView, ATTR_TEXT_APPEARANCE_LARGE);
        mTitleView.setSingleLine(true);
        mTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mTitleView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        macapkSetAospId(mTitleView, 0, "alertTitle");
        titleTemplate.addView(mTitleView, new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_WRAP));
        topPanel.addView(titleTemplate, ttlp);
        // <ImageView @+id/titleDivider match x 1dip gone fitXY
        //  src=@android:drawable/divider_horizontal_dark>
        android.widget.ImageView titleDivider = new android.widget.ImageView(c);
        titleDivider.setVisibility(View.GONE);
        titleDivider.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        titleDivider.setImageDrawable(macapkFrameworkDrawable("divider_horizontal_dark"));
        macapkSetAospId(titleDivider, 0, "titleDivider");
        topPanel.addView(titleDivider, new android.widget.LinearLayout.LayoutParams(LP_MATCH, macapkDp(1f)));
        parentPanel.addView(topPanel, new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_WRAP));

        // <LinearLayout @+id/contentPanel match/wrap weight=1 vertical>
        android.widget.LinearLayout contentPanel = new android.widget.LinearLayout(c);
        contentPanel.setOrientation(android.widget.LinearLayout.VERTICAL);
        macapkSetAospId(contentPanel, 0, "contentPanel");
        // <ScrollView @+id/scrollView match/wrap paddingTop=2dip paddingBottom=12dip
        //  paddingStart=14dip paddingEnd=10dip overScrollMode=ifContentScrolls>
        android.widget.ScrollView scrollView = new android.widget.ScrollView(c);
        scrollView.setPadding(macapkDp(14f), macapkDp(2f), macapkDp(10f), macapkDp(12f));
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        macapkSetAospId(scrollView, 0, "scrollView");
        // <TextView @+id/message style=?android:attr/textAppearanceMedium
        //  match/wrap padding=5dip>
        mMessageView = new android.widget.TextView(c);
        macapkApplyTextAppearance(mMessageView, ATTR_TEXT_APPEARANCE_MEDIUM);
        mMessageView.setPadding(macapkDp(5f), macapkDp(5f), macapkDp(5f), macapkDp(5f));
        macapkSetAospId(mMessageView, ID_MESSAGE, null);
        scrollView.addView(mMessageView, new android.widget.FrameLayout.LayoutParams(LP_MATCH, LP_WRAP));
        contentPanel.addView(scrollView, new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_WRAP));
        parentPanel.addView(contentPanel, new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_WRAP, 1f));

        // <FrameLayout @+id/customPanel match/wrap weight=1>
        //   <FrameLayout @+android:id/custom match/wrap paddingTop=5dip paddingBottom=5dip>
        android.widget.FrameLayout customPanel = new android.widget.FrameLayout(c);
        macapkSetAospId(customPanel, 0, "customPanel");
        android.widget.FrameLayout custom = new android.widget.FrameLayout(c);
        custom.setPadding(0, macapkDp(5f), 0, macapkDp(5f));
        macapkSetAospId(custom, ID_CUSTOM, null);
        customPanel.addView(custom, new android.widget.FrameLayout.LayoutParams(LP_MATCH, LP_WRAP));
        android.widget.LinearLayout.LayoutParams customLp =
                new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_WRAP, 1f);
        parentPanel.addView(customPanel, customLp);

        // <LinearLayout @+id/buttonPanel match/wrap minHeight=54dip vertical>
        android.widget.LinearLayout buttonPanel = new android.widget.LinearLayout(c);
        buttonPanel.setOrientation(android.widget.LinearLayout.VERTICAL);
        buttonPanel.setMinimumHeight(macapkDp(54f));
        macapkSetAospId(buttonPanel, 0, "buttonPanel");
        //   <LinearLayout style=?android:attr/buttonBarStyle match/wrap horizontal
        //    paddingTop=4dip paddingStart=2dip paddingEnd=2dip measureWithLargestChild>
        android.widget.LinearLayout bar = new android.widget.LinearLayout(c);
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        macapkApplyButtonBarStyle(bar);
        bar.setMeasureWithLargestChildEnabled(true);
        //     <LinearLayout @+id/leftSpacer weight=0.25 0dip x wrap horizontal gone>
        android.widget.LinearLayout leftSpacer = new android.widget.LinearLayout(c);
        leftSpacer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        leftSpacer.setVisibility(View.GONE);
        macapkSetAospId(leftSpacer, 0, "leftSpacer");
        bar.addView(leftSpacer, new android.widget.LinearLayout.LayoutParams(0, LP_WRAP, 0.25f));
        //     <Button @+id/button1 0dip x wrap layout_gravity=start weight=1
        //      style=?android:attr/buttonBarPositiveButtonStyle maxLines=2> and
        //     button3 (center_horizontal, Neutral) / button2 (end, Negative).
        mButtonPositive = macapkMakeTraditionalButton(BUTTON_POSITIVE,
                ATTR_BUTTON_BAR_POSITIVE_BUTTON_STYLE, ID_BUTTON1);
        mButtonNeutral  = macapkMakeTraditionalButton(BUTTON_NEUTRAL,
                ATTR_BUTTON_BAR_NEUTRAL_BUTTON_STYLE, ID_BUTTON3);
        mButtonNegative = macapkMakeTraditionalButton(BUTTON_NEGATIVE,
                ATTR_BUTTON_BAR_NEGATIVE_BUTTON_STYLE, ID_BUTTON2);
        bar.addView(mButtonPositive, macapkWeighted(android.view.Gravity.START));
        bar.addView(mButtonNeutral,  macapkWeighted(android.view.Gravity.CENTER_HORIZONTAL));
        bar.addView(mButtonNegative, macapkWeighted(android.view.Gravity.END));
        //     <LinearLayout @+id/rightSpacer 0dip x wrap weight=0.25 horizontal gone>
        android.widget.LinearLayout rightSpacer = new android.widget.LinearLayout(c);
        rightSpacer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        rightSpacer.setVisibility(View.GONE);
        macapkSetAospId(rightSpacer, 0, "rightSpacer");
        bar.addView(rightSpacer, new android.widget.LinearLayout.LayoutParams(0, LP_WRAP, 0.25f));
        buttonPanel.addView(bar, new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_WRAP));
        parentPanel.addView(buttonPanel, new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_WRAP));

        // ---------------- AlertController.setupView ----------------
        // setupCustomContent(customPanel)
        final boolean hasCustomView = mView != null;
        if (hasCustomView) {
            custom.addView(mView, new android.widget.FrameLayout.LayoutParams(LP_MATCH, LP_MATCH));
            if (mViewSpacingSpecified) {
                custom.setPadding(mViewSpacingLeft, mViewSpacingTop,
                                  mViewSpacingRight, mViewSpacingBottom);
            }
        } else {
            customPanel.setVisibility(View.GONE);
        }
        // AlertParams.createListView runs whenever items / an adapter were set.
        // The list panel is BRIGHT (setBackground: light[pos] = mListView !=
        // null), so its rows take the inverse primary text colour.
        if (mAdapter != null) {
            mListView = macapkCreateListView(true, true,
                    macapkThemeAttrColor("textColorPrimaryInverse", 0xFF000000));
            if (hasCustomView) customLp.weight = 0;
        }
        // setupContent(contentPanel)
        scrollView.setFocusable(false);
        if (mMessage != null) {
            mMessageView.setText(mMessage);
        } else {
            mMessageView.setVisibility(View.GONE);
            scrollView.removeView(mMessageView);
            if (mListView != null) {
                final int childIndex = contentPanel.indexOfChild(scrollView);
                contentPanel.removeViewAt(childIndex);
                contentPanel.addView(mListView, childIndex,
                        new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_MATCH));
            } else {
                contentPanel.setVisibility(View.GONE);
            }
        }
        mContentPanel = contentPanel;
        // setupButtons(buttonPanel)
        int whichButtons = 0;
        whichButtons |= macapkSetupTraditionalButton(mButtonPositive, mButtonPositiveText, 1);
        whichButtons |= macapkSetupTraditionalButton(mButtonNegative, mButtonNegativeText, 2);
        whichButtons |= macapkSetupTraditionalButton(mButtonNeutral,  mButtonNeutralText,  4);
        // shouldCenterSingleButton: true for this tree (see the header).
        if (whichButtons == 1) macapkCenterButton(mButtonPositive, leftSpacer, rightSpacer);
        else if (whichButtons == 2) macapkCenterButton(mButtonNegative, leftSpacer, rightSpacer);
        else if (whichButtons == 4) macapkCenterButton(mButtonNeutral, leftSpacer, rightSpacer);
        if (whichButtons == 0) buttonPanel.setVisibility(View.GONE);
        // setupTitle(topPanel)
        if (mCustomTitleView != null) {
            topPanel.addView(mCustomTitleView, 0,
                    new android.widget.LinearLayout.LayoutParams(LP_MATCH, LP_WRAP));
            titleTemplate.setVisibility(View.GONE);
        } else if (mTitle != null && mTitle.length() > 0) {
            mTitleView.setText(mTitle);
            if (mIconId != 0 && !mMacapkIconIdIsAttr) {
                icon.setImageDrawable(resolveDrawable(mIconId));
            } else if (mIcon != null) {
                icon.setImageDrawable(mIcon);
            } else {
                // "Apply the padding from the icon to ensure the title is
                // aligned correctly."
                mTitleView.setPadding(icon.getPaddingLeft(), icon.getPaddingTop(),
                        icon.getPaddingRight(), icon.getPaddingBottom());
                icon.setVisibility(View.GONE);
            }
        } else {
            titleTemplate.setVisibility(View.GONE);
            icon.setVisibility(View.GONE);
            topPanel.setVisibility(View.GONE);
        }
        final boolean hasCustomPanel = customPanel.getVisibility() != View.GONE;
        final boolean hasTopPanel = topPanel.getVisibility() != View.GONE;
        final boolean hasButtonPanel = buttonPanel.getVisibility() != View.GONE;
        // "Only show the divider if we have a title." alert_dialog.xml has no
        // @id/titleDividerNoCustom, so it is @id/titleDivider either way.
        if (hasTopPanel && (mMessage != null || mListView != null || hasCustomPanel)) {
            titleDivider.setVisibility(View.VISIBLE);
        }
        macapkSetTraditionalBackgrounds(topPanel, contentPanel, customPanel, buttonPanel,
                hasTopPanel, hasCustomPanel, hasButtonPanel);

        setContentView(parentPanel);
        // PhoneWindow.generateLayout's mBackgroundDrawable = the theme's
        // windowBackground; only a COLOUR is taken literally (the case this
        // tree's theme uses), anything else keeps Dialog's default panel.
        android.graphics.drawable.Drawable wbg = macapkThemeWindowBackgroundColour();
        if (wbg != null) macapkSetThemeWindowBackground(wbg);
    }

    /** android14 AlertController.setupButtons for one slot: the ButtonHandler
     *  click listener, the text, GONE when the text is empty. Returns its
     *  whichButtons bit when shown. */
    private static int macapkSetupTraditionalButton(android.widget.Button b, CharSequence text, int bit) {
        if (!macapkNotEmpty(text)) {
            b.setVisibility(View.GONE);
            return 0;
        }
        b.setText(text);
        b.setVisibility(View.VISIBLE);
        return bit;
    }

    /** android14 AlertController.centerButton (:839-852), verbatim in behaviour:
     *  gravity CENTER_HORIZONTAL, weight 0.5, both spacers VISIBLE (0.25 each),
     *  so a single button spans half the bar, centred. */
    private static void macapkCenterButton(android.widget.Button button, View leftSpacer, View rightSpacer) {
        android.widget.LinearLayout.LayoutParams params =
                (android.widget.LinearLayout.LayoutParams) button.getLayoutParams();
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        params.weight = 0.5f;
        button.setLayoutParams(params);
        if (leftSpacer != null) leftSpacer.setVisibility(View.VISIBLE);
        if (rightSpacer != null) rightSpacer.setVisibility(View.VISIBLE);
    }

    private static android.widget.LinearLayout.LayoutParams macapkWeighted(int gravity) {
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.gravity = gravity;
        return lp;
    }

    /** A traditional bar button: `style="?android:attr/buttonBar*ButtonStyle"`
     *  read from the dialog theme (for the root Theme ->
     *  ?buttonBarButtonStyle -> ?buttonStyle = Widget.Button: background
     *  @drawable/btn_default, textAppearance ?textAppearanceSmallInverse (14sp),
     *  textColor @color/primary_text_light), then `maxLines="2"`. The style's
     *  explicit textColor outranks its textAppearance, as on AOSP, so the
     *  appearance goes first. Built on Button(Context) so an unresolvable style
     *  still leaves a working button; the click reaches the app's listener and
     *  then dismisses (AlertController's ButtonHandler, as makeButton does). */
    private android.widget.Button macapkMakeTraditionalButton(final int which, int styleAttr, int publicId) {
        android.widget.Button b = new android.widget.Button(mContext);
        android.content.res.TypedArray a = null;
        try {
            // android.R.attr textAppearance / textColor / background, ascending.
            a = mContext.obtainStyledAttributes(null,
                    new int[] { 0x01010034, 0x01010098, 0x010100d4 }, styleAttr, 0);
            final int ta = a.getResourceId(0, 0);
            if (ta != 0) b.setTextAppearance(ta);
            if (a.hasValue(1)) {
                android.content.res.ColorStateList tc = a.getColorStateList(1);
                if (tc != null) b.setTextColor(tc);
            }
            if (a.hasValue(2)) {
                android.graphics.drawable.Drawable bg = a.getDrawable(2);
                if (bg != null) b.setBackground(bg);
            }
        } catch (Throwable ignored) {
        } finally {
            if (a != null) try { a.recycle(); } catch (Throwable ignored) { }
        }
        b.setMaxLines(2);
        macapkSetAospId(b, publicId, null);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                OnClickListener l = (which == BUTTON_POSITIVE) ? mButtonPositiveListener
                                  : (which == BUTTON_NEGATIVE) ? mButtonNegativeListener
                                  : mButtonNeutralListener;
                if (l != null) l.onClick(AlertDialog.this, which);
                dismiss();
            }
        });
        return b;
    }

    /** `style="?android:attr/buttonBarStyle"` plus the layout's own paddings:
     *  paddingTop 4dip, paddingStart / paddingEnd 2dip from alert_dialog.xml
     *  (they outrank the style's), paddingBottom and background from the style
     *  (root Theme: @style/ButtonBar, paddingBottom 1dip, background
     *  @drawable/bottom_bar). */
    private void macapkApplyButtonBarStyle(android.widget.LinearLayout bar) {
        int padBottom = 0;
        android.graphics.drawable.Drawable bg = null;
        android.content.res.TypedArray a = null;
        try {
            // android.R.attr background / paddingBottom, ascending.
            a = mContext.obtainStyledAttributes(null, new int[] { 0x010100d4, 0x010100d9 },
                    ATTR_BUTTON_BAR_STYLE, 0);
            if (a.hasValue(0)) bg = a.getDrawable(0);
            padBottom = a.getDimensionPixelSize(1, 0);
        } catch (Throwable ignored) {
        } finally {
            if (a != null) try { a.recycle(); } catch (Throwable ignored) { }
        }
        // setBackground first: it applies the drawable's padding, which the
        // explicit paddings below then replace, as inflation would.
        if (bg != null) bar.setBackground(bg);
        bar.setPadding(macapkDp(2f), macapkDp(4f), macapkDp(2f), padBottom);
    }

    /** setTextAppearance(the style `?attr` resolves to), i.e. the style's items
     *  applied to the view -- the same effect as `style="?android:attr/..."`
     *  on a TextView whose style is a TextAppearance. */
    private void macapkApplyTextAppearance(android.widget.TextView tv, int attr) {
        final int style = macapkResolveThemeAttr(mContext, attr);
        if (style != 0) {
            try { tv.setTextAppearance(style); } catch (Throwable ignored) { }
        }
    }

    /** A framework drawable by name (the popup_* 9-patches, ic_dialog_info,
     *  divider_horizontal_dark), resolved against the dialog theme. */
    private android.graphics.drawable.Drawable macapkFrameworkDrawable(String name) {
        return macapkDrawableById(macapkFrameworkId(name, "drawable"));
    }

    private android.graphics.drawable.Drawable macapkDrawableById(int id) {
        if (id == 0 || mContext == null) return null;
        try {
            android.content.res.Resources r = mContext.getResources();
            return r == null ? null : r.getDrawable(id, mContext.getTheme());
        } catch (Throwable t) {
            return null;
        }
    }

    /** android14 AlertController.setBackground (:854-975): each shown panel in
     *  order (title, content, custom, buttons) takes the top / center / bottom
     *  (or full) piece of the alertDialogStyle's two-tone frame; the title and a
     *  message are DARK, a list and the button bar BRIGHT (the bar's bottom
     *  piece is bottomMedium). The pieces come from the style (the root
     *  Theme's @style/AlertDialog names them all), defaulting to the framework
     *  popup_* drawables -- AOSP's needsDefaultBackgrounds default, which is a
     *  private attribute our theme cannot read, so its default (true) applies. */
    private void macapkSetTraditionalBackgrounds(View topPanel, View contentPanel, View customPanel,
            View buttonPanel, boolean hasTitle, boolean hasCustomView, boolean hasButtons) {
        // android.R.attr fullDark .. bottomMedium (0x010100c6 .. 0x010100ce).
        final String[] defaults = { "popup_full_dark", "popup_top_dark", "popup_center_dark",
                "popup_bottom_dark", "popup_full_bright", "popup_top_bright",
                "popup_center_bright", "popup_bottom_bright", "popup_bottom_medium" };
        final int[] attrs = new int[defaults.length];
        for (int i = 0; i < attrs.length; i++) attrs[i] = 0x010100c6 + i;
        final int[] ids = new int[defaults.length];
        android.content.res.TypedArray a = null;
        try {
            a = mContext.obtainStyledAttributes(null, attrs, ATTR_ALERT_DIALOG_STYLE, 0);
            for (int i = 0; i < ids.length; i++) {
                ids[i] = a.getResourceId(i, macapkFrameworkId(defaults[i], "drawable"));
            }
        } catch (Throwable t) {
            for (int i = 0; i < ids.length; i++) ids[i] = macapkFrameworkId(defaults[i], "drawable");
        } finally {
            if (a != null) try { a.recycle(); } catch (Throwable ignored) { }
        }
        final int fullDark = ids[0], topDark = ids[1], centerDark = ids[2], bottomDark = ids[3],
                fullBright = ids[4], topBright = ids[5], centerBright = ids[6],
                bottomBright = ids[7], bottomMedium = ids[8];

        final View[] views = new View[4];
        final boolean[] light = new boolean[4];
        int pos = 0;
        if (hasTitle) {
            views[pos] = topPanel;
            light[pos] = false;
            pos++;
        }
        views[pos] = contentPanel.getVisibility() == View.GONE ? null : contentPanel;
        light[pos] = mListView != null;
        pos++;
        if (hasCustomView) {
            views[pos] = customPanel;
            light[pos] = mForceInverseBackground;
            pos++;
        }
        if (hasButtons) {
            views[pos] = buttonPanel;
            light[pos] = true;
        }
        View lastView = null;
        boolean lastLight = false;
        boolean setView = false;
        for (pos = 0; pos < views.length; pos++) {
            final View v = views[pos];
            if (v == null) continue;
            if (lastView != null) {
                if (!setView) {
                    macapkPanelBackground(lastView, lastLight ? topBright : topDark);
                } else {
                    macapkPanelBackground(lastView, lastLight ? centerBright : centerDark);
                }
                setView = true;
            }
            lastView = v;
            lastLight = light[pos];
        }
        if (lastView != null) {
            if (setView) {
                macapkPanelBackground(lastView,
                        lastLight ? (hasButtons ? bottomMedium : bottomBright) : bottomDark);
            } else {
                macapkPanelBackground(lastView, lastLight ? fullBright : fullDark);
            }
        }
    }

    /** View.setBackgroundResource(id): the drawable, and (AOSP
     *  View.setBackgroundDrawable) its padding as the view's padding. */
    private void macapkPanelBackground(View v, int id) {
        android.graphics.drawable.Drawable d = macapkDrawableById(id);
        if (d != null) v.setBackground(d);
    }

    /** The dialog theme's windowBackground when it is a COLOUR (TYPE_FIRST_
     *  COLOR_INT .. TYPE_LAST_COLOR_INT), as a ColorDrawable; null otherwise. */
    private android.graphics.drawable.Drawable macapkThemeWindowBackgroundColour() {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (mContext == null || !mContext.getTheme().resolveAttribute(ATTR_WINDOW_BACKGROUND, tv, true)) {
                return null;
            }
            if (tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                return new android.graphics.drawable.ColorDrawable(tv.data);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private float mMacapkDensity = -1f;
    private float macapkDensity() {
        if (mMacapkDensity > 0f) return mMacapkDensity;
        float d = android.util.DisplayMetrics.macapkDeviceDensity();
        try {
            android.content.res.Resources r = mContext == null ? null : mContext.getResources();
            android.util.DisplayMetrics dm = r == null ? null : r.getDisplayMetrics();
            if (dm != null && dm.density > 0) d = dm.density;
        } catch (Throwable ignored) {}
        return mMacapkDensity = d;
    }
    private int macapkDp(float dp) { return Math.round(dp * macapkDensity()); }

    private android.widget.Space macapkSpace() { return new android.widget.Space(mContext); }

    private static android.view.ViewGroup.LayoutParams matchFixed(int heightPx) {
        return new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
    }

    /** AOSP AlertController.AlertParams.createListView plus the tail of
     *  AlertController.setupView, for all three list flavours:
     *
     *    - rows from select_dialog_item / _singlechoice / _multichoice (see
     *      ItemsAdapter.macapkNewRow);
     *    - `listView.setChoiceMode(CHOICE_MODE_SINGLE | CHOICE_MODE_MULTIPLE)`;
     *    - the initial checks: `listView.setItemChecked(checkedItem, true)` for a
     *      single-choice list, and every `mCheckedItems[i]` for a multi-choice
     *      one (AOSP does the latter lazily in its adapter's getView);
     *    - RecycleListView.setHasDecor: 8dp top padding with no title, 8dp bottom
     *      with no buttons (dialog_list_padding_top_no_title /
     *      _bottom_no_buttons, dimens_material.xml:130-131);
     *    - the click wiring, verbatim in behaviour: a plain/single list calls
     *      OnClickListener.onClick and dismisses unless single-choice; a
     *      multi-choice list reads the NEW state back from the ListView
     *      (`mCheckedItems[position] = listView.isItemChecked(position)`) and
     *      never dismisses. With no listener AOSP installs no item listener at
     *      all, so a click neither dismisses nor writes mCheckedItems.
     *
     *  We used to toggle our own copy of mCheckedItems and never tell the
     *  ListView, so no row could ever draw as checked. */
    private android.widget.ListView macapkCreateListView(boolean hasTitle, boolean hasButtons,
                                                         int textColor) {
        final android.widget.ListView listView = new android.widget.ListView(mContext);
        listView.setPadding(0, hasTitle ? 0 : macapkDp(8f), 0, hasButtons ? 0 : macapkDp(8f));
        // select_dialog.xml / select_dialog_material.xml: @+android:id/select_dialog_listview.
        macapkSetAospId(listView, 0, "select_dialog_listview");
        if (mAdapter instanceof ItemsAdapter) {
            ((ItemsAdapter) mAdapter).macapkStyleRows(
                    mIsMultiChoice ? ItemsAdapter.MULTI
                            : mIsSingleChoice ? ItemsAdapter.SINGLE : ItemsAdapter.PLAIN,
                    textColor, macapkDensity());
        }
        listView.setAdapter(mAdapter);
        if (mIsSingleChoice) {
            listView.setChoiceMode(android.widget.AbsListView.CHOICE_MODE_SINGLE);
        } else if (mIsMultiChoice) {
            listView.setChoiceMode(android.widget.AbsListView.CHOICE_MODE_MULTIPLE);
        }
        if (mIsMultiChoice && mCheckedItems != null) {
            for (int i = 0; i < mCheckedItems.length; i++) {
                if (mCheckedItems[i]) listView.setItemChecked(i, true);
            }
        }
        if (mCheckedItem > -1) listView.setItemChecked(mCheckedItem, true);
        // AOSP AlertController.createListView installs the params'
        // OnItemSelectedListener on the ListView here (see
        // Builder.setOnItemSelectedListener).
        if (mOnItemSelectedListener != null) {
            try { listView.setOnItemSelectedListener(mOnItemSelectedListener); }
            catch (Throwable ignored) {}
        }
        if (mListListener != null) {
            listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                @Override public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                                  int position, long id) {
                    if (mIsSingleChoice) mCheckedItem = position;
                    mListListener.onClick(AlertDialog.this, position);
                    if (!mIsSingleChoice) dismiss();
                }
            });
        } else if (mMultiChoiceListener != null) {
            listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                @Override public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                                  int position, long id) {
                    final boolean checked = listView.isItemChecked(position);
                    if (mCheckedItems != null && position < mCheckedItems.length) {
                        mCheckedItems[position] = checked;
                    }
                    mMultiChoiceListener.onClick(AlertDialog.this, position, checked);
                }
            });
        }
        return listView;
    }

    private android.widget.Button makeButton(final int which, CharSequence text,
                                             int textColor, int minWidth, int minHeight) {
        android.widget.Button b = new android.widget.Button(mContext);
        // AOSP styles an alert-dialog button with ?attr/buttonBarButtonStyle
        // (Widget.Material.Button.ButtonBar.AlertDialog -> .Borderless.Colored),
        // NOT with ?attr/buttonStyle. Button(Context) now applies the theme's
        // buttonStyle the way AOSP's constructor chain does, so drop the raised
        // face again here: a borderless dialog button is bare text on the panel,
        // which is exactly what the rest of this method already assumes.
        b.setBackgroundDrawable(null);
        if (MACAPK_ALERT_BUTTON_MASK_PADDING) {
            // BISECT GATE MACAPK_ALERT_BUTTON_MASK_PADDING (2026-09-26, default ON;
            // docs/fleet-newpipe-vlc-residual_2026-09-26.md). The borderless
            // background paints nothing at rest, but it DOES carry padding: AOSP
            // LayerDrawable.getPadding -> computeNestedPadding (android14
            // LayerDrawable.java :1030-1036, :1234-1251) sums EVERY layer, the
            // @id/mask of btn_borderless_material.xml included, and that mask is
            // btn_default_mtrl_shape = <inset> button_inset_horizontal_material 4dp /
            // button_inset_vertical_material 6dp around a <shape> with padding
            // button_padding_horizontal_material 8dp / control_padding_material 4dp.
            // So each dialog button is padded 12dp x 10dp. Measured on NewPipe's
            // dialog: SOLUTION's ink started 24 px (12dp) left of the emulator's and
            // DETAILS sat 15 px right of it, exactly the missing 12dp per side.
            // =0 restores the unpadded button.
            b.setPadding(macapkDp(12f), macapkDp(10f), macapkDp(12f), macapkDp(10f));
        }
        b.setText(text);
        // Widget.Material.Button.ButtonBar.AlertDialog, resolved leaf by leaf
        // (styles_material.xml:570 -> Borderless.Colored :565 -> textAppearance
        // TA.M.Widget.Button.Borderless.Colored :320 -> TA.Material.Button :251
        // = 14sp + textAllCaps, textColor @color/btn_colored_borderless_text_material
        // whose enabled row is ?attr/colorAccent).
        // `new Button(Context)` reaches a bare field assignment with NO
        // defStyleAttr cascade, so unlike real Android none of this arrives on
        // its own and every row has to be set here.
        //
        // NO BACKGROUND is set, and that is faithful rather than a shortcut:
        // Widget.Material.Button.Borderless uses @drawable/btn_borderless_material,
        // a <ripple> whose only item is the @id/mask -- it paints no chrome at
        // rest and contributes no padding. A borderless dialog button is bare
        // text on the panel; its 64dp minWidth is what separates the labels.
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        b.setAllCaps(true);
        b.setTextColor(textColor);
        b.setMinWidth(minWidth);
        b.setMinHeight(minHeight);
        b.setGravity(android.view.Gravity.CENTER);
        b.setVisibility(text == null ? View.GONE : View.VISIBLE);
        // AlertController's ButtonHandler: deliver the click to the app's
        // listener with the button constant as `which`, then dismiss the
        // dialog regardless of whether a listener was registered.
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                OnClickListener l = (which == BUTTON_POSITIVE) ? mButtonPositiveListener
                                  : (which == BUTTON_NEGATIVE) ? mButtonNegativeListener
                                  : mButtonNeutralListener;
                if (l != null) l.onClick(AlertDialog.this, which);
                dismiss();
            }
        });
        return b;
    }

    private static android.view.ViewGroup.LayoutParams matchWrap() {
        return new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private static android.view.ViewGroup.LayoutParams wrapWrap() {
        return new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private android.graphics.drawable.Drawable resolveDrawable(int resId) {
        if (resId == 0 || mContext == null) return null;
        android.content.res.Resources r = mContext.getResources();
        return r == null ? null : r.getDrawable(resId);
    }

    /** Adapter over a plain CharSequence[] (AOSP uses an ArrayAdapter over
     *  a simple list-item layout; we have no such layout resource, so the
     *  rows are built directly -- see macapkNewRow for the attribute-by-
     *  attribute source). hasStableIds() is AOSP's CheckedItemAdapter's. */
    private static final class ItemsAdapter extends android.widget.BaseAdapter {
        static final int PLAIN = 0, SINGLE = 1, MULTI = 2;
        private final Context mCtx;
        private final CharSequence[] mItems;
        private int mMode = PLAIN;
        private int mTextColor = 0xDE000000;
        private float mDensity = android.util.DisplayMetrics.macapkDeviceDensity();
        ItemsAdapter(Context ctx, CharSequence[] items) {
            mCtx = ctx;
            mItems = items == null ? new CharSequence[0] : items;
        }
        /** The row flavour and the dialog theme's text colour are only known
         *  when the dialog installs its content (AOSP picks the layout there,
         *  from AlertController.mSingleChoiceItemLayout & co). */
        void macapkStyleRows(int mode, int textColor, float density) {
            mMode = mode;
            mTextColor = textColor;
            if (density > 0f) mDensity = density;
        }
        @Override public int getCount() { return mItems.length; }
        @Override public Object getItem(int position) { return mItems[position]; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return true; }
        @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
            final boolean wantChecked = mMode != PLAIN;
            android.widget.TextView tv = (convertView instanceof android.widget.TextView
                    && (convertView instanceof android.widget.CheckedTextView) == wantChecked)
                    ? (android.widget.TextView) convertView
                    : macapkNewRow();
            tv.setText(mItems[position]);
            return tv;
        }
        private int dp(float v) { return Math.round(v * mDensity); }
        /** AOSP core/res/res/layout, attribute by attribute:
         *    select_dialog_item_material.xml (setItems): TextView, minHeight
         *      ?listPreferredItemHeightSmall (48dip, themes_material.xml:499),
         *      textAppearance ?textAppearanceListItemSmall = TextAppearance
         *      .Material.ListItem -> Subhead = 16sp, paddingStart/End
         *      ?listPreferredItemPadding* which the dialog themes rebind to
         *      ?dialogPreferredPadding = 24dp (themes_material.xml:1143-1146),
         *      gravity center_vertical.
         *    select_dialog_{single,multi}choice_material.xml: CheckedTextView,
         *      minHeight 48dip, textAppearance ?textAppearanceMedium = 18sp,
         *      paddingStart select_dialog_padding_start_material 20dp, paddingEnd
         *      ?dialogPreferredPadding 24dp, drawableStart
         *      ?listChoiceIndicatorSingle|Multiple, drawablePadding
         *      select_dialog_drawable_padding_start_material 20dp
         *      (dimens_material.xml:134-135), gravity center_vertical.
         *  All three take textColor ?textColorAlertDialogListItem, the dialog
         *  theme's primary text colour. The indicator is the framework one the
         *  inflater already falls back to (Resources.MacapkCompoundButtonDrawable,
         *  32dp); it follows the row's state_checked through TextView
         *  .drawableStateChanged. We used to hand back a bare TextView for every
         *  flavour: no indicator, no padding (ink at the panel edge), no 48dp
         *  row. */
        private android.widget.TextView macapkNewRow() {
            final boolean choice = mMode != PLAIN;
            final android.widget.TextView tv = choice
                    ? new android.widget.CheckedTextView(mCtx)
                    : new android.widget.TextView(mCtx);
            tv.setId(0x01020014);   // @android:id/text1
            tv.setMinHeight(dp(48f));
            tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
            tv.setTextColor(mTextColor);
            if (!choice) {
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
                tv.setPadding(dp(24f), 0, dp(24f), 0);
            } else {
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
                tv.setPadding(dp(20f), 0, dp(24f), 0);
                android.graphics.drawable.Drawable indicator = null;
                try {
                    android.content.res.Resources r = mCtx == null ? null : mCtx.getResources();
                    if (r != null) indicator = r.macapkFrameworkDrawableByName(
                            mMode == SINGLE ? "btn_radio_material" : "btn_check_material");
                } catch (Throwable ignored) {}
                tv.setCompoundDrawablePadding(dp(20f));
                if (indicator != null) {
                    tv.setCompoundDrawablesRelativeWithIntrinsicBounds(indicator, null, null, null);
                }
            }
            return tv;
        }
    }

    /** Concrete builder. Methods chain by returning `this`. */
    public static class Builder {
        private final Context context;
        private CharSequence mTitle;
        private View mCustomTitleView;
        private CharSequence mMessage;
        private int mIconId;
        private android.graphics.drawable.Drawable mIcon;
        private int mIconAttrId;
        private CharSequence mPositiveText, mNegativeText, mNeutralText;
        private OnClickListener mPositiveListener, mNegativeListener, mNeutralListener;
        private OnCancelListener mOnCancelListener;
        private OnDismissListener mOnDismissListener;
        private OnShowListener mOnShowListener;
        private OnKeyListener mOnKeyListener;
        private boolean mCancelable = true;
        private android.widget.ListAdapter mAdapter;
        private OnClickListener mListListener;
        private OnMultiChoiceClickListener mMultiChoiceListener;
        private boolean mIsSingleChoice;
        private boolean mIsMultiChoice;
        private int mCheckedItem = -1;
        private boolean[] mCheckedItems;
        private View mView;
        private int mViewLayoutResId;

        /** android14 AlertDialog.Builder (:469, :498-501):
         *      public Builder(Context context) {
         *          this(context, resolveDialogTheme(context, Resources.ID_NULL)); }
         *      public Builder(Context context, int themeResId) {
         *          P = new AlertController.AlertParams(new ContextThemeWrapper(
         *                  context, resolveDialogTheme(context, themeResId))); }
         *  so getContext() -- "a Context with the appropriate theme for dialogs
         *  created by this Builder. Applications should use this Context for
         *  obtaining LayoutInflaters" -- is the alert-theme wrapper, and create()
         *  hands it to `new AlertDialog(P.mContext, 0, false)` unwrapped again.
         *  Ours kept the caller's Context. MACAPK_DIALOG_THEME_WRAPPER=0 restores
         *  that. */
        public Builder(Context c) { this(c, 0); }
        public Builder(Context c, int themeResId) {
            this.context = MACAPK_DIALOG_THEME_WRAPPER
                    ? macapkThemedContext(c, resolveDialogTheme(c, themeResId), ATTR_ALERT_DIALOG_THEME)
                    : c;
        }

        public Context getContext() { return context; }

        private CharSequence res(int resId) {
            if (resId == 0 || context == null) return null;
            android.content.res.Resources r = context.getResources();
            return r == null ? null : r.getText(resId);
        }
        private CharSequence[] resArray(int resId) {
            if (resId == 0 || context == null) return null;
            android.content.res.Resources r = context.getResources();
            return r == null ? null : r.getTextArray(resId);
        }

        public Builder setTitle(CharSequence t)   { mTitle = t; return this; }
        public Builder setTitle(int resId)        { mTitle = res(resId); return this; }
        public Builder setCustomTitle(View customTitleView) { mCustomTitleView = customTitleView; return this; }
        public Builder setMessage(CharSequence t) { mMessage = t; return this; }
        public Builder setMessage(int resId)      { mMessage = res(resId); return this; }
        public Builder setIcon(int resId)         { mIconId = resId; return this; }
        public Builder setIcon(android.graphics.drawable.Drawable d) { mIcon = d; return this; }
        public Builder setIconAttribute(int attrId) { mIconAttrId = attrId; return this; }
        public Builder setPositiveButton(CharSequence t, OnClickListener l) { mPositiveText = t; mPositiveListener = l; return this; }
        public Builder setPositiveButton(int resId, OnClickListener l) { return setPositiveButton(res(resId), l); }
        public Builder setNegativeButton(CharSequence t, OnClickListener l) { mNegativeText = t; mNegativeListener = l; return this; }
        public Builder setNegativeButton(int resId, OnClickListener l) { return setNegativeButton(res(resId), l); }
        public Builder setNeutralButton(CharSequence t, OnClickListener l) { mNeutralText = t; mNeutralListener = l; return this; }
        public Builder setNeutralButton(int resId, OnClickListener l) { return setNeutralButton(res(resId), l); }
        public Builder setOnCancelListener(OnCancelListener l) { mOnCancelListener = l; return this; }
        public Builder setOnDismissListener(OnDismissListener l) { mOnDismissListener = l; return this; }
        public Builder setOnShowListener(OnShowListener l) { mOnShowListener = l; return this; }
        public Builder setOnKeyListener(OnKeyListener l) { mOnKeyListener = l; return this; }
        public Builder setCancelable(boolean c)   { mCancelable = c; return this; }
        public Builder setItems(CharSequence[] items, OnClickListener l) {
            mAdapter = new ItemsAdapter(context, items);
            mListListener = l;
            mIsSingleChoice = false; mIsMultiChoice = false;
            return this;
        }
        public Builder setItems(int resId, OnClickListener l) { return setItems(resArray(resId), l); }
        public Builder setSingleChoiceItems(CharSequence[] items, int checked, OnClickListener l) {
            mAdapter = new ItemsAdapter(context, items);
            mListListener = l;
            mCheckedItem = checked;
            mIsSingleChoice = true; mIsMultiChoice = false;
            return this;
        }
        public Builder setSingleChoiceItems(int resId, int checked, OnClickListener l) {
            return setSingleChoiceItems(resArray(resId), checked, l);
        }
        public Builder setSingleChoiceItems(android.widget.ListAdapter adapter, int checked, OnClickListener l) {
            mAdapter = adapter;
            mListListener = l;
            mCheckedItem = checked;
            mIsSingleChoice = true; mIsMultiChoice = false;
            return this;
        }
        public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checked, OnMultiChoiceClickListener l) {
            mAdapter = new ItemsAdapter(context, items);
            mMultiChoiceListener = l;
            mCheckedItems = checked;
            mIsMultiChoice = true; mIsSingleChoice = false;
            return this;
        }
        public Builder setMultiChoiceItems(int resId, boolean[] checked, OnMultiChoiceClickListener l) {
            return setMultiChoiceItems(resArray(resId), checked, l);
        }
        public Builder setAdapter(android.widget.ListAdapter adapter, OnClickListener l) {
            mAdapter = adapter;
            mListListener = l;
            mIsSingleChoice = false; mIsMultiChoice = false;
            return this;
        }
        public Builder setView(View v)            { mView = v; mViewLayoutResId = 0; return this; }
        public Builder setView(int layoutResId)   { mViewLayoutResId = layoutResId; mView = null; return this; }
        /** AOSP AlertParams.mForceInverseBackground, handed to the dialog by
         *  create() (only the traditional layout reads it). */
        public Builder setInverseBackgroundForced(boolean inverse) { mForceInverseBackground = inverse; return this; }
        private boolean mForceInverseBackground;

        // ---- AOSP API-36 surface completion (Cursor-backed lists + item
        // selection). AOSP ref: frameworks/base/core/java/android/app/
        // AlertDialog.java (android16-release). AOSP builds a
        // SimpleCursorAdapter over the cursor using `labelColumn` for the row
        // text; android.widget.SimpleCursorAdapter exists in this tree, so
        // these are the real thing rather than a shape.

        /** AOSP setCursor(Cursor, OnClickListener, String labelColumn): "Set a
         *  list of items, which are supplied by the given Cursor, to be
         *  displayed in the dialog as the content." */
        public Builder setCursor(android.database.Cursor cursor, OnClickListener l,
                String labelColumn) {
            mAdapter = cursorAdapter(cursor, labelColumn);
            mListListener = l;
            mIsSingleChoice = false; mIsMultiChoice = false;
            return this;
        }

        /** AOSP setSingleChoiceItems(Cursor, int checkedItem, String labelColumn,
         *  OnClickListener): the radio-button form over a Cursor. */
        public Builder setSingleChoiceItems(android.database.Cursor cursor, int checkedItem,
                String labelColumn, OnClickListener l) {
            mAdapter = cursorAdapter(cursor, labelColumn);
            mListListener = l;
            mCheckedItem = checkedItem;
            mIsSingleChoice = true; mIsMultiChoice = false;
            return this;
        }

        /** AOSP setMultiChoiceItems(Cursor, String isCheckedColumn, String
         *  labelColumn, OnMultiChoiceClickListener): the checkbox form over a
         *  Cursor; `isCheckedColumn` names the boolean column that seeds each
         *  row's checked state, which is read here rather than ignored. */
        public Builder setMultiChoiceItems(android.database.Cursor cursor,
                String isCheckedColumn, String labelColumn, OnMultiChoiceClickListener l) {
            mAdapter = cursorAdapter(cursor, labelColumn);
            mMultiChoiceListener = l;
            mCheckedItems = readCheckedColumn(cursor, isCheckedColumn);
            mIsMultiChoice = true; mIsSingleChoice = false;
            return this;
        }

        /** AOSP setOnItemSelectedListener(AdapterView.OnItemSelectedListener):
         *  "Sets a listener to be invoked when an item in the list is selected"
         *  -- selection (focus), not click. Stored and installed on the ListView
         *  by create() below, which is where AOSP's AlertController installs
         *  it. */
        public Builder setOnItemSelectedListener(
                android.widget.AdapterView.OnItemSelectedListener listener) {
            mOnItemSelectedListener = listener;
            return this;
        }
        private android.widget.AdapterView.OnItemSelectedListener mOnItemSelectedListener;

        /** AOSP's own construction: a SimpleCursorAdapter binding `labelColumn`
         *  into a single text view per row. */
        private android.widget.ListAdapter cursorAdapter(android.database.Cursor cursor,
                String labelColumn) {
            if (cursor == null) return null;
            try {
                return new android.widget.SimpleCursorAdapter(context, 0, cursor,
                        new String[] { labelColumn }, new int[] { 0 }, 0);
            } catch (Throwable t) {
                return null;
            }
        }

        /** AOSP's AlertController.AlertParams reads the isCheckedColumn for
         *  every row to seed the checkbox states; do the same, restoring the
         *  cursor position so the caller's cursor is not left moved. */
        private static boolean[] readCheckedColumn(android.database.Cursor cursor,
                String isCheckedColumn) {
            if (cursor == null || isCheckedColumn == null) return null;
            try {
                int saved = cursor.getPosition();
                int col = cursor.getColumnIndex(isCheckedColumn);
                if (col < 0) return null;
                boolean[] out = new boolean[cursor.getCount()];
                for (int i = 0; i < out.length; i++) {
                    if (cursor.moveToPosition(i)) out[i] = cursor.getInt(col) == 1;
                }
                cursor.moveToPosition(saved);
                return out;
            } catch (Throwable t) {
                return null;
            }
        }

        /** AOSP: `new AlertDialog(P.mContext, 0, false); P.apply(dialog.mAlert);` */
        public AlertDialog create() {
            // "Context has already been wrapped with the appropriate theme."
            AlertDialog d = MACAPK_DIALOG_THEME_WRAPPER
                    ? new AlertDialog(context, 0, false)
                    : new AlertDialog(context);
            if (mCustomTitleView != null) d.setCustomTitle(mCustomTitleView);
            else if (mTitle != null) d.setTitle(mTitle);
            if (mMessage != null) d.setMessage(mMessage);
            if (mIcon != null) d.setIcon(mIcon);
            else if (mIconId != 0) d.setIcon(mIconId);
            else if (mIconAttrId != 0) d.setIconAttribute(mIconAttrId);
            if (mPositiveText != null || mPositiveListener != null)
                d.setButton(BUTTON_POSITIVE, mPositiveText, mPositiveListener);
            if (mNegativeText != null || mNegativeListener != null)
                d.setButton(BUTTON_NEGATIVE, mNegativeText, mNegativeListener);
            if (mNeutralText != null || mNeutralListener != null)
                d.setButton(BUTTON_NEUTRAL, mNeutralText, mNeutralListener);
            if (mAdapter != null) {
                d.mAdapter = mAdapter;
                d.mListListener = mListListener;
                d.mMultiChoiceListener = mMultiChoiceListener;
                d.mIsSingleChoice = mIsSingleChoice;
                d.mIsMultiChoice = mIsMultiChoice;
                d.mCheckedItem = mCheckedItem;
                d.mCheckedItems = mCheckedItems;
                d.mOnItemSelectedListener = mOnItemSelectedListener;
            }
            View v = mView;
            if (v == null && mViewLayoutResId != 0) {
                // AlertController.setupCustomContent: mInflater.inflate(...), with
                // mInflater = LayoutInflater.from(the dialog context).
                android.view.LayoutInflater inf = MACAPK_DIALOG_THEME_WRAPPER
                        ? macapkInflaterFor(context)
                        : android.view.LayoutInflater.from(context);
                if (inf != null) v = inf.inflate(mViewLayoutResId, null, false);
            }
            if (v != null) d.setView(v);
            if (mForceInverseBackground) d.setInverseBackgroundForced(true);
            // The tail of AOSP's create(), verbatim in behavior: cancelable is
            // always pushed (and implies dismiss-on-outside-touch), the cancel
            // and dismiss listeners are installed unconditionally, the key
            // listener only when non-null.
            d.setCancelable(mCancelable);
            if (mCancelable) d.setCanceledOnTouchOutside(true);
            d.setOnCancelListener(mOnCancelListener);
            d.setOnDismissListener(mOnDismissListener);
            if (mOnShowListener != null) d.setOnShowListener(mOnShowListener);
            if (mOnKeyListener != null) d.setOnKeyListener(mOnKeyListener);
            return d;
        }
        public AlertDialog show()   { AlertDialog d = create(); try { d.show(); } catch (Throwable ignored) {} return d; }
    }
}
