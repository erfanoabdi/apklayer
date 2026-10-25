package android.telephony;

import android.os.PersistableBundle;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Access to per-carrier configuration.
 *
 * There is no SIM and no carrier on a Mac, so there is no carrier config. Every
 * getter returns an EMPTY PersistableBundle rather than null or an invented one:
 *
 *   - Empty, not null, because AOSP documents the modern getConfig(String...)
 *     and getConfigForSubId(int, String...) overloads as @NonNull, and every
 *     PersistableBundle getter an app then calls takes a default value which it
 *     will now correctly receive. Returning null would NPE callers that AOSP
 *     told may not check.
 *   - Empty, not populated, because a carrier config value is a claim about a
 *     network operator. We have no operator, so we have no such claim to make.
 *
 * The two DEPRECATED overloads (getConfig(), getConfigForSubId(int)) are
 * documented @Nullable by AOSP, and AOSP itself returns null when the config is
 * unavailable -- which is our situation -- so those return null. That asymmetry
 * is AOSP's, not ours.
 *
 * ⚠ KNOWN GAP, deliberate: AOSP declares several hundred KEY_* String/int
 * constants here plus the nested constant classes (Apn, Bsf, Gps, Ims*, Iwlan).
 * Those are compile-time constants, so javac INLINES them into the calling app
 * and a missing one cannot be observed at runtime -- an app reads its own copy
 * of the string and gets our empty bundle's default either way. Only the small
 * set below is declared, for framework readability.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CarrierConfigManager.java
 */
public class CarrierConfigManager {

    public static final String ACTION_CARRIER_CONFIG_CHANGED =
            "android.telephony.action.CARRIER_CONFIG_CHANGED";

    public static final String EXTRA_SLOT_INDEX = "android.telephony.extra.SLOT_INDEX";
    public static final String EXTRA_SUBSCRIPTION_INDEX =
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX;
    public static final String EXTRA_REBROADCAST_ON_UNLOCK =
            "android.telephony.extra.REBROADCAST_ON_UNLOCK";

    /** AOSP marks a config as "identified" with this key; ours never is. */
    public static final String KEY_CARRIER_CONFIG_APPLIED_BOOL = "carrier_config_applied_bool";

    public static final String KEY_CARRIER_VOLTE_AVAILABLE_BOOL = "carrier_volte_available_bool";
    public static final String KEY_CARRIER_VT_AVAILABLE_BOOL = "carrier_vt_available_bool";
    public static final String KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL =
            "carrier_wfc_ims_available_bool";
    public static final String KEY_VOICEMAIL_NOTIFICATION_PERSISTENT_BOOL =
            "voicemail_notification_persistent_bool";
    public static final String KEY_SUPPORT_SWAP_AFTER_MERGE_BOOL =
            "support_swap_after_merge_bool";
    public static final String KEY_ALLOW_ADDING_APNS_BOOL = "allow_adding_apns_bool";
    public static final String KEY_ALLOW_EMERGENCY_NUMBERS_IN_CALL_LOG_BOOL =
            "allow_emergency_numbers_in_call_log_bool";
    public static final String KEY_SMS_REQUIRES_DESTINATION_NUMBER_CONVERSION_BOOL =
            "sms_requires_destination_number_conversion_bool";
    public static final String KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL =
            "carrier_supports_ss_over_ut_bool";
    public static final String KEY_MMS_MAX_MESSAGE_SIZE_INT = "maxMessageSize";
    public static final String KEY_MMS_USER_AGENT_STRING = "userAgent";
    public static final String KEY_MMS_UA_PROF_URL_STRING = "uaProfUrl";

    private final CopyOnWriteArrayList<CarrierConfigChangeListener> mListeners =
            new CopyOnWriteArrayList<>();

    /** @hide -- obtained via Context.getSystemService(CARRIER_CONFIG_SERVICE). */
    public CarrierConfigManager() { }

    /** @deprecated AOSP deprecated this in API 33 in favour of getConfig(String...). */
    @Deprecated
    public PersistableBundle getConfig() { return null; }

    public PersistableBundle getConfig(String... keys) { return new PersistableBundle(); }

    /** @deprecated AOSP deprecated this in API 33 in favour of getConfigForSubId(int, String...). */
    @Deprecated
    public PersistableBundle getConfigForSubId(int subId) { return null; }

    public PersistableBundle getConfigForSubId(int subId, String... keys) {
        return new PersistableBundle();
    }

    public PersistableBundle getConfigByComponentForSubId(String prefix, int subId) {
        return null;
    }

    /**
     * AOSP returns true only when the bundle carries
     * KEY_CARRIER_CONFIG_APPLIED_BOOL. Our bundles never do, which is correct:
     * no carrier has been identified.
     */
    public static boolean isConfigForIdentifiedCarrier(PersistableBundle bundle) {
        return bundle != null && bundle.getBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL);
    }

    /** No carrier config exists to change, so nothing is broadcast. */
    public void notifyConfigChangedForSubId(int subId) { }

    public void registerCarrierConfigChangeListener(Executor executor,
            CarrierConfigChangeListener listener) {
        if (executor == null) throw new IllegalArgumentException("executor is null");
        if (listener == null) throw new IllegalArgumentException("listener is null");
        // Registration is kept so unregister works; nothing ever fires because
        // there is no carrier config to change.
        mListeners.add(listener);
    }

    public void unregisterCarrierConfigChangeListener(CarrierConfigChangeListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener is null");
        mListeners.remove(listener);
    }

    /** AOSP declares this as an interface with one method. */
    public interface CarrierConfigChangeListener {
        void onCarrierConfigChanged(int slotIndex, int subId, int carrierId, int specificCarrierId);
    }

    /**
     * Carrier-config keys for the Apn feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class Apn {
        private Apn() { }

        public static final String KEY_PREFIX = "apn.";
        public static final String KEY_SETTINGS_DEFAULT_PROTOCOL_STRING = "apn.settings_default_protocol_string";
        public static final String KEY_SETTINGS_DEFAULT_ROAMING_PROTOCOL_STRING = "apn.settings_default_roaming_protocol_string";
        public static final String PROTOCOL_IPV4 = "IP";
        public static final String PROTOCOL_IPV4V6 = "IPV4V6";
        public static final String PROTOCOL_IPV6 = "IPV6";
    }

    /**
     * Carrier-config keys for the Bsf feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class Bsf {
        private Bsf() { }

        public static final String KEY_BSF_SERVER_FQDN_STRING = "bsf.bsf_server_fqdn_string";
        public static final String KEY_BSF_SERVER_PORT_INT = "bsf.bsf_server_port_int";
        public static final String KEY_BSF_TRANSPORT_TYPE_INT = "bsf.bsf_transport_type_int";
        public static final String KEY_PREFIX = "bsf.";
    }

    /**
     * Carrier-config keys for the Gps feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class Gps {
        private Gps() { }

        public static final String KEY_ENABLE_NI_SUPL_MESSAGE_INJECTION_BOOL = "gps.enable_ni_supl_message_injection_bool";
        public static final String KEY_PERSIST_LPP_MODE_BOOL = "gps.persist_lpp_mode_bool";
        public static final String KEY_PREFIX = "gps.";
    }

    /**
     * Carrier-config keys for the Ims feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class Ims {
        private Ims() { }

        public static final int E911_RTCP_INACTIVITY_ON_CONNECTED = 3;
        public static final int E911_RTP_INACTIVITY_ON_CONNECTED = 4;
        public static final int GEOLOCATION_PIDF_FOR_EMERGENCY_ON_CELLULAR = 4;
        public static final int GEOLOCATION_PIDF_FOR_EMERGENCY_ON_WIFI = 2;
        public static final int GEOLOCATION_PIDF_FOR_NON_EMERGENCY_ON_CELLULAR = 3;
        public static final int GEOLOCATION_PIDF_FOR_NON_EMERGENCY_ON_WIFI = 1;
        public static final int IPSEC_AUTHENTICATION_ALGORITHM_HMAC_MD5 = 0;
        public static final int IPSEC_AUTHENTICATION_ALGORITHM_HMAC_SHA1 = 1;
        public static final int IPSEC_ENCRYPTION_ALGORITHM_AES_CBC = 2;
        public static final int IPSEC_ENCRYPTION_ALGORITHM_DES_EDE3_CBC = 1;
        public static final int IPSEC_ENCRYPTION_ALGORITHM_NULL = 0;
        public static final String KEY_CAPABILITY_TYPE_CALL_COMPOSER_INT_ARRAY = "ims.capability_type_call_composer_int_array";
        public static final String KEY_CAPABILITY_TYPE_OPTIONS_UCE_INT_ARRAY = "ims.capability_type_options_uce_int_array";
        public static final String KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY = "ims.capability_type_presence_uce_int_array";
        public static final String KEY_CAPABILITY_TYPE_SMS_INT_ARRAY = "ims.capability_type_sms_int_array";
        public static final String KEY_CAPABILITY_TYPE_UT_INT_ARRAY = "ims.capability_type_ut_int_array";
        public static final String KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY = "ims.capability_type_video_int_array";
        public static final String KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY = "ims.capability_type_voice_int_array";
        public static final String KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL = "ims.enable_presence_capability_exchange_bool";
        public static final String KEY_ENABLE_PRESENCE_GROUP_SUBSCRIBE_BOOL = "ims.enable_presence_group_subscribe_bool";
        public static final String KEY_ENABLE_PRESENCE_PUBLISH_BOOL = "ims.enable_presence_publish_bool";
        public static final String KEY_GEOLOCATION_PIDF_IN_SIP_INVITE_SUPPORT_INT_ARRAY = "ims.geolocation_pidf_in_sip_invite_support_int_array";
        public static final String KEY_GEOLOCATION_PIDF_IN_SIP_REGISTER_SUPPORT_INT_ARRAY = "ims.geolocation_pidf_in_sip_register_support_int_array";
        public static final String KEY_GRUU_ENABLED_BOOL = "ims.gruu_enabled_bool";
        public static final String KEY_IMS_PDN_ENABLED_IN_NO_VOPS_SUPPORT_INT_ARRAY = "ims.ims_pdn_enabled_in_no_vops_support_int_array";
        public static final String KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL = "ims.ims_single_registration_required_bool";
        public static final String KEY_IMS_USER_AGENT_STRING = "ims.ims_user_agent_string";
        public static final String KEY_IPSEC_AUTHENTICATION_ALGORITHMS_INT_ARRAY = "ims.ipsec_authentication_algorithms_int_array";
        public static final String KEY_IPSEC_ENCRYPTION_ALGORITHMS_INT_ARRAY = "ims.ipsec_encryption_algorithms_int_array";
        public static final String KEY_IPV4_SIP_MTU_SIZE_CELLULAR_INT = "ims.ipv4_sip_mtu_size_cellular_int";
        public static final String KEY_IPV6_SIP_MTU_SIZE_CELLULAR_INT = "ims.ipv6_sip_mtu_size_cellular_int";
        public static final String KEY_KEEP_PDN_UP_IN_NO_VOPS_BOOL = "ims.keep_pdn_up_in_no_vops_bool";
        public static final String KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE = "ims.mmtel_requires_provisioning_bundle";
        public static final String KEY_NON_RCS_CAPABILITIES_CACHE_EXPIRATION_SEC_INT = "ims.non_rcs_capabilities_cache_expiration_sec_int";
        public static final String KEY_PHONE_CONTEXT_DOMAIN_NAME_STRING = "ims.phone_context_domain_name_string";
        public static final String KEY_PREFIX = "ims.";
        public static final String KEY_RCS_BULK_CAPABILITY_EXCHANGE_BOOL = "ims.rcs_bulk_capability_exchange_bool";
        public static final String KEY_RCS_FEATURE_TAG_ALLOWED_STRING_ARRAY = "ims.rcs_feature_tag_allowed_string_array";
        public static final String KEY_RCS_REQUIRES_PROVISIONING_BUNDLE = "ims.rcs_requires_provisioning_bundle";
        public static final String KEY_REGISTRATION_EVENT_PACKAGE_SUPPORTED_BOOL = "ims.registration_event_package_supported_bool";
        public static final String KEY_REGISTRATION_EXPIRY_TIMER_SEC_INT = "ims.registration_expiry_timer_sec_int";
        public static final String KEY_REGISTRATION_RETRY_BASE_TIMER_MILLIS_INT = "ims.registration_retry_base_timer_millis_int";
        public static final String KEY_REGISTRATION_RETRY_MAX_TIMER_MILLIS_INT = "ims.registration_retry_max_timer_millis_int";
        public static final String KEY_REGISTRATION_SUBSCRIBE_EXPIRY_TIMER_SEC_INT = "ims.registration_subscribe_expiry_timer_sec_int";
        public static final String KEY_REQUEST_URI_TYPE_INT = "ims.request_uri_type_int";
        public static final String KEY_SIP_OVER_IPSEC_ENABLED_BOOL = "ims.sip_over_ipsec_enabled_bool";
        public static final String KEY_SIP_PREFERRED_TRANSPORT_INT = "ims.sip_preferred_transport_int";
        public static final String KEY_SIP_SERVER_PORT_NUMBER_INT = "ims.sip_server_port_number_int";
        public static final String KEY_SIP_TIMER_B_MILLIS_INT = "ims.sip_timer_b_millis_int";
        public static final String KEY_SIP_TIMER_C_MILLIS_INT = "ims.sip_timer_c_millis_int";
        public static final String KEY_SIP_TIMER_D_MILLIS_INT = "ims.sip_timer_d_millis_int";
        public static final String KEY_SIP_TIMER_F_MILLIS_INT = "ims.sip_timer_f_millis_int";
        public static final String KEY_SIP_TIMER_H_MILLIS_INT = "ims.sip_timer_h_millis_int";
        public static final String KEY_SIP_TIMER_J_MILLIS_INT = "ims.sip_timer_j_millis_int";
        public static final String KEY_SIP_TIMER_T1_MILLIS_INT = "ims.sip_timer_t1_millis_int";
        public static final String KEY_SIP_TIMER_T2_MILLIS_INT = "ims.sip_timer_t2_millis_int";
        public static final String KEY_SIP_TIMER_T4_MILLIS_INT = "ims.sip_timer_t4_millis_int";
        public static final String KEY_SUPPORTED_RATS_INT_ARRAY = "ims.supported_rats_int_array";
        public static final String KEY_USE_SIP_URI_FOR_PRESENCE_SUBSCRIBE_BOOL = "ims.use_sip_uri_for_presence_subscribe_bool";
        public static final String KEY_WIFI_OFF_DEFERRING_TIME_MILLIS_INT = "ims.wifi_off_deferring_time_millis_int";
        public static final int NETWORK_TYPE_HOME = 0;
        public static final int NETWORK_TYPE_ROAMING = 1;
        public static final int PREFERRED_TRANSPORT_DYNAMIC_UDP_TCP = 2;
        public static final int PREFERRED_TRANSPORT_TCP = 1;
        public static final int PREFERRED_TRANSPORT_TLS = 3;
        public static final int PREFERRED_TRANSPORT_UDP = 0;
        public static final int REQUEST_URI_FORMAT_SIP = 1;
        public static final int REQUEST_URI_FORMAT_TEL = 0;
        public static final int RTCP_INACTIVITY_ON_CONNECTED = 1;
        public static final int RTCP_INACTIVITY_ON_HOLD = 0;
        public static final int RTP_INACTIVITY_ON_CONNECTED = 2;
    }

    /**
     * Carrier-config keys for the ImsEmergency feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class ImsEmergency {
        private ImsEmergency() { }

        public static final int DOMAIN_CS = 1;
        public static final int DOMAIN_PS_3GPP = 2;
        public static final int DOMAIN_PS_NON_3GPP = 3;
        public static final String KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT = "imsemergency.cross_stack_redial_timer_sec_int";
        public static final String KEY_EMERGENCY_CALLBACK_MODE_SUPPORTED_BOOL = "imsemergency.emergency_callback_mode_supported_bool";
        public static final String KEY_EMERGENCY_CALL_SETUP_TIMER_ON_CURRENT_NETWORK_SEC_INT = "imsemergency.emergency_call_setup_timer_on_current_network_sec_int";
        public static final String KEY_EMERGENCY_CDMA_PREFERRED_NUMBERS_STRING_ARRAY = "imsemergency.emergency_cdma_preferred_numbers_string_array";
        public static final String KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY = "imsemergency.emergency_domain_preference_int_array";
        public static final String KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY = "imsemergency.emergency_domain_preference_roaming_int_array";
        public static final String KEY_EMERGENCY_LTE_PREFERRED_AFTER_NR_FAILED_BOOL = "imsemergency.emergency_lte_preferred_after_nr_failed_bool";
        public static final String KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT = "imsemergency.emergency_network_scan_type_int";
        public static final String KEY_EMERGENCY_OVER_CS_ROAMING_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY = "imsemergency.emergency_over_cs_roaming_supported_access_network_types_int_array";
        public static final String KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY = "imsemergency.emergency_over_cs_supported_access_network_types_int_array";
        public static final String KEY_EMERGENCY_OVER_IMS_ROAMING_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY = "imsemergency.emergency_over_ims_roaming_supported_3gpp_network_types_int_array";
        public static final String KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY = "imsemergency.emergency_over_ims_supported_3gpp_network_types_int_array";
        public static final String KEY_EMERGENCY_OVER_IMS_SUPPORTED_RATS_INT_ARRAY = "imsemergency.emergency_over_ims_supported_rats_int_array";
        public static final String KEY_EMERGENCY_QOS_PRECONDITION_SUPPORTED_BOOL = "imsemergency.emergency_qos_precondition_supported_bool";
        public static final String KEY_EMERGENCY_REGISTRATION_TIMER_MILLIS_INT = "imsemergency.emergency_registration_timer_millis_int";
        public static final String KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL = "imsemergency.emergency_requires_ims_registration_bool";
        public static final String KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL = "imsemergency.emergency_requires_volte_enabled_bool";
        public static final String KEY_EMERGENCY_SCAN_TIMER_SEC_INT = "imsemergency.emergency_scan_timer_sec_int";
        public static final String KEY_EMERGENCY_VOWIFI_REQUIRES_CONDITION_INT = "imsemergency.emergency_vowifi_requires_condition_int";
        public static final String KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT = "imsemergency.maximum_cellular_search_timer_sec_int";
        public static final String KEY_MAXIMUM_NUMBER_OF_EMERGENCY_TRIES_OVER_VOWIFI_INT = "imsemergency.maximum_number_of_emergency_tries_over_vowifi_int";
        public static final String KEY_PREFER_IMS_EMERGENCY_WHEN_VOICE_CALLS_ON_CS_BOOL = "imsemergency.prefer_ims_emergency_when_voice_calls_on_cs_bool";
        public static final String KEY_PREFIX = "imsemergency.";
        public static final String KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT = "imsemergency.quick_cross_stack_redial_timer_sec_int";
        public static final String KEY_REFRESH_GEOLOCATION_TIMEOUT_MILLIS_INT = "imsemergency.refresh_geolocation_timeout_millis_int";
        public static final String KEY_RETRY_EMERGENCY_ON_IMS_PDN_BOOL = "imsemergency.retry_emergency_on_ims_pdn_bool";
        public static final String KEY_SCAN_LIMITED_SERVICE_AFTER_VOLTE_FAILURE_BOOL = "imsemergency.scan_limited_service_after_volte_failure_bool";
        public static final String KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL = "imsemergency.start_quick_cross_stack_redial_timer_when_registered_bool";
        public static final int REDIAL_TIMER_DISABLED = 0;
        public static final int SCAN_TYPE_FULL_SERVICE = 1;
        public static final int SCAN_TYPE_FULL_SERVICE_FOLLOWED_BY_LIMITED_SERVICE = 2;
        public static final int SCAN_TYPE_NO_PREFERENCE = 0;
        public static final int VOWIFI_REQUIRES_NONE = 0;
        public static final int VOWIFI_REQUIRES_SETTING_ENABLED = 1;
        public static final int VOWIFI_REQUIRES_VALID_EID = 2;
    }

    /**
     * Carrier-config keys for the ImsRtt feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class ImsRtt {
        private ImsRtt() { }

        public static final String KEY_PREFIX = "imsrtt.";
        public static final String KEY_RED_PAYLOAD_TYPE_INT = "imsrtt.red_payload_type_int";
        public static final String KEY_T140_PAYLOAD_TYPE_INT = "imsrtt.t140_payload_type_int";
        public static final String KEY_TEXT_AS_BANDWIDTH_KBPS_INT = "imsrtt.text_as_bandwidth_kbps_int";
        public static final String KEY_TEXT_CODEC_CAPABILITY_PAYLOAD_TYPES_BUNDLE = "imsrtt.text_codec_capability_payload_types_bundle";
        public static final String KEY_TEXT_ON_DEFAULT_BEARER_SUPPORTED_BOOL = "imsrtt.text_on_default_bearer_supported_bool";
        public static final String KEY_TEXT_QOS_PRECONDITION_SUPPORTED_BOOL = "imsrtt.text_qos_precondition_supported_bool";
        public static final String KEY_TEXT_RR_BANDWIDTH_BPS_INT = "imsrtt.text_rr_bandwidth_bps_int";
        public static final String KEY_TEXT_RS_BANDWIDTH_BPS_INT = "imsrtt.text_rs_bandwidth_bps_int";
    }

    /**
     * Carrier-config keys for the ImsServiceEntitlement feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class ImsServiceEntitlement {
        private ImsServiceEntitlement() { }

        public static final String KEY_ENTITLEMENT_SERVER_URL_STRING = "imsserviceentitlement.entitlement_server_url_string";
        public static final String KEY_FCM_SENDER_ID_STRING = "imsserviceentitlement.fcm_sender_id_string";
        public static final String KEY_IMS_PROVISIONING_BOOL = "imsserviceentitlement.ims_provisioning_bool";
        public static final String KEY_PREFIX = "imsserviceentitlement.";
        public static final String KEY_SHOW_VOWIFI_WEBVIEW_BOOL = "imsserviceentitlement.show_vowifi_webview_bool";
    }

    /**
     * Carrier-config keys for the ImsSms feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class ImsSms {
        private ImsSms() { }

        public static final String KEY_PREFIX = "imssms.";
        public static final String KEY_SMS_CSFB_RETRY_ON_FAILURE_BOOL = "imssms.sms_csfb_retry_on_failure_bool";
        public static final String KEY_SMS_MAX_RETRY_COUNT_INT = "imssms.sms_max_retry_count_int";
        public static final String KEY_SMS_MAX_RETRY_OVER_IMS_COUNT_INT = "imssms.sms_max_retry_over_ims_count_int";
        public static final String KEY_SMS_OVER_IMS_FORMAT_INT = "imssms.sms_over_ims_format_int";
        public static final String KEY_SMS_OVER_IMS_SEND_RETRY_DELAY_MILLIS_INT = "imssms.sms_over_ims_send_retry_delay_millis_int";
        public static final String KEY_SMS_OVER_IMS_SUPPORTED_BOOL = "imssms.sms_over_ims_supported_bool";
        public static final String KEY_SMS_OVER_IMS_SUPPORTED_RATS_INT_ARRAY = "imssms.sms_over_ims_supported_rats_int_array";
        public static final String KEY_SMS_RP_CAUSE_VALUES_TO_FALLBACK_INT_ARRAY = "imssms.sms_rp_cause_values_to_fallback_int_array";
        public static final String KEY_SMS_RP_CAUSE_VALUES_TO_RETRY_OVER_IMS_INT_ARRAY = "imssms.sms_rp_cause_values_to_retry_over_ims_int_array";
        public static final String KEY_SMS_TR1_TIMER_MILLIS_INT = "imssms.sms_tr1_timer_millis_int";
        public static final String KEY_SMS_TR2_TIMER_MILLIS_INT = "imssms.sms_tr2_timer_millis_int";
        public static final int SMS_FORMAT_3GPP = 0;
        public static final int SMS_FORMAT_3GPP2 = 1;
    }

    /**
     * Carrier-config keys for the ImsSs feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class ImsSs {
        private ImsSs() { }

        public static final int CALL_WAITING_SYNC_FIRST_CHANGE = 3;
        public static final int CALL_WAITING_SYNC_FIRST_POWER_UP = 2;
        public static final int CALL_WAITING_SYNC_IMS_ONLY = 4;
        public static final int CALL_WAITING_SYNC_NONE = 0;
        public static final int CALL_WAITING_SYNC_USER_CHANGE = 1;
        public static final String KEY_NETWORK_INITIATED_USSD_OVER_IMS_SUPPORTED_BOOL = "imsss.network_initiated_ussd_over_ims_supported_bool";
        public static final String KEY_PREFIX = "imsss.";
        public static final String KEY_TERMINAL_BASED_CALL_WAITING_DEFAULT_ENABLED_BOOL = "imsss.terminal_based_call_waiting_default_enabled_bool";
        public static final String KEY_TERMINAL_BASED_CALL_WAITING_SYNC_TYPE_INT = "imsss.terminal_based_call_waiting_sync_type_int";
        public static final String KEY_USE_CSFB_ON_XCAP_OVER_UT_FAILURE_BOOL = "imsss.use_csfb_on_xcap_over_ut_failure_bool";
        public static final String KEY_UT_AS_SERVER_FQDN_STRING = "imsss.ut_as_server_fqdn_string";
        public static final String KEY_UT_AS_SERVER_PORT_INT = "imsss.ut_as_server_port_int";
        public static final String KEY_UT_IPTYPE_HOME_INT = "imsss.ut_iptype_home_int";
        public static final String KEY_UT_IPTYPE_ROAMING_INT = "imsss.ut_iptype_roaming_int";
        public static final String KEY_UT_REQUIRES_IMS_REGISTRATION_BOOL = "imsss.ut_requires_ims_registration_bool";
        public static final String KEY_UT_SERVER_BASED_SERVICES_INT_ARRAY = "imsss.ut_server_based_services_int_array";
        public static final String KEY_UT_SUPPORTED_WHEN_PS_DATA_OFF_BOOL = "imsss.ut_supported_when_ps_data_off_bool";
        public static final String KEY_UT_SUPPORTED_WHEN_ROAMING_BOOL = "imsss.ut_supported_when_roaming_bool";
        public static final String KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY = "imsss.ut_terminal_based_services_int_array";
        public static final String KEY_UT_TRANSPORT_TYPE_INT = "imsss.ut_transport_type_int";
        public static final String KEY_XCAP_OVER_UT_SUPPORTED_RATS_INT_ARRAY = "imsss.xcap_over_ut_supported_rats_int_array";
        public static final int SUPPLEMENTARY_SERVICE_CB_ACR = 20;
        public static final int SUPPLEMENTARY_SERVICE_CB_ALL = 12;
        public static final int SUPPLEMENTARY_SERVICE_CB_BAIC = 18;
        public static final int SUPPLEMENTARY_SERVICE_CB_BAOC = 14;
        public static final int SUPPLEMENTARY_SERVICE_CB_BIC_ROAM = 19;
        public static final int SUPPLEMENTARY_SERVICE_CB_BIL = 21;
        public static final int SUPPLEMENTARY_SERVICE_CB_BOIC = 15;
        public static final int SUPPLEMENTARY_SERVICE_CB_BOIC_EXHC = 16;
        public static final int SUPPLEMENTARY_SERVICE_CB_IBS = 17;
        public static final int SUPPLEMENTARY_SERVICE_CB_OBS = 13;
        public static final int SUPPLEMENTARY_SERVICE_CF_ALL = 1;
        public static final int SUPPLEMENTARY_SERVICE_CF_ALL_CONDITONAL_FORWARDING = 3;
        public static final int SUPPLEMENTARY_SERVICE_CF_CFB = 4;
        public static final int SUPPLEMENTARY_SERVICE_CF_CFNL = 7;
        public static final int SUPPLEMENTARY_SERVICE_CF_CFNRC = 6;
        public static final int SUPPLEMENTARY_SERVICE_CF_CFNRY = 5;
        public static final int SUPPLEMENTARY_SERVICE_CF_CFU = 2;
        public static final int SUPPLEMENTARY_SERVICE_CW = 0;
        public static final int SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIP = 8;
        public static final int SUPPLEMENTARY_SERVICE_IDENTIFICATION_OIR = 10;
        public static final int SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIP = 9;
        public static final int SUPPLEMENTARY_SERVICE_IDENTIFICATION_TIR = 11;
    }

    /**
     * Carrier-config keys for the ImsVoice feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class ImsVoice {
        private ImsVoice() { }

        public static final int ALERTING_SRVCC_SUPPORT = 1;
        public static final int BANDWIDTH_EFFICIENT = 0;
        public static final int BASIC_SRVCC_SUPPORT = 0;
        public static final int CONFERENCE_SUBSCRIBE_TYPE_IN_DIALOG = 0;
        public static final int CONFERENCE_SUBSCRIBE_TYPE_OUT_OF_DIALOG = 1;
        public static final int EVS_ENCODED_BW_TYPE_FB = 3;
        public static final int EVS_ENCODED_BW_TYPE_NB = 0;
        public static final int EVS_ENCODED_BW_TYPE_NB_WB = 4;
        public static final int EVS_ENCODED_BW_TYPE_NB_WB_SWB = 5;
        public static final int EVS_ENCODED_BW_TYPE_NB_WB_SWB_FB = 6;
        public static final int EVS_ENCODED_BW_TYPE_SWB = 2;
        public static final int EVS_ENCODED_BW_TYPE_WB = 1;
        public static final int EVS_ENCODED_BW_TYPE_WB_SWB = 7;
        public static final int EVS_ENCODED_BW_TYPE_WB_SWB_FB = 8;
        public static final int EVS_OPERATIONAL_MODE_AMRWB_IO = 1;
        public static final int EVS_OPERATIONAL_MODE_PRIMARY = 0;
        public static final int EVS_PRIMARY_MODE_BITRATE_128_0_KBPS = 11;
        public static final int EVS_PRIMARY_MODE_BITRATE_13_2_KBPS = 4;
        public static final int EVS_PRIMARY_MODE_BITRATE_16_4_KBPS = 5;
        public static final int EVS_PRIMARY_MODE_BITRATE_24_4_KBPS = 6;
        public static final int EVS_PRIMARY_MODE_BITRATE_32_0_KBPS = 7;
        public static final int EVS_PRIMARY_MODE_BITRATE_48_0_KBPS = 8;
        public static final int EVS_PRIMARY_MODE_BITRATE_5_9_KBPS = 0;
        public static final int EVS_PRIMARY_MODE_BITRATE_64_0_KBPS = 9;
        public static final int EVS_PRIMARY_MODE_BITRATE_7_2_KBPS = 1;
        public static final int EVS_PRIMARY_MODE_BITRATE_8_0_KBPS = 2;
        public static final int EVS_PRIMARY_MODE_BITRATE_96_0_KBPS = 10;
        public static final int EVS_PRIMARY_MODE_BITRATE_9_6_KBPS = 3;
        public static final String KEY_AMRNB_PAYLOAD_DESCRIPTION_BUNDLE = "imsvoice.amrnb_payload_description_bundle";
        public static final String KEY_AMRNB_PAYLOAD_TYPE_INT_ARRAY = "imsvoice.amrnb_payload_type_int_array";
        public static final String KEY_AMRWB_PAYLOAD_DESCRIPTION_BUNDLE = "imsvoice.amrwb_payload_description_bundle";
        public static final String KEY_AMRWB_PAYLOAD_TYPE_INT_ARRAY = "imsvoice.amrwb_payload_type_int_array";
        public static final String KEY_AMR_CODEC_ATTRIBUTE_MODESET_INT_ARRAY = "imsvoice.amr_codec_attribute_modeset_int_array";
        public static final String KEY_AMR_CODEC_ATTRIBUTE_PAYLOAD_FORMAT_INT = "imsvoice.amr_codec_attribute_payload_format_int";
        public static final String KEY_AUDIO_AS_BANDWIDTH_KBPS_INT = "imsvoice.audio_as_bandwidth_kbps_int";
        public static final String KEY_AUDIO_CODEC_CAPABILITY_PAYLOAD_TYPES_BUNDLE = "imsvoice.audio_codec_capability_payload_types_bundle";
        public static final String KEY_AUDIO_INACTIVITY_CALL_END_REASONS_INT_ARRAY = "imsvoice.audio_inactivity_call_end_reasons_int_array";
        public static final String KEY_AUDIO_RR_BANDWIDTH_BPS_INT = "imsvoice.audio_rr_bandwidth_bps_int";
        public static final String KEY_AUDIO_RS_BANDWIDTH_BPS_INT = "imsvoice.audio_rs_bandwidth_bps_int";
        public static final String KEY_AUDIO_RTCP_INACTIVITY_TIMER_MILLIS_INT = "imsvoice.audio_rtcp_inactivity_timer_millis_int";
        public static final String KEY_AUDIO_RTP_INACTIVITY_TIMER_MILLIS_INT = "imsvoice.audio_rtp_inactivity_timer_millis_int";
        public static final String KEY_CARRIER_VOLTE_ROAMING_AVAILABLE_BOOL = "imsvoice.carrier_volte_roaming_available_bool";
        public static final String KEY_CODEC_ATTRIBUTE_MODE_CHANGE_CAPABILITY_INT = "imsvoice.codec_attribute_mode_change_capability_int";
        public static final String KEY_CODEC_ATTRIBUTE_MODE_CHANGE_NEIGHBOR_INT = "imsvoice.codec_attribute_mode_change_neighbor_int";
        public static final String KEY_CODEC_ATTRIBUTE_MODE_CHANGE_PERIOD_INT = "imsvoice.codec_attribute_mode_change_period_int";
        public static final String KEY_CONFERENCE_FACTORY_URI_STRING = "imsvoice.conference_factory_uri_string";
        public static final String KEY_CONFERENCE_SUBSCRIBE_TYPE_INT = "imsvoice.conference_subscribe_type_int";
        public static final String KEY_DEDICATED_BEARER_WAIT_TIMER_MILLIS_INT = "imsvoice.dedicated_bearer_wait_timer_millis_int";
        public static final String KEY_DTMFNB_PAYLOAD_TYPE_INT_ARRAY = "imsvoice.dtmfnb_payload_type_int_array";
        public static final String KEY_DTMFWB_PAYLOAD_TYPE_INT_ARRAY = "imsvoice.dtmfwb_payload_type_int_array";
        public static final String KEY_EVS_CODEC_ATTRIBUTE_BANDWIDTH_INT = "imsvoice.evs_codec_attribute_bandwidth_int";
        public static final String KEY_EVS_CODEC_ATTRIBUTE_BITRATE_INT_ARRAY = "imsvoice.evs_codec_attribute_bitrate_int_array";
        public static final String KEY_EVS_CODEC_ATTRIBUTE_CHANNELS_INT = "imsvoice.evs_codec_attribute_channels_int";
        public static final String KEY_EVS_CODEC_ATTRIBUTE_CH_AW_RECV_INT = "imsvoice.evs_codec_attribute_ch_aw_recv_int";
        public static final String KEY_EVS_CODEC_ATTRIBUTE_CMR_INT = "imsvoice.codec_attribute_cmr_int";
        public static final String KEY_EVS_CODEC_ATTRIBUTE_DTX_BOOL = "imsvoice.evs_codec_attribute_dtx_bool";
        public static final String KEY_EVS_CODEC_ATTRIBUTE_DTX_RECV_BOOL = "imsvoice.evs_codec_attribute_dtx_recv_bool";
        public static final String KEY_EVS_CODEC_ATTRIBUTE_HF_ONLY_INT = "imsvoice.evs_codec_attribute_hf_only_int";
        public static final String KEY_EVS_CODEC_ATTRIBUTE_MODE_SWITCH_INT = "imsvoice.evs_codec_attribute_mode_switch_int";
        public static final String KEY_EVS_PAYLOAD_DESCRIPTION_BUNDLE = "imsvoice.evs_payload_description_bundle";
        public static final String KEY_EVS_PAYLOAD_TYPE_INT_ARRAY = "imsvoice.evs_payload_type_int_array";
        public static final String KEY_INCLUDE_CALLER_ID_SERVICE_CODES_IN_SIP_INVITE_BOOL = "imsvoice.include_caller_id_service_codes_in_sip_invite_bool";
        public static final String KEY_MINIMUM_SESSION_EXPIRES_TIMER_SEC_INT = "imsvoice.minimum_session_expires_timer_sec_int";
        public static final String KEY_MO_CALL_REQUEST_TIMEOUT_MILLIS_INT = "imsvoice.mo_call_request_timeout_millis_int";
        public static final String KEY_MULTIENDPOINT_SUPPORTED_BOOL = "imsvoice.multiendpoint_supported_bool";
        public static final String KEY_OIP_SOURCE_FROM_HEADER_BOOL = "imsvoice.oip_source_from_header_bool";
        public static final String KEY_PRACK_SUPPORTED_FOR_18X_BOOL = "imsvoice.prack_supported_for_18x_bool";
        public static final String KEY_PREFIX = "imsvoice.";
        public static final String KEY_RINGBACK_TIMER_MILLIS_INT = "imsvoice.ringback_timer_millis_int";
        public static final String KEY_RINGING_TIMER_MILLIS_INT = "imsvoice.ringing_timer_millis_int";
        public static final String KEY_SESSION_EXPIRES_TIMER_SEC_INT = "imsvoice.session_expires_timer_sec_int";
        public static final String KEY_SESSION_PRIVACY_TYPE_INT = "imsvoice.session_privacy_type_int";
        public static final String KEY_SESSION_REFRESHER_TYPE_INT = "imsvoice.session_refresher_type_int";
        public static final String KEY_SESSION_REFRESH_METHOD_INT = "imsvoice.session_refresh_method_int";
        public static final String KEY_SESSION_TIMER_SUPPORTED_BOOL = "imsvoice.session_timer_supported_bool";
        public static final String KEY_SRVCC_TYPE_INT_ARRAY = "imsvoice.srvcc_type_int_array";
        public static final String KEY_VOICE_ON_DEFAULT_BEARER_SUPPORTED_BOOL = "imsvoice.voice_on_default_bearer_supported_bool";
        public static final String KEY_VOICE_QOS_PRECONDITION_SUPPORTED_BOOL = "imsvoice.voice_qos_precondition_supported_bool";
        public static final int MIDCALL_SRVCC_SUPPORT = 3;
        public static final int OCTET_ALIGNED = 1;
        public static final int PREALERTING_SRVCC_SUPPORT = 2;
        public static final int SESSION_PRIVACY_TYPE_HEADER = 0;
        public static final int SESSION_PRIVACY_TYPE_ID = 2;
        public static final int SESSION_PRIVACY_TYPE_NONE = 1;
        public static final int SESSION_REFRESHER_TYPE_UAC = 1;
        public static final int SESSION_REFRESHER_TYPE_UAS = 2;
        public static final int SESSION_REFRESHER_TYPE_UNKNOWN = 0;
        public static final int SESSION_REFRESH_METHOD_INVITE = 0;
        public static final int SESSION_REFRESH_METHOD_UPDATE_PREFERRED = 1;
    }

    /**
     * Carrier-config keys for the ImsVt feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class ImsVt {
        private ImsVt() { }

        public static final String KEY_H264_PAYLOAD_DESCRIPTION_BUNDLE = "imsvt.h264_payload_description_bundle";
        public static final String KEY_H264_PAYLOAD_TYPE_INT_ARRAY = "imsvt.h264_payload_type_int_array";
        public static final String KEY_H264_VIDEO_CODEC_ATTRIBUTE_PROFILE_LEVEL_ID_STRING = "imsvt.h264_video_codec_attribute_profile_level_id_string";
        public static final String KEY_PREFIX = "imsvt.";
        public static final String KEY_VIDEO_AS_BANDWIDTH_KBPS_INT = "imsvt.video_as_bandwidth_kbps_int";
        public static final String KEY_VIDEO_CODEC_ATTRIBUTE_FRAME_RATE_INT = "imsvt.video_codec_attribute_frame_rate_int";
        public static final String KEY_VIDEO_CODEC_ATTRIBUTE_PACKETIZATION_MODE_INT = "imsvt.video_codec_attribute_packetization_mode_int";
        public static final String KEY_VIDEO_CODEC_ATTRIBUTE_RESOLUTION_INT_ARRAY = "imsvt.video_codec_attribute_resolution_int_array";
        public static final String KEY_VIDEO_CODEC_CAPABILITY_PAYLOAD_TYPES_BUNDLE = "imsvt.video_codec_capability_payload_types_bundle";
        public static final String KEY_VIDEO_ON_DEFAULT_BEARER_SUPPORTED_BOOL = "imsvt.video_on_default_bearer_supported_bool";
        public static final String KEY_VIDEO_QOS_PRECONDITION_SUPPORTED_BOOL = "imsvt.video_qos_precondition_supported_bool";
        public static final String KEY_VIDEO_RR_BANDWIDTH_BPS_INT = "imsvt.video_rr_bandwidth_bps_int";
        public static final String KEY_VIDEO_RS_BANDWIDTH_BPS_INT = "imsvt.video_rs_bandwidth_bps_int";
        public static final String KEY_VIDEO_RTCP_INACTIVITY_TIMER_MILLIS_INT = "imsvt.video_rtcp_inactivity_timer_millis_int";
        public static final String KEY_VIDEO_RTP_DSCP_INT = "imsvt.video_rtp_dscp_int";
        public static final String KEY_VIDEO_RTP_INACTIVITY_TIMER_MILLIS_INT = "imsvt.video_rtp_inactivity_timer_millis_int";
    }

    /**
     * Carrier-config keys for the ImsWfc feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class ImsWfc {
        private ImsWfc() { }

        public static final String KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL = "imswfc.emergency_call_over_emergency_pdn_bool";
        public static final String KEY_PIDF_SHORT_CODE_STRING_ARRAY = "imswfc.pidf_short_code_string_array";
        public static final String KEY_PREFIX = "imswfc.";
    }

    /**
     * Carrier-config keys for the Iwlan feature area.
     *
     * CONSTANTS ONLY (AOSP declares no members beyond a private constructor).
     * Every key is a compile-time constant, so javac inlines it into the
     * calling app -- these classes exist so a SOURCE reference or a
     * Class-literal resolves, not because the value could otherwise be missed
     * at runtime. There is no carrier here, so CarrierConfigManager returns an
     * empty bundle and every lookup falls back to the caller's default.
     */
    public static final class Iwlan {
        private Iwlan() { }

        public static final int AUTHENTICATION_METHOD_CERT = 1;
        public static final int AUTHENTICATION_METHOD_EAP_ONLY = 0;
        public static final int EPDG_ADDRESS_CELLULAR_LOC = 3;
        public static final int EPDG_ADDRESS_IPV4_ONLY = 2;
        public static final int EPDG_ADDRESS_IPV4_PREFERRED = 0;
        public static final int EPDG_ADDRESS_IPV6_PREFERRED = 1;
        public static final int EPDG_ADDRESS_PCO = 2;
        public static final int EPDG_ADDRESS_PLMN = 1;
        public static final int EPDG_ADDRESS_STATIC = 0;
        public static final int EPDG_ADDRESS_VISITED_COUNTRY = 4;
        public static final int ID_TYPE_FQDN = 2;
        public static final int ID_TYPE_KEY_ID = 11;
        public static final int ID_TYPE_RFC822_ADDR = 3;
        public static final String KEY_ADD_KE_TO_CHILD_SESSION_REKEY_BOOL = "iwlan.add_ke_to_child_session_rekey_bool";
        public static final String KEY_CHILD_SA_REKEY_HARD_TIMER_SEC_INT = "iwlan.child_sa_rekey_hard_timer_sec_int";
        public static final String KEY_CHILD_SA_REKEY_SOFT_TIMER_SEC_INT = "iwlan.child_sa_rekey_soft_timer_sec_int";
        public static final String KEY_CHILD_SESSION_AES_CBC_KEY_SIZE_INT_ARRAY = "iwlan.child_session_aes_cbc_key_size_int_array";
        public static final String KEY_CHILD_SESSION_AES_CTR_KEY_SIZE_INT_ARRAY = "iwlan.child_session_aes_ctr_key_size_int_array";
        public static final String KEY_CHILD_SESSION_AES_GCM_KEY_SIZE_INT_ARRAY = "iwlan.child_session_aes_gcm_key_size_int_array";
        public static final String KEY_DIFFIE_HELLMAN_GROUPS_INT_ARRAY = "iwlan.diffie_hellman_groups_int_array";
        public static final String KEY_DPD_TIMER_SEC_INT = "iwlan.dpd_timer_sec_int";
        public static final String KEY_EPDG_ADDRESS_IP_TYPE_PREFERENCE_INT = "iwlan.epdg_address_ip_type_preference_int";
        public static final String KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY = "iwlan.epdg_address_priority_int_array";
        public static final String KEY_EPDG_AUTHENTICATION_METHOD_INT = "iwlan.epdg_authentication_method_int";
        public static final String KEY_EPDG_PCO_ID_IPV4_INT = "iwlan.epdg_pco_id_ipv4_int";
        public static final String KEY_EPDG_PCO_ID_IPV6_INT = "iwlan.epdg_pco_id_ipv6_int";
        public static final String KEY_EPDG_STATIC_ADDRESS_ROAMING_STRING = "iwlan.epdg_static_address_roaming_string";
        public static final String KEY_EPDG_STATIC_ADDRESS_STRING = "iwlan.epdg_static_address_string";
        public static final String KEY_IKE_LOCAL_ID_TYPE_INT = "iwlan.ike_local_id_type_int";
        public static final String KEY_IKE_REKEY_HARD_TIMER_SEC_INT = "iwlan.ike_rekey_hard_timer_in_sec";
        public static final String KEY_IKE_REKEY_SOFT_TIMER_SEC_INT = "iwlan.ike_rekey_soft_timer_sec_int";
        public static final String KEY_IKE_REMOTE_ID_TYPE_INT = "iwlan.ike_remote_id_type_int";
        public static final String KEY_IKE_SESSION_AES_CBC_KEY_SIZE_INT_ARRAY = "iwlan.ike_session_encryption_aes_cbc_key_size_int_array";
        public static final String KEY_IKE_SESSION_AES_CTR_KEY_SIZE_INT_ARRAY = "iwlan.ike_session_encryption_aes_ctr_key_size_int_array";
        public static final String KEY_IKE_SESSION_AES_GCM_KEY_SIZE_INT_ARRAY = "iwlan.ike_session_encryption_aes_gcm_key_size_int_array";
        public static final String KEY_MAX_RETRIES_INT = "iwlan.max_retries_int";
        public static final String KEY_MCC_MNCS_STRING_ARRAY = "iwlan.mcc_mncs_string_array";
        public static final String KEY_NATT_KEEP_ALIVE_TIMER_SEC_INT = "iwlan.natt_keep_alive_timer_sec_int";
        public static final String KEY_PREFIX = "iwlan.";
        public static final String KEY_RETRANSMIT_TIMER_MSEC_INT_ARRAY = "iwlan.retransmit_timer_sec_int_array";
        public static final String KEY_SUPPORTED_CHILD_SESSION_AEAD_ALGORITHMS_INT_ARRAY = "iwlan.supported_child_session_aead_algorithms_int_array";
        public static final String KEY_SUPPORTED_CHILD_SESSION_ENCRYPTION_ALGORITHMS_INT_ARRAY = "iwlan.supported_child_session_encryption_algorithms_int_array";
        public static final String KEY_SUPPORTED_IKE_SESSION_AEAD_ALGORITHMS_INT_ARRAY = "iwlan.supported_ike_session_aead_algorithms_int_array";
        public static final String KEY_SUPPORTED_IKE_SESSION_ENCRYPTION_ALGORITHMS_INT_ARRAY = "iwlan.supported_ike_session_encryption_algorithms_int_array";
        public static final String KEY_SUPPORTED_INTEGRITY_ALGORITHMS_INT_ARRAY = "iwlan.supported_integrity_algorithms_int_array";
        public static final String KEY_SUPPORTED_PRF_ALGORITHMS_INT_ARRAY = "iwlan.supported_prf_algorithms_int_array";
        public static final String KEY_SUPPORTS_CHILD_SESSION_MULTIPLE_SA_PROPOSALS_BOOL = "iwlan.supports_child_session_multiple_sa_proposals_bool";
        public static final String KEY_SUPPORTS_EAP_AKA_FAST_REAUTH_BOOL = "iwlan.supports_eap_aka_fast_reauth_bool";
        public static final String KEY_SUPPORTS_IKE_SESSION_MULTIPLE_SA_PROPOSALS_BOOL = "iwlan.supports_ike_session_multiple_sa_proposals_bool";
    }
}
