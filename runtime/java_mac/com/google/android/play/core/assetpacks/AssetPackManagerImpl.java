package com.google.android.play.core.assetpacks;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import com.google.android.play.core.assetpacks.model.AssetPackStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

// Mac.apk's AssetPackManager. Every asset pack a Unity title references is an
// install-time pack: its bundle is in the APK assets and Mac.apk extracts the
// whole aa/ tree to the data dir on launch (see apk_unpacker). There is no
// Google Play to download from, so every requested pack is reported COMPLETED
// (status 4) with its assets already present -- which is the truth on Mac.apk.
//
// WHY THIS EXISTS (root cause it fixes): Unity's PlayAssetDeliveryAssetBundle-
// Provider does NOT load a bundle until the owning pack reports COMPLETED via
// AssetPackManager.getPackStates(); the status query goes Java
// PlayAssetDeliveryUnityWrapper -> AssetPackManager. Without this class the
// wrapper's reflection probe (loadClass on AssetPackManager) threw
// ClassNotFound, the wrapper reported "Play Core API missing", the status
// callback never fired, the Addressables ProviderOperation stayed not-done
// forever, and the splash/menu load hung (Crossy: flat-blue past the age gate,
// ProviderOperation m_Status=None). Reporting COMPLETED lets the provider
// proceed to the real bundle load (which already resolves from the extracted
// aa/Android tree). App-agnostic: any Unity-PAD title benefits.
public final class AssetPackManagerImpl implements AssetPackManager {
    private final Context context;
    private final List<AssetPackStateUpdateListener> listeners =
        new CopyOnWriteArrayList<AssetPackStateUpdateListener>();

    AssetPackManagerImpl(Context context) {
        this.context = context;
    }

    // The asset-pack assetsPath() Unity's PAD integration uses as the
    // Addressables RuntimePath. When PAD is active Unity builds the catalog /
    // settings / bundle paths as `assetsPath() + "/aa/..."` (the "aa" folder
    // is INSIDE the pack's assets). Mac.apk extracts the APK's assets/aa/*
    // tree to <dataDir>/aa (settings.json + catalog.bin at <dataDir>/aa/, the
    // bundles at <dataDir>/aa/Android/), so assetsPath() must be <dataDir>
    // (the PARENT of aa) -- returning <dataDir>/aa would double the segment
    // (<dataDir>/aa/aa/settings.json) and break Addressables init, so the
    // catalog/settings/bundles never load. (The actual bundle file is opened
    // through the catalog InternalId; the Mac.apk file shim remaps it to the
    // extracted tree, but the RuntimePath base must still be correct so the
    // C# constructs a path the shim can remap.)
    private String assetsRoot() {
        try {
            File data = context != null ? context.getDataDir() : null;
            if (data != null) return data.getAbsolutePath();
        } catch (Throwable t) {
            // fall through to a relative default
        }
        return ".";
    }

    // The directory holding the extracted Addressables bundles
    // (<dataDir>/aa/Android), used for best-effort size + single-asset lookups.
    private File bundleDir() {
        return new File(new File(assetsRoot(), "aa"), "Android");
    }

    private long bundleSize(String packName) {
        // Best-effort: an install-time pack is fully present, so its
        // "downloaded" size equals its on-disk size. We do not need the exact
        // figure (the provider only checks status==COMPLETED), but a non-zero
        // value keeps progress reporting sane. We sum the Android bundle dir
        // lazily; if unreadable, report a nominal 1 byte (already complete).
        try {
            File dir = bundleDir();
            File[] files = dir.listFiles();
            if (files != null) {
                long sum = 0;
                for (File f : files) {
                    if (f.isFile() && f.getName().contains(packName)) sum += f.length();
                }
                if (sum > 0) return sum;
            }
        } catch (Throwable t) { /* ignore */ }
        return 1L;
    }

    private AssetPackStates statesFor(List<String> packNames) {
        Map<String, AssetPackState> map = new LinkedHashMap<String, AssetPackState>();
        long total = 0;
        if (packNames != null) {
            for (String name : packNames) {
                if (name == null) continue;
                long sz = bundleSize(name);
                total += sz;
                map.put(name, AssetPackState.completed(name, sz));
            }
        }
        return AssetPackStates.of(total, map);
    }

    private void notifyCompleted(final List<String> packNames) {
        if (listeners.isEmpty() || packNames == null) return;
        // Real Play Core delivers AssetPackStateUpdateListener callbacks on its
        // own background thread (a Play Core executor), NOT synchronously on the
        // caller's thread. Mirror that: dispatch off a daemon thread so a
        // listener that re-enters AssetPackManager or blocks (Unity's C# PAD
        // callback marshals back to the player loop) can never deadlock the
        // Unity main thread that called getPackStates/fetch.
        final List<AssetPackStateUpdateListener> snapshot =
            new ArrayList<AssetPackStateUpdateListener>(listeners);
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                for (String name : packNames) {
                    if (name == null) continue;
                    AssetPackState st = AssetPackState.completed(name, bundleSize(name));
                    for (AssetPackStateUpdateListener l : snapshot) {
                        try { l.onStateUpdate(st); }
                        catch (Throwable e) { /* listener threw; ignore */ }
                    }
                }
            }
        }, "AssetPackStateUpdate");
        t.setDaemon(true);
        t.start();
    }

    private static void log(String m) {
        try { com.macapk.loader.NativeLog.i("AssetPackMgr", m); }
        catch (Throwable t) { /* logging unavailable */ }
    }

    // Unity's core install-time packs. Their content (the il2cpp Data, the
    // Addressables catalog/bundles, StreamingAssets) ships inside the base APK.
    // The Unity player builds the Addressables RuntimePath as
    //   getPackLocation(UnityStreamingAssetsPack).assetsPath() + "/aa"
    // and loads catalog.bin / settings.json / the bundles relative to it. The
    // ONLY value that works on Mac.apk is the in-APK streaming-assets jar URL
    // ("jar:file://<apk>!/assets"), which is exactly what the engine's DEFAULT
    // (no-PAD) RuntimePath resolves to and which the Mac.apk file shim serves
    // in-place (no local-file UnityCache decompress). Empirically:
    //   - a filesystem dir (<dataDir>) -> RuntimePath <dataDir>/aa -> the player
    //     treats the bundles as local files and stalls in a UnityCache decompress;
    //   - null / APK_ASSETS (null assetsPath) -> RuntimePath "/aa" -> settings.json
    //     not found -> Addressables init never completes -> the first scene never
    //     loads (pre-age-gate stall).
    // So we return the jar streaming-assets base; RuntimePath then matches the
    // baseline and the boot proceeds exactly as it does with no PAD at all.
    private static boolean isCoreInstallTimePack(String packName) {
        // The engine names them "UnityDataAssetPack" / "UnityStreamingAssetsPack"
        // (the manual also documents the singular "UnityStreamingAssetPack").
        // No on-demand pack is "Unity"-prefixed, so this is an exact engine-pack
        // match, not a heuristic on app content.
        return packName != null && packName.startsWith("Unity");
    }

    // v1.0.1586 (docs/unity-addressables-apk-path-root-cause_2026-09-19.md):
    // the split that carries a pack, if the installed set has one. Unity's
    // core packs ("UnityDataAssetPack", "UnityStreamingAssetsPack") exist as
    // `split_<name>.apk` only when the developer chose the Play Asset Delivery
    // split layout (Among Us, Fruit Ninja); Crossy Road and Subway Surfers keep
    // that content in base.apk and ship NO such split. splitSourceDirs is the
    // real installed split list since v1.0.1586.
    private String coreSplitFor(String packName) {
        try {
            if (packName == null || context == null) return null;
            android.content.pm.ApplicationInfo ai = context.getApplicationInfo();
            String[] splits = ai != null ? ai.splitSourceDirs : null;
            if (splits == null) return null;
            String want = "split_" + packName + ".apk";
            for (String d : splits) {
                if (d != null && new java.io.File(d).getName().equals(want)) return d;
            }
        } catch (Throwable t) { /* no split list: no split */ }
        return null;
    }

    /** True when the core packs in {@code packNames} are ones this install does
     *  NOT ship as splits -- the case where a real Play install answers
     *  PACK_UNAVAILABLE and libunity keeps its own jar: streaming-assets path. */
    private boolean coreUnavailable(List<String> packNames) {
        if (packNames == null) return false;
        for (String n : packNames) {
            if (isCoreInstallTimePack(n) && coreSplitFor(n) == null) return true;
        }
        return false;
    }

    private AssetPackException packUnavailable(List<String> packNames) {
        // The message shape real Play Core produces; Unity's onComplete walks
        // the cause chain for an AssetPackException and reads getErrorCode().
        return new AssetPackException(
            "Asset Pack Download Error(-2): The requested asset pack is unavailable: " + packNames,
            com.google.android.play.core.assetpacks.model.AssetPackErrorCode.PACK_UNAVAILABLE);
    }

    @Override public AssetPackLocation getPackLocation(String packName) {
        if (isCoreInstallTimePack(packName)) {
            // THE DEVICE ANSWER (v1.0.1586, root-caused in docs/unity-addressables-
            // apk-path-root-cause_2026-09-19.md from libunity's own code at
            // Crossy 0x88854c / Subway 0x6fdbb0 and Unity's PlayAssetDeliveryUnity-
            // Wrapper bytecode): libunity only ever ASKS for this path after
            // getPackStates reported the pack COMPLETED, and it then stores
            //     m_StreamingAssetsPath = "file://" + getAssetPackPath(name)
            // -- so ANY non-null answer here becomes "file://<answer>" and any
            // APK_ASSETS answer becomes the literal "file://" (the "empty base"
            // behind file:///aa/...). For a pack Play cannot find, getPackStates
            // fails with PACK_UNAVAILABLE, libunity never asks, and
            // Application.streamingAssetsPath stays its computed default
            // jar:file://<apk>!/assets, which the engine's own VFS zip mount
            // serves from the APK. That is the path every Crossy/Subway Play
            // install takes, and it needs no extracted tree. The two earlier
            // "regimes" here (a jar: STORAGE_FILES answer; a catalog-format
            // detector choosing APK_ASSETS) were the same bug seen twice.
            String split = coreSplitFor(packName);
            if (split == null) {
                log("getPackLocation(" + packName + ") -> null (this install ships no split_"
                    + packName + ".apk; Play answers PACK_UNAVAILABLE, libunity keeps its jar: base)");
                return null;
            }
            // A pack that IS an installed split (Among Us's
            // split_UnityDataAssetPack.apk): Play reports it APK_ASSETS -- read
            // through AssetManager, which mounts that split.
            log("getPackLocation(" + packName + ") -> APK_ASSETS (null paths; the pack is " + split + ")");
            return AssetPackLocation.apkAssets();
        }
        // On-demand pack: its bundle is extracted under <dataDir>/aa/Android.
        // path() = pack root, assetsPath() = the assets subfolder; the C# PAD
        // runtime resolves the bundle relative to the catalog RuntimePath, and
        // the legacy provider only uses this as a presence check, so <dataDir>
        // is correct here.
        String root = assetsRoot();
        log("getPackLocation(" + packName + ") -> " + root);
        return AssetPackLocation.of(root, root);
    }

    @Override public Map<String, AssetPackLocation> getPackLocations() {
        return new LinkedHashMap<String, AssetPackLocation>();
    }

    @Override public AssetLocation getAssetLocation(String packName, String assetPath) {
        File f = new File(bundleDir(), assetPath);
        return AssetLocation.of(f.getAbsolutePath(), 0L, f.exists() ? f.length() : 0L);
    }

    @Override public Task<AssetPackStates> getPackStates(List<String> packNames) {
        // A core pack this install does not ship as a split is one a Play
        // install cannot find: the task FAILS with PACK_UNAVAILABLE(-2) (see the
        // getPackLocation comment). libunity treats -2 as "done, no pack" and
        // keeps its jar: streaming-assets base; answering COMPLETED instead made
        // it ask for a path and prefix "file://" to whatever came back.
        if (coreUnavailable(packNames)) {
            log("getPackStates(" + packNames + ") -> FAILED AssetPackException(PACK_UNAVAILABLE -2)");
            return Tasks.forException(packUnavailable(packNames));
        }
        log("getPackStates(" + packNames + ") -> all COMPLETED");
        // Real Play Core also drives registered listeners with the current
        // state; some Unity PAD init paths wait on the listener callback rather
        // than (or in addition to) the Task result, so notify COMPLETED here.
        notifyCompleted(packNames);
        return Tasks.forResult(statesFor(packNames));
    }

    @Override public Task<AssetPackStates> fetch(List<String> packNames) {
        if (coreUnavailable(packNames)) {
            log("fetch(" + packNames + ") -> FAILED AssetPackException(PACK_UNAVAILABLE -2)");
            return Tasks.forException(packUnavailable(packNames));
        }
        // Install-time packs are already present -> "fetch" completes instantly
        // as COMPLETED, and we notify any registered listeners (real Play Core
        // fires onStateUpdate during a fetch).
        log("fetch(" + packNames + ") -> COMPLETED + notify " + listeners.size() + " listeners");
        notifyCompleted(packNames);
        return Tasks.forResult(statesFor(packNames));
    }

    @Override public AssetPackStates cancel(List<String> packNames) {
        return statesFor(packNames);
    }

    @Override public void registerListener(AssetPackStateUpdateListener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
        log("registerListener -> " + listeners.size() + " total");
    }

    @Override public void unregisterListener(AssetPackStateUpdateListener listener) {
        if (listener != null) listeners.remove(listener);
    }

    @Override public void clearListeners() {
        listeners.clear();
    }

    @Override public Task<Void> removePack(String packName) {
        // We never delete the extracted bundles (they are the install image).
        return Tasks.forResult(null);
    }

    @Override public Task<Integer> showConfirmationDialog(Activity activity) {
        // No confirmation is ever required (everything is install-time).
        // Activity.RESULT_OK == -1.
        return Tasks.forResult(Integer.valueOf(-1));
    }

    @Override public Task<Integer> showCellularDataConfirmation(Activity activity) {
        return Tasks.forResult(Integer.valueOf(-1));
    }

    // Suppress unused-warning on the constant import while keeping it available
    // for readers correlating status() == AssetPackStatus.COMPLETED.
    @SuppressWarnings("unused")
    private static final int COMPLETED_REF = AssetPackStatus.COMPLETED;
}
