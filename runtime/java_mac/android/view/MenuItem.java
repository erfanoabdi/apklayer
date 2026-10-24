// android.view.MenuItem -- a single item in a Menu. AOSP's interface
// is wide (~50 methods) because every aspect of menu items is exposed:
// title / icon / shortcut / checked state / sub-menu / action-view
// integration. Material widgets and AppCompat's MenuBuilder rely on
// the chain-call setX(...) -> MenuItem signature. We declare them all
// so consumers can fluent-chain without NoSuchMethodError; concrete
// MenuBuilder.MenuItemImpl implements the real behavior.
//
// v1.0.97: switched all methods to `default` no-ops so lightweight
// in-tree implementations (PopupMenu.MenuItemStub etc.) only need to
// override the handful of methods they care about. Concrete impls in
// the APK (e.g. androidx.appcompat.view.menu.MenuItemImpl) override
// the methods as before -- defaults only kick in for methods they
// don't define, matching AOSP-default behavior.
//
// AOSP source: frameworks/base/core/java/android/view/MenuItem.java
package android.view;

public interface MenuItem {
    // ID + grouping
    default int getItemId() { return 0; }
    default int getGroupId() { return 0; }
    default int getOrder()  { return 0; }

    // Title
    default CharSequence getTitle() { return null; }
    default CharSequence getTitleCondensed() { return null; }
    default MenuItem setTitle(CharSequence title) { return this; }
    default MenuItem setTitle(int titleRes) { return this; }
    default MenuItem setTitleCondensed(CharSequence title) { return this; }

    // Icon
    default android.graphics.drawable.Drawable getIcon() { return null; }
    default MenuItem setIcon(android.graphics.drawable.Drawable icon) { return this; }
    default MenuItem setIcon(int iconRes) { return this; }
    default MenuItem setIconTintList(android.content.res.ColorStateList iconTintList) { return this; }
    default android.content.res.ColorStateList getIconTintList() { return null; }
    default MenuItem setIconTintMode(android.graphics.PorterDuff.Mode mode) { return this; }
    /** AOSP MenuItem.setIconTintBlendMode(BlendMode) / getIconTintBlendMode()
     *  (API 29), the BlendMode twin of the PorterDuff pair above. AOSP declares
     *  both as interface DEFAULTS that no-op / return null, and MenuItemImpl
     *  overrides them -- reproduced exactly. */
    default MenuItem setIconTintBlendMode(android.graphics.BlendMode blendMode) { return this; }
    default android.graphics.BlendMode getIconTintBlendMode() { return null; }
    default android.graphics.PorterDuff.Mode getIconTintMode() { return null; }

    // Intent
    default MenuItem setIntent(android.content.Intent intent) { return this; }
    default android.content.Intent getIntent() { return null; }

    // Shortcut
    default MenuItem setShortcut(char numericChar, char alphaChar) { return this; }
    default MenuItem setNumericShortcut(char numericChar) { return this; }
    default MenuItem setAlphabeticShortcut(char alphaChar) { return this; }
    default char getNumericShortcut() { return 0; }
    default char getAlphabeticShortcut() { return 0; }

    // Modifier-aware shortcut overloads (API 26, desktop/DeX keyboard
    // shortcuts, e.g. Ctrl+Shift+letter). AOSP's own default bodies only
    // accept a modifier combination that reduces to plain Ctrl once masked to
    // the supported bits, and otherwise delegate to the 1-arg forms above --
    // reproduced verbatim below, including that validation, since Mac.apk
    // runs single-window with no multi-modifier chrome to honor a richer
    // modifier anyway.
    default MenuItem setShortcut(char numericChar, char alphaChar,
                                 int numericModifiers, int alphaModifiers) {
        if ((alphaModifiers & supportedShortcutModifiersMask()) == android.view.KeyEvent.META_CTRL_ON
                && (numericModifiers & supportedShortcutModifiersMask()) == android.view.KeyEvent.META_CTRL_ON) {
            return setShortcut(numericChar, alphaChar);
        }
        return this;
    }
    default MenuItem setNumericShortcut(char numericChar, int numericModifiers) {
        if ((numericModifiers & supportedShortcutModifiersMask()) == android.view.KeyEvent.META_CTRL_ON)
            return setNumericShortcut(numericChar);
        return this;
    }
    default MenuItem setAlphabeticShortcut(char alphaChar, int alphaModifiers) {
        if ((alphaModifiers & supportedShortcutModifiersMask()) == android.view.KeyEvent.META_CTRL_ON)
            return setAlphabeticShortcut(alphaChar);
        return this;
    }
    // AOSP default: both shortcut kinds report plain Ctrl as their modifier
    // when no richer one was recorded via the 2-arg setters above.
    default int getNumericModifiers() { return android.view.KeyEvent.META_CTRL_ON; }
    default int getAlphabeticModifiers() { return android.view.KeyEvent.META_CTRL_ON; }

    /** AOSP android.view.Menu.SUPPORTED_MODIFIERS_MASK, inlined here because
     *  this tree's Menu.java (a separate file/owner) does not declare it; the
     *  expression is pinned verbatim from AOSP's Menu.java. A private
     *  interface method (Java 9+) rather than a field so it does not add a
     *  constant to MenuItem's own public surface, which AOSP's real MenuItem
     *  does not have either -- SUPPORTED_MODIFIERS_MASK lives on Menu there. */
    private static int supportedShortcutModifiersMask() {
        return android.view.KeyEvent.META_META_ON | android.view.KeyEvent.META_CTRL_ON
                | android.view.KeyEvent.META_ALT_ON | android.view.KeyEvent.META_SHIFT_ON
                | android.view.KeyEvent.META_SYM_ON | android.view.KeyEvent.META_FUNCTION_ON;
    }

    // Checkable / checked
    default MenuItem setCheckable(boolean checkable) { return this; }
    default boolean isCheckable() { return false; }
    default MenuItem setChecked(boolean checked) { return this; }
    default boolean isChecked() { return false; }

    // Visibility / enabled
    default boolean isVisible() { return true; }
    default MenuItem setVisible(boolean visible) { return this; }
    default boolean isEnabled() { return true; }
    default MenuItem setEnabled(boolean enabled) { return this; }

    // Sub-menu
    default boolean hasSubMenu() { return false; }
    default SubMenu getSubMenu() { return null; }

    // Click handling
    interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }
    default MenuItem setOnMenuItemClickListener(OnMenuItemClickListener listener) { return this; }

    // ActionProvider / action view (API 14+)
    // AOSP's MenuItem.setActionProvider/getActionProvider use the TOP-LEVEL
    // android.view.ActionProvider (the nested ActionProvider below is a legacy
    // artifact). The corpus references the top-level type, so these must be
    // typed to it or the descriptor mismatches. FQN keeps the nested name from
    // shadowing.
    default MenuItem setActionProvider(android.view.ActionProvider actionProvider) { return this; }
    default android.view.ActionProvider getActionProvider() { return null; }
    default MenuItem setActionView(View view) { return this; }
    default MenuItem setActionView(int resId) { return this; }
    default View getActionView() { return null; }
    int SHOW_AS_ACTION_NEVER  = 0;
    int SHOW_AS_ACTION_IF_ROOM = 1;
    int SHOW_AS_ACTION_ALWAYS = 2;
    int SHOW_AS_ACTION_WITH_TEXT = 4;
    int SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW = 8;
    // AOSP: setShowAsAction returns VOID (only the Flags variant
    // chains). The MenuItem-returning form was a different descriptor
    // and threw NoSuchMethodError from menu inflation the moment the
    // namespace-aware attr lookup made `app:showAsAction` visible.
    default void setShowAsAction(int actionEnum) {}
    default MenuItem setShowAsActionFlags(int actionEnum) { return this; }

    // Tooltip + content description (API 26+)
    default MenuItem setTooltipText(CharSequence tooltipText) { return this; }
    default CharSequence getTooltipText() { return null; }
    default MenuItem setContentDescription(CharSequence contentDescription) { return this; }
    default CharSequence getContentDescription() { return null; }

    // Expand / collapse (action-view) flow
    default boolean expandActionView() { return false; }
    default boolean collapseActionView() { return false; }
    default boolean isActionViewExpanded() { return false; }
    default MenuItem setOnActionExpandListener(OnActionExpandListener listener) { return this; }

    interface OnActionExpandListener {
        boolean onMenuItemActionExpand(MenuItem item);
        boolean onMenuItemActionCollapse(MenuItem item);
    }

    /** AOSP MenuItem.ActionProvider base class -- subclasses provide
     *  custom action-bar items. ShareActionProvider is the canonical
     *  one. */
    abstract class ActionProvider {
        public android.view.View onCreateActionView() { return null; }
        public android.view.View onCreateActionView(MenuItem forItem) { return onCreateActionView(); }
        public boolean overridesItemVisibility() { return false; }
        public boolean isVisible() { return true; }
        public void refreshVisibility() {}
        public boolean hasSubMenu() { return false; }
        public boolean onPerformDefaultAction() { return false; }
        public void onPrepareSubMenu(SubMenu subMenu) {}
    }

    default android.view.ContextMenu.ContextMenuInfo getMenuInfo() { return null; }
}

