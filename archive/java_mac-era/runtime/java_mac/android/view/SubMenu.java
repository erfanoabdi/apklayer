// android.view.SubMenu -- a menu nested inside a MenuItem. AOSP
// declares it as an interface extending Menu. Material's MenuBuilder
// uses it for the sub-menu chain (e.g. overflow sub-menus); the
// methods are stubbed to satisfy the verifier.
package android.view;

public interface SubMenu extends Menu {
    SubMenu setHeaderTitle(int titleRes);
    SubMenu setHeaderTitle(CharSequence title);
    SubMenu setHeaderIcon(int iconRes);
    SubMenu setHeaderIcon(android.graphics.drawable.Drawable icon);
    SubMenu setHeaderView(View view);
    void clearHeader();
    SubMenu setIcon(int iconRes);
    SubMenu setIcon(android.graphics.drawable.Drawable icon);
    MenuItem getItem();
}
