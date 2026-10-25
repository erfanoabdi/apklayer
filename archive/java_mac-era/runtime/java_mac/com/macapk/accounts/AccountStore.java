package com.macapk.accounts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The persistent half of AOSP's AccountManagerService for the account types an app's OWN
 * authenticator manages: the rows of the CE accounts database, kept on disk so an account an
 * app adds survives a relaunch exactly as it does on a device.
 *
 * WHAT IT MIRRORS (android14-release, services/core/java/com/android/server/accounts/):
 *   AccountsDb.java:135   CE_DATABASE_NAME = "accounts_ce.db", one per user under
 *                         /data/system_ce/<userId>/ -- system-owned, not the app's sandbox.
 *   AccountsDb.java:210-229 the three CE tables this class models:
 *       accounts   (name, type, password, UNIQUE(name,type))
 *       authtokens (accounts_id, type, authtoken, UNIQUE(accounts_id,type))
 *       extras     (accounts_id, key, value, UNIQUE(accounts_id,key))
 *   AccountManagerService.addAccountInternal (:1843-1935) -- the add rules reproduced in
 *       {@link #add}: name or type longer than 200 chars -> false, an existing (name,type)
 *       -> false ("skipping since the account already exists"), more than 100 accounts on
 *       the device -> false, otherwise insert the account then each extra as a String.
 *   setPasswordInternal -- a password change deletes every auth token of that account
 *       (accountsDb.deleteAuthTokensByAccountId), see {@link #setPassword}.
 *   invalidateAuthTokenLocked -- deletes, across every account of the TYPE, each token whose
 *       VALUE equals the one passed (the caller names a value, not a token type).
 *
 * WHY NOT SQLITE. The on-disk format of accounts_ce.db is private to system_server: no app
 * can open it on a device (it lives under /data/system_ce, mode 0600 system:system), so the
 * format is not part of any contract an app can observe. A Properties file written by an
 * atomic rename keeps this class free of the runtime's JNI sqlite binding, which is what lets
 * debug/appaccounts_gate.sh exercise it in a bare JVM. The observable contract is the method
 * semantics above, and those are AOSP's.
 *
 * ONE STORE PER ANDROID ROOT, like one AccountManagerService per device: the file sits at
 * <android_root>/data/system_ce/0/accounts_ce.properties. Visibility (which TYPES a caller
 * may touch) is not decided here; AccountManager asks AppAuthenticators which types the
 * caller's own authenticator manages, the same split as AOSP's isAccountManagedByCaller.
 */
public final class AccountStore {
    /** AccountManagerService.addAccountInternal's two length guards and its device cap. */
    public static final int MAX_NAME_OR_TYPE = 200;
    public static final int MAX_ACCOUNTS = 100;

    private static final Map<String, AccountStore> STORES = new HashMap<>();

    /** The store backed by {@code file}; one instance per canonical path. */
    public static AccountStore at(File file) {
        String key;
        try { key = file.getCanonicalPath(); } catch (IOException e) { key = file.getAbsolutePath(); }
        synchronized (STORES) {
            AccountStore s = STORES.get(key);
            if (s == null) { s = new AccountStore(new File(key)); STORES.put(key, s); }
            return s;
        }
    }

    private static final class Rec {
        String name;
        final String type;
        String password;
        String previousName;   // the DE table's previous_name (renameAccountInternal)
        final LinkedHashMap<String, String> extras = new LinkedHashMap<>();
        final LinkedHashMap<String, String> tokens = new LinkedHashMap<>();
        Rec(String name, String type) { this.name = name; this.type = type; }
    }

    private final File mFile;
    private List<Rec> mRecs;   // insertion order, like the accounts table's _id order

    private AccountStore(File file) { mFile = file; }

    public File file() { return mFile; }

    private List<Rec> recs() {
        if (mRecs == null) mRecs = load(mFile);
        return mRecs;
    }

    private Rec find(String name, String type) {
        for (Rec r : recs()) if (r.name.equals(name) && r.type.equals(type)) return r;
        return null;
    }

    /** AOSP addAccountInternal. Returns true only when a row was inserted. */
    public synchronized boolean add(String name, String type, String password,
            Map<String, String> extras) {
        if (name == null || type == null) return false;
        if (name.length() > MAX_NAME_OR_TYPE || type.length() > MAX_NAME_OR_TYPE) return false;
        if (find(name, type) != null) return false;
        if (recs().size() > MAX_ACCOUNTS) return false;
        Rec r = new Rec(name, type);
        r.password = password;
        if (extras != null) {
            for (Map.Entry<String, String> e : extras.entrySet()) {
                if (e.getKey() != null) r.extras.put(e.getKey(), e.getValue());
            }
        }
        recs().add(r);
        save();
        return true;
    }

    /** AOSP removeAccountInternal: true when the account existed and is gone. Its auth tokens
     *  and extras go with it (the CE tables cascade on accounts_id). */
    public synchronized boolean remove(String name, String type) {
        Rec r = find(name, type);
        if (r == null) return false;
        recs().remove(r);
        save();
        return true;
    }

    public synchronized boolean exists(String name, String type) {
        return find(name, type) != null;
    }

    /** {name, type} pairs whose type is in {@code types} (every type when null), in insertion
     *  order. */
    public synchronized List<String[]> accounts(Set<String> types) {
        List<String[]> out = new ArrayList<>();
        for (Rec r : recs()) {
            if (types == null || types.contains(r.type)) out.add(new String[] { r.name, r.type });
        }
        return out;
    }

    public synchronized String userData(String name, String type, String key) {
        Rec r = find(name, type);
        return r == null ? null : r.extras.get(key);
    }

    /** AOSP setUserdataInternal: a no-op for a missing account; a null value reads back null. */
    public synchronized void setUserData(String name, String type, String key, String value) {
        Rec r = find(name, type);
        if (r == null) return;
        if (value == null) r.extras.remove(key); else r.extras.put(key, value);
        save();
    }

    public synchronized String peekToken(String name, String type, String tokenType) {
        Rec r = find(name, type);
        return r == null ? null : r.tokens.get(tokenType);
    }

    /** AOSP saveAuthTokenToDatabase: false when the account does not exist; otherwise the
     *  (account, tokenType) row is replaced. */
    public synchronized boolean setToken(String name, String type, String tokenType, String token) {
        Rec r = find(name, type);
        if (r == null) return false;
        if (token == null) r.tokens.remove(tokenType); else r.tokens.put(tokenType, token);
        save();
        return true;
    }

    /** AOSP invalidateAuthTokenLocked: every token of every account of {@code type} whose
     *  value equals {@code token} is deleted. Returns how many ACCOUNTS lost a token. */
    public synchronized int invalidateToken(String type, String token) {
        if (type == null || token == null) return 0;
        int n = 0;
        for (Rec r : recs()) {
            if (!r.type.equals(type)) continue;
            n += r.tokens.values().removeIf(token::equals) ? 1 : 0;
        }
        if (n > 0) save();
        return n;
    }

    public synchronized String password(String name, String type) {
        Rec r = find(name, type);
        return r == null ? null : r.password;
    }

    /** AOSP setPasswordInternal: update the password and delete the account's auth tokens. A
     *  missing account is a no-op. clearPassword is setPassword(null). Returns AOSP's
     *  isChanged -- true whenever the account existed, which is what decides whether
     *  LOGIN_ACCOUNTS_CHANGED goes out. */
    public synchronized boolean setPassword(String name, String type, String password) {
        Rec r = find(name, type);
        if (r == null) return false;
        r.password = password;
        r.tokens.clear();
        save();
        return true;
    }

    /** AOSP renameAccountInternal (AccountManagerService.java:2148, android14-release): false
     *  (AOSP: a null result) when an account named {@code newName} of the same type already
     *  exists or the account itself does not; otherwise the row keeps its extras and auth tokens
     *  (they hang off accounts_id) and records the old name as previous_name. */
    public synchronized boolean rename(String name, String type, String newName) {
        if (newName == null || find(newName, type) != null) return false;
        Rec r = find(name, type);
        if (r == null) return false;
        r.previousName = name;
        r.name = newName;
        save();
        return true;
    }

    /** AOSP readPreviousNameInternal: the name before the last rename, null if never renamed. */
    public synchronized String previousName(String name, String type) {
        Rec r = find(name, type);
        return r == null ? null : r.previousName;
    }

    // ---- persistence -----------------------------------------------------------------------
    // Keys: n=<count>; a.<i>.name / .type / .pw; a.<i>.x.<extraKey>; a.<i>.t.<tokenType>.
    // Properties escapes every key and value itself, so a key containing '.' or '=' is safe:
    // the record prefix is parsed up to the third '.' only.

    private static List<Rec> load(File f) {
        List<Rec> out = new ArrayList<>();
        if (!f.isFile()) return out;
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("[accounts] could not read " + f + ": " + e);
            return out;
        }
        int n;
        try { n = Integer.parseInt(p.getProperty("n", "0")); } catch (NumberFormatException e) { n = 0; }
        for (int i = 0; i < n; i++) {
            String pre = "a." + i + ".";
            String name = p.getProperty(pre + "name"), type = p.getProperty(pre + "type");
            if (name == null || type == null) continue;
            Rec r = new Rec(name, type);
            r.password = p.getProperty(pre + "pw");
            r.previousName = p.getProperty(pre + "pn");
            out.add(r);
        }
        // Second pass for the per-account maps. Properties does not keep the file's key order,
        // so the keys are sorted for a deterministic reload.
        List<String> keys = new ArrayList<>(p.stringPropertyNames());
        java.util.Collections.sort(keys);
        for (String k : keys) {
            if (!k.startsWith("a.")) continue;
            int d1 = k.indexOf('.', 2);
            if (d1 < 0) continue;
            int d2 = k.indexOf('.', d1 + 1);
            if (d2 < 0) continue;
            String kind = k.substring(d1 + 1, d2);
            if (!kind.equals("x") && !kind.equals("t")) continue;
            int idx;
            try { idx = Integer.parseInt(k.substring(2, d1)); } catch (NumberFormatException e) { continue; }
            if (idx < 0 || idx >= n) continue;
            String name = p.getProperty("a." + idx + ".name"), type = p.getProperty("a." + idx + ".type");
            Rec target = null;
            for (Rec r : out) if (r.name.equals(name) && r.type.equals(type)) { target = r; break; }
            if (target == null) continue;
            String sub = k.substring(d2 + 1);
            if (kind.equals("x")) target.extras.put(sub, p.getProperty(k));
            else target.tokens.put(sub, p.getProperty(k));
        }
        return out;
    }

    private void save() {
        Properties p = new Properties();
        List<Rec> rs = recs();
        p.setProperty("n", Integer.toString(rs.size()));
        for (int i = 0; i < rs.size(); i++) {
            Rec r = rs.get(i);
            String pre = "a." + i + ".";
            p.setProperty(pre + "name", r.name);
            p.setProperty(pre + "type", r.type);
            if (r.password != null) p.setProperty(pre + "pw", r.password);
            if (r.previousName != null) p.setProperty(pre + "pn", r.previousName);
            for (Map.Entry<String, String> e : r.extras.entrySet()) {
                if (e.getValue() != null) p.setProperty(pre + "x." + e.getKey(), e.getValue());
            }
            for (Map.Entry<String, String> e : r.tokens.entrySet()) {
                if (e.getValue() != null) p.setProperty(pre + "t." + e.getKey(), e.getValue());
            }
        }
        // Atomic replace, so a crash mid-write leaves the previous store, never a torn one --
        // the property AOSP gets from SQLite's journal.
        // Owner-only (0700 dir, 0600 file): the rows hold passwords, auth tokens and session
        // user data, which on a device sit in /data/system_ce/<user>, readable by system_server
        // alone. The default umask would leave them world-readable (the installed apps' android
        // roots live under /Applications, mode 755).
        File dir = mFile.getParentFile();
        try {
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
                ownerOnly(dir, "rwx------");
            }
            File tmp = new File(dir, mFile.getName() + ".tmp");
            Files.deleteIfExists(tmp.toPath());
            Files.createFile(tmp.toPath());
            ownerOnly(tmp, "rw-------");
            try (OutputStream out = new FileOutputStream(tmp)) {
                p.store(out, "Mac.apk accounts_ce (AccountManagerService CE rows)");
            }
            Files.move(tmp.toPath(), mFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[accounts] could not write " + mFile + ": " + e);
        }
    }

    private static void ownerOnly(File f, String perms) {
        try {
            Files.setPosixFilePermissions(f.toPath(),
                    java.nio.file.attribute.PosixFilePermissions.fromString(perms));
        } catch (IOException | UnsupportedOperationException e) {
            System.err.println("[accounts] could not restrict " + f + ": " + e);
        }
    }
}
