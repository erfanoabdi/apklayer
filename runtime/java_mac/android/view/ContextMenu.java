package android.view;
public interface ContextMenu extends Menu {
    interface ContextMenuInfo {}
    ContextMenu setHeaderTitle(int titleRes);
    ContextMenu setHeaderTitle(CharSequence title);
    ContextMenu setHeaderIcon(int iconRes);
    ContextMenu setHeaderIcon(android.graphics.drawable.Drawable icon);
    void clearHeader();
    /** AOSP ContextMenu.setHeaderView(View): replace the whole header with a
     *  custom view. Declared on the interface alongside the other setHeader*
     *  members, all of which return `this` for chaining. */
    ContextMenu setHeaderView(android.view.View view);
}
