// android.view.Menu -- AOSP's menu-item container interface. v1.0.97
// established the "every method is `default`" pattern (see MenuItem.java)
// so lightweight in-tree implementors (PopupMenu.MenuStub) only need to
// override the handful of methods they actually back with storage; any
// concrete Menu supplied by the APK itself (e.g. androidx MenuBuilder)
// overrides everything as before. This pass adds the rest of the AOSP
// surface that was outright missing (addSubMenu / addIntentOptions /
// group visible-enabled-checkable / setQwertyMode / performShortcut /
// performIdentifierAction / isShortcutKey / setGroupDividerEnabled) as
// default bodies for the same reason: without them, ANY code holding a
// Menu-typed reference that calls one of these gets NoSuchMethodError at
// the invokeinterface site regardless of what the concrete backing object
// supports (JVMS 5.4.3.4 resolves against the DECLARED interface, not the
// runtime class).
//
// AOSP source: frameworks/base/core/java/android/view/Menu.java
package android.view;

public interface Menu {
    int NONE = 0;
    int FIRST = 1;
    int CATEGORY_MASK = 0xffff0000;

    MenuItem add(CharSequence title);
    MenuItem add(int titleRes);
    MenuItem add(int groupId, int itemId, int order, CharSequence title);
    MenuItem add(int groupId, int itemId, int order, int titleRes);
    int size();
    MenuItem getItem(int index);
    MenuItem findItem(int id);
    void clear();
    void close();
    void removeItem(int id);
    void removeGroup(int groupId);
    boolean hasVisibleItems();

    // -- addSubMenu: AOSP also inserts a child MenuItem (the submenu's own
    // entry point) into THIS menu via add(...), then returns a fresh
    // SubMenu for the caller to populate. A default method has no shared
    // backing store to reach into generically, so the returned SubMenu is
    // the small self-contained implementation below -- real enough that
    // .add(...)/.setHeaderTitle(...) chains on it work rather than NPE,
    // though it isn't wired back into the parent item's getSubMenu() /
    // hasSubMenu() unless the concrete Menu/MenuItem implementor also
    // supports that link (PopupMenu's does not).
    default SubMenu addSubMenu(CharSequence title) {
        return addSubMenu(NONE, NONE, size(), title);
    }
    default SubMenu addSubMenu(int titleRes) {
        return addSubMenu(NONE, NONE, size(), titleRes);
    }
    default SubMenu addSubMenu(int groupId, int itemId, int order, int titleRes) {
        return addSubMenu(groupId, itemId, order, (CharSequence) null);
    }
    default SubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
        MenuItem parentItem = add(groupId, itemId, order, title);
        return new SubMenuStub(parentItem);
    }

    // -- addIntentOptions: AOSP queries PackageManager for activities
    // matching `intent`/`specifics` and adds one MenuItem per match,
    // returning the count added. Mac.apk has no such query path exposed
    // to a generic interface default; the honest answer for "how many
    // matching activities did you find" without running one is zero,
    // matching the real contract's return value when nothing matches.
    default int addIntentOptions(int groupId, int itemId, int order,
            android.content.ComponentName caller, android.content.Intent[] specifics,
            android.content.Intent intent, int flags, MenuItem[] outSpecificItems) {
        return 0;
    }

    // -- group state: generic over any concrete Menu, since MenuItem's own
    // getGroupId()/setCheckable()/setEnabled()/setVisible() are already
    // default-backed (MenuItem.java v1.0.97). A concrete Menu whose
    // add(...) doesn't record the real groupId (a pre-existing, separate
    // gap in PopupMenu.MenuStub, which hardcodes getGroupId()==0) won't
    // have these actually target the right items, but the interface
    // contract itself is correct for any implementor that does track it
    // (SubMenuStub below does).
    default void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
        for (int i = 0; i < size(); i++) {
            MenuItem item = getItem(i);
            if (item != null && item.getGroupId() == group) item.setCheckable(checkable);
        }
    }
    default void setGroupEnabled(int group, boolean enabled) {
        for (int i = 0; i < size(); i++) {
            MenuItem item = getItem(i);
            if (item != null && item.getGroupId() == group) item.setEnabled(enabled);
        }
    }
    default void setGroupVisible(int group, boolean visible) {
        for (int i = 0; i < size(); i++) {
            MenuItem item = getItem(i);
            if (item != null && item.getGroupId() == group) item.setVisible(visible);
        }
    }
    // AOSP itself declares this one as an empty default (API 28+; older
    // Menu implementors that predate the divider feature need no behavior).
    default void setGroupDividerEnabled(boolean groupDividerEnabled) {}

    default void setQwertyMode(boolean isQwerty) {}
    default boolean performShortcut(int keyCode, KeyEvent event, int flags) { return false; }
    default boolean performIdentifierAction(int id, int flags) { return false; }
    default boolean isShortcutKey(int keyCode, KeyEvent event) { return false; }

    /** Minimal, self-contained SubMenu backing addSubMenu(...) above.
     *  Mirrors the existing PopupMenu.MenuStub precedent (a real
     *  list-backed Menu) but lives at the interface level so every Menu
     *  implementor gets a working addSubMenu for free. */
    final class SubMenuStub implements SubMenu {
        private final java.util.List<MenuItem> items = new java.util.ArrayList<>();
        private final MenuItem parentItem;

        SubMenuStub(MenuItem parentItem) { this.parentItem = parentItem; }

        @Override public MenuItem add(CharSequence title) {
            return add(NONE, NONE, items.size(), title);
        }
        @Override public MenuItem add(int titleRes) {
            return add(NONE, NONE, items.size(), (CharSequence) null);
        }
        @Override public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
            MenuItem item = new StubItem(groupId, itemId, order, title);
            items.add(item);
            return item;
        }
        @Override public MenuItem add(int groupId, int itemId, int order, int titleRes) {
            return add(groupId, itemId, order, (CharSequence) null);
        }
        @Override public int size() { return items.size(); }
        @Override public MenuItem getItem(int index) {
            return (index < 0 || index >= items.size()) ? null : items.get(index);
        }
        @Override public MenuItem findItem(int id) {
            for (MenuItem it : items) if (it.getItemId() == id) return it;
            return null;
        }
        @Override public void clear() { items.clear(); }
        @Override public void close() {}
        @Override public void removeItem(int id) { items.removeIf(it -> it.getItemId() == id); }
        @Override public void removeGroup(int groupId) { items.removeIf(it -> it.getGroupId() == groupId); }
        @Override public boolean hasVisibleItems() {
            for (MenuItem it : items) if (it.isVisible()) return true;
            return false;
        }

        @Override public SubMenu setHeaderTitle(int titleRes) { return this; }
        @Override public SubMenu setHeaderTitle(CharSequence title) { return this; }
        @Override public SubMenu setHeaderIcon(int iconRes) { return this; }
        @Override public SubMenu setHeaderIcon(android.graphics.drawable.Drawable icon) { return this; }
        @Override public SubMenu setHeaderView(View view) { return this; }
        @Override public void clearHeader() {}
        @Override public SubMenu setIcon(int iconRes) { return this; }
        @Override public SubMenu setIcon(android.graphics.drawable.Drawable icon) { return this; }
        @Override public MenuItem getItem() { return parentItem; }
    }

    /** Field-backed MenuItem for SubMenuStub's own items -- MenuItem's
     *  interface methods are all default (v1.0.97), so this only needs to
     *  override the accessors add(...) actually populates. */
    final class StubItem implements MenuItem {
        private final int groupId, itemId, order;
        private CharSequence title;
        private boolean visible = true;
        private boolean enabled = true;
        private boolean checkable;
        private boolean checked;

        StubItem(int groupId, int itemId, int order, CharSequence title) {
            this.groupId = groupId; this.itemId = itemId; this.order = order; this.title = title;
        }
        @Override public int getGroupId() { return groupId; }
        @Override public int getItemId() { return itemId; }
        @Override public int getOrder() { return order; }
        @Override public CharSequence getTitle() { return title; }
        @Override public MenuItem setTitle(CharSequence t) { title = t; return this; }
        @Override public MenuItem setTitle(int titleRes) { return this; }
        @Override public boolean isVisible() { return visible; }
        @Override public MenuItem setVisible(boolean v) { visible = v; return this; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public MenuItem setEnabled(boolean e) { enabled = e; return this; }
        @Override public boolean isCheckable() { return checkable; }
        @Override public MenuItem setCheckable(boolean c) { checkable = c; return this; }
        @Override public boolean isChecked() { return checked; }
        @Override public MenuItem setChecked(boolean c) { checked = c; return this; }
    }
}
