package apklayer.host;

import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import com.android.media.permission.UidPackageState.PackageState;
import com.android.server.audio.AudioServerPermissionProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * system_server's part in audioserver's permission controller (AudioService.initializeAudioServerPermissionProvider, with the framework's own
 * AudioServerPermissionProvider): which app id is which package and which uids hold the audio permissions. Unfed, AudioPolicyService refused every app's
 * track (getOutputForAttr's permission checks: "NPC::checkPermission: controller never populated by system_server", -38 to AudioTrack).
 * shortcut: an app installed while the hub runs is fed at the next stack start (AudioService follows ACTION_PACKAGE_ADDED); fine while installs restart it.
 */
final class HubAudioPermissions {
    private HubAudioPermissions() {}

    static void feed(HubPackages packages) {
        IBinder policy = RealBinder.lookup("media.audio_policy");
        if (policy == null) return;  // an instance without the audio services
        Map<Integer, Map<String, PackageState>> byAppId = new HashMap<>();
        Map<Integer, List<String>> byUid = new HashMap<>();
        add(byAppId, Process.SYSTEM_UID, "android", Build.VERSION.SDK_INT, true);  // the framework package: the system uid holds every permission
        for (String name : packages.installed()) {
            int uid = packages.uid(name);
            var parsed = packages.get(name);
            if (uid < 0 || parsed == null) continue;
            add(byAppId, UserHandle.getAppId(uid), name, parsed.applicationInfo.targetSdkVersion, parsed.applicationInfo.isAudioPlaybackCaptureAllowed());
            byUid.computeIfAbsent(UserHandle.getAppId(uid), unused -> new ArrayList<>()).add(name);
        }
        var provider = new AudioServerPermissionProvider(byAppId, (uid, permission) -> UserHandle.getAppId(uid) < Process.FIRST_APPLICATION_UID
                || byUid.getOrDefault(UserHandle.getAppId(uid), List.of()).stream().anyMatch(name -> packages.holds(name, permission)), () -> new int[] {0});
        try {
            provider.onServiceStart(android.media.IAudioPolicyService.Stub.asInterface(policy).getPermissionController());
            System.out.println("[apklayer-hub] audio permissions fed: " + byAppId.size() + " app ids");
        } catch (android.os.RemoteException failure) { System.out.println("[apklayer-hub] audio permissions not fed: " + failure); }
    }

    private static void add(Map<Integer, Map<String, PackageState>> byAppId, int appId, String name, int targetSdk, boolean capture) {
        PackageState state = new PackageState();
        state.packageName = name; state.targetSdk = targetSdk; state.isPlaybackCaptureAllowed = capture; state.pccId = Process.INVALID_UID;
        byAppId.computeIfAbsent(appId, unused -> new HashMap<>()).put(name, state);
    }
}
