// android.view.MenuInflater -- parse a menu resource and populate
// the given Menu. F-Droid's MainViewAdapter (NOT SupportMenuInflater)
// hits this path; it does `menuInflater.inflate(R.menu.x, popupMenu
// .getMenu())` and counts items to drive the RecyclerView tab pump.
// Without a real impl, the menu stays empty, getItemCount() returns
// 0, and the RV renders no children.
//
// We parse the apktool-decoded XML directly via DocumentBuilder
// (same path our LayoutInflater uses) and call menu.add for each
// <item>. Title strings are resolved via Resources.getString when
// they carry `@string/foo`; IDs via Resources.getIdentifier on
// `@id/foo` / `@+id/foo`.
//
// Generic across APKs. v1.0.97.
package android.view;

import android.content.Context;
import android.content.res.Resources;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.InputStream;

public class MenuInflater {
    private static final String TAG = "Mac.apk.MenuInflater";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private final Context context;

    public MenuInflater(Context ctx) { this.context = ctx; }

    public void inflate(int menuRes, Menu menu) {
        if (menu == null || menuRes == 0 || context == null) return;
        Resources r = context.getResources();
        if (r == null) return;
        InputStream in = null;
        try {
            in = r.openRawResource(menuRes);
            if (in == null) return;
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            DocumentBuilder db = f.newDocumentBuilder();
            Document doc = db.parse(in);
            Element root = doc.getDocumentElement();
            if (root == null) return;
            parseMenuChildren(root, menu, r);
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.w(TAG,
                "inflate(0x" + Integer.toHexString(menuRes) + ") failed: " + t);
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private void parseMenuChildren(Element parent, Menu menu, Resources r) {
        NodeList kids = parent.getChildNodes();
        int order = 0;
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (!(n instanceof Element)) continue;
            Element e = (Element) n;
            String tag = e.getTagName();
            if ("item".equals(tag)) {
                int id = parseIdAttr(e, r);
                CharSequence title = parseTitleAttr(e, r);
                int order_ = parseIntAttr(e, "orderInCategory", order);

                // AOSP menu XML nests a <menu> element INSIDE <item> to declare
                // that item's submenu (frameworks/base MenuInflater.parseMenu:
                // hitting XML_MENU while an item is pending calls
                // menuState.addSubMenuItem(), which commits the pending item via
                // Menu.addSubMenu(...) instead of add(...), then recurses
                // parseMenu into the returned SubMenu). Our DOM walk sees the
                // same nesting as a <menu> child element of this <item>.
                Element nestedMenu = findChildElement(e, "menu");
                MenuItem item;
                if (nestedMenu != null) {
                    SubMenu subMenu = menu.addSubMenu(0, id, order_, title);
                    item = subMenu.getItem();
                    com.macapk.loader.NativeLog.i(TAG,
                        "added submenu id=0x" + Integer.toHexString(id)
                        + " order=" + order_ + " title='" + title + "'");
                    parseMenuChildren(nestedMenu, subMenu, r);
                } else {
                    item = menu.add(0, id, order_, title);
                    com.macapk.loader.NativeLog.i(TAG,
                        "added item id=0x" + Integer.toHexString(id)
                        + " order=" + order_ + " title='" + title + "'");
                }
                if (item != null) {
                    String visible = e.getAttributeNS(ANDROID_NS, "visible");
                    if ("false".equals(visible)) item.setVisible(false);
                    String enabled = e.getAttributeNS(ANDROID_NS, "enabled");
                    if ("false".equals(enabled)) item.setEnabled(false);
                    String checkable = e.getAttributeNS(ANDROID_NS, "checkable");
                    if ("true".equals(checkable)) item.setCheckable(true);
                    String checked = e.getAttributeNS(ANDROID_NS, "checked");
                    if ("true".equals(checked)) item.setChecked(true);
                    // AOSP readItem(): the toolbar-placement hint that
                    // SearchView / ShareActionProvider-style action items rely
                    // on. android.view.MenuInflater (this class, NOT AndroidX's
                    // SupportMenuInflater) only ever reads the android:
                    // namespace on real Android -- app:showAsAction is an
                    // AndroidX convention read by AndroidX's OWN inflater,
                    // which ships as real bytecode inside any APK that uses it
                    // and never calls into this framework class.
                    String showAsAction = e.getAttributeNS(ANDROID_NS, "showAsAction");
                    if (showAsAction != null && !showAsAction.isEmpty()) {
                        item.setShowAsAction(parseShowAsActionFlags(showAsAction));
                    }
                    // AOSP readItem(): actionViewClass / actionProviderClass,
                    // each a fully-qualified class name with a (Context)
                    // constructor, reflectively instantiated and attached --
                    // the XML-declaration path for SearchView /
                    // ShareActionProvider on a toolbar item.
                    String actionViewClass = e.getAttributeNS(ANDROID_NS, "actionViewClass");
                    if (actionViewClass != null && !actionViewClass.isEmpty()) {
                        Object view = newInstance(actionViewClass);
                        if (view instanceof android.view.View) {
                            item.setActionView((android.view.View) view);
                        }
                    }
                    String actionProviderClass = e.getAttributeNS(ANDROID_NS, "actionProviderClass");
                    if (actionProviderClass != null && !actionProviderClass.isEmpty()) {
                        Object provider = newInstance(actionProviderClass);
                        // AOSP actionProviderClass names a top-level
                        // android.view.ActionProvider subclass (e.g. ShareActionProvider).
                        if (provider instanceof android.view.ActionProvider) {
                            item.setActionProvider((android.view.ActionProvider) provider);
                        }
                    }
                    // v1.0.99: parse android:icon = @drawable/foo
                    String iconRef = e.getAttributeNS(ANDROID_NS, "icon");
                    if (iconRef != null && !iconRef.isEmpty()) {
                        int iconId = resolveResourceRef(iconRef, "drawable", r);
                        // v1.0.100: trace icon resolution per item so we can
                        // tell visual gaps (missing icons in bottom nav) from
                        // resource-resolution gaps (id=0 means name not found)
                        // vs drawable-decoding gaps (id present but getDrawable
                        // threw / returned null).
                        if (iconId == 0) {
                            com.macapk.loader.NativeLog.i("MenuInflater",
                                "icon='" + iconRef + "' -> id=0 (name not resolved)");
                        } else {
                            try {
                                android.graphics.drawable.Drawable d = r.getDrawable(iconId);
                                if (d != null) {
                                    item.setIcon(d);
                                    com.macapk.loader.NativeLog.i("MenuInflater",
                                        "icon='" + iconRef + "' -> id=0x"
                                        + Integer.toHexString(iconId) + " -> "
                                        + d.getClass().getSimpleName());
                                } else {
                                    com.macapk.loader.NativeLog.i("MenuInflater",
                                        "icon='" + iconRef + "' -> id=0x"
                                        + Integer.toHexString(iconId) + " -> null Drawable");
                                }
                            } catch (Throwable t) {
                                com.macapk.loader.NativeLog.i("MenuInflater",
                                    "icon='" + iconRef + "' -> id=0x"
                                    + Integer.toHexString(iconId) + " threw " + t);
                            }
                        }
                    }
                }
                order++;
            } else if ("group".equals(tag)) {
                parseMenuChildren(e, menu, r);
            }
        }
    }

    private int parseIdAttr(Element e, Resources r) {
        String v = e.getAttributeNS(ANDROID_NS, "id");
        if (v == null || v.isEmpty()) return 0;
        return resolveResourceRef(v, "id", r);
    }

    private CharSequence parseTitleAttr(Element e, Resources r) {
        String v = e.getAttributeNS(ANDROID_NS, "title");
        if (v == null || v.isEmpty()) return "";
        if (v.startsWith("@string/") || v.startsWith("@android:string/")) {
            int id = resolveResourceRef(v, "string", r);
            if (id != 0) {
                try { return r.getString(id); } catch (Throwable ignored) {}
            }
            return "";
        }
        return v;
    }

    private int parseIntAttr(Element e, String name, int def) {
        String v = e.getAttributeNS(ANDROID_NS, name);
        if (v == null || v.isEmpty()) return def;
        try { return Integer.parseInt(v); } catch (NumberFormatException nfe) {}
        return def;
    }

    /** First DIRECT child element named `tagName`, or null. (Not
     *  Element.getElementsByTagName -- that searches the whole descendant
     *  subtree, which would wrongly match a <menu> nested two levels down
     *  inside a grandchild submenu instead of only this item's own.) */
    private static Element findChildElement(Element parent, String tagName) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element && tagName.equals(((Element) n).getTagName())) {
                return (Element) n;
            }
        }
        return null;
    }

    /** AOSP android:showAsAction is a flag attribute; apktool's XML decode
     *  renders it back as symbolic names (possibly '|'-combined, e.g.
     *  "ifRoom|withText") rather than the compiled integer. Mirrors
     *  MenuItem.SHOW_AS_ACTION_* constants. */
    private static int parseShowAsActionFlags(String value) {
        int flags = 0;
        for (String part : value.split("\\|")) {
            String p = part.trim();
            if ("never".equals(p)) flags |= MenuItem.SHOW_AS_ACTION_NEVER;
            else if ("ifRoom".equals(p)) flags |= MenuItem.SHOW_AS_ACTION_IF_ROOM;
            else if ("always".equals(p)) flags |= MenuItem.SHOW_AS_ACTION_ALWAYS;
            else if ("withText".equals(p)) flags |= MenuItem.SHOW_AS_ACTION_WITH_TEXT;
            else if ("collapseActionView".equals(p)) flags |= MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW;
            else {
                // Numeric fallback, in case apktool left an unrecognized flag
                // as a raw integer string rather than a symbolic name.
                try { flags |= Integer.parseInt(p); } catch (NumberFormatException ignored) {}
            }
        }
        return flags;
    }

    /** AOSP MenuInflater.newInstance: reflectively construct an
     *  actionViewClass/actionProviderClass naming a (Context) constructor
     *  (frameworks/base MenuInflater.java's ACTION_VIEW_CONSTRUCTOR_SIGNATURE
     *  / ACTION_PROVIDER_CONSTRUCTOR_SIGNATURE are both just {Context.class}).
     *  Scoped try/catch per item -- one bad class name shouldn't drop the
     *  rest of the menu (mirrors the per-icon resolve pattern below). */
    private Object newInstance(String className) {
        try {
            ClassLoader cl = context.getClassLoader();
            Class<?> clazz = Class.forName(className, false, cl);
            java.lang.reflect.Constructor<?> ctor = clazz.getConstructor(Context.class);
            return ctor.newInstance(context);
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.w(TAG, "newInstance(" + className + ") failed: " + t);
            return null;
        }
    }

    /** Parse `@[+][pkg:]<type>/<name>` -> int ID via Resources.getIdentifier. */
    private static int resolveResourceRef(String ref, String defaultType, Resources r) {
        if (ref == null || ref.isEmpty() || ref.charAt(0) != '@') return 0;
        String body = ref.substring(1);
        if (body.startsWith("+")) body = body.substring(1);
        int colon = body.indexOf(':');
        if (colon >= 0) body = body.substring(colon + 1);
        String type = defaultType;
        String name = body;
        int slash = body.indexOf('/');
        if (slash > 0) {
            type = body.substring(0, slash);
            name = body.substring(slash + 1);
        }
        try { return r.getIdentifier(name, type, null); }
        catch (Throwable t) { return 0; }
    }
}
