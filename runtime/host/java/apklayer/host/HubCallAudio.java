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
    private record Request(HubDirectory.Peer peer, int mode, AudioDeviceAttributes device, boolean privileged, long since) {}
    private static final long GRACE_MS = 6000, NATIVE_POLL_MS = 1000;  // AudioService.CHECK_MODE_FOR_UID_PERIOD_MS; how often a native stream's start or end is looked for
    private final List<Request> modes = new ArrayList<>(), routes = new ArrayList<>();  // oldest first
    private final HubPackages packages;
    private final HubDirectory directory;
    private final ExecutorService pushes = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "hub-call-audio"); t.setDaemon(true); return t; });  // in order, off the caller's lock
    private final java.util.Set<Integer> playing = new java.util.HashSet<>(), reporters = new java.util.HashSet<>();  // pids of the processes with a player or recorder started; of those that ever reported one
    private final java.util.concurrent.ScheduledExecutorService timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "hub-call-audio-timer"); t.setDaemon(true); return t; });
    private boolean polling;
    private int appliedMode = AudioSystem.MODE_NORMAL, appliedOwner = -1;  // the owner's pid
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
        if (mode != AudioSystem.MODE_NORMAL) modes.add(new Request(peer, mode, null, privileged, System.nanoTime() / 1_000_000));
        apply(peer);
        if (mode == AudioSystem.MODE_IN_COMMUNICATION) timer.schedule(this::recheck, GRACE_MS + 100, java.util.concurrent.TimeUnit.MILLISECONDS);  // the new requester's grace ends
        return appliedMode;
    }

    /** AudioDeviceBroker.setCommunicationDevice: {@code device} null clears this requester's choice. */
    synchronized void setCommunicationDevice(HubDirectory.Peer peer, AudioDeviceAttributes device) {
        routes.removeIf(request -> request.peer().pid() == peer.pid());
        if (device != null) routes.add(new Request(peer, 0, device, false, 0));
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

    /** The caller's process has a player or recorder started, or none (PlayerBase/AudioRecord, ShadowHostPlayerBase): who owns the mode may change. */
    synchronized void activity(HubDirectory.Peer peer, boolean on) {
        reporters.add(peer.pid());
        if (on ? playing.add(peer.pid()) : playing.remove(peer.pid())) apply(null);
    }

    synchronized void died(HubDirectory.Peer peer) {
        playing.remove(peer.pid());
        reporters.remove(peer.pid());
        if (modes.removeIf(request -> request.peer().pid() == peer.pid()) | routes.removeIf(request -> request.peer().pid() == peer.pid())) apply(null);
    }

    /**
     * AudioService.getAudioModeOwnerHandler over the requests. Its activity (SetModeDeathHandler.isActive): privileged requesters, MODE_RINGTONE and
     * MODE_CALL_SCREENING always; MODE_IN_COMMUNICATION while the requester plays or records (the processes' reports, activity) or in its first 6 s
     * (GRACE_MS), and the newest such requester wins. A native (AAudio/OpenSL) stream sends no report: a requester whose process never reported counts
     * as playing while the audio policy has a voice call stream or a voice communication capture active (it cannot say whose it is).
     * shortcut: that policy answer is the instance's, not the process's, and is looked for every NATIVE_POLL_MS while it can matter (two or more
     * communication requesters, one of them silent in reports); upgrade with the per-client playback configurations of the policy service.
     * With no active requester the oldest MODE_IN_COMMUNICATION one stays the owner (a lone native call app keeps its mode).
     */
    private Request owner() {
        Request privileged = null, active = null, oldestCommunication = null, assistant = null;
        long now = System.nanoTime() / 1_000_000;
        for (Request request : modes) {  // oldest first: a later one replaces an earlier one
            if (request.privileged() && request.mode() != AudioSystem.MODE_ASSISTANT_CONVERSATION) privileged = request;
            else if (request.mode() == AudioSystem.MODE_IN_COMMUNICATION) {
                if (oldestCommunication == null) oldestCommunication = request;
                if (playing.contains(request.peer().pid()) || now - request.since() < GRACE_MS || (!reporters.contains(request.peer().pid()) && nativeActive())) active = request;
            }
            else if (request.mode() == AudioSystem.MODE_RINGTONE || request.mode() == AudioSystem.MODE_CALL_SCREENING) active = request;
            else if (request.mode() == AudioSystem.MODE_ASSISTANT_CONVERSATION) assistant = request;
        }
        return privileged != null ? privileged : active != null ? active : oldestCommunication != null ? oldestCommunication : assistant;
    }

    private Boolean nativeActive;  // the policy's answer within one owner() pass
    private boolean nativeActive() {
        if (nativeActive == null) {
            NativeMedia.ensureInitialized();
            nativeActive = AudioSystem.isStreamActive(AudioSystem.STREAM_VOICE_CALL, 0) || AudioSystem.isSourceActive(7);  // MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
        return nativeActive;
    }

    private synchronized void recheck() { polling = false; apply(null); }

    /** Sets what the requests ask on the policy; a change goes to every app but {@code requester} (its own listeners run in its process). */
    private void apply(HubDirectory.Peer requester) {
        nativeActive = null;
        Request owner = owner();
        if (!polling && nativeAmbiguous()) { polling = true; timer.schedule(this::recheck, NATIVE_POLL_MS, java.util.concurrent.TimeUnit.MILLISECONDS); }
        int mode = owner == null ? AudioSystem.MODE_NORMAL : owner.mode();
        boolean changed = false;
        int uid = owner == null ? android.os.Process.SYSTEM_UID : owner.peer().appUid();
        int pid = owner == null ? -1 : owner.peer().pid();
        if (mode != appliedMode || pid != appliedOwner) {  // another owner of the same mode: the policy hears its uid
            int status = AudioSystem.setPhoneState(mode, uid);
            System.out.println("[apklayer-hub] audio mode " + mode + (owner == null ? "" : " of " + owner.peer().name() + " pid " + owner.peer().pid()) + (status == AudioSystem.SUCCESS ? "" : " refused by the policy: " + status));
            if (status == AudioSystem.SUCCESS) { changed = mode != appliedMode; appliedMode = mode; appliedOwner = pid; }
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

    /** Two or more communication requesters, one of whose processes never reported a stream: a native stream's start or end may change the owner. */
    private boolean nativeAmbiguous() {
        int communication = 0;
        boolean silent = false;
        for (Request request : modes) if (request.mode() == AudioSystem.MODE_IN_COMMUNICATION) { communication++; silent |= !reporters.contains(request.peer().pid()); }
        return communication >= 2 && silent;
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
