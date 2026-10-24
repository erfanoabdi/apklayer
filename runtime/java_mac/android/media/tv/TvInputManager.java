package android.media.tv;

import java.util.Collections;
import java.util.List;

/**
 * Android TV input framework (tuners, HDMI inputs, TV apps).
 *
 * Mac.apk is not a TV device and declares no TV inputs, which is what AOSP
 * reports on any phone or tablet: an empty input list. 19 of 37 corpus apps
 * reference the type without calling into it -- a leaf branch of some media
 * capability check -- so it only needs to exist and answer honestly.
 *
 * Source: frameworks/base/media/java/android/media/tv/TvInputManager.java
 */
public final class TvInputManager {

    public static final int VIDEO_UNAVAILABLE_REASON_UNKNOWN = 0;
    public static final int VIDEO_UNAVAILABLE_REASON_TUNING = 1;
    public static final int VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL = 2;
    public static final int VIDEO_UNAVAILABLE_REASON_BUFFERING = 3;
    public static final int VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY = 4;

    public static final int INPUT_STATE_CONNECTED = 0;
    public static final int INPUT_STATE_CONNECTED_STANDBY = 1;
    public static final int INPUT_STATE_DISCONNECTED = 2;

    public TvInputManager() { }

    public List<TvInputInfo> getTvInputList() { return Collections.emptyList(); }

    public TvInputInfo getTvInputInfo(String inputId) { return null; }

    public int getInputState(String inputId) { return INPUT_STATE_DISCONNECTED; }

    public void registerCallback(TvInputCallback callback, android.os.Handler handler) { }

    public void unregisterCallback(TvInputCallback callback) { }

    /**
     * API 21. AOSP reads the parental-controls master switch out of
     * Settings.Secure (TV_PARENTAL_CONTROLS_ENABLED, which only the TV Settings
     * app writes). Mac.apk is not a TV device and has no such setting, so
     * parental controls are OFF -- which is exactly what a phone reports, and
     * what makes isRatingBlocked() below answer false consistently.
     */
    public boolean isParentalControlsEnabled() { return false; }

    /**
     * AOSP: whether the given rating is on the user's blocked list. With
     * parental controls off (above) and an empty blocked list (below), nothing
     * is blocked.
     *
     * ⚠ Answering FALSE here is the SAFE direction only because it matches the
     * device state we honestly report: parental controls are not configured, so
     * nothing has been blocked. It is not a bypass -- an app that enabled
     * parental controls and expected a block would first have to find
     * isParentalControlsEnabled() true, which it never is here.
     */
    public boolean isRatingBlocked(TvContentRating rating) {
        java.util.Objects.requireNonNull(rating, "rating cannot be null");
        return false;
    }

    /** AOSP: the ratings the user has blocked. Empty, per
     *  isParentalControlsEnabled() above. */
    public List<TvContentRating> getBlockedRatings() { return Collections.emptyList(); }

    /**
     * API 26. A TvInputService updates the description of an input it owns.
     * There are no inputs on this device, so AOSP's own path here -- which
     * throws IllegalArgumentException for an input the service does not own --
     * is the honest answer for every argument, rather than silently accepting an
     * update that is stored nowhere and can never be read back.
     */
    public void updateTvInputInfo(TvInputInfo inputInfo) {
        java.util.Objects.requireNonNull(inputInfo, "inputInfo cannot be null");
        throw new IllegalArgumentException("inputInfo is not owned by this app: " + inputInfo);
    }

    /** AOSP declares this as an abstract class apps subclass. */
    public abstract static class TvInputCallback {
        public void onInputStateChanged(String inputId, int state) { }
        public void onInputAdded(String inputId) { }
        public void onInputRemoved(String inputId) { }
        public void onInputUpdated(String inputId) { }
        /** API 26. Fired when a service calls updateTvInputInfo(); never here,
         *  because that call cannot succeed (see above). */
        public void onTvInputInfoUpdated(TvInputInfo inputInfo) { }
    }
}
