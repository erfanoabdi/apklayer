// android.view.CollapsibleActionView -- implemented by a View that is used as
// a MenuItem action view, so it is told when the host menu item expands or
// collapses it. SearchView is the canonical implementer. AOSP:
//   frameworks/base/core/java/android/view/CollapsibleActionView.java
//   public interface CollapsibleActionView { ... }
//
// This MUST be an interface. MenuItemImpl.expandActionView() /
// collapseActionView() (and AppCompat's equivalent) gate the notification on
// `actionView instanceof CollapsibleActionView` before downcasting, so a
// placeholder CLASS here -- which no View subclass can ever implement, since
// Java has no multiple inheritance -- makes that check permanently false and
// the toolbar search-icon expand/collapse lifecycle silently never fires.
// v1.0.92 shipped it as an empty class; that was the bug.
package android.view;

public interface CollapsibleActionView {
    /**
     * Called when this view is expanded as an action view.
     * See {@link MenuItem#expandActionView()}.
     */
    public void onActionViewExpanded();

    /**
     * Called when this view is collapsed as an action view.
     * See {@link MenuItem#collapseActionView()}.
     */
    public void onActionViewCollapsed();
}
