// android.telephony.PhoneStateListener -- the pre-API-31 telephony callback.
//
// Mac.apk has no radio, so beyond the ONE immediate snapshot AOSP's
// TelephonyRegistry delivers at registration time (see TelephonyManager.listen)
// nothing ever fires. All 22 callbacks are declared so an app's @Override
// compiles and so listen() can dispatch the ones it can answer.
//
// @deprecated AOSP deprecated the whole class in API 31 in favour of
// TelephonyCallback; still shipped and still what most apps use.
//
// Source: frameworks/base/telephony/java/android/telephony/PhoneStateListener.java
package android.telephony;

import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsReasonInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Deprecated
public class PhoneStateListener {

    public static final int LISTEN_NONE = 0;
    public static final int LISTEN_SERVICE_STATE = 0x00000001;
    public static final int LISTEN_SIGNAL_STRENGTH = 0x00000002;
    public static final int LISTEN_MESSAGE_WAITING_INDICATOR = 0x00000004;
    public static final int LISTEN_CALL_FORWARDING_INDICATOR = 0x00000008;
    public static final int LISTEN_CELL_LOCATION = 0x00000010;
    public static final int LISTEN_CALL_STATE = 0x00000020;
    public static final int LISTEN_DATA_CONNECTION_STATE = 0x00000040;
    public static final int LISTEN_DATA_ACTIVITY = 0x00000080;
    public static final int LISTEN_SIGNAL_STRENGTHS = 0x00000100;
    public static final int LISTEN_CELL_INFO = 0x00000400;
    public static final int LISTEN_PRECISE_DATA_CONNECTION_STATE = 0x00001000;
    public static final int LISTEN_USER_MOBILE_DATA_STATE = 0x00080000;
    public static final int LISTEN_DISPLAY_INFO_CHANGED = 0x00100000;
    public static final int LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE = 0x00400000;
    public static final int LISTEN_EMERGENCY_NUMBER_LIST = 0x01000000;
    public static final int LISTEN_CALL_DISCONNECT_CAUSES = 0x02000000;
    public static final int LISTEN_IMS_CALL_DISCONNECT_CAUSES = 0x08000000;
    public static final int LISTEN_REGISTRATION_FAILURE = 0x40000000;
    public static final int LISTEN_BARRING_INFO = 0x80000000;

    /** AOSP captures the delivery Executor here at CONSTRUCTION time. */
    private final Executor mExecutor;

    public PhoneStateListener() { mExecutor = null; }

    public PhoneStateListener(Executor executor) {
        if (executor == null) throw new IllegalArgumentException("PhoneStateListener Executor must be non-null");
        mExecutor = executor;
    }

    /** @hide -- used by TelephonyManager.listen when the app supplied an Executor. */
    public Executor getExecutor() { return mExecutor; }

    public void onServiceStateChanged(ServiceState serviceState) { }

    /** @deprecated AOSP replaced this by onSignalStrengthsChanged in API 7. */
    @Deprecated public void onSignalStrengthChanged(int asu) { }

    public void onSignalStrengthsChanged(SignalStrength signalStrength) { }

    public void onMessageWaitingIndicatorChanged(boolean mwi) { }

    public void onCallForwardingIndicatorChanged(boolean cfi) { }

    public void onCellLocationChanged(CellLocation location) { }

    public void onCallStateChanged(int state, String phoneNumber) { }

    /** @deprecated AOSP replaced this by the (state, networkType) form in API 28. */
    @Deprecated public void onDataConnectionStateChanged(int state) { }

    public void onDataConnectionStateChanged(int state, int networkType) { }

    public void onDataActivity(int direction) { }

    public void onCellInfoChanged(List<CellInfo> cellInfo) { }

    public void onPreciseDataConnectionStateChanged(
            PreciseDataConnectionState dataConnectionState) { }

    public void onUserMobileDataStateChanged(boolean enabled) { }

    public void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo) { }

    public void onActiveDataSubscriptionIdChanged(int subId) { }

    public void onEmergencyNumberListChanged(
            Map<Integer, List<EmergencyNumber>> emergencyNumberList) { }

    public void onCallDisconnectCauseChanged(int disconnectCause, int preciseDisconnectCause) { }

    public void onImsCallDisconnectCauseChanged(ImsReasonInfo imsReasonInfo) { }

    public void onRegistrationFailed(CellIdentity cellIdentity, String chosenPlmn, int domain,
            int causeCode, int additionalCauseCode) { }

    public void onBarringInfoChanged(BarringInfo barringInfo) { }
}
