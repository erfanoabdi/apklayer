package apklayer.host;

import android.content.Intent;
import android.media.AudioDeviceAttributes;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.audiopolicy.AudioProductStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * system_server's call audio state (AudioService.setMode with its SetModeDeathHandlers, AudioDeviceBroker's communication route clients,
 * setMicrophoneMute), which apklayer does not run: the instance's one audio mode, communication device and microphone mute, set on the policy from
 * the hub (the system uid: setPhoneState and the communication strategy's preferred device need MODIFY_AUDIO_SETTINGS / MODIFY_AUDIO_ROUTING).
 * A requester's state ends with its process. The mode owner is AudioService's getAudioModeOwnerHandler: the newest privileged requester
 * (MODIFY_PHONE_STATE), else the newest active one, else the newest MODE_ASSISTANT_CONVERSATION one. A change reaches every other app's
 * listeners (ENDPOINT_CALL_AUDIO: OnModeChangedListener, OnCommunicationDeviceChangedListener); a mute change is ACTION_MICROPHONE_MUTE_CHANGED.
 */
final class HubCallAudio {
    private record Request(HubDirectory.Peer peer, int mode, AudioDeviceAttributes device, boolean privileged) {}
    private final List<Request> modes = new ArrayList<>(), routes = new ArrayList<>();  // oldest first
    private final HubPackages packages;
    private final HubDirectory directory;
    private final ExecutorService pushes = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "hub-call-audio"); t.setDaemon(true); return t; });  // in order, off the caller's lock
    private int appliedMode = AudioSystem.MODE_NORMAL;
    private AudioDeviceAttributes appliedDevice;
    private int strategy = -1;

    HubCallAudio(HubPackages packages, HubDirectory directory) { this.packages = packages; this.directory = directory; }

    /** AudioService.setMode: MODE_CURRENT only reads; the call modes need MODIFY_PHONE_STATE. The mode in force after the call. */
    synchronized int setMode(HubDirectory.Peer peer, int mode) {
        if (mode < AudioSystem.MODE_CURRENT || mode >= AudioSystem.NUM_MODES || mode == AudioSystem.MODE_CURRENT) return appliedMode;
        boolean privileged = packages.uidHolds(peer.appUid(), android.Manifest.permission.MODIFY_PHONE_STATE);
        if ((mode == AudioSystem.MODE_IN_CALL || mode == AudioSystem.MODE_CALL_REDIRECT || mode == AudioSystem.MODE_COMMUNICATION_REDIRECT
                || mode == AudioSystem.MODE_CALL_SCREENING) && !privileged) {
            System.out.println("[apklayer-hub] setMode(" + mode + ") refused: " + peer.name() + " lacks MODIFY_PHONE_STATE");
            return appliedMode;
        }
        modes.removeIf(request -> request.peer().pid() == peer.pid());
        if (mode != AudioSystem.MODE_NORMAL) modes.add(new Request(peer, mode, null, privileged));
        apply(peer);
        return appliedMode;
    }

    /** AudioDeviceBroker.setCommunicationDevice: {@code device} null clears this requester's choice. */
    synchronized void setCommunicationDevice(HubDirectory.Peer peer, AudioDeviceAttributes device) {
        routes.removeIf(request -> request.peer().pid() == peer.pid());
        if (device != null) routes.add(new Request(peer, 0, device, false));
        apply(peer);
    }
    synchronized AudioDeviceAttributes communicationDevice() { return appliedDevice; }

    /** AudioService.setMicrophoneMute: a caller without MODIFY_AUDIO_SETTINGS changes nothing; the mute is AudioFlinger's (the HAL silences capture).
     *  A change is broadcast to registered receivers, as AudioService's setMicrophoneMuteNoCallerCheck does. */
    synchronized boolean setMicrophoneMute(HubDirectory.Peer peer, boolean on) {
        NativeMedia.ensureInitialized();
        boolean before = AudioSystem.isMicrophoneMuted();
        if (packages.uidHolds(peer.appUid(), android.Manifest.permission.MODIFY_AUDIO_SETTINGS) && before != on) {
            int status = AudioSystem.muteMicrophone(on);
            if (status != AudioSystem.SUCCESS) System.out.println("[apklayer-hub] microphone mute " + on + " refused: " + status);
        }
        boolean now = AudioSystem.isMicrophoneMuted();
        if (now != before) {
            System.out.println("[apklayer-hub] microphone " + (now ? "muted" : "unmuted") + " by " + peer.name());
            pushes.execute(() -> directory.sendSystem(new Intent(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY), false));
        }
        return now;
    }

    synchronized void died(HubDirectory.Peer peer) {
        if (modes.removeIf(request -> request.peer().pid() == peer.pid()) | routes.removeIf(request -> request.peer().pid() == peer.pid())) apply(null);
    }

    /**
     * AudioService.getAudioModeOwnerHandler over the requests. Its activity (SetModeDeathHandler.isActive): privileged requesters, MODE_RINGTONE and
     * MODE_CALL_SCREENING always; MODE_IN_COMMUNICATION while the requester plays or records voice communication, which AudioService only checks 6 s
     * after the request. shortcut: apklayer tracks no per-app playback, so the oldest MODE_IN_COMMUNICATION requester counts as the active one (the
     * app in a call keeps the mode until it releases it or dies; a later one only takes over then); upgrade with the apps' player reports.
     */
    private Request owner() {
        Request privileged = null, active = null, assistant = null;
        boolean communication = false;
        for (Request request : modes) {  // oldest first: a later one replaces an earlier one
            if (request.privileged() && request.mode() != AudioSystem.MODE_ASSISTANT_CONVERSATION) privileged = request;
            else if (request.mode() == AudioSystem.MODE_IN_COMMUNICATION) { if (!communication) active = request; communication = true; }
            else if (request.mode() == AudioSystem.MODE_RINGTONE || request.mode() == AudioSystem.MODE_CALL_SCREENING) active = request;
            else if (request.mode() == AudioSystem.MODE_ASSISTANT_CONVERSATION) assistant = request;
        }
        return privileged != null ? privileged : active != null ? active : assistant;
    }

    /** Sets what the requests ask on the policy; a change goes to every app but {@code requester} (its own listeners run in its process). */
    private void apply(HubDirectory.Peer requester) {
        Request owner = owner();
        int mode = owner == null ? AudioSystem.MODE_NORMAL : owner.mode();
        boolean changed = false;
        if (mode != appliedMode) {
            int status = AudioSystem.setPhoneState(mode, owner == null ? android.os.Process.SYSTEM_UID : owner.peer().appUid());
            System.out.println("[apklayer-hub] audio mode " + mode + (owner == null ? "" : " of " + owner.peer().name()) + (status == AudioSystem.SUCCESS ? "" : " refused by the policy: " + status));
            if (status == AudioSystem.SUCCESS) { appliedMode = mode; changed = true; }
        }
        AudioDeviceAttributes device = routes.isEmpty() ? null : routes.get(routes.size() - 1).device();
        if (!java.util.Objects.equals(device, appliedDevice) && strategy() >= 0) {
            int status = device == null ? AudioSystem.clearDevicesRoleForStrategy(strategy, AudioSystem.DEVICE_ROLE_PREFERRED)
                    : AudioSystem.setDevicesRoleForStrategy(strategy, AudioSystem.DEVICE_ROLE_PREFERRED, List.of(device));
            System.out.println("[apklayer-hub] communication device " + device + (status == AudioSystem.SUCCESS ? "" : " refused by the policy: " + status));
            if (status == AudioSystem.SUCCESS || device == null) { appliedDevice = device; changed = true; }  // clearing a role never set fails: nothing is preferred then either
        }
        if (!changed) return;
        int nowMode = appliedMode;
        AudioDeviceAttributes nowDevice = appliedDevice;
        pushes.execute(() -> {
            for (HubDirectory.Peer peer : directory.peersExcept(requester)) {
                try { HubProtocol.call(peer.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_CALL_AUDIO, request -> { request.writeInt(nowMode); request.writeTypedObject(nowDevice, 0); }, reply -> null); }
                catch (IllegalStateException gone) { /* it ended meanwhile */ }
            }
        });
    }

    /** AudioDeviceBroker.initRoutingStrategyIds: the strategy of STREAM_VOICE_CALL. */
    private int strategy() {
        if (strategy >= 0) return strategy;
        NativeMedia.ensureInitialized();
        java.util.ArrayList<AudioProductStrategy> strategies = new java.util.ArrayList<>();
        int status = AudioProductStrategy.native_list_audio_product_strategies(strategies);  // AudioSystemAdapter's list
        for (AudioProductStrategy candidate : strategies)
            if (candidate.getAudioAttributesForLegacyStreamType(AudioSystem.STREAM_VOICE_CALL) != null) { strategy = candidate.getId(); break; }
        if (strategy < 0) System.out.println("[apklayer-hub] no communication strategy (status " + status + ", " + strategies.size() + " strategies)");
        return strategy;
    }
}
