package apklayer.host;

import android.media.AudioDeviceAttributes;
import android.media.AudioSystem;
import android.media.audiopolicy.AudioProductStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * system_server's call audio state (AudioService.setMode with its SetModeDeathHandlers, AudioDeviceBroker's communication route clients), which
 * apklayer does not run: the instance's one audio mode and communication device, set on the policy from the hub (the system uid: setPhoneState and
 * the communication strategy's preferred device need MODIFY_AUDIO_SETTINGS / MODIFY_AUDIO_ROUTING). A requester's state ends with its process.
 * The newest requester wins (AudioService also weighs which requester plays audio).
 */
final class HubCallAudio {
    private record Request(HubDirectory.Peer peer, int mode, AudioDeviceAttributes device) {}
    private final List<Request> modes = new ArrayList<>(), routes = new ArrayList<>();  // oldest first
    private final HubPackages packages;
    private int appliedMode = AudioSystem.MODE_NORMAL;
    private AudioDeviceAttributes appliedDevice;
    private int strategy = -1;

    HubCallAudio(HubPackages packages) { this.packages = packages; }

    /** AudioService.setMode: MODE_CURRENT only reads; the call modes need MODIFY_PHONE_STATE. The mode in force after the call. */
    synchronized int setMode(HubDirectory.Peer peer, int mode) {
        if (mode < AudioSystem.MODE_CURRENT || mode >= AudioSystem.NUM_MODES || mode == AudioSystem.MODE_CURRENT) return appliedMode;
        if ((mode == AudioSystem.MODE_IN_CALL || mode == AudioSystem.MODE_CALL_REDIRECT || mode == AudioSystem.MODE_COMMUNICATION_REDIRECT
                || mode == AudioSystem.MODE_CALL_SCREENING) && !packages.uidHolds(peer.appUid(), android.Manifest.permission.MODIFY_PHONE_STATE)) {
            System.out.println("[apklayer-hub] setMode(" + mode + ") refused: " + peer.name() + " lacks MODIFY_PHONE_STATE");
            return appliedMode;
        }
        modes.removeIf(request -> request.peer().pid() == peer.pid());
        if (mode != AudioSystem.MODE_NORMAL) modes.add(new Request(peer, mode, null));
        apply();
        return appliedMode;
    }

    /** AudioDeviceBroker.setCommunicationDevice: {@code device} null clears this requester's choice. */
    synchronized void setCommunicationDevice(HubDirectory.Peer peer, AudioDeviceAttributes device) {
        routes.removeIf(request -> request.peer().pid() == peer.pid());
        if (device != null) routes.add(new Request(peer, 0, device));
        apply();
    }
    synchronized AudioDeviceAttributes communicationDevice() { return appliedDevice; }

    synchronized void died(HubDirectory.Peer peer) {
        if (modes.removeIf(request -> request.peer().pid() == peer.pid()) | routes.removeIf(request -> request.peer().pid() == peer.pid())) apply();
    }

    private void apply() {
        Request owner = modes.isEmpty() ? null : modes.get(modes.size() - 1);
        int mode = owner == null ? AudioSystem.MODE_NORMAL : owner.mode();
        if (mode != appliedMode) {
            int status = AudioSystem.setPhoneState(mode, owner == null ? android.os.Process.SYSTEM_UID : owner.peer().appUid());
            System.out.println("[apklayer-hub] audio mode " + mode + (status == AudioSystem.SUCCESS ? "" : " refused by the policy: " + status));
            if (status == AudioSystem.SUCCESS) appliedMode = mode;
        }
        AudioDeviceAttributes device = routes.isEmpty() ? null : routes.get(routes.size() - 1).device();
        if (java.util.Objects.equals(device, appliedDevice) || strategy() < 0) return;
        int status = device == null ? AudioSystem.clearDevicesRoleForStrategy(strategy, AudioSystem.DEVICE_ROLE_PREFERRED)
                : AudioSystem.setDevicesRoleForStrategy(strategy, AudioSystem.DEVICE_ROLE_PREFERRED, List.of(device));
        System.out.println("[apklayer-hub] communication device " + device + (status == AudioSystem.SUCCESS ? "" : " refused by the policy: " + status));
        if (status == AudioSystem.SUCCESS || device == null) appliedDevice = device;  // clearing a role never set fails: nothing is preferred then either
    }

    /** AudioDeviceBroker.initRoutingStrategyIds: the strategy of STREAM_VOICE_CALL. */
    private int strategy() {
        java.util.ArrayList<AudioProductStrategy> strategies = new java.util.ArrayList<>();
        if (strategy < 0 && AudioProductStrategy.native_list_audio_product_strategies(strategies) == AudioSystem.SUCCESS)  // AudioSystemAdapter's list
            for (AudioProductStrategy candidate : strategies)
                if (candidate.getAudioAttributesForLegacyStreamType(AudioSystem.STREAM_VOICE_CALL) != null) { strategy = candidate.getId(); break; }
        return strategy;
    }
}
