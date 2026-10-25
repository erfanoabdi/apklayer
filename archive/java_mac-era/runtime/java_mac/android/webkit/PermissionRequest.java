package android.webkit;

import android.net.Uri;

// android.webkit.PermissionRequest (AOSP API-21).
// Passed to WebChromeClient.onPermissionRequest for WebRTC camera/mic and
// protected-media access. Abstract in AOSP. The RESOURCE_* strings are the
// stable permission identifiers a page requests. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/PermissionRequest.java
public abstract class PermissionRequest {

    public static final String RESOURCE_VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE";
    public static final String RESOURCE_AUDIO_CAPTURE = "android.webkit.resource.AUDIO_CAPTURE";
    public static final String RESOURCE_PROTECTED_MEDIA_ID = "android.webkit.resource.PROTECTED_MEDIA_ID";
    public static final String RESOURCE_MIDI_SYSEX = "android.webkit.resource.MIDI_SYSEX";

    public abstract Uri getOrigin();

    public abstract String[] getResources();

    public abstract void grant(String[] resources);

    public abstract void deny();
}
