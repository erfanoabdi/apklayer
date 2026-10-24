package android.media;

import android.content.Context;
import android.os.Handler;

/**
 * Conditional-access (CAS) plugin interface -- the broadcast-TV analogue of
 * MediaDrm, used to descramble a DVB/ATSC transport stream.
 *
 * Mac.apk has NO CAS plugin of any kind, and would not add one: descrambling a
 * protected broadcast is the same category of thing this project explicitly
 * keeps out of scope (CLAUDE.md, "Explicitly OUT of scope"). AOSP's own contract
 * for a CA system the device cannot service is
 * MediaCasException.UnsupportedCasException from the constructor, and
 * isSystemIdSupported() answering false -- which is exactly what an ordinary
 * phone (no CAS plugins at all) reports, and what apps already branch on.
 *
 * The class exists so MediaExtractor.setMediaCas() and the tuner APIs can be
 * DECLARED, and so an app's capability probe links and gets a truthful "no".
 *
 * Source: frameworks/base/media/java/android/media/MediaCas.java
 * (android16-release).
 */
public final class MediaCas implements AutoCloseable {

    public static final int PLUGIN_STATUS_PHYSICAL_MODULE_CHANGED = 0;
    public static final int PLUGIN_STATUS_SESSION_NUMBER_CHANGED = 1;

    public static final int SCRAMBLING_MODE_RESERVED = 0;
    public static final int SCRAMBLING_MODE_DVB_CSA1 = 1;
    public static final int SCRAMBLING_MODE_DVB_CSA2 = 2;
    public static final int SCRAMBLING_MODE_DVB_CSA3_STANDARD = 3;
    public static final int SCRAMBLING_MODE_DVB_CSA3_MINIMAL = 4;
    public static final int SCRAMBLING_MODE_DVB_CSA3_ENHANCE = 5;
    public static final int SCRAMBLING_MODE_DVB_CISSA_V1 = 6;
    public static final int SCRAMBLING_MODE_DVB_IDSA = 7;
    public static final int SCRAMBLING_MODE_MULTI2 = 8;
    public static final int SCRAMBLING_MODE_AES128 = 9;
    public static final int SCRAMBLING_MODE_AES_ECB = 10;
    public static final int SCRAMBLING_MODE_AES_SCTE52 = 11;
    public static final int SCRAMBLING_MODE_TDES_ECB = 12;
    public static final int SCRAMBLING_MODE_TDES_SCTE52 = 13;
    public static final int SCRAMBLING_MODE_AES_CBC = 14;

    public static final int SESSION_USAGE_LIVE = 0;
    public static final int SESSION_USAGE_PLAYBACK = 1;
    public static final int SESSION_USAGE_RECORD = 2;
    public static final int SESSION_USAGE_TIMESHIFT = 3;

    public MediaCas(int casSystemId) throws MediaCasException.UnsupportedCasException {
        throw new MediaCasException.UnsupportedCasException(
                "No CAS plugin is available for system id " + casSystemId);
    }

    public MediaCas(Context context, int casSystemId, String tvInputServiceSessionId,
            int priorityHint) throws MediaCasException.UnsupportedCasException {
        this(casSystemId);
    }

    public MediaCas(Context context, int casSystemId, String tvInputServiceSessionId,
            int priorityHint, Handler handler, EventListener listener)
            throws MediaCasException.UnsupportedCasException {
        this(casSystemId);
    }

    /** AOSP: whether the device has a plugin for the CA system. None here. */
    public static boolean isSystemIdSupported(int casSystemId) { return false; }

    /** AOSP: the plugins the device has. None here, so an empty array -- which
     *  is AOSP's own answer on a device with no CAS plugins. */
    public static PluginDescriptor[] enumeratePlugins() { return new PluginDescriptor[0]; }

    private static IllegalStateException noPlugin() {
        return new IllegalStateException("No CAS plugin available");
    }

    public Session openSession() throws MediaCasException { throw noPlugin(); }
    public Session openSession(int sessionUsage, int scramblingMode) throws MediaCasException {
        throw noPlugin();
    }
    public void setPrivateData(byte[] data) throws MediaCasException { throw noPlugin(); }
    public void processEmm(byte[] data) throws MediaCasException { throw noPlugin(); }
    public void processEmm(byte[] data, int offset, int length) throws MediaCasException {
        throw noPlugin();
    }
    public void sendEvent(int event, int arg, byte[] data) throws MediaCasException {
        throw noPlugin();
    }
    public void provision(String provisionString) throws MediaCasException { throw noPlugin(); }
    public void refreshEntitlements(int refreshType, byte[] refreshData)
            throws MediaCasException {
        throw noPlugin();
    }
    public void setEventListener(EventListener listener, Handler handler) { }

    @Override public void close() { }

    @Override protected void finalize() throws Throwable {
        try { close(); } finally { super.finalize(); }
    }

    /** One descrambling session. Never produced here (openSession cannot
     *  succeed); the type exists for linkage. */
    public final class Session implements AutoCloseable {
        Session() { }
        public byte[] getSessionId() { throw noPlugin(); }
        public void setPrivateData(byte[] data) throws MediaCasException { throw noPlugin(); }
        public void processEcm(byte[] data) throws MediaCasException { throw noPlugin(); }
        public void processEcm(byte[] data, int offset, int length) throws MediaCasException {
            throw noPlugin();
        }
        public void sendSessionEvent(int event, int arg, byte[] data) throws MediaCasException {
            throw noPlugin();
        }
        @Override public void close() { }
    }

    /** Describes one installed CAS plugin. None are, so none is produced. */
    public static class PluginDescriptor {
        private final int mSystemId;
        private final String mName;
        PluginDescriptor(int systemId, String name) { mSystemId = systemId; mName = name; }
        public int getSystemId() { return mSystemId; }
        public String getName() { return mName; }
    }

    /** Plugin events. Never delivered -- no plugin exists to raise one. */
    public interface EventListener {
        void onEvent(MediaCas mediaCas, int event, int arg, byte[] data);
        default void onSessionEvent(MediaCas mediaCas, Session session, int event, int arg,
                byte[] data) { }
        default void onPluginStatusUpdate(MediaCas mediaCas, int status, int arg) { }
        default void onResourceLost(MediaCas mediaCas) { }
    }
}
