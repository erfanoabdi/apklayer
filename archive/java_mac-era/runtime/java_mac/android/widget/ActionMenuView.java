// android.widget.ActionMenuView -- the horizontal LinearLayout that lays out
// a Menu's action items inside a Toolbar/ActionBar.
// AOSP: frameworks/base/core/java/android/widget/ActionMenuView.java
//   public class ActionMenuView extends LinearLayout
//
// THE MENU HALF (2026-09-26, gate MACAPK_PLATFORM_TOOLBAR, shared with Toolbar).
// Until now getMenu() handed out PopupMenu's storage-only MenuStub and nothing
// was ever drawn: an item added to a Toolbar's menu never became a view, and an
// overflow item could not be reached. With the gate ON this class plays the
// three AOSP roles that make a Toolbar menu work, each ported from android14:
//   * MenuBuilder / MenuItemImpl (com/android/internal/view/menu/) -- the menu
//     itself: ordering by category + orderInCategory (MenuBuilder :60-67,
//     :800-809), change notification, and MenuItemImpl.invoke (:146-175) with
//     its icon-tint rule (:540-555);
//   * ActionMenuPresenter (android/widget/ActionMenuPresenter.java) --
//     flagActionItems (:630-760, the non-strict branch a Toolbar uses) with
//     ActionBarPolicy's limits (com/android/internal/view/ActionBarPolicy.java
//     :52-78), updateMenuView (:437-487) and the overflow button;
//   * ActionMenuItemView (com/android/internal/view/menu/ActionMenuItemView.java)
//     -- the view each action item becomes, inflated on a device from
//     action_menu_item_layout.xml in ?android:actionButtonStyle.
// Nested classes, not the com.android.internal ones, because nothing an app can
// see names those types (they are hidden API) and this lane owns only this file.
//
// NOT PORTED (stated): the exact-width "format items" measure/layout
// (onMeasureExactFormat and the cell-distributing onLayout, AOSP :143-560) --
// ActionMenuView only enters it when measured EXACTLY, which a Toolbar never
// does (its menu view is wrap_content and measured AT_MOST), so here it lays
// out as the plain horizontal LinearLayout AOSP falls back to. Submenus open no
// popup of their own, and an item's expandable action view is not expanded.
//
// The overflow popup reuses android.widget.PopupMenu, whose show() already
// builds a real anchored PopupWindow with Material row metrics; AOSP's
// OverflowPopup is a MenuPopupHelper over the same kind of list.
package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ActionMenuView extends LinearLayout {
    private static final String TAG = "Mac.apk.ActionMenuView";

    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }

    private Menu mMenu;
    private OnMenuItemClickListener mOnMenuItemClickListener;
    private int mPopupTheme;

    public ActionMenuView(Context context) { super(context); setOrientation(HORIZONTAL); macapkInit(); }
    public ActionMenuView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs); setOrientation(HORIZONTAL); macapkInit();
    }

    /** AOSP ActionMenuView(Context, AttributeSet) :78-88: baselineAligned off,
     *  the cell constants, "mPopupContext = context". */
    private void macapkInit() {
        if (!Toolbar.MACAPK_PLATFORM_TOOLBAR) return;
        setBaselineAligned(false);
        mPopupContext = getContext();
    }

    public Menu getMenu() {
        if (mMenu == null) {
            if (Toolbar.MACAPK_PLATFORM_TOOLBAR) {
                // AOSP :641-655 -- a MenuBuilder with this view's presenter,
                // "mPresenter.setReserveOverflow(true)"; adding the presenter
                // runs its initForMenu (ActionMenuPresenter :153-168), which
                // creates the overflow button up front, so getOverflowIcon()
                // answers the style's icon before any item exists.
                mReserveOverflow = true;
                mMenu = new MenuImpl(getContext(), this);
                overflowButton();
            } else {
                // Reuse PopupMenu's concrete Menu (MenuStub) so inflate + getItem
                // work; ActionMenuView itself does not paint the items.
                mMenu = new PopupMenu(getContext(), this).getMenu();
            }
        }
        return mMenu;
    }

    /** AOSP onMeasure (:143-173), the branch a wrap_content menu view always
     *  takes: "Previous measurement at exact format may have set margins -
     *  reset them", then LinearLayout's measure. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (Toolbar.MACAPK_PLATFORM_TOOLBAR) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final ViewGroup.LayoutParams lp = getChildAt(i).getLayoutParams();
                if (lp instanceof LayoutParams) {
                    ((LayoutParams) lp).leftMargin = ((LayoutParams) lp).rightMargin = 0;
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** AOSP peekMenu() (:672) -- the menu if one has been created, else null.
     *  Package-private here because AOSP's returns the internal MenuBuilder type. */
    Menu peekMenu() { return mMenu; }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        mOnMenuItemClickListener = listener;
    }

    /** AOSP :95-104 -- the popup context is what the overflow popup runs in. */
    public void setPopupTheme(int resId) {
        if (!Toolbar.MACAPK_PLATFORM_TOOLBAR) { mPopupTheme = resId; return; }
        if (mPopupTheme != resId) {
            mPopupTheme = resId;
            mPopupContext = resId == 0 ? getContext()
                    : new android.view.ContextThemeWrapper(getContext(), resId);
        }
    }
    public int  getPopupTheme() { return mPopupTheme; }

    /** AOSP :681-686 -> ActionMenuPresenter.showOverflowMenu (:550-565): only
     *  when overflow is reserved, not already showing, and there is something
     *  to put in it; the popup opens from a post, as AOSP's OpenOverflowRunnable
     *  does, so a menu view that has not been laid out yet anchors correctly. */
    public boolean showOverflowMenu() {
        if (!Toolbar.MACAPK_PLATFORM_TOOLBAR) return false;
        if (mReserveOverflow && !isOverflowMenuShowing() && mMenu instanceof MenuImpl
                && mPostedOpenRunnable == null && !overflowItems((MenuImpl) mMenu).isEmpty()) {
            mPostedOpenRunnable = new Runnable() {
                @Override public void run() {
                    mPostedOpenRunnable = null;
                    macapkOpenOverflowPopup();
                }
            };
            post(mPostedOpenRunnable);
            return true;
        }
        return false;
    }
    /** AOSP ActionMenuPresenter.hideOverflowMenu (:572-585). */
    public boolean hideOverflowMenu() {
        if (!Toolbar.MACAPK_PLATFORM_TOOLBAR) return false;
        if (mPostedOpenRunnable != null) {
            removeCallbacks(mPostedOpenRunnable);
            mPostedOpenRunnable = null;
            return true;
        }
        PopupMenu p = mOverflowPopup;
        if (p != null && mOverflowPopupShowing) {
            p.dismiss();
            return true;
        }
        return false;
    }
    public boolean isOverflowMenuShowing() {
        if (!Toolbar.MACAPK_PLATFORM_TOOLBAR) return false;
        return mOverflowPopup != null && mOverflowPopupShowing;
    }
    /** AOSP isOverflowReserved (:570). */
    public boolean isOverflowReserved() { return mReserveOverflow; }
    public void dismissPopupMenus() {
        if (!Toolbar.MACAPK_PLATFORM_TOOLBAR) return;
        hideOverflowMenu();
    }

    // ==================================================================
    // API-36 surface completion. AOSP:
    // frameworks/base/core/java/android/widget/ActionMenuView.java (android16).
    // ==================================================================

    /** AOSP :552 / :563 -- both go through the ActionMenuPresenter, which owns
     *  the overflow button: "getMenu(); mPresenter.setOverflowIcon(icon)", and
     *  the presenter holds the icon as PENDING until the button exists. With the
     *  gate off the icon is simply held on the view, as before. */
    private android.graphics.drawable.Drawable mOverflowIcon;
    public void setOverflowIcon(android.graphics.drawable.Drawable icon) {
        if (!Toolbar.MACAPK_PLATFORM_TOOLBAR) { mOverflowIcon = icon; return; }
        getMenu();
        if (mOverflowButton != null) {
            mOverflowButton.setImageDrawable(icon);
        } else {
            mOverflowIcon = icon;
            mPendingOverflowIconSet = true;
        }
    }
    public android.graphics.drawable.Drawable getOverflowIcon() {
        if (!Toolbar.MACAPK_PLATFORM_TOOLBAR) return mOverflowIcon;
        getMenu();
        if (mOverflowButton != null) return mOverflowButton.getDrawable();
        return mPendingOverflowIconSet ? mOverflowIcon : null;
    }

    /** AOSP :593 -- note the CENTER_VERTICAL default AOSP forces when the
     *  source carries no gravity, and that a null source falls through to
     *  generateDefaultLayoutParams().
     *  Overrides ViewGroup's (and LinearLayout's) generateLayoutParams(
     *  ViewGroup.LayoutParams), which addView calls when checkLayoutParams
     *  rejects a foreign LayoutParams. The covariant return is the SDK's
     *  descriptor (ActionMenuView$LayoutParams); javac bridges the others. */
    protected LayoutParams generateLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (p != null) {
            final LayoutParams result = (p instanceof LayoutParams)
                    ? new LayoutParams((LayoutParams) p)
                    : new LayoutParams(p);
            if (result.gravity <= android.view.Gravity.NO_GRAVITY) {
                result.gravity = android.view.Gravity.CENTER_VERTICAL;
            }
            return result;
        }
        return new LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** AOSP ActionMenuView.LayoutParams (android16.txt:60134) --
     *  `extends LinearLayout.LayoutParams`, four constructors, no extra public
     *  members. Its `gravity` field is LinearLayout.LayoutParams's. */
    /** v1.0.1643: AOSP checkLayoutParams, so addView converts a foreign
     *  LayoutParams into this container's own kind (ViewGroup.addView ->
     *  generateLayoutParams). Gate MACAPK_CHECK_LAYOUT_PARAMS=0 restores the old
     *  accept-anything answer for an A/B. */
    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (!MACAPK_CHECK_LP) return true;
        return p instanceof ActionMenuView.LayoutParams;
    }
    private static final boolean MACAPK_CHECK_LP =
        !"0".equals(System.getenv("MACAPK_CHECK_LAYOUT_PARAMS"));

    /** AOSP ActionMenuView.generateLayoutParams(AttributeSet). See TableRow.
     *
     *  Declared COVARIANTLY (2026-09-26), as the SDK does: android-34 android.jar
     *  has `public ActionMenuView$LayoutParams generateLayoutParams(AttributeSet)`,
     *  and ours returned ViewGroup.LayoutParams, so a call compiled against the
     *  SDK through an ActionMenuView-typed reference named a descriptor we did
     *  not have (NoSuchMethodError). The body was already ungated and always
     *  built an ActionMenuView.LayoutParams, so only the descriptor changes:
     *  javac emits the ViewGroup$LayoutParams-returning bridge, which keeps
     *  every existing caller (LayoutInflater, through a ViewGroup reference)
     *  on the same method. No gate for that reason. Unlike Toolbar's pair, no
     *  bisect arm here needs to hand back a bare LayoutParams.
     *  (generateDefaultLayoutParams, the SDK's third ActionMenuView$LayoutParams
     *  descriptor, is still inherited from LinearLayout: declaring it
     *  covariantly changes what addView(child) stores, and a covariant method
     *  cannot give a =0 arm LinearLayout's LayoutParams back, so it waits for
     *  MACAPK_PLATFORM_TOOLBAR to retire; 0 corpus references,
     *  debug/apishape_census.py 2026-09-26.) */
    @Override
    public ActionMenuView.LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new ActionMenuView.LayoutParams(getContext(), attrs);
    }

    public static class LayoutParams extends LinearLayout.LayoutParams {
        /** AOSP :795 (@hide): marks the overflow button, which the exact-format
         *  layout pins to the end. Set by generateOverflowButtonLayoutParams. */
        boolean isOverflowButton;
        public LayoutParams(Context c, android.util.AttributeSet attrs) { super(c, attrs); }
        public LayoutParams(android.view.ViewGroup.LayoutParams other) { super(other); }
        public LayoutParams(LayoutParams other) { super(other); isOverflowButton = other.isOverflowButton; }
        public LayoutParams(int width, int height) { super(width, height); }
    }

    // ==================================================================
    // THE PORT (MACAPK_PLATFORM_TOOLBAR). Everything below runs only when the
    // menu was created by getMenu() under the gate.
    // ==================================================================

    /** android.R.attr ids (public-final.xml). */
    private static final int ATTR_ACTION_BUTTON_STYLE = 0x010102d8;
    private static final int ATTR_ACTION_OVERFLOW_BUTTON_STYLE = 0x010102f6;
    private static final int ATTR_ACTION_MENU_TEXT_APPEARANCE = 0x01010360;
    private static final int ATTR_MIN_WIDTH = 0x0101013f;
    /** ActionMenuItemView.MAX_ICON_SIZE (:55), dp. */
    private static final int MAX_ICON_SIZE_DP = 32;

    private Context mPopupContext;
    private boolean mReserveOverflow;
    private OverflowMenuButton mOverflowButton;
    private boolean mPendingOverflowIconSet;
    private Runnable mPostedOpenRunnable;
    private PopupMenu mOverflowPopup;
    private boolean mOverflowPopupShowing;
    /** BaseMenuPresenter reuses an item's view across updates (the convertView
     *  path); keyed by item identity, so a rebuilt menu keeps its views' state. */
    private final Map<ItemImpl, View> mItemViews = new IdentityHashMap<>();

    /** AOSP ActionMenuView.generateDefaultLayoutParams (:580-584) as used by the
     *  presenter: wrap/wrap, CENTER_VERTICAL. */
    private LayoutParams macapkDefaultItemLp() {
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        return params;
    }

    /** AOSP generateOverflowButtonLayoutParams (:612-616). */
    private LayoutParams generateOverflowButtonLayoutParams() {
        LayoutParams result = macapkDefaultItemLp();
        result.isOverflowButton = true;
        return result;
    }

    // ---- ActionBarPolicy (:52-78) ----
    private int maxActionButtons() {
        android.content.res.Configuration config = getContext().getResources().getConfiguration();
        final int width = config.screenWidthDp;
        final int height = config.screenHeightDp;
        final int smallest = config.smallestScreenWidthDp;
        if (smallest > 600 || (width > 960 && height > 720) || (width > 720 && height > 960)) {
            return 5;   // values-w600dp, values-sw600dp, values-xlarge
        } else if (width >= 500 || (width > 640 && height > 480) || (width > 480 && height > 640)) {
            return 4;   // values-w500dp, values-large
        } else if (width >= 360) {
            return 3;   // values-w360dp
        } else {
            return 2;
        }
    }
    private int embeddedMenuWidthLimit() {
        return getContext().getResources().getDisplayMetrics().widthPixels / 2;
    }

    /** AOSP ActionMenuPresenter.initForMenu (:134-173): the action-item width
     *  limit is the embedded limit less the overflow button, measured unbounded. */
    private int actionItemWidthLimit() {
        int width = embeddedMenuWidthLimit();
        if (mReserveOverflow) {
            OverflowMenuButton b = overflowButton();
            final int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            b.measure(spec, spec);
            width -= b.getMeasuredWidth();
        }
        return width;
    }

    private OverflowMenuButton overflowButton() {
        if (mOverflowButton == null) {
            mOverflowButton = new OverflowMenuButton(getContext(), this);
            if (mPendingOverflowIconSet) {
                mOverflowButton.setImageDrawable(mOverflowIcon);
                mOverflowIcon = null;
                mPendingOverflowIconSet = false;
            }
        }
        return mOverflowButton;
    }

    /** MenuBuilder.onItemsChanged -> the presenter's updateMenuView. */
    void macapkOnItemsChanged() {
        if (!(mMenu instanceof MenuImpl)) return;
        final MenuImpl menu = (MenuImpl) mMenu;
        flagActionItems(menu);
        updateMenuView(menu);
    }

    /** AOSP ActionMenuPresenter.flagActionItems (:630-760), the non-strict
     *  branch (mStrictWidthLimit is only set by the split action bar). */
    private void flagActionItems(MenuImpl menu) {
        final List<ItemImpl> visibleItems = menu.visibleItems();
        final int itemsSize = visibleItems.size();
        int maxActions = maxActionButtons();
        int widthLimit = actionItemWidthLimit();
        final int querySpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        int requiredItems = 0;
        int requestedItems = 0;
        int firstActionWidth = 0;
        boolean hasOverflow = false;
        for (int i = 0; i < itemsSize; i++) {
            ItemImpl item = visibleItems.get(i);
            if (item.requiresActionButton()) {
                requiredItems++;
            } else if (item.requestsActionButton()) {
                requestedItems++;
            } else {
                hasOverflow = true;
            }
        }

        // Reserve a spot for the overflow item if needed.
        if (mReserveOverflow && (hasOverflow || requiredItems + requestedItems > maxActions)) {
            maxActions--;
        }
        maxActions -= requiredItems;

        final Map<Integer, Boolean> seenGroups = new HashMap<>();

        // Flag as many more requested items as will fit.
        for (int i = 0; i < itemsSize; i++) {
            ItemImpl item = visibleItems.get(i);

            if (item.requiresActionButton()) {
                View v = itemView(item);
                v.measure(querySpec, querySpec);
                final int measuredWidth = v.getMeasuredWidth();
                widthLimit -= measuredWidth;
                if (firstActionWidth == 0) firstActionWidth = measuredWidth;
                final int groupId = item.getGroupId();
                if (groupId != 0) seenGroups.put(groupId, true);
                item.mIsActionButton = true;
            } else if (item.requestsActionButton()) {
                // Items in a group with other items that already have an action
                // slot can break the max actions rule, but not the width limit.
                final int groupId = item.getGroupId();
                final boolean inGroup = Boolean.TRUE.equals(seenGroups.get(groupId));
                boolean isAction = (maxActions > 0 || inGroup) && widthLimit > 0;

                if (isAction) {
                    View v = itemView(item);
                    v.measure(querySpec, querySpec);
                    final int measuredWidth = v.getMeasuredWidth();
                    widthLimit -= measuredWidth;
                    if (firstActionWidth == 0) firstActionWidth = measuredWidth;
                    // Did this push the entire first item past the limit?
                    isAction &= widthLimit + firstActionWidth > 0;
                }

                if (isAction && groupId != 0) {
                    seenGroups.put(groupId, true);
                } else if (inGroup) {
                    // We broke the width limit. Demote the whole group, they all
                    // overflow now.
                    seenGroups.put(groupId, false);
                    for (int j = 0; j < i; j++) {
                        ItemImpl areYouMyGroupie = visibleItems.get(j);
                        if (areYouMyGroupie.getGroupId() == groupId) {
                            // Give back the action slot
                            if (areYouMyGroupie.mIsActionButton) maxActions++;
                            areYouMyGroupie.mIsActionButton = false;
                        }
                    }
                }

                if (isAction) maxActions--;
                item.mIsActionButton = isAction;
            } else {
                // Neither requires nor requests an action button.
                item.mIsActionButton = false;
            }
        }
    }

    /** The visible items that did not get an action slot (MenuBuilder.getNonActionItems). */
    private static List<ItemImpl> overflowItems(MenuImpl menu) {
        final ArrayList<ItemImpl> out = new ArrayList<>();
        for (ItemImpl it : menu.visibleItems()) if (!it.mIsActionButton) out.add(it);
        return out;
    }

    /** AOSP BaseMenuPresenter.updateMenuView (:83-125) + ActionMenuPresenter.
     *  updateMenuView (:437-487): the action items' views in menu order, then
     *  the overflow button when anything is left over. Children are rebuilt only
     *  when the wanted list differs, so an unchanged menu does not churn views. */
    private void updateMenuView(MenuImpl menu) {
        final ArrayList<View> wanted = new ArrayList<>();
        for (ItemImpl item : menu.visibleItems()) {
            if (!item.mIsActionButton) continue;
            wanted.add(itemView(item));
        }
        final List<ItemImpl> nonAction = overflowItems(menu);
        final boolean hasOverflow = mReserveOverflow && !nonAction.isEmpty();
        if (hasOverflow) wanted.add(overflowButton());

        boolean same = wanted.size() == getChildCount();
        for (int i = 0; same && i < wanted.size(); i++) same = getChildAt(i) == wanted.get(i);
        if (!same) {
            removeAllViews();
            for (View v : wanted) {
                // BaseMenuPresenter.addItemView (:126-132): an action view the
                // app built may still sit in another parent; detach it first.
                if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (v == mOverflowButton) {
                    addView(v, generateOverflowButtonLayoutParams());
                } else {
                    addView(v, lp instanceof LayoutParams ? lp : macapkItemLp(lp));
                }
            }
        }
        // Dropped items must not keep a view cached against them forever.
        mItemViews.keySet().retainAll(menu.items);
        requestLayout();
        invalidate();
    }

    /** ActionMenuPresenter.getItemView (:237-253): an item's own action view if
     *  it has one, else its ActionMenuItemView; either way in this container's
     *  LayoutParams. */
    private View itemView(ItemImpl item) {
        View v = mItemViews.get(item);
        View actionView = item.getActionView();
        if (actionView != null) {
            v = actionView;
        } else if (!(v instanceof ItemView)) {
            v = new ItemView(getContext(), this);
        }
        if (v instanceof ItemView) ((ItemView) v).initialize(item);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (!(lp instanceof LayoutParams)) v.setLayoutParams(macapkItemLp(lp));
        mItemViews.put(item, v);
        return v;
    }

    /** action_menu_item_layout.xml: wrap_content x wrap_content,
     *  layout_gravity="center"; a foreign LayoutParams converts as
     *  generateLayoutParams would. */
    private LayoutParams macapkItemLp(ViewGroup.LayoutParams from) {
        if (from != null) return generateLayoutParams(from);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    /** MenuItemImpl.invoke (:146-175), with ActionMenuView's MenuBuilderCallback
     *  (:766-775) as the menu's dispatchMenuItemSelected. */
    boolean macapkInvokeItem(ItemImpl item) {
        if (item == null || !item.isEnabled()) return false;
        if (item.mClickListener != null && item.mClickListener.onMenuItemClick(item)) return true;
        if (mOnMenuItemClickListener != null && mOnMenuItemClickListener.onMenuItemClick(item)) {
            return true;
        }
        if (item.mIntent != null) {
            try {
                getContext().startActivity(item.mIntent);
                return true;
            } catch (Throwable t) {
                com.macapk.loader.NativeLog.w(TAG, "Can't find activity to handle intent; ignoring: " + t);
            }
        }
        if (item.mActionProvider != null && item.mActionProvider.onPerformDefaultAction()) return true;
        return false;
    }

    /** AOSP OpenOverflowRunnable -> OverflowPopup.tryShow(), anchored to the
     *  overflow button with Gravity.END (ActionMenuPresenter :937-950, :1003-1020). */
    private void macapkOpenOverflowPopup() {
        if (!(mMenu instanceof MenuImpl) || mOverflowButton == null) return;
        final List<ItemImpl> items = overflowItems((MenuImpl) mMenu);
        if (items.isEmpty()) return;
        final Context ctx = mPopupContext != null ? mPopupContext : getContext();
        final PopupMenu p = new PopupMenu(ctx, mOverflowButton, Gravity.END);
        final Menu pm = p.getMenu();
        final ArrayList<MenuItem> rows = new ArrayList<>();
        for (ItemImpl it : items) {
            MenuItem row = pm.add(it.getGroupId(), it.getItemId(), it.getOrder(), it.getTitle());
            row.setEnabled(it.isEnabled());
            row.setCheckable(it.isCheckable());
            row.setChecked(it.isChecked());
            row.setIcon(it.getIcon());
            rows.add(row);
        }
        p.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem row) {
                int i = rows.indexOf(row);
                return i >= 0 && macapkInvokeItem(items.get(i));
            }
        });
        p.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override public void onDismiss(PopupMenu menu) {
                mOverflowPopupShowing = false;
                mOverflowPopup = null;
            }
        });
        mOverflowPopup = p;
        mOverflowPopupShowing = true;
        p.show();
    }

    // ---- ActionMenuItemView (com/android/internal/view/menu/ActionMenuItemView.java) ----

    /** One action item. Built the way action_menu_item_layout.xml inflates on a
     *  device: style ?android:actionButtonStyle, then the element's own
     *  gravity="center", focusable, paddingTop/Bottom 4dip, paddingStart/End
     *  8dip and textAppearance ?android:actionMenuTextAppearance. */
    static final class ItemView extends TextView implements View.OnClickListener {
        private final ActionMenuView mHost;
        private ItemImpl mItemData;
        private CharSequence mTitle;
        private Drawable mIcon;
        private final int mMinWidth;
        private final int mMaxIconSize;
        private int mSavedPaddingLeft = -1;

        ItemView(Context context, ActionMenuView host) {
            super(context, null, ATTR_ACTION_BUTTON_STYLE, 0);
            mHost = host;
            int minW = 0;
            try {
                TypedArray a = context.obtainStyledAttributes(null,
                        new int[] { ATTR_MIN_WIDTH }, ATTR_ACTION_BUTTON_STYLE, 0);
                minW = a.getDimensionPixelSize(0, 0);
                a.recycle();
            } catch (Throwable ignored) {}
            mMinWidth = minW;
            final float density = context.getResources().getDisplayMetrics().density;
            mMaxIconSize = (int) (MAX_ICON_SIZE_DP * density + 0.5f);
            setGravity(Gravity.CENTER);
            setFocusable(true);
            final int p4 = (int) (4 * density + 0.5f);
            final int p8 = (int) (8 * density + 0.5f);
            setPaddingRelative(p8, p4, p8, p4);
            mSavedPaddingLeft = p8;
            try {
                android.util.TypedValue tv = new android.util.TypedValue();
                if (context.getTheme().resolveAttribute(ATTR_ACTION_MENU_TEXT_APPEARANCE, tv, true)
                        && tv.resourceId != 0) {
                    setTextAppearance(tv.resourceId);
                }
            } catch (Throwable ignored) {}
            setOnClickListener(this);
            setSaveEnabled(false);
        }

        /** AOSP :100-107. */
        @Override public void setPadding(int l, int t, int r, int b) {
            mSavedPaddingLeft = l;
            super.setPadding(l, t, r, b);
        }

        /** AOSP initialize (:117-130). */
        void initialize(ItemImpl itemData) {
            mItemData = itemData;
            setIcon(itemData.getIcon());
            CharSequence tc = itemData.getTitleCondensed();
            setTitle(tc != null ? tc : itemData.getTitle());   // prefersCondensedTitle
            setId(itemData.getItemId());
            setVisibility(itemData.isVisible() ? View.VISIBLE : View.GONE);
            setEnabled(itemData.isEnabled());
        }

        @Override public void onClick(View v) { mHost.macapkInvokeItem(mItemData); }

        private boolean shouldAllowTextWithIcon() {
            final android.content.res.Configuration configuration =
                    getContext().getResources().getConfiguration();
            final int width = configuration.screenWidthDp;
            final int height = configuration.screenHeightDp;
            return width >= 480 || (width >= 640 && height >= 480)
                    || configuration.orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        }

        /** AOSP updateTextButtonVisibility (:190-215). */
        private void updateTextButtonVisibility() {
            boolean visible = !TextUtils.isEmpty(mTitle);
            visible &= mIcon == null
                    || (mItemData != null && mItemData.showsTextAsAction() && shouldAllowTextWithIcon());
            setText(visible ? mTitle : null);
            final CharSequence contentDescription =
                    mItemData != null ? mItemData.getContentDescription() : null;
            if (TextUtils.isEmpty(contentDescription)) {
                setContentDescription(visible ? null : (mItemData != null ? mItemData.getTitle() : null));
            } else {
                setContentDescription(contentDescription);
            }
            final CharSequence tooltipText = mItemData != null ? mItemData.getTooltipText() : null;
            if (TextUtils.isEmpty(tooltipText)) {
                setTooltipText(visible ? null : (mItemData != null ? mItemData.getTitle() : null));
            } else {
                setTooltipText(tooltipText);
            }
        }

        /** AOSP setIcon (:217-238): bounds capped at 32dp, kept proportional. */
        void setIcon(Drawable icon) {
            mIcon = icon;
            if (icon != null) {
                int width = icon.getIntrinsicWidth();
                int height = icon.getIntrinsicHeight();
                if (width > mMaxIconSize) {
                    final float scale = (float) mMaxIconSize / width;
                    width = mMaxIconSize;
                    height *= scale;
                }
                if (height > mMaxIconSize) {
                    final float scale = (float) mMaxIconSize / height;
                    height = mMaxIconSize;
                    width *= scale;
                }
                icon.setBounds(0, 0, width, height);
            }
            setCompoundDrawables(icon, null, null, null);
            updateTextButtonVisibility();
        }

        void setTitle(CharSequence title) {
            mTitle = title;
            updateTextButtonVisibility();
        }

        boolean hasText() { return !TextUtils.isEmpty(getText()); }

        /** AOSP onMeasure (:287-313): re-measure up to minWidth, and pad the
         *  icon into the centre when no text shows. */
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final boolean textVisible = hasText();
            if (textVisible && mSavedPaddingLeft >= 0) {
                super.setPadding(mSavedPaddingLeft, getPaddingTop(),
                        getPaddingRight(), getPaddingBottom());
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            final int oldMeasuredWidth = getMeasuredWidth();
            final int targetWidth = widthMode == MeasureSpec.AT_MOST
                    ? Math.min(widthSize, mMinWidth) : mMinWidth;
            if (widthMode != MeasureSpec.EXACTLY && mMinWidth > 0 && oldMeasuredWidth < targetWidth) {
                // Remeasure at exactly the minimum width.
                super.onMeasure(MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY),
                        heightMeasureSpec);
            }
            if (!textVisible && mIcon != null) {
                // TextView won't center compound drawables in both dimensions
                // without a little coercion. Pad in to center the icon after
                // we've measured.
                final int w = getMeasuredWidth();
                final int dw = mIcon.getBounds().width();
                super.setPadding((w - dw) / 2, getPaddingTop(), getPaddingRight(), getPaddingBottom());
            }
        }
    }

    /** ActionMenuPresenter.OverflowMenuButton (:846-900): an ImageButton in
     *  ?android:actionOverflowButtonStyle (Widget.Material.ActionButton.Overflow,
     *  whose android:src is the three-dot icon) that opens the overflow menu. */
    static final class OverflowMenuButton extends ImageButton {
        private final ActionMenuView mHost;
        OverflowMenuButton(Context context, ActionMenuView host) {
            super(context, null, ATTR_ACTION_OVERFLOW_BUTTON_STYLE);
            mHost = host;
            setClickable(true);
            setFocusable(true);
            setVisibility(VISIBLE);
            setEnabled(true);
        }
        @Override public boolean performClick() {
            if (super.performClick()) return true;
            mHost.showOverflowMenu();
            return true;
        }
    }

    // ---- MenuBuilder (com/android/internal/view/menu/MenuBuilder.java) ----

    static final class MenuImpl implements Menu {
        // MenuBuilder :48-67.
        private static final int USER_MASK = 0x0000ffff;
        private static final int CATEGORY_SHIFT = 16;
        private static final int[] sCategoryToOrder = new int[] {
            1, /* No category */
            4, /* CONTAINER */
            5, /* SYSTEM */
            3, /* SECONDARY */
            2, /* ALTERNATIVE */
            0, /* SELECTED_ALTERNATIVE */
        };

        final Context mContext;
        final ActionMenuView mView;
        final ArrayList<ItemImpl> items = new ArrayList<>();

        MenuImpl(Context context, ActionMenuView view) {
            mContext = context;
            mView = view;
        }

        void onItemsChanged() { mView.macapkOnItemsChanged(); }

        List<ItemImpl> visibleItems() {
            final ArrayList<ItemImpl> out = new ArrayList<>();
            for (ItemImpl it : items) if (it.isVisible()) out.add(it);
            return out;
        }

        /** MenuBuilder.getOrdering (:733-741). */
        private static int getOrdering(int categoryOrder) {
            final int index = (categoryOrder & CATEGORY_MASK) >> CATEGORY_SHIFT;
            if (index < 0 || index >= sCategoryToOrder.length) {
                throw new IllegalArgumentException("order does not contain a valid category.");
            }
            return (sCategoryToOrder[index] << CATEGORY_SHIFT) | (categoryOrder & USER_MASK);
        }

        /** MenuBuilder.findInsertIndex (:800-809). */
        private static int findInsertIndex(ArrayList<ItemImpl> items, int ordering) {
            for (int i = items.size() - 1; i >= 0; i--) {
                if (items.get(i).mOrdering <= ordering) return i + 1;
            }
            return 0;
        }

        /** MenuBuilder.addInternal (:422-433); a MenuBuilder's default
         *  showAsAction is SHOW_AS_ACTION_NEVER. */
        private MenuItem addInternal(int group, int id, int categoryOrder, CharSequence title) {
            final int ordering = getOrdering(categoryOrder);
            final ItemImpl item = new ItemImpl(this, group, id, categoryOrder, ordering, title);
            items.add(findInsertIndex(items, ordering), item);
            onItemsChanged();
            return item;
        }

        @Override public MenuItem add(CharSequence title) { return addInternal(0, 0, 0, title); }
        @Override public MenuItem add(int titleRes) {
            return addInternal(0, 0, 0, mContext.getResources().getString(titleRes));
        }
        @Override public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
            return addInternal(groupId, itemId, order, title);
        }
        @Override public MenuItem add(int groupId, int itemId, int order, int titleRes) {
            return addInternal(groupId, itemId, order, mContext.getResources().getString(titleRes));
        }
        @Override public int size() { return items.size(); }
        @Override public MenuItem getItem(int index) { return items.get(index); }
        /** MenuBuilder.findItem (:650-668), submenus included. */
        @Override public MenuItem findItem(int id) {
            for (ItemImpl item : items) {
                if (item.getItemId() == id) return item;
                if (item.hasSubMenu()) {
                    MenuItem possible = item.getSubMenu().findItem(id);
                    if (possible != null) return possible;
                }
            }
            return null;
        }
        @Override public void clear() {
            items.clear();
            onItemsChanged();
        }
        @Override public void close() { mView.hideOverflowMenu(); }
        /** MenuBuilder.removeItem (:520-522). */
        @Override public void removeItem(int id) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getItemId() == id) {
                    items.remove(i);
                    onItemsChanged();
                    return;
                }
            }
        }
        /** MenuBuilder.removeGroup (:524-538). */
        @Override public void removeGroup(int groupId) {
            boolean removed = false;
            for (int i = items.size() - 1; i >= 0; i--) {
                if (items.get(i).getGroupId() == groupId) { items.remove(i); removed = true; }
            }
            if (removed) onItemsChanged();
        }
        /** MenuBuilder.hasVisibleItems (:637-648). */
        @Override public boolean hasVisibleItems() {
            for (ItemImpl item : items) if (item.isVisible()) return true;
            return false;
        }
        /** MenuBuilder.performIdentifierAction -> performItemAction(findItem(id)). */
        @Override public boolean performIdentifierAction(int id, int flags) {
            MenuItem it = findItem(id);
            return it instanceof ItemImpl && mView.macapkInvokeItem((ItemImpl) it);
        }
        /** MenuBuilder.setGroupVisible (:609-630): one update for the group. */
        @Override public void setGroupVisible(int group, boolean visible) {
            boolean changed = false;
            for (ItemImpl item : items) {
                if (item.getGroupId() == group && item.mVisible != visible) {
                    item.mVisible = visible;
                    changed = true;
                }
            }
            if (changed) onItemsChanged();
        }
        @Override public void setGroupEnabled(int group, boolean enabled) {
            for (ItemImpl item : items) if (item.getGroupId() == group) item.setEnabled(enabled);
        }
        @Override public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
            for (ItemImpl item : items) {
                if (item.getGroupId() == group) {
                    item.mExclusive = exclusive;
                    item.setCheckable(checkable);
                }
            }
        }

        /** MenuBuilder.setExclusiveItemChecked (:581-600). */
        void setExclusiveItemChecked(ItemImpl checked) {
            final int group = checked.getGroupId();
            for (ItemImpl cur : items) {
                if (cur.getGroupId() == group && cur.mExclusive && cur.isCheckable()) {
                    cur.mChecked = cur == checked;
                }
            }
            onItemsChanged();
        }
    }

    // ---- MenuItemImpl (com/android/internal/view/menu/MenuItemImpl.java) ----

    static final class ItemImpl implements MenuItem {
        final MenuImpl mMenu;
        private final int mGroup;
        private final int mId;
        private final int mCategoryOrder;
        final int mOrdering;
        private CharSequence mTitle;
        private CharSequence mTitleCondensed;
        private Drawable mIconDrawable;
        private int mIconResId;
        private android.content.res.ColorStateList mIconTintList;
        private android.graphics.PorterDuff.Mode mIconTintMode;
        private boolean mHasIconTint;
        private boolean mHasIconTintMode;
        private boolean mNeedToApplyIconTint;
        android.content.Intent mIntent;
        private char mShortcutNumericChar;
        private char mShortcutAlphabeticChar;
        boolean mVisible = true;
        private boolean mEnabled = true;
        private boolean mCheckable;
        boolean mChecked;
        boolean mExclusive;
        private int mShowAsAction = SHOW_AS_ACTION_NEVER;
        private View mActionView;
        android.view.ActionProvider mActionProvider;
        MenuItem.OnMenuItemClickListener mClickListener;
        private CharSequence mContentDescription;
        private CharSequence mTooltipText;
        boolean mIsActionButton;

        ItemImpl(MenuImpl menu, int group, int id, int categoryOrder, int ordering,
                 CharSequence title) {
            mMenu = menu;
            mGroup = group;
            mId = id;
            mCategoryOrder = categoryOrder;
            mOrdering = ordering;
            mTitle = title;
        }

        private void changed() { mMenu.onItemsChanged(); }

        boolean requestsActionButton() {
            return (mShowAsAction & SHOW_AS_ACTION_IF_ROOM) == SHOW_AS_ACTION_IF_ROOM;
        }
        boolean requiresActionButton() {
            return (mShowAsAction & SHOW_AS_ACTION_ALWAYS) == SHOW_AS_ACTION_ALWAYS;
        }
        boolean showsTextAsAction() {
            return (mShowAsAction & SHOW_AS_ACTION_WITH_TEXT) == SHOW_AS_ACTION_WITH_TEXT;
        }

        @Override public int getItemId() { return mId; }
        @Override public int getGroupId() { return mGroup; }
        /** MenuItemImpl.getOrder: the category order as passed to add(). */
        @Override public int getOrder() { return mCategoryOrder; }

        @Override public CharSequence getTitle() { return mTitle; }
        @Override public MenuItem setTitle(CharSequence title) { mTitle = title; changed(); return this; }
        @Override public MenuItem setTitle(int titleRes) {
            return setTitle(mMenu.mContext.getString(titleRes));
        }
        @Override public CharSequence getTitleCondensed() {
            return mTitleCondensed != null ? mTitleCondensed : mTitle;
        }
        @Override public MenuItem setTitleCondensed(CharSequence title) {
            mTitleCondensed = title;
            changed();
            return this;
        }

        /** MenuItemImpl.getIcon (:471-484) + applyIconTintIfNecessary (:540-555). */
        @Override public Drawable getIcon() {
            if (mIconDrawable != null) return applyIconTintIfNecessary(mIconDrawable);
            if (mIconResId != 0) {
                Drawable icon = null;
                try { icon = mMenu.mContext.getDrawable(mIconResId); } catch (Throwable ignored) {}
                mIconResId = 0;
                mIconDrawable = icon;
                return applyIconTintIfNecessary(icon);
            }
            return null;
        }
        private Drawable applyIconTintIfNecessary(Drawable icon) {
            if (icon != null && mNeedToApplyIconTint && (mHasIconTint || mHasIconTintMode)) {
                icon = icon.mutate();
                if (mHasIconTint) icon.setTintList(mIconTintList);
                if (mHasIconTintMode) icon.setTintMode(mIconTintMode);
                mNeedToApplyIconTint = false;
                mIconDrawable = icon;
            }
            return icon;
        }
        @Override public MenuItem setIcon(Drawable icon) {
            mIconResId = 0;
            mIconDrawable = icon;
            mNeedToApplyIconTint = true;
            changed();
            return this;
        }
        @Override public MenuItem setIcon(int iconRes) {
            mIconDrawable = null;
            mIconResId = iconRes;
            mNeedToApplyIconTint = true;
            changed();
            return this;
        }
        @Override public MenuItem setIconTintList(android.content.res.ColorStateList tint) {
            mIconTintList = tint;
            mHasIconTint = true;
            mNeedToApplyIconTint = true;
            changed();
            return this;
        }
        @Override public android.content.res.ColorStateList getIconTintList() { return mIconTintList; }
        @Override public MenuItem setIconTintMode(android.graphics.PorterDuff.Mode mode) {
            mIconTintMode = mode;
            mHasIconTintMode = true;
            mNeedToApplyIconTint = true;
            changed();
            return this;
        }
        @Override public android.graphics.PorterDuff.Mode getIconTintMode() { return mIconTintMode; }

        @Override public MenuItem setIntent(android.content.Intent intent) { mIntent = intent; return this; }
        @Override public android.content.Intent getIntent() { return mIntent; }

        @Override public MenuItem setShortcut(char numericChar, char alphaChar) {
            mShortcutNumericChar = numericChar;
            mShortcutAlphabeticChar = Character.toLowerCase(alphaChar);
            changed();
            return this;
        }
        @Override public MenuItem setNumericShortcut(char numericChar) {
            mShortcutNumericChar = numericChar;
            changed();
            return this;
        }
        @Override public MenuItem setAlphabeticShortcut(char alphaChar) {
            mShortcutAlphabeticChar = Character.toLowerCase(alphaChar);
            changed();
            return this;
        }
        @Override public char getNumericShortcut() { return mShortcutNumericChar; }
        @Override public char getAlphabeticShortcut() { return mShortcutAlphabeticChar; }

        @Override public MenuItem setCheckable(boolean checkable) {
            if (mCheckable != checkable) { mCheckable = checkable; changed(); }
            return this;
        }
        @Override public boolean isCheckable() { return mCheckable; }
        /** MenuItemImpl.setChecked: an exclusive group unchecks its siblings. */
        @Override public MenuItem setChecked(boolean checked) {
            if (mExclusive && checked) {
                mMenu.setExclusiveItemChecked(this);
            } else if (mChecked != checked) {
                mChecked = checked;
                changed();
            }
            return this;
        }
        @Override public boolean isChecked() { return mChecked; }

        @Override public boolean isVisible() { return mVisible; }
        @Override public MenuItem setVisible(boolean visible) {
            if (mVisible != visible) { mVisible = visible; changed(); }
            return this;
        }
        @Override public boolean isEnabled() { return mEnabled; }
        @Override public MenuItem setEnabled(boolean enabled) {
            mEnabled = enabled;
            changed();
            return this;
        }

        @Override public MenuItem setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener l) {
            mClickListener = l;
            return this;
        }

        @Override public MenuItem setActionProvider(android.view.ActionProvider p) {
            mActionProvider = p;
            changed();
            return this;
        }
        @Override public android.view.ActionProvider getActionProvider() { return mActionProvider; }
        @Override public MenuItem setActionView(View view) {
            mActionView = view;
            if (view != null && view.getId() == View.NO_ID && mId > 0) view.setId(mId);
            changed();
            return this;
        }
        @Override public MenuItem setActionView(int resId) {
            View v = null;
            try {
                v = android.view.LayoutInflater.from(mMenu.mContext).inflate(resId, null, false);
            } catch (Throwable ignored) {}
            return setActionView(v);
        }
        @Override public View getActionView() { return mActionView; }

        /** MenuItemImpl.setShowAsAction (:698-713): the three base modes are
         *  mutually exclusive. */
        @Override public void setShowAsAction(int actionEnum) {
            switch (actionEnum & 0x3 /* SHOW_AS_ACTION_MASK */) {
                case SHOW_AS_ACTION_ALWAYS:
                case SHOW_AS_ACTION_IF_ROOM:
                case SHOW_AS_ACTION_NEVER:
                    break;
                default:
                    throw new IllegalArgumentException("SHOW_AS_ACTION_ALWAYS, SHOW_AS_ACTION_IF_ROOM,"
                            + " and SHOW_AS_ACTION_NEVER are mutually exclusive.");
            }
            mShowAsAction = actionEnum;
            changed();
        }
        @Override public MenuItem setShowAsActionFlags(int actionEnum) {
            setShowAsAction(actionEnum);
            return this;
        }

        @Override public MenuItem setContentDescription(CharSequence d) {
            mContentDescription = d;
            changed();
            return this;
        }
        @Override public CharSequence getContentDescription() { return mContentDescription; }
        @Override public MenuItem setTooltipText(CharSequence t) {
            mTooltipText = t;
            changed();
            return this;
        }
        @Override public CharSequence getTooltipText() { return mTooltipText; }

        @Override public String toString() { return mTitle != null ? mTitle.toString() : null; }
    }
}
