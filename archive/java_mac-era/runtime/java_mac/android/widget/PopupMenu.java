// android.widget.PopupMenu -- API 11+ contextual popup menu, anchored
// to a View. F-Droid's main UI uses PopupMenu in a few places (overflow
// menu on app rows, etc.). Mac.apk has no popup-menu UI primitive yet;
// this stub exists so MainActivity.onCreate can construct PopupMenu
// references without NoClassDefFoundError on first access. show/dismiss
// are no-ops -- the menu never appears, but the activity init reaches
// completion.
package android.widget;

import android.content.Context;
import android.view.MenuInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

public class PopupMenu {
    private final Context context;
    private final View anchor;
    private final Menu menu;

    public PopupMenu(Context context, View anchor) {
        this(context, anchor, 0);
    }
    public PopupMenu(Context context, View anchor, int gravity) {
        this(context, anchor, gravity, 0, 0);
    }
    public PopupMenu(Context context, View anchor, int gravity,
                      int popupStyleAttr, int popupStyleRes) {
        this.context = context;
        this.anchor = anchor;
        this.menu = new MenuStub();
    }

    public Menu getMenu() { return menu; }
    public MenuInflater getMenuInflater() { return new MenuInflater(context); }
    /** AOSP PopupMenu.inflate: `getMenuInflater().inflate(menuRes, mMenu);`
     *  This was an empty method, so `popupMenu.inflate(R.menu.x)` -- the shorter
     *  of the two idioms, and the one most apps use -- produced an empty menu.
     *  Apps using the longer form (getMenuInflater().inflate(res, pm.getMenu()))
     *  already worked, which is why the gap survived. */
    public void inflate(int menuRes) {
        try { getMenuInflater().inflate(menuRes, menu); } catch (Throwable ignored) { }
    }

    private OnMenuItemClickListener mClickListener;
    private OnDismissListener mDismissListener;
    private PopupWindow mPopup;

    /** AOSP PopupMenu.show() delegates to MenuPopupHelper, which puts a
     *  MenuPopupWindow (a ListPopupWindow) on screen anchored to mAnchor. This
     *  was `public void show() {}`, so no PopupMenu in any app ever appeared.
     *
     *  What this builds: a real PopupWindow (now a real overlay layer, see
     *  PopupWindow's own notes) containing one clickable row per VISIBLE menu
     *  item, anchored under the anchor view. Row geometry follows AOSP's
     *  `abc_popup_menu_item_layout` / Widget.Material.PopupMenu metrics -- 48dp
     *  minimum row height, 16dp horizontal padding, textAppearanceLargePopupMenu
     *  at 16sp -- and the two colours come from the app's own theme through
     *  Resources.macapkThemeColor, the same route ProgressBar and AlertDialog use.
     *
     *  SIMPLIFICATION, stated: AOSP backs the rows with a ListView + MenuAdapter.
     *  A LinearLayout is used here because a PopupMenu never exposes its list to
     *  the app, so the choice is not observable through the public API, and
     *  ListPopupWindow's WRAP_CONTENT path would size the menu to the ANCHOR's
     *  width (typically a 24dp overflow icon) rather than to its content.
     *  Not ported: submenus, icons, checkable items, and the item ripple. */
    public void show() {
        if (anchor == null || context == null) return;
        if (mPopup != null && mPopup.isShowing()) return;
        final java.util.List<MenuItem> visible = new java.util.ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem it = menu.getItem(i);
            if (it != null && it.isVisible()) visible.add(it);
        }
        if (visible.isEmpty()) return;

        float density = 1f;
        try { density = context.getResources().getDisplayMetrics().density; }
        catch (Throwable ignored) { }
        final int padH   = Math.round(16f * density);
        final int minRow = Math.round(48f * density);
        final int bg = android.content.res.Resources.macapkThemeColor(
                "colorBackground", 0xFFFAFAFA);
        final int fg = android.content.res.Resources.macapkThemeColor(
                "textColorPrimary", 0xDE000000);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(bg);
        for (final MenuItem item : visible) {
            TextView row = new TextView(context);
            row.setText(item.getTitle() == null ? "" : item.getTitle());
            row.setTextColor(fg);
            row.setTextSize(16f);
            row.setPadding(padH, 0, padH, 0);
            row.setMinHeight(minRow);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setEnabled(item.isEnabled());
            row.setClickable(item.isEnabled());
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    OnMenuItemClickListener l = mClickListener;
                    dismiss();
                    if (l != null) {
                        try { l.onMenuItemClick(item); } catch (Throwable ignored) { }
                    }
                }
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        PopupWindow p = new PopupWindow(context);
        p.setContentView(content);
        p.setWidth(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setFocusable(true);
        p.setOutsideTouchable(true);
        p.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override public void onDismiss() {
                mPopup = null;
                if (mDismissListener != null) {
                    try { mDismissListener.onDismiss(PopupMenu.this); } catch (Throwable ignored) { }
                }
            }
        });
        mPopup = p;
        // AOSP anchors the popup under the anchor; mGravity (setGravity) selects
        // which horizontal edge it aligns to, exactly as MenuPopupHelper passes
        // its own mDropDownGravity through to ListPopupWindow.
        p.showAsDropDown(anchor, 0, 0,
                mGravity == android.view.Gravity.NO_GRAVITY
                        ? (android.view.Gravity.TOP | android.view.Gravity.START)
                        : mGravity);
    }

    public void dismiss() {
        PopupWindow p = mPopup;
        if (p != null) p.dismiss();
        mPopup = null;
    }
    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        mClickListener = listener;
    }
    public void setOnDismissListener(OnDismissListener listener) {
        mDismissListener = listener;
    }
    // AOSP PopupMenu.setGravity/getGravity was `{}` / hardcoded 0 with no
    // backing field -- the real setter also delegates to the internal popup
    // (mPopup.setGravity(gravity)) so the value affects where the menu opens,
    // but this class's own show()/dismiss() are already stubs (no real popup
    // window is presented here), so this fix is scoped to the same
    // accessor-round-trip correctness as the rest of this class's stubs.
    private int mGravity = android.view.Gravity.NO_GRAVITY;
    public void setGravity(int gravity) { mGravity = gravity; }
    public int  getGravity() { return mGravity; }
    public void setForceShowIcon(boolean forceShowIcon) {}

    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }
    public interface OnDismissListener {
        void onDismiss(PopupMenu menu);
    }

    /** v1.0.97: real-storage Menu so MenuInflater.inflate populates
     *  it. F-Droid's MainViewAdapter does:
     *    PopupMenu pm = new PopupMenu(ctx, null);
     *    getMenuInflater().inflate(R.menu.main_activity_screens,
     *                               pm.getMenu());
     *    positionToId = new SparseIntArray(pm.getMenu().size());
     *    for (i = 0; i < pm.getMenu().size(); i++)
     *        positionToId.put(i, pm.getMenu().getItem(i).getItemId());
     *  RecyclerView.Adapter.getItemCount returns positionToId.size().
     *  With the prior no-op MenuStub, size() was always 0 so the
     *  RecyclerView never asked for any ViewHolders. */
    private static final class MenuStub implements Menu {
        private final java.util.List<MenuItem> items = new java.util.ArrayList<>();

        @Override public MenuItem add(CharSequence title) {
            return add(0, 0, items.size(), title);
        }
        @Override public MenuItem add(int titleRes) {
            return add(0, 0, items.size(), (CharSequence) null);
        }
        @Override public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
            MenuItemStub it = new MenuItemStub(itemId, order, title == null ? "" : title);
            items.add(it);
            return it;
        }
        @Override public MenuItem add(int groupId, int itemId, int order, int titleRes) {
            return add(groupId, itemId, order, (CharSequence) null);
        }
        @Override public int size() { return items.size(); }
        @Override public MenuItem getItem(int index) {
            if (index < 0 || index >= items.size()) return null;
            return items.get(index);
        }
        @Override public MenuItem findItem(int id) {
            for (MenuItem it : items) if (it.getItemId() == id) return it;
            return null;
        }
        @Override public void clear() { items.clear(); }
        @Override public void close() {}
        @Override public void removeItem(int id) {
            items.removeIf(it -> it.getItemId() == id);
        }
        @Override public void removeGroup(int groupId) {}
        @Override public boolean hasVisibleItems() {
            for (MenuItem it : items) if (it.isVisible()) return true;
            return false;
        }
    }

    /** Minimal MenuItem impl that PopupMenu's MenuStub uses. Stores
     *  the bits MainViewAdapter reads (id, title, visibility, etc).
     *  v1.0.97. */
    private static final class MenuItemStub implements MenuItem {
        private final int itemId;
        private final int order;
        private CharSequence title;
        private boolean visible = true;
        private boolean enabled = true;
        private boolean checkable;
        private boolean checked;
        private android.graphics.drawable.Drawable icon;

        MenuItemStub(int itemId, int order, CharSequence title) {
            this.itemId = itemId; this.order = order; this.title = title;
        }
        @Override public int getItemId() { return itemId; }
        @Override public int getOrder()  { return order; }
        @Override public CharSequence getTitle() { return title; }
        @Override public MenuItem setTitle(CharSequence t) { this.title = t; return this; }
        @Override public MenuItem setTitle(int titleRes) { return this; }
        @Override public boolean isVisible() { return visible; }
        @Override public MenuItem setVisible(boolean v) { this.visible = v; return this; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public MenuItem setEnabled(boolean e) { this.enabled = e; return this; }
        @Override public boolean isCheckable() { return checkable; }
        @Override public MenuItem setCheckable(boolean c) { this.checkable = c; return this; }
        @Override public boolean isChecked() { return checked; }
        @Override public MenuItem setChecked(boolean c) { this.checked = c; return this; }
        @Override public android.graphics.drawable.Drawable getIcon() { return icon; }
        @Override public MenuItem setIcon(android.graphics.drawable.Drawable d) { this.icon = d; return this; }
        @Override public MenuItem setIcon(int resId) { return this; }
        @Override public int getGroupId() { return 0; }
    }

    public android.view.View.OnTouchListener getDragToOpenListener() { return null; }
}

