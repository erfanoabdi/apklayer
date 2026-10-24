package android.media;
// android.media.AudioDeviceCallback (API 23+). Apps register one with
// AudioManager.registerAudioDeviceCallback to learn about device hotplug; on
// macOS our device set is static so no callbacks fire, but the abstract class
// must exist + be subclassable (GeoDash Meltdown subclasses it).
public abstract class AudioDeviceCallback {
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {}
    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {}
}
