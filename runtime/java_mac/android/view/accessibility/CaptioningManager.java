package android.view.accessibility;

import android.graphics.Color;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * Closed-caption appearance preferences.
 *
 * macOS does expose a system captioning preference pane, but it is not readable
 * without a private API, and AOSP's contract on a device where the user has
 * never enabled captions is exactly what we report: isEnabled() false and the
 * platform default style. Media players call isEnabled() first and only consult
 * the style when it is true, so this degrades the way AOSP intends rather than
 * inventing values.
 *
 * Source: frameworks/base/core/java/android/view/accessibility/CaptioningManager.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/view/accessibility/CaptioningManager.java
 */
public class CaptioningManager {

    private final java.util.ArrayList<CaptioningChangeListener> mListeners =
            new java.util.ArrayList<>();

    public CaptioningManager() { }

    /** No captioning preference is set, so AOSP reports captions off. */
    public boolean isEnabled() { return false; }

    /** AOSP returns null when the user has not chosen a captioning locale. */
    public Locale getLocale() { return null; }

    /** AOSP's default when no scale preference exists is 1.0. */
    public float getFontScale() { return 1.0f; }

    public CaptionStyle getUserStyle() { return CaptionStyle.DEFAULT; }

    /**
     * Listeners are retained so add/remove pair correctly and an app that
     * unregisters in onDestroy does not leak, but nothing ever fires them:
     * there is no captioning preference on this platform to change.
     */
    public void addCaptioningChangeListener(CaptioningChangeListener listener) {
        if (listener == null) return;
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) mListeners.add(listener);
        }
    }

    public void removeCaptioningChangeListener(CaptioningChangeListener listener) {
        synchronized (mListeners) { mListeners.remove(listener); }
    }

    /**
     * AOSP exposes the style fields as public finals and pairs each with a
     * has*() predicate, because a style may legitimately leave a component
     * unspecified (meaning "inherit from the track"). Apps read the predicate
     * before the field, so both halves must exist.
     */
    public static final class CaptionStyle {

        public static final int EDGE_TYPE_UNSPECIFIED = -1;
        public static final int EDGE_TYPE_NONE = 0;
        public static final int EDGE_TYPE_OUTLINE = 1;
        public static final int EDGE_TYPE_DROP_SHADOW = 2;
        public static final int EDGE_TYPE_RAISED = 3;
        public static final int EDGE_TYPE_DEPRESSED = 4;

        private static final int COLOR_UNSPECIFIED = 0x000000FF;

        public final int foregroundColor;
        public final int backgroundColor;
        public final int edgeType;
        public final int edgeColor;
        public final int windowColor;

        private final boolean mHasForegroundColor;
        private final boolean mHasBackgroundColor;
        private final boolean mHasEdgeType;
        private final boolean mHasEdgeColor;
        private final boolean mHasWindowColor;

        private final String mRawTypeface;

        /** AOSP's DEFAULT: white on black, no edge, transparent window. */
        public static final CaptionStyle DEFAULT = new CaptionStyle(
                Color.WHITE, Color.BLACK, EDGE_TYPE_NONE, Color.BLACK,
                Color.TRANSPARENT, null);

        private CaptionStyle(int foregroundColor, int backgroundColor, int edgeType,
                             int edgeColor, int windowColor, String rawTypeface) {
            mHasForegroundColor = foregroundColor != COLOR_UNSPECIFIED;
            mHasBackgroundColor = backgroundColor != COLOR_UNSPECIFIED;
            mHasEdgeType = edgeType != EDGE_TYPE_UNSPECIFIED;
            mHasEdgeColor = edgeColor != COLOR_UNSPECIFIED;
            mHasWindowColor = windowColor != COLOR_UNSPECIFIED;

            this.foregroundColor = foregroundColor;
            this.backgroundColor = backgroundColor;
            this.edgeType = edgeType;
            this.edgeColor = edgeColor;
            this.windowColor = windowColor;
            mRawTypeface = rawTypeface;
        }

        public boolean hasForegroundColor() { return mHasForegroundColor; }
        public boolean hasBackgroundColor() { return mHasBackgroundColor; }
        public boolean hasEdgeType() { return mHasEdgeType; }
        public boolean hasEdgeColor() { return mHasEdgeColor; }
        public boolean hasWindowColor() { return mHasWindowColor; }

        /** AOSP returns null when no typeface is specified. */
        public Typeface getTypeface() {
            if (mRawTypeface == null || mRawTypeface.isEmpty()) return null;
            return Typeface.create(mRawTypeface, Typeface.NORMAL);
        }
    }

    /**
     * AOSP declares this as an abstract class with empty default methods, NOT
     * an interface, so apps subclass it and override only what they care
     * about. Declaring it as an interface here would break every such subclass
     * at class-load with an IncompatibleClassChangeError.
     */
    public abstract static class CaptioningChangeListener {
        /** AOSP CaptioningChangeListener.onSystemAudioCaptioningChanged(boolean)
         *  / onSystemAudioCaptioningUiChanged(boolean) (API 31). AOSP's bodies
         *  are empty -- a listener overrides only the callbacks it cares about.
         *  Neither ever fires here (see isSystemAudioCaptioningEnabled). */
        public void onSystemAudioCaptioningChanged(boolean enabled) { }
        public void onSystemAudioCaptioningUiChanged(boolean enabled) { }
        public void onEnabledChanged(boolean enabled) { }
        public void onUserStyleChanged(CaptionStyle userStyle) { }
        public void onLocaleChanged(Locale locale) { }
        public void onFontScaleChanged(float fontScale) { }
    }

    /** AOSP CaptioningManager.isSystemAudioCaptioningEnabled() (API 31): is the
     *  system-wide "live caption" feature on? macOS has no such setting and
     *  Mac.apk exposes none, so it is off -- the same answer a device gives with
     *  live caption disabled, which is the default. */
    public boolean isSystemAudioCaptioningEnabled() { return false; }

    /** AOSP CaptioningManager.isSystemAudioCaptioningUiEnabled() (API 31):
     *  should the volume dialog show the live-caption toggle? Off, consistently
     *  with isSystemAudioCaptioningEnabled above. */
    public boolean isSystemAudioCaptioningUiEnabled() { return false; }

    /** AOSP CaptioningManager.isCallCaptioningEnabled() (API 35): is call
     *  captioning available on this device? There is no telephony captioning
     *  service here. */
    public boolean isCallCaptioningEnabled() { return false; }
}
