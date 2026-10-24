// android.content.pm.ShortcutManager (API 25) -- app-shortcut registry. Apps
// obtain it via Context.getSystemService(SHORTCUT_SERVICE) and add/query
// dynamic shortcuts at boot (Crossy Road's "initialize-shortcuts" thread loads
// the class). Mac.apk surfaces no launcher shortcuts, so queries return empty
// and mutations are no-ops -- the same observable result as a launcher that
// doesn't support pinning. The class must exist or referencing code throws
// NoClassDefFoundError. List element type is ShortcutInfo in AOSP; we use raw
// java.util.List so the (erased) descriptors match without needing a
// ShortcutInfo class. Mirrors the AOSP public method surface.
package android.content.pm;

import java.util.Collections;
import java.util.List;

public class ShortcutManager {
    /** AOSP ShortcutManager.FLAG_MATCH_* (API 30): the filter bits for
     *  getShortcuts(int). Values verbatim -- they are the same numbering as
     *  LauncherApps.ShortcutQuery's, which is the launcher-side spelling of the
     *  same query. javac inlines them, so an app cannot observe their absence,
     *  but our own getShortcuts(int) and any reflective reader can. */
    public static final int FLAG_MATCH_MANIFEST = 1 << 0;
    public static final int FLAG_MATCH_DYNAMIC  = 1 << 1;
    public static final int FLAG_MATCH_PINNED   = 1 << 2;
    public static final int FLAG_MATCH_CACHED   = 1 << 3;

    public ShortcutManager() {}

    @SuppressWarnings("rawtypes")
    public List getDynamicShortcuts()  { return Collections.emptyList(); }
    @SuppressWarnings("rawtypes")
    public List getManifestShortcuts() { return Collections.emptyList(); }
    @SuppressWarnings("rawtypes")
    public List getPinnedShortcuts()   { return Collections.emptyList(); }

    @SuppressWarnings("rawtypes")
    public boolean setDynamicShortcuts(List shortcutInfoList) { return false; }
    @SuppressWarnings("rawtypes")
    public boolean addDynamicShortcuts(List shortcutInfoList) { return false; }
    @SuppressWarnings("rawtypes")
    public boolean updateShortcuts(List shortcutInfoList) { return false; }

    public void removeAllDynamicShortcuts() {}
    @SuppressWarnings("rawtypes")
    public void removeDynamicShortcuts(List shortcutIds) {}
    public void disableShortcuts(List<String> shortcutIds) {}
    public void enableShortcuts(List<String> shortcutIds) {}
    public void reportShortcutUsed(String shortcutId) {}

    public int getMaxShortcutCountPerActivity() { return 0; }
    public boolean isRateLimitingActive() { return false; }
    public int getRemainingCallCount() { return 0; }
    public long getRateLimitResetTime() { return 0L; }

    public boolean isRequestPinShortcutSupported() { return false; }

    /** AOSP requestPinShortcut (API 26): request the launcher pin a shortcut.
     *  Mac.apk has no launcher, so pinning is not supported -> false. */
    public boolean requestPinShortcut(ShortcutInfo shortcut, android.content.IntentSender resultIntent) { return false; }
    public android.content.Intent createShortcutResultIntent(ShortcutInfo shortcut) { return new android.content.Intent(); }
    public java.util.List<ShortcutInfo> getShortcuts(int matchFlags) { return java.util.Collections.emptyList(); }
    public int getIconMaxWidth() { return 0; }
    public int getIconMaxHeight() { return 0; }
    public void pushDynamicShortcut(ShortcutInfo shortcut) { }
    public void disableShortcuts(java.util.List<String> shortcutIds, CharSequence disabledMessage) { }
    public void removeLongLivedShortcuts(java.util.List<String> shortcutIds) { }
}

