package android.media;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * DRM plugin interface (Widevine, PlayReady, ClearKey).
 *
 * Mac.apk has no DRM plugin of any kind. AOSP's contract for a scheme the
 * device cannot service is an UnsupportedSchemeException from the constructor,
 * and that is what we do -- this is a real device state (many Android TV boxes
 * and all AOSP-without-Widevine builds behave identically), and ExoPlayer /
 * Media3 already handle it: they surface "DRM scheme unsupported" and either
 * fall back to unprotected renditions or fail the item cleanly.
 *
 * The alternative -- pretending a session opened and then returning empty keys
 * -- would hang the playback pipeline waiting for a license that can never
 * arrive, which is strictly worse than an honest refusal.
 *
 * Everything below the constructor therefore exists to satisfy linkage and to
 * throw the AOSP-documented exception if somehow reached.
 *
 * Source: frameworks/base/media/java/android/media/MediaDrm.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/media/java/android/media/MediaDrm.java
 */
public final class MediaDrm implements AutoCloseable {

    public static final int KEY_TYPE_STREAMING = 1;
    public static final int KEY_TYPE_OFFLINE = 2;
    public static final int KEY_TYPE_RELEASE = 3;

    public static final int EVENT_PROVISION_REQUIRED = 1;
    public static final int EVENT_KEY_REQUIRED = 2;
    public static final int EVENT_KEY_EXPIRED = 3;
    public static final int EVENT_VENDOR_DEFINED = 4;
    public static final int EVENT_SESSION_RECLAIMED = 5;

    public static final int HDCP_LEVEL_UNKNOWN = 0;
    public static final int HDCP_NONE = 1;
    public static final int SECURITY_LEVEL_UNKNOWN = 0;

    public static final String PROPERTY_VENDOR = "vendor";
    public static final String PROPERTY_VERSION = "version";
    public static final String PROPERTY_DESCRIPTION = "description";
    public static final String PROPERTY_ALGORITHMS = "algorithms";

    /**
     * No DRM plugin is installed, so no crypto scheme is supported. Apps call
     * this to decide whether to even attempt protected playback.
     */
    public static boolean isCryptoSchemeSupported(UUID uuid) { return false; }

    public static boolean isCryptoSchemeSupported(UUID uuid, String mimeType) { return false; }

    public static boolean isCryptoSchemeSupported(UUID uuid, String mimeType, int securityLevel) {
        return false;
    }

    public static List<UUID> getSupportedCryptoSchemes() {
        return java.util.Collections.emptyList();
    }

    public MediaDrm(UUID uuid) throws UnsupportedSchemeException {
        throw new UnsupportedSchemeException(
                "No DRM plugin is available for scheme " + uuid);
    }

    private static IllegalStateException noPlugin() {
        return new IllegalStateException("No DRM plugin available");
    }

    public byte[] openSession() throws NotProvisionedException, ResourceBusyException {
        throw noPlugin();
    }

    /** API 28. Opens a session at a specific security level. Same terminal
     *  answer as the no-argument form: no plugin exists to open one. */
    public byte[] openSession(int level) throws NotProvisionedException, ResourceBusyException {
        throw noPlugin();
    }
    public void closeSession(byte[] sessionId) { throw noPlugin(); }

    public KeyRequest getKeyRequest(byte[] scope, byte[] init, String mimeType,
                                    int keyType, HashMap<String, String> optionalParameters)
            throws NotProvisionedException { throw noPlugin(); }

    public byte[] provideKeyResponse(byte[] scope, byte[] response)
            throws NotProvisionedException, DeniedByServerException { throw noPlugin(); }

    public void restoreKeys(byte[] sessionId, byte[] keySetId) { throw noPlugin(); }
    public void removeKeys(byte[] sessionId) { throw noPlugin(); }

    public HashMap<String, String> queryKeyStatus(byte[] sessionId) { throw noPlugin(); }

    public ProvisionRequest getProvisionRequest() { throw noPlugin(); }
    public void provideProvisionResponse(byte[] response) throws DeniedByServerException {
        throw noPlugin();
    }

    public String getPropertyString(String propertyName) { throw noPlugin(); }
    public byte[] getPropertyByteArray(String propertyName) { throw noPlugin(); }
    public void setPropertyString(String propertyName, String value) { throw noPlugin(); }
    public void setPropertyByteArray(String propertyName, byte[] value) { throw noPlugin(); }

    /** AOSP (frameworks/base android14-release media/java/android/media/
     *  MediaDrm.java:2173-2182): "public static final int
     *  getMaxSecurityLevel() { return SECURITY_LEVEL_MAX; }" -- a STATIC that
     *  returns the hidden sentinel 6, meaning "open the session at the highest
     *  level this device has", not a query of any plugin. It was an INSTANCE
     *  method returning SECURITY_LEVEL_UNKNOWN here, so every app call site
     *  (compiled as invokestatic against the SDK) threw
     *  IncompatibleClassChangeError before the body could run; the 0 was never
     *  observed by anyone. Signature per the SDK-34 android.jar, which drops
     *  the redundant final. Found by debug/apishape_gate.sh (KNOWN_OPEN,
     *  struck 2026-09-26). */
    public static int getMaxSecurityLevel() { return SECURITY_LEVEL_MAX; }
    /** @hide in AOSP (MediaDrm.java:2173): the "use the maximum" sentinel. */
    private static final int SECURITY_LEVEL_MAX = 6;
    public int getConnectedHdcpLevel() { return HDCP_LEVEL_UNKNOWN; }
    public int getMaxHdcpLevel() { return HDCP_LEVEL_UNKNOWN; }
    public int getOpenSessionCount() { return 0; }
    public int getMaxSessionCount() { return 0; }

    public void setOnEventListener(OnEventListener listener) { }
    public void setOnEventListener(OnEventListener listener, android.os.Handler handler) { }
    public void setOnEventListener(java.util.concurrent.Executor executor,
                                   OnEventListener listener) { }
    public void setOnKeyStatusChangeListener(OnKeyStatusChangeListener listener,
                                             android.os.Handler handler) { }
    public void setOnExpirationUpdateListener(OnExpirationUpdateListener listener,
                                              android.os.Handler handler) { }
    public void setOnSessionLostStateListener(OnSessionLostStateListener listener,
                                              android.os.Handler handler) { }
    public void setOnSessionLostStateListener(java.util.concurrent.Executor executor,
                                              OnSessionLostStateListener listener) { }

    // AOSP's clear* pair with the set* above; every listener registration here
    // is already inert (no plugin ever fires an event), so clearing one is
    // likewise a no-op -- but the methods must exist, because an app that
    // registers on start and clears on stop would otherwise die on teardown.
    public void clearOnEventListener() { }
    public void clearOnKeyStatusChangeListener() { }
    public void clearOnExpirationUpdateListener() { }
    public void clearOnSessionLostStateListener() { }

    /** API 29. Fired when a session's state is lost. Never here. */
    public interface OnSessionLostStateListener {
        void onSessionLostState(MediaDrm md, byte[] sessionId);
    }

    public void release() { }

    @Override public void close() { release(); }

    /** AOSP declares these as interfaces. */
    public interface OnEventListener {
        void onEvent(MediaDrm md, byte[] sessionId, int event, int extra, byte[] data);
    }

    public interface OnKeyStatusChangeListener {
        void onKeyStatusChange(MediaDrm md, byte[] sessionId,
                               List<KeyStatus> keyInformation, boolean hasNewUsableKey);
    }

    public interface OnExpirationUpdateListener {
        void onExpirationUpdate(MediaDrm md, byte[] sessionId, long expirationTime);
    }

    public static final class KeyStatus {
        public static final int STATUS_USABLE = 0;
        public static final int STATUS_EXPIRED = 1;
        public static final int STATUS_OUTPUT_NOT_ALLOWED = 2;
        public static final int STATUS_PENDING = 3;
        public static final int STATUS_INTERNAL_ERROR = 4;

        private final byte[] mKeyId;
        private final int mStatusCode;

        KeyStatus(byte[] keyId, int statusCode) { mKeyId = keyId; mStatusCode = statusCode; }

        public byte[] getKeyId() { return mKeyId; }
        public int getStatusCode() { return mStatusCode; }
    }

    public static final class KeyRequest {
        public static final int REQUEST_TYPE_INITIAL = 0;
        public static final int REQUEST_TYPE_RENEWAL = 1;
        public static final int REQUEST_TYPE_RELEASE = 2;
        public static final int REQUEST_TYPE_NONE = 3;
        public static final int REQUEST_TYPE_UPDATE = 4;

        private byte[] mData;
        private String mDefaultUrl;
        private int mRequestType;

        KeyRequest() { }

        public byte[] getData() { return mData; }
        public String getDefaultUrl() { return mDefaultUrl; }
        public int getRequestType() { return mRequestType; }
    }

    public static final class ProvisionRequest {
        private byte[] mData;
        private String mDefaultUrl;

        ProvisionRequest() { }

        public byte[] getData() { return mData; }
        public String getDefaultUrl() { return mDefaultUrl; }
    }

    /** AOSP nests this one inside MediaDrm; it extends IllegalStateException. */
    public static final class MediaDrmStateException extends IllegalStateException
            implements MediaDrmThrowable {
        private final String mDiagnosticInfo;
        private final int mErrorCode;

        public MediaDrmStateException(int errorCode, String detailMessage) {
            super(detailMessage);
            mErrorCode = errorCode;
            mDiagnosticInfo = "android.media.MediaDrm.error_" + errorCode;
        }

        public String getDiagnosticInfo() { return mDiagnosticInfo; }

        /** AOSP: the ErrorCodes value the plugin reported. Returns the code the
         *  exception was CONSTRUCTED with -- it used to be dropped on the floor,
         *  so getDiagnosticInfo() named a code getErrorCode() could not report. */
        public int getErrorCode() { return mErrorCode; }

        /** AOSP: whether retrying the operation might succeed. Every error this
         *  class can carry comes from "there is no DRM plugin", which retrying
         *  cannot change, so false. */
        public boolean isTransient() { return false; }
    }

    public static final class SessionException extends RuntimeException
            implements MediaDrmThrowable {
        /** @deprecated in AOSP; superseded by the ErrorCodes constants. */
        @Deprecated public static final int ERROR_UNKNOWN = 0;
        /** @deprecated in AOSP; superseded by ERROR_RESOURCE_CONTENTION in
         *  MediaDrm.ErrorCodes. */
        @Deprecated public static final int ERROR_RESOURCE_CONTENTION = 1;

        private final int mErrorCode;

        public SessionException(int errorCode, String detailMessage) {
            super(detailMessage);
            mErrorCode = errorCode;
        }

        /** @deprecated in AOSP. Returns the code the exception was constructed
         *  with; it used to answer ERROR_UNKNOWN unconditionally, discarding the
         *  caller's own argument. */
        @Deprecated public int getErrorCode() { return mErrorCode; }

        /** AOSP: true only for ERROR_RESOURCE_CONTENTION, which is the one
         *  condition that can clear on its own. */
        public boolean isTransient() { return mErrorCode == ERROR_RESOURCE_CONTENTION; }
    }

    /** The plugin error codes MediaDrmStateException.getErrorCode() reports.
     *  AOSP MediaDrm.ErrorCodes; the values are frozen public API. */
    public static final class ErrorCodes {
        private ErrorCodes() { }
        public static final int ERROR_UNKNOWN = 0;
        public static final int ERROR_NO_KEY = 1;
        public static final int ERROR_KEY_EXPIRED = 2;
        public static final int ERROR_RESOURCE_BUSY = 3;
        public static final int ERROR_INSUFFICIENT_OUTPUT_PROTECTION = 4;
        public static final int ERROR_SESSION_NOT_OPENED = 5;
        public static final int ERROR_UNSUPPORTED_OPERATION = 6;
        public static final int ERROR_INSUFFICIENT_SECURITY = 7;
        public static final int ERROR_FRAME_TOO_LARGE = 8;
        public static final int ERROR_LOST_STATE = 9;
        public static final int ERROR_CERTIFICATE_MALFORMED = 10;
        public static final int ERROR_CERTIFICATE_MISSING = 11;
        public static final int ERROR_CRYPTO_LIBRARY = 12;
        public static final int ERROR_GENERIC_OEM = 13;
        public static final int ERROR_GENERIC_PLUGIN = 14;
        public static final int ERROR_INIT_DATA = 15;
        public static final int ERROR_KEY_NOT_LOADED = 16;
        public static final int ERROR_LICENSE_PARSE = 17;
        public static final int ERROR_LICENSE_POLICY = 18;
        public static final int ERROR_LICENSE_RELEASE = 19;
        public static final int ERROR_LICENSE_REQUEST_REJECTED = 20;
        public static final int ERROR_LICENSE_RESTORE = 21;
        public static final int ERROR_LICENSE_STATE = 22;
        public static final int ERROR_MEDIA_FRAMEWORK = 23;
        public static final int ERROR_PROVISIONING_CERTIFICATE = 24;
        public static final int ERROR_PROVISIONING_CONFIG = 25;
        public static final int ERROR_PROVISIONING_PARSE = 26;
        public static final int ERROR_PROVISIONING_REQUEST_REJECTED = 27;
        public static final int ERROR_PROVISIONING_RETRY = 28;
        public static final int ERROR_SECURE_STOP_RELEASE = 29;
        public static final int ERROR_STORAGE_READ = 30;
        public static final int ERROR_STORAGE_WRITE = 31;
        public static final int ERROR_ZERO_SUBSAMPLES = 32;
    }

    /** A signing/encrypting session obtained from getCryptoSession(). Never
     *  produced here -- getCryptoSession() cannot be reached past the
     *  constructor -- so the type exists for linkage. */
    public final class CryptoSession {
        CryptoSession() { }
        public byte[] decrypt(byte[] keyid, byte[] input, byte[] iv) { throw noPlugin(); }
        public byte[] encrypt(byte[] keyid, byte[] input, byte[] iv) { throw noPlugin(); }
        public byte[] sign(byte[] keyid, byte[] message) { throw noPlugin(); }
        public boolean verify(byte[] keyid, byte[] message, byte[] signature) {
            throw noPlugin();
        }
    }

    /** One line of the plugin's own log. AOSP MediaDrm.LogMessage. */
    public static final class LogMessage {
        private final long mTimestampMillis;
        private final int mPriority;
        private final String mMessage;

        LogMessage(long timestampMillis, int priority, String message) {
            mTimestampMillis = timestampMillis;
            mPriority = priority;
            mMessage = message;
        }

        public long getTimestampMillis() { return mTimestampMillis; }
        public int getPriority() { return mPriority; }
        public String getMessage() { return mMessage; }
    }

    /** AOSP MediaDrm.getPlaybackComponent (API 31; frameworks/base
     *  android14-release MediaDrm.java:3031-3036): IllegalArgumentException for
     *  a null id, else mPlaybackComponentMap.get(sessionId) -- a map that only
     *  openSession fills, so an id with no open session answers null. No
     *  session can ever be open here (the constructor itself throws
     *  UnsupportedSchemeException, see the class header), so this body is
     *  unreachable in practice and the AOSP answer for every id is null. It
     *  used to return a fresh component for ANY id, which claimed a session
     *  that did not exist. */
    public PlaybackComponent getPlaybackComponent(byte[] sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is null");
        }
        return null;
    }

    /** Ties one decrypt session to a media-metrics LogSessionId. API 31.
     *
     *  A FINAL INNER CLASS, not an interface: AOSP declares "public final class
     *  PlaybackComponent" as a non-static member of MediaDrm (MediaDrm.java:
     *  3041) and the SDK-34 android.jar shows the same (this$0, final). It was
     *  an interface here, so an app's getPlaybackComponent(id)
     *  .setLogSessionId(...) -- compiled as invokevirtual on a class -- threw
     *  IncompatibleClassChangeError at the call. Media3/ExoPlayer's
     *  FrameworkMediaDrm.setPlayerIdForSession makes exactly that call on API
     *  31+ (invokevirtual MediaDrm$PlaybackComponent.setLogSessionId, read
     *  from AntennaPod's converted classes.jar), and 13 of the 62 corpus apps
     *  reference it (debug/apishape_census.py, 2026-09-26). LATENT today: that
     *  path needs a MediaDrm instance and the constructor above always
     *  throws, so no app reached it yet; it would the day a scheme is served.
     *  Found by debug/apishape_gate.sh (KNOWN_OPEN, struck 2026-09-26).
     *  Source: frameworks/base/media/java/android/media/MediaDrm.java */
    public final class PlaybackComponent {
        private final byte[] mSessionId;
        private android.media.metrics.LogSessionId mLogSessionId =
                android.media.metrics.LogSessionId.LOG_SESSION_ID_NONE;

        /** @hide in AOSP (public there, hidden from the SDK, which is why the
         *  stub jar shows no byte[] constructor). Package-private here so no
         *  app can construct one, which is the effect @hide has. */
        PlaybackComponent(byte[] sessionId) {
            mSessionId = sessionId;
        }

        /** AOSP MediaDrm.java:3060-3067: null-check, reject a LogSessionId
         *  with no string id, hand the pair to the plugin, then remember it.
         *  There is no plugin to hand it to (see the class header). */
        public void setLogSessionId(android.media.metrics.LogSessionId logSessionId) {
            java.util.Objects.requireNonNull(logSessionId);
            if (logSessionId.getStringId() == null) {
                throw new IllegalArgumentException("playbackId is null");
            }
            mLogSessionId = logSessionId;
        }

        /** AOSP: LOG_SESSION_ID_NONE until set, never null. */
        public android.media.metrics.LogSessionId getLogSessionId() {
            return mLogSessionId;
        }
    }

    public java.util.List<byte[]> getOfflineLicenseKeySetIds() { return java.util.Collections.emptyList(); }
    public void removeOfflineLicense(byte[] keySetId) { }
    public int getOfflineLicenseState(byte[] keySetId) { return 0; }

    /** The metrics-bundle keys AOSP publishes for getMetrics(). Pure key
     *  strings, verbatim from android16.txt; no plugin here ever populates a
     *  bundle with them, so getMetrics() below stays empty. */
    public static final class MetricsConstants {
        private MetricsConstants() { }
        public static final String CLOSE_SESSION_ERROR_COUNT = "drm.mediadrm.close_session.error.count";
        public static final String CLOSE_SESSION_ERROR_LIST = "drm.mediadrm.close_session.error.list";
        public static final String CLOSE_SESSION_OK_COUNT = "drm.mediadrm.close_session.ok.count";
        public static final String EVENT_KEY_EXPIRED_COUNT = "drm.mediadrm.event.KEY_EXPIRED.count";
        public static final String EVENT_KEY_NEEDED_COUNT = "drm.mediadrm.event.KEY_NEEDED.count";
        public static final String EVENT_PROVISION_REQUIRED_COUNT = "drm.mediadrm.event.PROVISION_REQUIRED.count";
        public static final String EVENT_SESSION_RECLAIMED_COUNT = "drm.mediadrm.event.SESSION_RECLAIMED.count";
        public static final String EVENT_VENDOR_DEFINED_COUNT = "drm.mediadrm.event.VENDOR_DEFINED.count";
        public static final String GET_DEVICE_UNIQUE_ID_ERROR_COUNT = "drm.mediadrm.get_device_unique_id.error.count";
        public static final String GET_DEVICE_UNIQUE_ID_ERROR_LIST = "drm.mediadrm.get_device_unique_id.error.list";
        public static final String GET_DEVICE_UNIQUE_ID_OK_COUNT = "drm.mediadrm.get_device_unique_id.ok.count";
        public static final String GET_KEY_REQUEST_ERROR_COUNT = "drm.mediadrm.get_key_request.error.count";
        public static final String GET_KEY_REQUEST_ERROR_LIST = "drm.mediadrm.get_key_request.error.list";
        public static final String GET_KEY_REQUEST_OK_COUNT = "drm.mediadrm.get_key_request.ok.count";
        public static final String GET_KEY_REQUEST_OK_TIME_MICROS = "drm.mediadrm.get_key_request.ok.average_time_micros";
        public static final String GET_PROVISION_REQUEST_ERROR_COUNT = "drm.mediadrm.get_provision_request.error.count";
        public static final String GET_PROVISION_REQUEST_ERROR_LIST = "drm.mediadrm.get_provision_request.error.list";
        public static final String GET_PROVISION_REQUEST_OK_COUNT = "drm.mediadrm.get_provision_request.ok.count";
        public static final String KEY_STATUS_EXPIRED_COUNT = "drm.mediadrm.key_status.EXPIRED.count";
        public static final String KEY_STATUS_INTERNAL_ERROR_COUNT = "drm.mediadrm.key_status.INTERNAL_ERROR.count";
        public static final String KEY_STATUS_OUTPUT_NOT_ALLOWED_COUNT = "drm.mediadrm.key_status_change.OUTPUT_NOT_ALLOWED.count";
        public static final String KEY_STATUS_PENDING_COUNT = "drm.mediadrm.key_status_change.PENDING.count";
        public static final String KEY_STATUS_USABLE_COUNT = "drm.mediadrm.key_status_change.USABLE.count";
        public static final String OPEN_SESSION_ERROR_COUNT = "drm.mediadrm.open_session.error.count";
        public static final String OPEN_SESSION_ERROR_LIST = "drm.mediadrm.open_session.error.list";
        public static final String OPEN_SESSION_OK_COUNT = "drm.mediadrm.open_session.ok.count";
        public static final String PROVIDE_KEY_RESPONSE_ERROR_COUNT = "drm.mediadrm.provide_key_response.error.count";
        public static final String PROVIDE_KEY_RESPONSE_ERROR_LIST = "drm.mediadrm.provide_key_response.error.list";
        public static final String PROVIDE_KEY_RESPONSE_OK_COUNT = "drm.mediadrm.provide_key_response.ok.count";
        public static final String PROVIDE_KEY_RESPONSE_OK_TIME_MICROS = "drm.mediadrm.provide_key_response.ok.average_time_micros";
        public static final String PROVIDE_PROVISION_RESPONSE_ERROR_COUNT = "drm.mediadrm.provide_provision_response.error.count";
        public static final String PROVIDE_PROVISION_RESPONSE_ERROR_LIST = "drm.mediadrm.provide_provision_response.error.list";
        public static final String PROVIDE_PROVISION_RESPONSE_OK_COUNT = "drm.mediadrm.provide_provision_response.ok.count";
        public static final String SESSION_END_TIMES_MS = "drm.mediadrm.session_end_times_ms";
        public static final String SESSION_START_TIMES_MS = "drm.mediadrm.session_start_times_ms";
    }

    public android.os.PersistableBundle getMetrics() { return new android.os.PersistableBundle(); }

    /** API 28. The signing/encrypting session for a given algorithm pair.
     *  Unreachable past the constructor; throws the same no-plugin state. */
    public CryptoSession getCryptoSession(byte[] sessionId, String cipherAlgorithm,
                                          String macAlgorithm) {
        throw noPlugin();
    }

    /** API 31. AOSP: "@return the list of log messages", and it is @NonNull.
     *  There is no plugin to have logged anything, so the list is empty --
     *  AOSP's own answer for a plugin that logs nothing. */
    public java.util.List<LogMessage> getLogMessages() {
        return java.util.Collections.emptyList();
    }

    // ---- secure stops (all @Deprecated in AOSP) ---------------------------
    // A "secure stop" is the plugin's record that a playback session ended, kept
    // so the license server can be told. There is no plugin and therefore no
    // record: the queries return empty (@NonNull in AOSP, so empty and not
    // null), and the mutators are no-ops because there is nothing to release or
    // remove. AOSP itself deprecated the whole family.

    @Deprecated public java.util.List<byte[]> getSecureStops() {
        return java.util.Collections.emptyList();
    }

    @Deprecated public java.util.List<byte[]> getSecureStopIds() {
        return java.util.Collections.emptyList();
    }

    /** @Deprecated. @NonNull in AOSP, and AOSP throws for an unknown id rather
     *  than returning null -- there are no ids here, so every id is unknown. */
    @Deprecated public byte[] getSecureStop(byte[] ssid) {
        throw noPlugin();
    }

    @Deprecated public void releaseSecureStops(byte[] ssRelease) { }
    @Deprecated public void removeSecureStop(byte[] ssid) { }
    @Deprecated public void releaseAllSecureStops() { }
    @Deprecated public void removeAllSecureStops() { }
    public int getSecurityLevel(byte[] sessionId) { return SECURITY_LEVEL_UNKNOWN; }
    public boolean requiresSecureDecoder(String mime) { return false; }
    public boolean requiresSecureDecoder(String mime, int level) { return false; }
}


