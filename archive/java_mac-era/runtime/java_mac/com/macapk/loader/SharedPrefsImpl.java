// File-backed SharedPreferences, in ANDROID'S on-disk layout.
//
// v1.0.1061: this used to store a java.util.Properties XML under
// <files-dir>/shared_prefs/<name>.xml, justified as "intra-app state that only
// roundtrips through us". Two things were wrong with that. The PATH was not
// AOSP's (ContextImpl's prefsDir is <dataDir>/shared_prefs, not
// <dataDir>/files/shared_prefs), and the FORMAT was not Android's
// (<entry key=...> instead of <map><boolean name=... value=.../></map>).
//
// "Only roundtrips through us" is the assumption that fails: anything that
// touches the file directly sees a file Android would not recognise. It has
// already cost real time -- a seeded `key_is_first_launch=false` for Clock was
// silently ignored because it went to AOSP's path, and
// debug/aurora_reset_intro.sh carries its own note about the format surprise.
// An app that ships a default prefs file, parses its own, or restores a backup
// hits the same wall.
//
// Both are now AOSP's. Existing data is MIGRATED on first access (legacy path
// + Properties format -> AOSP path + Android XML), and the legacy file is only
// unlinked after the replacement is on disk and non-empty.
//
// Why a real implementation here (instead of stubs returning defaults):
// many APKs gate behavior on prefs (sound on/off, last level played,
// best score). A pure-stub Editor that drops writes silently makes
// games look broken in subtle ways.
package com.macapk.loader;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class SharedPrefsImpl implements SharedPreferences {
    private final File file;
    private final HashMap<String, Object> map = new HashMap<>();
    /** Guards `map`. AOSP's SharedPreferencesImpl guards mMap with mLock and
     *  runs the listener callbacks OUTSIDE it (notifyListeners is called after
     *  commitToMemory returns); we do the same, so a listener that reads the
     *  prefs back cannot deadlock against the commit that woke it. */
    private final Object lock = new Object();

    /** AOSP SharedPreferencesImpl.CALLBACK_ON_CLEAR_CHANGE (119147584L) is
     *  `@EnabledAfter(targetSdkVersion = Build.VERSION_CODES.Q)`
     *  (android11-release, SharedPreferencesImpl.java:73-75), so it is ON for
     *  targetSdk >= 30. Above that level an Editor.clear() notifies every
     *  registered listener with a NULL key; below it, clear() notifies nothing.
     *  Ours notified nothing at any level, so an app on 30+ that repaints from
     *  onSharedPreferenceChanged never learned its preferences had been wiped. */
    private boolean macapkCallbackOnClear() { return macapkTargetSdk >= 30; }

    /** The APP's targetSdkVersion, captured once at construction the way
     *  LinearLayout.macapkInitCompat does. This is a targetSdk COMPAT GATE, not
     *  a device-level check: AOSP varies this behaviour per app through
     *  CompatChanges, and Build.VERSION.SDK_INT (what PresentedSdk answers)
     *  is the wrong input for it. Reading it here rather than per-commit keeps
     *  the hot path free of a Context walk, and a Context that cannot answer
     *  leaves the legacy behaviour in place. */
    private int macapkTargetSdk;

    // ---- one instance per FILE ------------------------------------------
    //
    // AOSP keeps a single static ContextImpl.sSharedPrefsCache keyed by file, so
    // one prefs file has exactly one SharedPreferencesImpl and one lock. We had
    // TWO caches -- Application.prefsCache (an instance field) and
    // Context.sBasePrefs (static) -- so the same file reached through the
    // Application override and through a raw Context / ContextThemeWrapper
    // produced two objects with INDEPENDENT locks.
    //
    // That was merely stale-read-shaped while save() truncated in place. With
    // the atomic backup dance it turns destructive: two interleaved saves can
    // have one instance delete the other's freshly written file as "suspect",
    // or leave BOTH the file and its backup unlinked -- total data loss, a state
    // the old writer could not reach. Interning here fixes it for every caller
    // at once, whichever cache they came through.
    private static final java.util.HashMap<String, SharedPrefsImpl> sByPath =
        new java.util.HashMap<>();

    /** AOSP ContextImpl.getPreferencesDir(): <dataDir>/shared_prefs.
     *  getDataDir() is the PARENT of getFilesDir() here (Context.java:776).
     *  The ONE place this path is computed, so the intern key and the file the
     *  instance writes can never disagree. */
    static File macapkFileFor(Context ctx, String name) {
        File dir = new File(ctx.getDataDir(), "shared_prefs");
        dir.mkdirs();
        return new File(dir, name + ".xml");
    }

    /** Per-FILE lock, keyed by canonical path and shared by every instance that
     *  points at that file. Interning above should mean there is only ever one
     *  such instance, but interning is a convention that a future `new
     *  SharedPrefsImpl(...)` could quietly break -- and the cost of breaking it
     *  is not a stale read, it is BOTH the file and its backup unlinked.
     *  Measured before this lock existed: two instances churning one file lost
     *  both copies 70 times in 800 saves (debug/prefs_atomic/Conc.java). So the
     *  atomicity is enforced here, where it cannot be bypassed, and interning is
     *  the correctness-and-performance layer on top rather than the only guard. */
    private static final java.util.HashMap<String, Object> sPathLocks =
        new java.util.HashMap<>();

    private Object macapkPathLock() {
        String key;
        try { key = file.getCanonicalPath(); } catch (Throwable t) { key = file.getAbsolutePath(); }
        synchronized (sPathLocks) {
            Object o = sPathLocks.get(key);
            if (o == null) { o = new Object(); sPathLocks.put(key, o); }
            return o;
        }
    }

    /** The only sanctioned way to obtain one. Both prefs caches route here. */
    public static SharedPrefsImpl macapkGet(Context ctx, String name) {
        File f = macapkFileFor(ctx, name);
        String key;
        try { key = f.getCanonicalPath(); } catch (Throwable t) { key = f.getAbsolutePath(); }
        synchronized (sByPath) {
            SharedPrefsImpl p = sByPath.get(key);
            if (p == null) { p = new SharedPrefsImpl(ctx, name); sByPath.put(key, p); }
            return p;
        }
    }

    public SharedPrefsImpl(Context ctx, String name) {
        // Capture the app's targetSdk once. Defensive because a Context that
        // cannot answer must leave the LEGACY behaviour in place rather than
        // take the process down over a preferences read.
        try {
            android.content.pm.ApplicationInfo ai = ctx.getApplicationInfo();
            if (ai != null && ai.targetSdkVersion > 0) macapkTargetSdk = ai.targetSdkVersion;
        } catch (Throwable ignored) { }
        this.file = macapkFileFor(ctx, name);
        // One-time migration off the pre-v1.0.1061 location/format. Only when
        // the AOSP-path file does not exist, so this runs once per prefs file
        // and never overwrites newer data.
        File legacy = new File(new File(ctx.getFilesDir(), "shared_prefs"), name + ".xml");
        // A leftover .bak holds the only good copy after an interrupted write, so
        // it counts as the file EXISTING. Without this the migration branch would
        // ignore it, write stale legacy data, and then save()'s backup.delete()
        // would destroy the newer copy. Narrow (needs a previously-failed legacy
        // delete) but it is one condition to close.
        if (!file.exists() && !macapkBackupFile().exists() && legacy.exists()) {
            try {
                loadLegacyProperties(legacy);
                save();
                // Unlink the old copy only once the replacement is really on
                // disk: a failed write must not lose the app's data.
                if (file.exists() && file.length() > 0) {
                    if (!legacy.delete()) legacy.deleteOnExit();
                    NativeLog.i("SharedPrefs", "migrated " + name
                        + " to the AOSP path (" + map.size() + " entries)");
                }
            } catch (Throwable t) {
                NativeLog.w("SharedPrefs", "migration failed for " + name
                    + ", keeping the legacy file: " + t);
            }
            return;
        }
        load();
    }

    private void load() {
        synchronized (macapkPathLock()) { macapkLoadLocked(); }
    }

    private void macapkLoadLocked() {
        // AOSP SharedPreferencesImpl.loadFromDisk (android11-release :150-157):
        // a backup file left on disk means the last write did not finish, so the
        // backup -- not the live file -- is the last COMPLETE state.
        File backup = macapkBackupFile();
        if (backup.exists()) {
            file.delete();
            if (backup.renameTo(file)) {
                NativeLog.w("SharedPrefs", "restored " + file.getName()
                    + " from a backup left by an interrupted write");
            }
        }
        if (!file.exists()) return;
        String xml;
        try { xml = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8"); }
        catch (Throwable ignored) { return; }
        // A file written before v1.0.1061 (or by hand) is Properties XML; it is
        // recognisable by <entry key=...> and is read with the old reader so no
        // data is stranded.
        if (xml.indexOf("<entry") >= 0 && xml.indexOf("<map") < 0) {
            loadLegacyProperties(file);
            return;
        }
        parseAndroidXml(xml);
    }

    /** Android's own prefs XML. Hand-parsed rather than via javax.xml: the
     *  staged JRE ships a reduced module set, and a prefs read must not depend
     *  on java.xml being present. The format is machine-written and tiny. */
    private void parseAndroidXml(String xml) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "<(boolean|int|long|float)\\s+name=\"([^\"]*)\"\\s+value=\"([^\"]*)\"\\s*/>")
            .matcher(xml);
        while (m.find()) {
            String kind = m.group(1), k = unesc(m.group(2)), v = m.group(3);
            try {
                if      (kind.equals("boolean")) map.put(k, Boolean.parseBoolean(v));
                else if (kind.equals("int"))     map.put(k, Integer.parseInt(v));
                else if (kind.equals("long"))    map.put(k, Long.parseLong(v));
                else                             map.put(k, Float.parseFloat(v));
            } catch (Throwable ignored) { }
        }
        m = java.util.regex.Pattern.compile(
            "<string\\s+name=\"([^\"]*)\">(.*?)</string>", java.util.regex.Pattern.DOTALL)
            .matcher(xml);
        while (m.find()) map.put(unesc(m.group(1)), unesc(m.group(2)));
        m = java.util.regex.Pattern.compile(
            "<set\\s+name=\"([^\"]*)\">(.*?)</set>", java.util.regex.Pattern.DOTALL)
            .matcher(xml);
        while (m.find()) {
            Set<String> out = new HashSet<>();
            java.util.regex.Matcher em = java.util.regex.Pattern.compile(
                "<string>(.*?)</string>", java.util.regex.Pattern.DOTALL).matcher(m.group(2));
            while (em.find()) out.add(unesc(em.group(1)));
            map.put(unesc(m.group(1)), out);
        }
    }

    /** The pre-v1.0.1061 reader, kept for migration and for a legacy file that
     *  a harness or an older build left behind. */
    private void loadLegacyProperties(File src) {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(src)) { p.loadFromXML(in); }
        catch (Throwable ignored) { return; }
        for (String key : p.stringPropertyNames()) {
            String v = p.getProperty(key);
            // Type prefix encoded by Editor.commit ("i:42" / "b:true" / ...)
            if (v == null) continue;
            if      (v.startsWith("i:")) map.put(key, Integer.parseInt(v.substring(2)));
            else if (v.startsWith("l:")) map.put(key, Long.parseLong(v.substring(2)));
            else if (v.startsWith("f:")) map.put(key, Float.parseFloat(v.substring(2)));
            else if (v.startsWith("b:")) map.put(key, Boolean.parseBoolean(v.substring(2)));
            else if (v.startsWith("s:")) map.put(key, v.substring(2));
            else if (v.startsWith("S:")) map.put(key, decodeStringSet(v.substring(2)));
            else map.put(key, v);
        }
    }

    /** Write Android's prefs XML, byte-shaped like the platform's own writer so
     *  a file this produces is one a device would accept. */
    private boolean save() {
        synchronized (macapkPathLock()) { return macapkSaveLocked(); }
    }

    private boolean macapkSaveLocked() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n");
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            String k = esc(e.getKey());
            if      (v instanceof Boolean) sb.append("    <boolean name=\"").append(k).append("\" value=\"").append(v).append("\" />\n");
            else if (v instanceof Integer) sb.append("    <int name=\"").append(k).append("\" value=\"").append(v).append("\" />\n");
            else if (v instanceof Long)    sb.append("    <long name=\"").append(k).append("\" value=\"").append(v).append("\" />\n");
            else if (v instanceof Float)   sb.append("    <float name=\"").append(k).append("\" value=\"").append(v).append("\" />\n");
            else if (v instanceof String)  sb.append("    <string name=\"").append(k).append("\">").append(esc((String) v)).append("</string>\n");
            else if (v instanceof Set) {
                sb.append("    <set name=\"").append(k).append("\">\n");
                for (Object o : (Set<?>) v) sb.append("        <string>").append(esc(String.valueOf(o))).append("</string>\n");
                sb.append("    </set>\n");
            }
        }
        sb.append("</map>\n");
        // AOSP SharedPreferencesImpl.writeToFile (android11-release :765-850).
        // The live file is RENAMED aside before the new one is written, the
        // stream is fsync'd, and the backup is unlinked only once the write has
        // succeeded -- so a crash, kill or power loss at any instant leaves
        // either the new file or a complete backup on disk, never a truncated
        // one. load() above restores from the backup.
        //
        // Before this, the write truncated the live file in place with no sync
        // and swallowed every failure. That is silent user-data loss: the regex
        // reader parses whatever bytes survived without complaining, so an
        // interrupted write reset an app's entire settings to defaults with no
        // error anywhere.
        File backup = macapkBackupFile();
        if (file.exists()) {
            if (backup.exists()) {
                // A backup already here means the PREVIOUS write did not finish.
                // The live file is the suspect one, so keep the backup and drop
                // the live file rather than overwriting a known-good copy.
                file.delete();
            } else if (!file.renameTo(backup)) {
                NativeLog.w("SharedPrefs", "cannot move " + file.getName()
                    + " aside; refusing to write rather than risk the existing data");
                return false;
            }
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(sb.toString().getBytes("UTF-8"));
            out.flush();
            // FileUtils.sync(str) at AOSP :833. Without it the directory entry
            // can be durable while the CONTENT is not, which is exactly the
            // corruption this whole dance exists to prevent.
            out.getFD().sync();
        } catch (Throwable t) {
            // Leave the backup in place: the next load() restores from it.
            file.delete();
            NativeLog.w("SharedPrefs", "write failed for " + file.getName()
                + ", the previous contents are preserved: " + t);
            return false;
        }
        backup.delete();
        return true;
    }

    /** AOSP makeBackupFile(): the prefs file's path with ".bak" appended. */
    private File macapkBackupFile() { return new File(file.getPath() + ".bak"); }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == '&')  b.append("&amp;");
            else if (c == '<')  b.append("&lt;");
            else if (c == '>')  b.append("&gt;");
            else if (c == '"')  b.append("&quot;");
            else if (c == '\'') b.append("&apos;");
            else b.append(c);
        }
        return b.toString();
    }
    private static String unesc(String s) {
        if (s == null) return "";
        return s.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");   // last, so &amp;lt; does not become <
    }

    // StringSet persistence (putStringSet/getStringSet). Length-prefixed
    // encoding ("<len>:<chars>" per element) so any element content -- commas,
    // colons, newlines -- round-trips through Properties XML intact. Was
    // dropped entirely: save() had no Set case (continue) and getStringSet
    // ignored the map, so a stored set was lost on both read and persist.
    private static String encodeStringSet(Set<?> set) {
        StringBuilder sb = new StringBuilder();
        for (Object o : set) {
            String el = String.valueOf(o);
            sb.append(el.length()).append(':').append(el);
        }
        return sb.toString();
    }
    private static Set<String> decodeStringSet(String s) {
        HashSet<String> out = new HashSet<>();
        int i = 0, n = s.length();
        while (i < n) {
            int colon = s.indexOf(':', i);
            if (colon < 0) break;
            int len;
            try { len = Integer.parseInt(s.substring(i, colon)); }
            catch (NumberFormatException e) { break; }
            int start = colon + 1;
            if (len < 0 || start + len > n) break;
            out.add(s.substring(start, start + len));
            i = start + len;
        }
        return out;
    }

    // AOSP's getters HARD-CAST the stored value and only fall back to the
    // default when the map held NOTHING for the key:
    //   String v = (String)mMap.get(key); return v != null ? v : defValue;
    // (frameworks/base/core/java/android/app/SharedPreferencesImpl.java:270-276,
    //  android-11.0.0_r48). So reading a key at the WRONG type throws
    // ClassCastException -- it does NOT quietly return the default.
    //
    // We used to `instanceof`-test and return the default on a type mismatch,
    // which looks defensive but breaks the apps that depend on the throw: the
    // standard prefs-migration idiom is
    //     try { v = sp.getInt(K, 0); } catch (ClassCastException e) { migrate(); }
    // and an app written that way NEVER migrates against a silent default,
    // because the CCE it is waiting for never arrives. Mirroring AOSP means
    // mirroring the throw. 30 SPF.cce.* probe cases cover the full 6x5 matrix
    // of stored-type vs read-type.
    @Override public Map<String, ?> getAll() {
        synchronized (lock) { return new HashMap<>(map); }
    }
    @Override public String getString(String key, String d) {
        synchronized (lock) { String v = (String) map.get(key); return v != null ? v : d; }
    }
    @Override public Set<String> getStringSet(String key, Set<String> d) {
        synchronized (lock) {
            @SuppressWarnings("unchecked") Set<String> v = (Set<String>) map.get(key);
            // AOSP returns the STORED set, not a defensive copy, so mutating the
            // returned set is visible on the next read (SharedPreferencesImpl
            // :277-283). It is a wart, but it is the specified behaviour and a
            // copy answers differently -- SPF.stringSet.returnedSetAliases.
            return v != null ? v : d;
        }
    }
    @Override public int getInt(String key, int d) {
        synchronized (lock) { Integer v = (Integer) map.get(key); return v != null ? v : d; }
    }
    @Override public long getLong(String key, long d) {
        synchronized (lock) { Long v = (Long) map.get(key); return v != null ? v : d; }
    }
    @Override public float getFloat(String key, float d) {
        synchronized (lock) { Float v = (Float) map.get(key); return v != null ? v : d; }
    }
    @Override public boolean getBoolean(String key, boolean d) {
        synchronized (lock) { Boolean v = (Boolean) map.get(key); return v != null ? v : d; }
    }
    @Override public boolean contains(String key) {
        synchronized (lock) { return map.containsKey(key); }
    }
    @Override public Editor edit()                                { return new EditorImpl(); }
    // AOSP fires these on every apply/commit per changed key. androidx
    // PreferenceManager / PreferenceFragment, DataStore-on-prefs shims, and app
    // settings screens register here to re-read on change; was a no-op so they
    // were never notified. v1.0.423.
    private final java.util.List<OnSharedPreferenceChangeListener> listeners =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { if (l != null) listeners.add(l); }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { listeners.remove(l); }

    /** AOSP's EditorImpl keeps ONE map (mModified) plus an mClear flag -- there
     *  is no separate "staged" and "removed" set. A removal is recorded by
     *  putting the editor itself into that same map as a magic sentinel
     *  (SharedPreferencesImpl.java:424-429, android-11.0.0_r48):
     *      public Editor remove(String key) {
     *          synchronized (mEditorLock) { mModified.put(key, this); return this; }
     *      }
     *  One map is what makes the LAST call on a key win. We used to drain
     *  `removed` first and then `putAll(staged)`, so a put ALWAYS beat a remove
     *  no matter which was called last, and
     *      e.putString(K, v); e.remove(K); e.commit();
     *  left K present (SPF.editorOrder.putThenRemove). */
    private final class EditorImpl implements Editor {
        private final HashMap<String, Object> mModified = new HashMap<>();
        private boolean mClear;

        // putString(key, null) stages a NULL, which commitToMemory below treats
        // as a removal -- see the AOSP comment quoted there. Do NOT translate it
        // to a remove here: AOSP stores the null and resolves it at commit time,
        // which is what makes a later put on the same key override it.
        @Override public Editor putString(String key, String value)   { synchronized (mModified) { mModified.put(key, value); } return this; }
        // AOSP copies the CALLER's set at put time so a later mutation of the
        // caller's set is not visible in the store (SPF.stringSet
        // .callerSetDefensiveCopy), and preserves a null as a removal request.
        @Override public Editor putStringSet(String key, Set<String> values) { synchronized (mModified) { mModified.put(key, values == null ? null : new HashSet<>(values)); } return this; }
        @Override public Editor putInt(String key, int value)         { synchronized (mModified) { mModified.put(key, value); } return this; }
        @Override public Editor putLong(String key, long value)       { synchronized (mModified) { mModified.put(key, value); } return this; }
        @Override public Editor putFloat(String key, float value)     { synchronized (mModified) { mModified.put(key, value); } return this; }
        @Override public Editor putBoolean(String key, boolean value) { synchronized (mModified) { mModified.put(key, value); } return this; }
        @Override public Editor remove(String key)                    { synchronized (mModified) { mModified.put(key, this); } return this; }
        @Override public Editor clear()                                { synchronized (mModified) { mClear = true; } return this; }

        // AOSP: commit() "returns true if the new values were successfully
        // written to persistent storage". Returning an unconditional true made
        // every failure -- a read-only dir, a full disk, a rename we could not
        // do -- invisible to an app that checks, which is the whole point of
        // choosing commit() over apply().
        @Override public boolean commit() { return commitToMemory(); }
        @Override public void apply()     { commitToMemory(); }

        /** Mirrors AOSP SharedPreferencesImpl.EditorImpl.commitToMemory()
         *  (:454-524). Three contracts live here that a naive implementation
         *  gets wrong, each with its own probe case:
         *
         *  1. A null value is a REMOVE. AOSP's comment, verbatim:
         *       // "this" is the magic value for a removal mutation. In addition,
         *       // setting a value to "null" for a given key is specified to be
         *       // equivalent to calling remove on that key.
         *     so putString(k, null).commit() leaves contains(k) FALSE. We used
         *     to insert the null, making contains(k) true and handing the app a
         *     key whose value is null (SPF.putNull.*).
         *
         *  2. An unchanged value is NOT a change: when the map already holds an
         *     equal value the entry is skipped, so it never reaches the
         *     changed-keys list and no listener fires for it.
         *
         *  3. The editor is RESET at the end -- `mModified.clear()` plus
         *     `mClear = false` -- so committing the same Editor twice applies
         *     its edits ONCE. Ours kept both, so a second commit() replayed the
         *     original writes and resurrected a value the app had deliberately
         *     removed in between (SPF.reuse.replay / SPF.reuse.clearReplay). */
        private boolean commitToMemory() {
            java.util.ArrayList<String> keysModified = new java.util.ArrayList<>();
            boolean changesMade = false;
            // Starts true because AOSP reports SUCCESS for a commit that had
            // nothing to write (writeToFile's memory-state-generation early
            // return, :44-46 of that method). Only a real failed write is false.
            boolean wrote = true;
            // AOSP sets keysCleared whenever a clear() was part of this commit,
            // OUTSIDE the !isEmpty() check (SharedPreferencesImpl.java:545-552)
            // -- so clearing an already-empty prefs file still notifies. Do not
            // "optimise" this to only fire when something was removed.
            boolean keysCleared = false;
            synchronized (lock) {
                synchronized (mModified) {
                    if (mClear) {
                        if (!map.isEmpty()) { changesMade = true; map.clear(); }
                        keysCleared = true;
                        mClear = false;
                    }
                    for (Map.Entry<String, Object> e : mModified.entrySet()) {
                        String k = e.getKey();
                        Object v = e.getValue();
                        if (v == this || v == null) {
                            if (!map.containsKey(k)) continue;
                            map.remove(k);
                        } else {
                            if (map.containsKey(k)) {
                                Object existing = map.get(k);
                                if (existing != null && existing.equals(v)) continue;
                            }
                            map.put(k, v);
                        }
                        changesMade = true;
                        keysModified.add(k);
                    }
                    mModified.clear();
                }
                // Only touch the disk when something actually moved. AOSP reaches
                // the same outcome through the memory-state generation check in
                // writeToFile (a no-change commit reports success without
                // writing), so a replayed no-op editor does not rewrite the file.
                if (changesMade) wrote = save();
            }
            // AOSP notifies OUTSIDE the lock. The NULL-key clear callback comes
            // FIRST, once per listener, and only then the per-key loop
            // (SharedPreferencesImpl.java:626-639).
            if (keysCleared && macapkCallbackOnClear()) {
                for (OnSharedPreferenceChangeListener l : listeners) {
                    try { l.onSharedPreferenceChanged(SharedPrefsImpl.this, null); }
                    catch (Throwable ignored) {}
                }
            }
            // Newest key first, and only for keys that really changed.
            for (int i = keysModified.size() - 1; i >= 0; i--) {
                String k = keysModified.get(i);
                for (OnSharedPreferenceChangeListener l : listeners) {
                    try { l.onSharedPreferenceChanged(SharedPrefsImpl.this, k); } catch (Throwable ignored) {}
                }
            }
            return wrote;
        }
    }
}
