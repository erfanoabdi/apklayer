// android.app.SyncNotedAppOp -- "Description of an app-op that was noted for the
// current process", handed to AppOpsManager.OnOpNotedCallback.onNoted /
// onSelfNoted when a synchronous app-op note happens in this process. API 30.
//
// AOSP ref: frameworks/base/core/java/android/app/SyncNotedAppOp.java
// (android16-release). AOSP stores the op CODE and resolves getOp() through
// AppOpsManager.opToPublicName(code), which indexes AppOpsManager's sAppOpInfos
// array by that code. The op-code -> op-name table below is that array's
// (code, OPSTR) column, extracted verbatim from AOSP's sAppOpInfos declaration
// (AppOpsManager.java:2802 onward, android16-release) so getOp() answers the
// same string a device would rather than a placeholder. The array INDEX is the
// op code, which is how AOSP itself defines the mapping.
package android.app;

import android.os.Parcel;
import android.os.Parcelable;

public final class SyncNotedAppOp implements Parcelable {

    /** AOSP AppOpsManager.sAppOpInfos, name column, indexed by op code. */
    private static final String[] OP_TO_NAME = new String[] {
            "android:coarse_location", "android:fine_location", "android:gps",
            "android:vibrate", "android:read_contacts", "android:write_contacts",
            "android:read_call_log", "android:write_call_log", "android:read_calendar",
            "android:write_calendar", "android:wifi_scan", "android:post_notification",
            "android:neighboring_cells", "android:call_phone", "android:read_sms",
            "android:write_sms", "android:receive_sms", "android:receive_emergency_broadcast",
            "android:receive_mms", "android:receive_wap_push", "android:send_sms",
            "android:read_icc_sms", "android:write_icc_sms", "android:write_settings",
            "android:system_alert_window", "android:access_notifications", "android:camera",
            "android:record_audio", "android:play_audio", "android:read_clipboard",
            "android:write_clipboard", "android:take_media_buttons", "android:take_audio_focus",
            "android:audio_master_volume", "android:audio_voice_volume",
            "android:audio_ring_volume", "android:audio_media_volume",
            "android:audio_alarm_volume", "android:audio_notification_volume",
            "android:audio_bluetooth_volume", "android:wake_lock", "android:monitor_location",
            "android:monitor_location_high_power", "android:get_usage_stats",
            "android:mute_microphone", "android:toast_window", "android:project_media",
            "android:activate_vpn", "android:write_wallpaper", "android:assist_structure",
            "android:assist_screenshot", "android:read_phone_state", "android:add_voicemail",
            "android:use_sip", "android:process_outgoing_calls", "android:use_fingerprint",
            "android:body_sensors", "android:read_cell_broadcasts", "android:mock_location",
            "android:read_external_storage", "android:write_external_storage",
            "android:turn_screen_on", "android:get_accounts", "android:run_in_background",
            "android:audio_accessibility_volume", "android:read_phone_numbers",
            "android:request_install_packages", "android:picture_in_picture",
            "android:instant_app_start_foreground", "android:answer_phone_calls",
            "android:run_any_in_background", "android:change_wifi_state",
            "android:request_delete_packages", "android:bind_accessibility_service",
            "android:accept_handover", "android:manage_ipsec_tunnels",
            "android:start_foreground", "android:bluetooth_scan", "android:use_biometric",
            "android:activity_recognition", "android:sms_financial_transactions",
            "android:read_media_audio", "android:write_media_audio", "android:read_media_video",
            "android:write_media_video", "android:read_media_images",
            "android:write_media_images", "android:legacy_storage",
            "android:access_accessibility", "android:read_device_identifiers",
            "android:access_media_location", "android:query_all_packages",
            "android:manage_external_storage", "android:interact_across_profiles",
            "android:activate_platform_vpn", "android:loader_usage_stats",
            "android:auto_revoke_permissions_if_unused",
            "android:auto_revoke_managed_by_installer", "android:no_isolated_storage",
            "android:phone_call_microphone", "android:phone_call_camera",
            "android:record_audio_hotword", "android:manage_ongoing_calls",
            "android:manage_credentials", "android:use_icc_auth_with_device_identifier",
            "android:record_audio_output", "android:schedule_exact_alarm",
            "android:fine_location_source", "android:coarse_location_source",
            "android:manage_media", "android:bluetooth_connect", "android:uwb_ranging",
            "android:activity_recognition_source", "android:bluetooth_advertise",
            "android:record_incoming_phone_audio", "android:nearby_wifi_devices",
            "android:establish_vpn_service", "android:establish_vpn_manager",
            "android:access_restricted_settings", "android:receive_ambient_trigger_audio",
            "android:receive_explicit_user_interaction_audio",
            "android:run_user_initiated_jobs", "android:read_media_visual_user_selected",
            "android:system_exempt_from_suspension",
            "android:system_exempt_from_dismissible_notifications",
            "android:read_write_health_data", "android:foreground_service_special_use",
            "android:system_exempt_from_power_restrictions",
            "android:system_exempt_from_hibernation",
            "android:system_exempt_from_activity_bg_start_restriction",
            "android:capture_consentless_bugreport_on_userdebug_build", "android:deprecated_2",
            "android:use_full_screen_intent", "android:camera_sandboxed",
            "android:record_audio_sandboxed", "android:receive_sandbox_trigger_audio",
            "android:deprecated_3", "android:create_accessibility_overlay",
            "android:media_routing_control", "android:enable_mobile_data_by_user",
            "android:reserved_for_testing", "android:rapid_clear_notifications_by_listener",
            "android:read_system_grammatical_gender", "android:deprecated_4",
            "android:archive_icon_overlay", "android:unarchival_support",
            "android:emergency_location", "android:receive_sensitive_notifications",
            "android:read_heart_rate", "android:read_skin_temperature", "android:ranging",
            "android:read_oxygen_saturation", "android:write_system_preferences",
            "android:control_audio", "android:control_audio_partial",
            "android:eye_tracking_coarse", "android:eye_tracking_fine", "android:face_tracking",
            "android:hand_tracking", "android:head_tracking",
            "android:scene_understanding_coarse", "android:scene_understanding_fine"
    };

    private final int mOpCode;
    private final String mAttributionTag;
    private final String mOpName;

    /** AOSP SyncNotedAppOp(int opCode, String attributionTag): the public
     *  constructor. AOSP validates opCode >= 0. */
    public SyncNotedAppOp(int opCode, String attributionTag) {
        if (opCode < 0) {
            throw new IllegalArgumentException("opCode must be a non-negative op code");
        }
        mOpCode = opCode;
        mAttributionTag = attributionTag;
        mOpName = null;
    }

    /** @hide -- AOSP's op-NAME constructor, used when the note already carries
     *  the public name. */
    SyncNotedAppOp(String op, String attributionTag) {
        mOpCode = -1;
        mAttributionTag = attributionTag;
        mOpName = op;
    }

    private SyncNotedAppOp(Parcel in) {
        mOpCode = in.readInt();
        mAttributionTag = in.readString();
        mOpName = in.readString();
    }

    /** AOSP getOp(): {@code AppOpsManager.opToPublicName(mOpCode)} -- never
     *  null. An op code outside the table is not something AOSP can produce, so
     *  it reports the code rather than throwing from an @NonNull accessor. */
    public String getOp() {
        if (mOpName != null) return mOpName;
        if (mOpCode >= 0 && mOpCode < OP_TO_NAME.length) return OP_TO_NAME[mOpCode];
        return "android:unknown_op_" + mOpCode;
    }

    /** AOSP getAttributionTag(): the attribution tag the op was noted for, or
     *  null for the default attribution. */
    public String getAttributionTag() { return mAttributionTag; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOpCode);
        dest.writeString(mAttributionTag);
        dest.writeString(mOpName);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof SyncNotedAppOp)) return false;
        SyncNotedAppOp other = (SyncNotedAppOp) o;
        return getOp().equals(other.getOp())
                && java.util.Objects.equals(mAttributionTag, other.mAttributionTag);
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(getOp(), mAttributionTag);
    }

    @Override public String toString() {
        return "SyncNotedAppOp { op=" + getOp() + ", attributionTag=" + mAttributionTag + " }";
    }

    public static final Parcelable.Creator<SyncNotedAppOp> CREATOR =
            new Parcelable.Creator<SyncNotedAppOp>() {
        @Override public SyncNotedAppOp createFromParcel(Parcel in) {
            return new SyncNotedAppOp(in);
        }
        @Override public SyncNotedAppOp[] newArray(int size) { return new SyncNotedAppOp[size]; }
    };
}
