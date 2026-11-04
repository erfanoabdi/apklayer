package apklayer.host;

import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import com.android.media.permission.UidPackageState.PackageState;
import com.android.server.audio.AudioServerPermissionProvider;
import java.util.HashMap;
import java.util.Map;

/**
 * system_server's part in audioserver's permission controller (AudioService.initializeAudioServerPermissionProvider, with the framework's own
 * AudioServerPermissionProvider): which app id is which package and which uids hold the audio permissions. Unfed, AudioPolicyService refused every app's
 * track (getOutputForAttr's permission checks: "NPC::checkPermission: controller never populated by system_server", -38 to AudioTrack).
 * Fed as AudioService does: whenever audioserver registers (the hub's start; a restarted audioserver starts unfed), after the hub's installer added,
 * updated or removed a package, and when an app the last feed did not know registers with the hub (installed by the host's `apklayer install` while
 * the hub ran: its tracks would pass the permission lists and fail the package checks, "uid not found").
 */
final class HubAudioPermissions {
    private HubAudioPermissions() {}
    private static final String POLICY = "media.audio_policy";
    private static final Map<String, Object> fed = new HashMap<>();  // package -> its parsed package in the last feed (HubPackages parses anew after every install)

    /** Runs for the hub's life: feeds each audioserver as it registers, again after one died and its successor came. */
    static void watch(HubPackages packages) {
        while (true) {
            IBinder policy = RealBinder.await(POLICY, 30_000);
            if (policy == null) continue;  // no audio services (yet): the next wait ends at their registration
            var gone = new java.util.concurrent.CountDownLatch(1);
            try { policy.linkToDeath(gone::countDown, 0); }
            catch (android.os.RemoteException dead) { continue; }  // the dead one is still listed: a later wait returns its successor
            feed(packages);
            try { gone.await(); } catch (InterruptedException interrupted) { return; }
        }
    }

    /** An app registers with the hub: feeds first when the last feed did not know this install of its package. */
    static void registered(HubPackages packages, String name) {
        Object parsed = packages.get(name);
        synchronized (fed) { if (parsed != null && fed.get(name) == parsed) return; }
        feed(packages);
    }

    static void feed(HubPackages packages) {
        IBinder policy = RealBinder.lookup(POLICY);
        if (policy == null) return;  // an instance without the audio services
        synchronized (fed) { feed(packages, policy); }
    }

    private static void feed(HubPackages packages, IBinder policy) {
        fed.clear();
        Map<Integer, Map<String, PackageState>> byAppId = new HashMap<>();
        add(byAppId, Process.SYSTEM_UID, "android", Build.VERSION.SDK_INT, true);  // the framework package: the system uid holds every permission
        for (String name : packages.installed()) {
            int uid = packages.uid(name);
            var parsed = packages.get(name);
            if (uid < 0 || parsed == null) continue;
            add(byAppId, UserHandle.getAppId(uid), name, parsed.applicationInfo.targetSdkVersion, parsed.applicationInfo.isAudioPlaybackCaptureAllowed());
            fed.put(name, parsed);
        }
        var provider = new AudioServerPermissionProvider(byAppId, packages::uidHolds, () -> new int[] {0});
        try {
            provider.onServiceStart(android.media.IAudioPolicyService.Stub.asInterface(policy).getPermissionController());
            System.out.println("[apklayer-hub] audio permissions fed: " + byAppId.size() + " app ids");
        } catch (android.os.RemoteException failure) { fed.clear(); System.out.println("[apklayer-hub] audio permissions not fed: " + failure); }
    }

    private static void add(Map<Integer, Map<String, PackageState>> byAppId, int appId, String name, int targetSdk, boolean capture) {
        PackageState state = new PackageState();
        state.packageName = name; state.targetSdk = targetSdk; state.isPlaybackCaptureAllowed = capture; state.pccId = Process.INVALID_UID;
        byAppId.computeIfAbsent(appId, unused -> new HashMap<>()).put(name, state);
    }
}
