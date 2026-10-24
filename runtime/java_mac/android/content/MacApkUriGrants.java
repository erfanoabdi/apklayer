// android.content.MacApkUriGrants -- Mac.apk's in-process stand-in for the URI-grant
// half of system_server: UriGrantsManagerService plus its per-grant record
// com.android.server.uri.UriPermission (android14-release,
// services/core/java/com/android/server/uri/{UriGrantsManagerService,UriPermission}.java).
// Gate: MACAPK_PERSISTED_URI_GRANTS (default ON, v1.0.1654). With =0 nothing is
// recorded and ContentResolver's take/release/get methods are the pre-change no-ops.
//
// Why it exists. On a device, an ACTION_OPEN_DOCUMENT / OPEN_DOCUMENT_TREE result
// carries FLAG_GRANT_PERSISTABLE_URI_PERMISSION, and ActivityRecord.sendResult
// (services/core/java/com/android/server/wm/ActivityRecord.java:4761-4766) hands the
// result's URIs to UriGrantsManagerService.grantUriPermissionUncheckedFromIntent,
// which records a grant per URI with that flag. The app then calls
// ContentResolver.takePersistableUriPermission, and getPersistedUriPermissions lists
// it after every restart. Before this, all three were no-ops here
// (docs/shared-storage-research_2026-09-25.md:503): an app that re-lists its grants
// on relaunch saw none and asked the user to pick again.
//
// What is mirrored, with the AOSP lines:
//   * recording a grant from a result Intent: data + every ClipData item (and a
//     ClipData item's Intent, recursively), mode = the Intent's flags, PREFIX when
//     FLAG_GRANT_PREFIX_URI_PERMISSION is set
//     (UriGrantsManagerService.checkGrantUriPermissionFromIntentUnlocked :587-660,
//     GrantUri.resolve; UriPermission.grantModes :126-148);
//   * takePersistableUriPermission: flags must be a subset of READ|WRITE
//     (Preconditions.checkFlagsArgument, IllegalArgumentException), then an EXACT
//     and a PREFIX record are looked up for the same Uri, and the call needs one
//     whose persistable modes cover the request, else SecurityException
//     "No persistable permission grants found for UID <uid> and Uri <safe uri>"
//     (UGMS :346-393); the persisted time is System.currentTimeMillis() at each
//     take (UriPermission.takePersistableModes :154-174);
//   * the cap: MAX_PERSISTED_URI_GRANTS = 512, oldest-first pruning by persisted
//     time (UGMS :112, maybePrunePersistedUriGrantsLocked :558-583);
//   * releasePersistableUriPermission (UGMS :410-455; releasePersistableModes
//     :176-189; a record with no modes left is dropped);
//   * getPersistedUriPermissions = incoming, persistedOnly: every record with
//     persisted modes, as UriPermission(uri, persistedModeFlags, persistedCreateTime)
//     (UGMS :266-305, buildPersistedPublicApiObject :389-391);
//   * a provider revoking a document (DocumentsProvider.revokeDocumentPermission ->
//     Context.revokeUriPermission(uri, ~0)) drops every record whose Uri has the
//     revoked Uri as a PATH PREFIX, persisted modes included (UGMS
//     revokeUriPermissionLocked :873-900, UriPermission.revokeModes :194-238,
//     Uri.isPathPrefixMatch :2427-2444);
//   * only PERSISTED records survive a restart, reloaded as persistable = persisted
//     (UGMS readGrantedUriPermissionsLocked + UriPermission.initPersistedModes :115-124).
//     AOSP writes /data/system/urigrants.xml; ours is <dataDir>/.macapk/uri_grants.json,
//     written atomically.
//
// Deliberate divergences (labelled):
//   * ONE app, so no uid / package dimension: every record's target is this app.
//     getOutgoingPersistedUriPermissions (grants FROM this app to another) is
//     therefore always empty -- there is no other app a grant could go to.
//   * Access IS gated on these records since v1.0.1657 (MACAPK_URI_GRANT_ENFORCE,
//     default ON, needs this store): check() is UGMS.checkUriPermissionLocked, and it
//     answers ContentProvider.enforceRead/WritePermissionInner's "last chance" for a
//     provider that stands in for ANOTHER package (HostDocumentsProvider =
//     com.android.externalstorage) and Context.checkUriPermission and its family.
//     So a document URI the app stored without taking it no longer opens after a
//     restart, as on a device. With =0 access is decided by HostDocumentsProvider's
//     own per-pick capability (the minted id and its write bit), as before.
//   * A grant's non-persisted modes live as long as the process (AOSP ties them to
//     the receiving activity's UriPermissionOwner).
//   * No "N@" user-id prefix handling (ContentProvider.getUriWithoutUserId): one user.
package android.content;

import android.net.Uri;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class MacApkUriGrants {
    private MacApkUriGrants() {}

    private static final String TAG = "UriGrants";

    /** MACAPK_PERSISTED_URI_GRANTS, default ON. */
    public static final boolean ENABLED =
            !"0".equals(System.getenv("MACAPK_PERSISTED_URI_GRANTS"));

    /** MACAPK_URI_GRANT_ENFORCE, default ON (v1.0.1657): the records GATE access. It
     *  needs the records, so it is off whenever MACAPK_PERSISTED_URI_GRANTS=0 (there
     *  is nothing to consult). =0: nothing is checked against them, Context's URI
     *  permission checks answer GRANTED and HostDocumentsProvider's per-pick
     *  capability alone decides, exactly as before. */
    public static final boolean ENFORCE =
            ENABLED && !"0".equals(System.getenv("MACAPK_URI_GRANT_ENFORCE"));

    /** UriGrantsManagerService.java:112. */
    public static final int MAX_PERSISTED_URI_GRANTS = 512;

    private static final int RW = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    /** com.android.server.uri.UriPermission, reduced to one target. Split into
     *  owned/global (android14-release :64-73, quoted verbatim above the class
     *  javadoc) since review issue 2 (2026-09-28): a caller-side
     *  Context.revokeUriPermission must drop only the OWNERLESS half
     *  (revokeUriPermissionLocked's "revoke any ownerless URI permissions"
     *  branch, :838-861), leaving a picker-result grant alone. */
    private static final class Perm {
        final Uri uri;
        final boolean prefix;
        /** Allowed modes with an active owner (grantFromIntent: a picker result,
         *  AOSP's ActivityRecord owner). */
        int ownedModeFlags;
        /** Allowed modes without an explicit owner (grantFromProvider:
         *  AMS.grantUriPermission :6401-6431 passes owner=null). */
        int globalModeFlags;
        int persistableModeFlags;
        int persistedModeFlags;
        long persistedCreateTime = UriPermission.INVALID_TIME;

        Perm(Uri uri, boolean prefix) { this.uri = uri; this.prefix = prefix; }

        int modeFlags() { return ownedModeFlags | globalModeFlags | persistedModeFlags; }

        /** UriPermission.grantModes :126-148 (quoted above): owner==null sets
         *  globalModeFlags, a non-null owner sets ownedModeFlags. */
        void grantModes(int modeFlags, boolean owned) {
            final boolean persistable = (modeFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0;
            modeFlags &= RW;
            if (persistable) persistableModeFlags |= modeFlags;
            if (owned) ownedModeFlags |= modeFlags; else globalModeFlags |= modeFlags;
        }

        /** com.android.server.uri.UriPermission.getStrength (android14-release
         *  :243-259, quoted above): each mode set must cover the request ON ITS
         *  OWN, persistable, then global, then owned. */
        int getStrength(int modeFlags) {
            modeFlags &= RW;
            if ((persistableModeFlags & modeFlags) == modeFlags) return STRENGTH_PERSISTABLE;
            if ((globalModeFlags & modeFlags) == modeFlags) return STRENGTH_GLOBAL;
            if ((ownedModeFlags & modeFlags) == modeFlags) return STRENGTH_OWNED;
            return STRENGTH_NONE;
        }

        /** UriPermission.takePersistableModes :154-174. */
        boolean takePersistableModes(int modeFlags) {
            modeFlags &= RW;
            if ((modeFlags & persistableModeFlags) != modeFlags) return false;
            final int before = persistedModeFlags;
            persistedModeFlags |= (persistableModeFlags & modeFlags);
            if (persistedModeFlags != 0) persistedCreateTime = System.currentTimeMillis();
            return persistedModeFlags != before;
        }

        /** UriPermission.releasePersistableModes :176-189. */
        boolean releasePersistableModes(int modeFlags) {
            modeFlags &= RW;
            final int before = persistedModeFlags;
            persistedModeFlags &= ~modeFlags;
            if (persistedModeFlags == 0) persistedCreateTime = UriPermission.INVALID_TIME;
            return persistedModeFlags != before;
        }

        /** UriPermission.revokeModes(modeFlags, includingOwners) :194-238 (quoted
         *  above): persistable/persisted bits and globalModeFlags always drop;
         *  ownedModeFlags only when includingOwners (the provider-side "delete
         *  the document" revoke). includingOwners=false is the caller-side
         *  "ownerless" branch -- a grant this app OWNS (a picker result) needs
         *  its real owner to revoke it, not a self-service call. */
        boolean revokeModes(int modeFlags, boolean includingOwners) {
            final boolean persistable = (modeFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0;
            modeFlags &= RW;
            final int before = persistedModeFlags;
            if (persistable) {
                persistableModeFlags &= ~modeFlags;
                persistedModeFlags &= ~modeFlags;
            }
            globalModeFlags &= ~modeFlags;
            if (includingOwners) ownedModeFlags &= ~modeFlags;
            if (persistedModeFlags == 0) persistedCreateTime = UriPermission.INVALID_TIME;
            return persistedModeFlags != before;
        }
    }

    /** com.android.server.uri.UriPermission :44-47, fetched from
     *  raw.githubusercontent.com/aosp-mirror/platform_frameworks_base
     *  /android14-release/services/core/java/com/android/server/uri/
     *  UriPermission.java (2026-09-28): STRENGTH_NONE=0, STRENGTH_OWNED=1,
     *  STRENGTH_GLOBAL=2, STRENGTH_PERSISTABLE=3. */
    private static final int STRENGTH_NONE = 0;
    private static final int STRENGTH_OWNED = 1;
    private static final int STRENGTH_GLOBAL = 2;
    private static final int STRENGTH_PERSISTABLE = 3;

    private static final Object sLock = new Object();
    /** GrantUri -> record. GrantUri equality is (user, uri, prefix); one user here. */
    private static final LinkedHashMap<String, Perm> sPerms = new LinkedHashMap<>();
    private static File sStore;
    private static boolean sLoaded;

    private static String key(Uri uri, boolean prefix) {
        return (prefix ? "P " : "E ") + uri;
    }

    /** Where the persisted records live (<dataDir>/.macapk/uri_grants.json); drops
     *  every in-memory record and reloads the persisted ones, which is exactly the
     *  state a process start has. Called from HostDocumentsProvider.init. */
    public static void init(File dataDir) {
        if (!ENABLED || dataDir == null) return;
        synchronized (sLock) {
            sStore = new File(dataDir, ".macapk/uri_grants.json");
            sPerms.clear();
            sLoaded = false;
            loadLocked();
        }
    }

    private static void ensureLoadedLocked() {
        if (sLoaded) return;
        if (sStore == null) {
            // HostDocumentsProvider.init names the store at boot. Fall back to the
            // app's data dir if something asks first.
            try {
                android.app.Application app = android.app.ActivityThread.currentApplication();
                String dir = app != null && app.getApplicationInfo() != null
                        ? app.getApplicationInfo().dataDir : null;
                if (dir != null) sStore = new File(dir, ".macapk/uri_grants.json");
            } catch (Throwable ignored) {}
        }
        loadLocked();
    }

    /** UGMS readGrantedUriPermissionsLocked: each persisted row becomes a record
     *  with persistable = persisted = its modes (UriPermission.initPersistedModes
     *  :115-124). */
    private static void loadLocked() {
        sLoaded = true;
        File store = sStore;
        if (store == null || !store.isFile()) return;
        try {
            org.json.JSONArray a = new org.json.JSONArray(
                    new String(Files.readAllBytes(store.toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Uri uri = Uri.parse(o.optString("uri"));
                boolean prefix = o.optBoolean("prefix");
                int mode = o.optInt("modeFlags") & RW;
                if (mode == 0) continue;
                Perm p = new Perm(uri, prefix);
                p.persistableModeFlags = mode;
                p.persistedModeFlags = mode;
                p.persistedCreateTime = o.optLong("createdTime", UriPermission.INVALID_TIME);
                sPerms.put(key(uri, prefix), p);
            }
        } catch (Throwable t) {
            android.util.Log.w(TAG, "persisted URI grants unreadable (" + t + "); starting empty");
        }
    }

    /** UGMS writeGrantedUriPermissions: only records with persisted modes. */
    private static void persistLocked() {
        File store = sStore;
        if (store == null) return;
        try {
            org.json.JSONArray a = new org.json.JSONArray();
            for (Perm p : sPerms.values()) {
                if (p.persistedModeFlags == 0) continue;
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("uri", p.uri.toString());
                o.put("prefix", p.prefix);
                o.put("modeFlags", p.persistedModeFlags);
                o.put("createdTime", p.persistedCreateTime);
                a.put(o);
            }
            File dir = store.getParentFile();
            if (dir != null) dir.mkdirs();
            File tmp = new File(store.getPath() + ".tmp");
            Files.write(tmp.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), store.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "could not persist URI grants: " + t);
        }
    }

    /** The grant a result Intent carries to the app that receives it:
     *  checkGrantUriPermissionFromIntentUnlocked (:587-660) collects data and each
     *  ClipData item's Uri (or, for an item with no Uri, its Intent's), and
     *  grantUriPermissionUncheckedFromIntent (:787-798) grants each with the
     *  Intent's flags. Called by DocumentPickerHandler when it delivers RESULT_OK,
     *  the point where ActivityRecord.sendResult makes that call on a device. */
    public static void grantFromIntent(Intent intent) {
        if (!ENABLED || intent == null) return;
        final int mode = intent.getFlags();
        if ((mode & RW) == 0) return;   // checkGrantUriPermission: no READ/WRITE, no grant
        List<Uri> uris = new ArrayList<>();
        collect(intent, uris);
        if (uris.isEmpty()) return;
        final boolean prefix = (mode & Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) != 0;
        synchronized (sLock) {
            ensureLoadedLocked();
            for (Uri u : uris) {
                if (!ContentResolver.SCHEME_CONTENT.equals(u.getScheme())) continue;
                // A result Intent's grant is OWNED: on a device the receiving
                // ActivityRecord is the owner (grantUriPermissionUncheckedFromIntent).
                recordGrantLocked(u, prefix, mode, /*owned=*/true);
            }
        }
    }

    /** Shared by grantFromIntent (owned) and grantFromProvider (global). Caller
     *  holds sLock. */
    private static void recordGrantLocked(Uri u, boolean prefix, int mode, boolean owned) {
        String k = key(u, prefix);
        Perm p = sPerms.get(k);
        if (p == null) { p = new Perm(u, prefix); sPerms.put(k, p); }
        p.grantModes(mode, owned);
    }

    private static void collect(Intent intent, List<Uri> out) {
        Uri data = intent.getData();
        if (data != null) out.add(data);
        ClipData clip = intent.getClipData();
        if (clip == null) return;
        for (int i = 0; i < clip.getItemCount(); i++) {
            ClipData.Item item = clip.getItemAt(i);
            Uri u = item.getUri();
            if (u != null) out.add(u);
            else if (item.getIntent() != null) collect(item.getIntent(), out);
        }
    }

    /** Preconditions.checkFlagsArgument (frameworks/base android11-release
     *  core/java/com/android/internal/util/Preconditions.java:195-203). */
    private static void checkFlagsArgument(int requestedFlags, int allowedFlags) {
        if ((requestedFlags & allowedFlags) != requestedFlags) {
            throw new IllegalArgumentException("Requested flags 0x"
                    + Integer.toHexString(requestedFlags) + ", but only 0x"
                    + Integer.toHexString(allowedFlags) + " are allowed");
        }
    }

    /** UGMS.takePersistableUriPermission :346-393. */
    public static void takePersistable(Uri uri, int modeFlags) {
        checkFlagsArgument(modeFlags, RW);
        synchronized (sLock) {
            ensureLoadedLocked();
            boolean persistChanged = false;
            Perm exactPerm = sPerms.get(key(uri, false));
            Perm prefixPerm = sPerms.get(key(uri, true));
            final boolean exactValid = exactPerm != null
                    && (modeFlags & exactPerm.persistableModeFlags) == modeFlags;
            final boolean prefixValid = prefixPerm != null
                    && (modeFlags & prefixPerm.persistableModeFlags) == modeFlags;
            if (!(exactValid || prefixValid)) {
                throw new SecurityException("No persistable permission grants found for UID "
                        + android.os.Process.myUid() + " and Uri " + toSafeString(uri));
            }
            if (exactValid) persistChanged |= exactPerm.takePersistableModes(modeFlags);
            if (prefixValid) persistChanged |= prefixPerm.takePersistableModes(modeFlags);
            persistChanged |= maybePrunePersistedUriGrantsLocked();
            if (persistChanged) persistLocked();
        }
    }

    /** UGMS.releasePersistableUriPermission :410-455. */
    public static void releasePersistable(Uri uri, int modeFlags) {
        checkFlagsArgument(modeFlags, RW);
        synchronized (sLock) {
            ensureLoadedLocked();
            boolean persistChanged = false;
            Perm exactPerm = sPerms.get(key(uri, false));
            Perm prefixPerm = sPerms.get(key(uri, true));
            if (exactPerm == null && prefixPerm == null) {
                throw new SecurityException("No permission grants found for UID "
                        + android.os.Process.myUid() + " and Uri " + toSafeString(uri));
            }
            if (exactPerm != null) {
                persistChanged |= exactPerm.releasePersistableModes(modeFlags);
                removeIfNeededLocked(exactPerm);
            }
            if (prefixPerm != null) {
                persistChanged |= prefixPerm.releasePersistableModes(modeFlags);
                removeIfNeededLocked(prefixPerm);
            }
            if (persistChanged) persistLocked();
        }
    }

    /** UGMS.removeUriPermissionIfNeededLocked: a record with no modes is dropped. */
    private static void removeIfNeededLocked(Perm p) {
        if (p.modeFlags() == 0) sPerms.remove(key(p.uri, p.prefix));
    }

    /** UGMS.maybePrunePersistedUriGrantsLocked :558-583, including its quirk that
     *  the size test counts every record while the trim counts persisted ones. */
    private static boolean maybePrunePersistedUriGrantsLocked() {
        if (sPerms.size() < MAX_PERSISTED_URI_GRANTS) return false;
        final ArrayList<Perm> persisted = new ArrayList<>();
        for (Perm p : sPerms.values()) if (p.persistedModeFlags != 0) persisted.add(p);
        final int trimCount = persisted.size() - MAX_PERSISTED_URI_GRANTS;
        if (trimCount <= 0) return false;
        // UriPermission.PersistedTimeComparator :356-361 (stable, like AOSP's sort).
        Collections.sort(persisted, (l, r) -> Long.compare(l.persistedCreateTime, r.persistedCreateTime));
        for (int i = 0; i < trimCount; i++) {
            final Perm p = persisted.get(i);
            p.releasePersistableModes(~0);
            removeIfNeededLocked(p);
        }
        return true;
    }

    /** UGMS.getUriPermissions(pkg, incoming=true, persistedOnly=true) :266-305. */
    public static List<UriPermission> persisted() {
        synchronized (sLock) {
            ensureLoadedLocked();
            ArrayList<UriPermission> out = new ArrayList<>();
            for (Perm p : sPerms.values()) {
                if (p.persistedModeFlags != 0) {
                    out.add(new UriPermission(p.uri, p.persistedModeFlags, p.persistedCreateTime));
                }
            }
            return out;
        }
    }

    /** DocumentsProvider.revokeDocumentPermission -> Context.revokeUriPermission(uri,
     *  ~0) by the providing side: every record whose Uri has `uri` as a path prefix
     *  loses every mode, persisted ones included (revokeUriPermissionLocked :873-900
     *  with modeFlags | FLAG_GRANT_PERSISTABLE_URI_PERMISSION, includingOwners). */
    public static void revoke(Uri uri) {
        if (!ENABLED || uri == null) return;
        synchronized (sLock) {
            ensureLoadedLocked();
            boolean persistChanged = false;
            for (Iterator<Perm> it = sPerms.values().iterator(); it.hasNext(); ) {
                Perm p = it.next();
                if (!isPathPrefixMatch(p.uri, uri)) continue;
                persistChanged |= p.revokeModes(~0 | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, true);
                if (p.modeFlags() == 0) it.remove();
            }
            if (persistChanged) persistLocked();
        }
    }

    /** UGMS.revokeUriPermissionLocked's CALLER-side branch (android14-release
     *  :838-861, quoted in the header above): when the caller does not hold the
     *  provider's own permissions -- true for this app against
     *  HostDocumentsProvider, since MANAGE_DOCUMENTS is signature|role -- only
     *  its OWNERLESS (global) grants on a matching path prefix are dropped,
     *  includingOwners=false. A grant this app OWNS (a picker result) survives:
     *  only the real owner can drop that one, mirroring
     *  ActivityRecord/UriPermissionOwner's lifecycle-tied revocation, which
     *  this Context.revokeUriPermission call is not. Review issue 2
     *  (2026-09-28): wired from Context.revokeUriPermission, which used to be a
     *  pure no-op labelled "one app, nothing to reach" -- true only when this
     *  app IS the provider, not for a provider standing in for another package. */
    public static void revokeNonOwned(Uri uri, int modeFlags) {
        if (!ENFORCE || uri == null) return;
        synchronized (sLock) {
            ensureLoadedLocked();
            boolean persistChanged = false;
            for (Iterator<Perm> it = sPerms.values().iterator(); it.hasNext(); ) {
                Perm p = it.next();
                if (!isPathPrefixMatch(p.uri, uri)) continue;
                persistChanged |= p.revokeModes(modeFlags | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, false);
                if (p.modeFlags() == 0) it.remove();
            }
            if (persistChanged) persistLocked();
        }
    }

    /** UGMS.checkUriPermissionLocked (android14-release :1275-1305) for this app's
     *  uid: an EXACT record (GrantUri equality, prefix = the request's PREFIX bit)
     *  whose strength covers the request, else ANY prefix record whose Uri is a
     *  path prefix of the request's. FLAG_GRANT_PERSISTABLE_URI_PERMISSION in the
     *  request raises the minimum to a persistable grant. Reached from
     *  ActivityManagerService.checkUriPermission (:6362-6377), i.e. every
     *  Context.checkUriPermission and ContentProvider's last-chance check. */
    public static boolean check(Uri uri, int modeFlags) {
        if (!ENFORCE || uri == null) return false;
        final boolean persistable = (modeFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0;
        final int minStrength = persistable ? STRENGTH_PERSISTABLE : STRENGTH_OWNED;
        final boolean prefix = (modeFlags & Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) != 0;
        synchronized (sLock) {
            ensureLoadedLocked();
            Perm exactPerm = sPerms.get(key(uri, prefix));
            if (exactPerm != null && exactPerm.getStrength(modeFlags) >= minStrength) return true;
            for (Perm perm : sPerms.values()) {
                if (perm.prefix && isPathPrefixMatch(uri, perm.uri)
                        && perm.getStrength(modeFlags) >= minStrength) {
                    return true;
                }
            }
            return false;
        }
    }

    /** ActivityManagerService.checkUriPermission (:6362-6377) as this app sees it:
     *  root (uid 0) gets everything; this app's uid is answered from its records;
     *  any other uid holds no grant here (one app). AMS's "pid == MY_PID" arm is
     *  system_server's own pid, which no caller in this process is. */
    public static int checkUriPermission(Uri uri, int pid, int uid, int modeFlags) {
        if (uid == 0) return android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (uid != android.os.Process.myUid()) return android.content.pm.PackageManager.PERMISSION_DENIED;
        return check(uri, modeFlags) ? android.content.pm.PackageManager.PERMISSION_GRANTED
                                     : android.content.pm.PackageManager.PERMISSION_DENIED;
    }

    /** DocumentsProvider.getCallingOrSelfUriPermissionModeFlags (android14-release
     *  :1025-1044) answered for this app: READ, WRITE, and PERSISTABLE when a
     *  persistable read grant covers the Uri. */
    public static int callingModeFlags(Uri uri) {
        int modeFlags = 0;
        if (check(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)) {
            modeFlags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }
        if (check(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
            modeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }
        if (check(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) {
            modeFlags |= Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        }
        return modeFlags;
    }

    /** A grant a provider standing in for another package issues to this app
     *  (Context.grantUriPermission from that provider's side: DocumentsProvider's
     *  canonicalize / rename / copy / move narrow grants). ActivityManagerService
     *  .grantUriPermission (:6401-6431) builds exactly this Intent -- data = uri,
     *  flags = modeFlags -- and grants it with no owner. */
    public static void grantFromProvider(Uri uri, int modeFlags) {
        if (!ENFORCE || uri == null) return;
        checkFlagsArgument(modeFlags, RW | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if ((modeFlags & RW) == 0) return;
        final boolean prefix = (modeFlags & Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) != 0;
        synchronized (sLock) {
            ensureLoadedLocked();
            // AMS.grantUriPermission passes owner=null here (:6431): GLOBAL, not
            // owned, which is what lets Context.revokeUriPermission's caller-side
            // branch (revokeNonOwned) drop it later (review issue 2).
            recordGrantLocked(uri, prefix, modeFlags, /*owned=*/false);
        }
    }

    /** Platform permissions no third-party app can hold, so a provider protected by
     *  one is reachable only through a URI grant: MANAGE_DOCUMENTS is
     *  signature|role and WRITE_MEDIA_STORAGE signature|privileged (frameworks/base
     *  android14-release core/res/AndroidManifest.xml:3011-3012, :3002-3003);
     *  they are exactly what ExternalStorageProvider's manifest requires
     *  (packages/ExternalStorageProvider/AndroidManifest.xml:11-25). Platform
     *  data, the same for every app. */
    public static boolean neverHeldByApps(String permission) {
        return android.Manifest.permission.MANAGE_DOCUMENTS.equals(permission)
                || "android.permission.WRITE_MEDIA_STORAGE".equals(permission);
    }

    /** The manifest package the loader parsed (com.macapk.loader.Boot, straight
     *  off AndroidManifest.xml), captured ONCE at boot -- set by
     *  Boot.setManifestPackage alongside Resources.macapkSetPackageName. Review
     *  issue 4 (2026-09-28): appPackage() used to be a fresh virtual
     *  Application.getPackageName() call (via ActivityThread.currentPackageName)
     *  on every check. An app that overrides getPackageName() in a
     *  stack-dependent way (some do) could then get a DIFFERENT answer at
     *  check time than the one Boot stamped into the app's own ProviderInfo,
     *  turning the app's own provider into "another package" on some calls and
     *  not others -- denying its own unexported FileProvider. */
    private static volatile String sManifestPackage;

    public static void setManifestPackage(String pkg) {
        sManifestPackage = (pkg != null && !pkg.isEmpty()) ? pkg : null;
    }

    /** The app's own package, the uid dimension this one-app store collapses to:
     *  a provider whose ProviderInfo names ANOTHER package is not "the same app"
     *  (ContentProvider.enforceReadPermissionInner's UserHandle.isSameApp test). */
    public static String appPackage() {
        String manifest = sManifestPackage;
        if (manifest != null) return manifest;
        // Fallback for a caller that never went through com.macapk.loader.Boot
        // (an offline harness, a unit test): the original virtual lookup, so
        // nothing regresses where the loader path is skipped.
        try {
            String p = android.app.ActivityThread.currentPackageName();
            return p == null || p.isEmpty() ? null : p;   // "" = no Application yet
        } catch (Throwable t) {
            return null;
        }
    }

    /** android.net.Uri.isPathPrefixMatch (android14-release :2427-2444). Ours in
     *  android/net/Uri.java compares whole strings, which would let tree/ab match
     *  tree/abc, so the AOSP segment form is used here. */
    static boolean isPathPrefixMatch(Uri uri, Uri prefix) {
        if (!Objects.equals(uri.getScheme(), prefix.getScheme())) return false;
        if (!Objects.equals(uri.getAuthority(), prefix.getAuthority())) return false;
        List<String> seg = uri.getPathSegments();
        List<String> prefixSeg = prefix.getPathSegments();
        final int prefixSize = prefixSeg.size();
        if (seg.size() < prefixSize) return false;
        for (int i = 0; i < prefixSize; i++) {
            if (!Objects.equals(seg.get(i), prefixSeg.get(i))) return false;
        }
        return true;
    }

    /** android.net.Uri.toSafeString (android14-release :399-434): scheme, then for
     *  tel/sip/sms/smsto/mailto/nfc the ssp with everything but '-', '@', '.'
     *  masked, otherwise "//" + host when there is an authority and "/..." -- never
     *  the path, which is where a document id lives. */
    static String toSafeString(Uri uri) {
        String scheme = uri.getScheme();
        String ssp = uri.getSchemeSpecificPart();
        StringBuilder b = new StringBuilder(64);
        if (scheme != null) {
            b.append(scheme).append(':');
            if (scheme.equalsIgnoreCase("tel") || scheme.equalsIgnoreCase("sip")
                    || scheme.equalsIgnoreCase("sms") || scheme.equalsIgnoreCase("smsto")
                    || scheme.equalsIgnoreCase("mailto") || scheme.equalsIgnoreCase("nfc")) {
                if (ssp != null) {
                    for (int i = 0; i < ssp.length(); i++) {
                        char c = ssp.charAt(i);
                        b.append(c == '-' || c == '@' || c == '.' ? c : 'x');
                    }
                }
                return b.toString();
            }
            final String host = uri.getHost();
            final int port = uri.getPort();
            final String path = uri.getPath();
            final String authority = uri.getAuthority();
            if (authority != null) b.append("//");
            if (host != null) b.append(host);
            if (port != -1) b.append(':').append(port);
            if (authority != null || path != null) b.append("/...");
        }
        return b.toString();
    }
}
