// com.google.firebase.messaging.RemoteMessage -- the payload object an FCM
// push consumer receives in FirebaseMessagingService.onMessageReceived and
// can also build to send upstream. On Mac.apk there is no FCM channel (this
// is a de-Googled / no-Play-services device), so onMessageReceived is never
// auto-invoked. The class still has to expose the FULL published getter
// surface so push-consumer code compiles against our shadow and so apps that
// construct RemoteMessage via the Builder (upstream-send path, or their own
// tests) get the exact public API.
//
// Faithful to the firebase-android-sdk
// firebase-messaging/.../RemoteMessage.java public surface:
//   constants PRIORITY_UNKNOWN/HIGH/NORMAL; getSenderId/getFrom/getTo/getData/
//   getCollapseKey/getMessageId/getMessageType/getSentTime/getTtl/
//   getOriginalPriority/getPriority/getNotification/toIntent/writeToParcel;
//   nested Notification (all localization + display getters); nested Builder.
package com.google.firebase.messaging;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class RemoteMessage implements Parcelable {

    // Priority levels reported by getPriority()/getOriginalPriority().
    public static final int PRIORITY_UNKNOWN = 0;
    public static final int PRIORITY_HIGH    = 1;
    public static final int PRIORITY_NORMAL  = 2;

    // The real SDK backs every field with a single Bundle keyed by the FCM
    // wire keys (e.g. "from", "google.message_id", "google.ttl"). We mirror
    // that so toIntent() round-trips and so the data map and metadata stay in
    // one place. Builder writes into this same Bundle.
    private final Bundle bundle;

    // Lazily-parsed application data (the non-reserved keys). The wire format
    // stores each data entry directly in the bundle; we cache the extracted
    // map on first getData() like the SDK does.
    private Map<String, String> data;

    private Notification notification;

    RemoteMessage(Bundle bundle) {
        this.bundle = bundle != null ? bundle : new Bundle();
    }

    public RemoteMessage(android.os.Parcel in) {
        this(in.readBundle());
    }

    public String getSenderId() {
        return bundle.getString("google.c.sender.id");
    }

    public String getFrom() {
        return bundle.getString("from");
    }

    public String getTo() {
        return bundle.getString("google.to");
    }

    public Map<String, String> getData() {
        if (data == null) {
            Map<String, String> result = new HashMap<>();
            // Reserved keys (FCM control fields) are excluded from the data map
            // exactly as the SDK does -- anything starting with "google." or
            // "gcm." or equal to "from"/"message_type"/"collapse_key" etc.
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                if (value instanceof String) {
                    if (isReservedKey(key)) continue;
                    result.put(key, (String) value);
                }
            }
            data = Collections.unmodifiableMap(result);
        }
        return data;
    }

    private static boolean isReservedKey(String key) {
        if (key == null) return true;
        return key.startsWith("google.")
            || key.startsWith("gcm.")
            || key.equals("from")
            || key.equals("message_type")
            || key.equals("collapse_key");
    }

    public String getCollapseKey() {
        return bundle.getString("collapse_key");
    }

    public String getMessageId() {
        String id = bundle.getString("google.message_id");
        if (id == null) id = bundle.getString("message_id");
        return id;
    }

    public String getMessageType() {
        return bundle.getString("message_type");
    }

    public long getSentTime() {
        Object value = bundle.get("google.sent_time");
        if (value instanceof Long) return (Long) value;
        try {
            String s = bundle.getString("google.sent_time");
            if (s != null) return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
        }
        return 0L;
    }

    public int getTtl() {
        Object value = bundle.get("google.ttl");
        if (value instanceof Integer) return (Integer) value;
        try {
            String s = bundle.getString("google.ttl");
            if (s != null) return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    public int getOriginalPriority() {
        return parsePriority(bundle.getString("google.original_priority"));
    }

    public int getPriority() {
        String p = bundle.getString("google.delivered_priority");
        if (p == null) {
            // The SDK falls back to the "google.priority" / reduced flag; we
            // surface original priority when delivered priority is absent,
            // matching the documented "current message priority" contract.
            return getOriginalPriority();
        }
        return parsePriority(p);
    }

    private static int parsePriority(String value) {
        if ("high".equals(value)) return PRIORITY_HIGH;
        if ("normal".equals(value)) return PRIORITY_NORMAL;
        return PRIORITY_UNKNOWN;
    }

    public Notification getNotification() {
        if (notification == null && NotificationParams.isNotification(bundle)) {
            notification = new Notification(bundle);
        }
        return notification;
    }

    // toIntent(): the SDK packs the whole bundle into an Intent so a consumer
    // can forward the message. We mirror that single-VM behavior.
    public Intent toIntent() {
        Intent intent = new Intent("com.google.android.c2dm.intent.RECEIVE");
        intent.putExtras(bundle);
        return intent;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeBundle(bundle);
    }

    public static final Parcelable.Creator<RemoteMessage> CREATOR =
        new Parcelable.Creator<RemoteMessage>() {
            @Override public RemoteMessage createFromParcel(Parcel in) {
                return new RemoteMessage(in.readBundle());
            }
            @Override public RemoteMessage[] newArray(int size) {
                return new RemoteMessage[size];
            }
        };

    // -----------------------------------------------------------------
    // Internal helper mirroring the SDK's NotificationParams reserved-key
    // detection. Kept package-private and minimal -- just enough to decide
    // whether a notification block is present in the bundle.
    // -----------------------------------------------------------------
    static final class NotificationParams {
        private NotificationParams() {}
        static boolean isNotification(Bundle bundle) {
            // Real SDK keys notification fields under "gcm.notification.*";
            // a body or title under that prefix means a Notification exists.
            return bundle.getString("gcm.notification.title") != null
                || bundle.getString("gcm.notification.body") != null
                || bundle.getString("gcm.notification.e") != null;
        }
    }

    // -----------------------------------------------------------------
    // RemoteMessage.Notification -- the display block of an FCM notification
    // message. All getters read the "gcm.notification.*" keys exactly like
    // the SDK. Localization args are returned as the parsed String[]/values.
    // -----------------------------------------------------------------
    public static class Notification {
        private final Bundle b;

        Notification(Bundle b) { this.b = b; }

        private String s(String key) { return b.getString("gcm.notification." + key); }

        public String getTitle()              { return s("title"); }
        public String getTitleLocalizationKey() { return s("title_loc_key"); }
        public String[] getTitleLocalizationArgs() { return parseArgs(s("title_loc_args")); }
        public String getBody()               { return s("body"); }
        public String getBodyLocalizationKey() { return s("body_loc_key"); }
        public String[] getBodyLocalizationArgs() { return parseArgs(s("body_loc_args")); }
        public String getIcon()               { return s("icon"); }
        public String getSound()              { String v = s("sound2"); return v != null ? v : s("sound"); }
        public String getTag()                { return s("tag"); }
        public String getColor()              { return s("color"); }
        public String getClickAction()        { return s("click_action"); }
        public String getChannelId()          { return s("android_channel_id"); }
        public String getTicker()             { return s("ticker"); }

        public Uri getImageUrl()              { return parseUri(s("image")); }
        public Uri getLink()                  { return parseUri(s("link")); }

        public boolean getSticky()            { return parseBool(s("sticky")); }
        public boolean getLocalOnly()         { return parseBool(s("local_only")); }
        public boolean getDefaultSound()      { return parseBool(s("default_sound")); }
        public boolean getDefaultVibrateSettings() { return parseBool(s("default_vibrate_timings")); }
        public boolean getDefaultLightSettings()   { return parseBool(s("default_light_settings")); }

        public Integer getNotificationPriority() { return parseInteger(s("notification_priority")); }
        public Integer getVisibility()           { return parseInteger(s("visibility")); }
        public Integer getNotificationCount()    { return parseInteger(s("notification_count")); }

        public Long getEventTime() {
            String v = s("event_time");
            if (v == null) return null;
            try { return Long.parseLong(v); } catch (NumberFormatException e) { return null; }
        }

        public int[] getLightSettings()       { return parseIntArray(s("light_settings")); }
        public long[] getVibrateTimings()     { return parseLongArray(s("vibrate_timings")); }

        private static String[] parseArgs(String raw) {
            if (raw == null) return null;
            // The SDK JSON-decodes a "[...]" array; we accept the common
            // bracketed comma form without pulling in a JSON dependency.
            String t = raw.trim();
            if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length() - 1);
            if (t.isEmpty()) return new String[0];
            String[] parts = t.split(",");
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim().replaceAll("^\"|\"$", "");
            }
            return parts;
        }

        private static Uri parseUri(String raw) {
            return raw == null ? null : Uri.parse(raw);
        }

        private static boolean parseBool(String raw) {
            return "1".equals(raw) || "true".equalsIgnoreCase(raw);
        }

        private static Integer parseInteger(String raw) {
            if (raw == null) return null;
            try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return null; }
        }

        private static int[] parseIntArray(String raw) {
            String[] parts = parseArgs(raw);
            if (parts == null) return null;
            int[] out = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try { out[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException e) { out[i] = 0; }
            }
            return out;
        }

        private static long[] parseLongArray(String raw) {
            String[] parts = parseArgs(raw);
            if (parts == null) return null;
            long[] out = new long[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try { out[i] = Long.parseLong(parts[i]); } catch (NumberFormatException e) { out[i] = 0L; }
            }
            return out;
        }
    }

    // -----------------------------------------------------------------
    // RemoteMessage.Builder -- constructs an upstream (send) message. Fluent
    // setters write into the backing bundle; build() snapshots it.
    // -----------------------------------------------------------------
    public static class Builder {
        private final Bundle bundle = new Bundle();
        private final Map<String, String> data = new HashMap<>();

        public Builder(String to) {
            if (to == null || to.isEmpty()) {
                throw new IllegalArgumentException("Invalid to: " + to);
            }
            bundle.putString("google.to", to);
        }

        public Builder setMessageId(String messageId) {
            bundle.putString("google.message_id", messageId);
            return this;
        }

        public Builder setMessageType(String messageType) {
            bundle.putString("message_type", messageType);
            return this;
        }

        public Builder setTtl(int ttl) {
            if (ttl < 0 || ttl > 86400) {
                throw new IllegalArgumentException("Invalid ttl: " + ttl);
            }
            bundle.putString("google.ttl", Integer.toString(ttl));
            return this;
        }

        public Builder setCollapseKey(String collapseKey) {
            bundle.putString("collapse_key", collapseKey);
            return this;
        }

        public Builder addData(String key, String value) {
            data.put(key, value);
            return this;
        }

        public Builder setData(Map<String, String> data) {
            this.data.clear();
            if (data != null) this.data.putAll(data);
            return this;
        }

        public Builder clearData() {
            data.clear();
            return this;
        }

        public RemoteMessage build() {
            Bundle out = new Bundle(bundle);
            for (Map.Entry<String, String> e : data.entrySet()) {
                out.putString(e.getKey(), e.getValue());
            }
            return new RemoteMessage(out);
        }
    }
}
