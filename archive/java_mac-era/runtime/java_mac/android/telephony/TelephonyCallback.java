// android.telephony.TelephonyCallback
//
// The API 31+ replacement for PhoneStateListener. Apps subclass it and
// implement the per-event interfaces (CallStateListener, etc.) then register
// via TelephonyManager.registerTelephonyCallback. Mac.apk has no radio, so
// values are always the honest "no service" defaults, but the type + the
// nested listener interfaces must resolve for callback-registering code to
// compile. Mirrors
// frameworks/base/telephony/java/android/telephony/TelephonyCallback.java.
//
// ServiceStateListener / DisplayInfoListener / CellLocationListener used to
// type their callback parameter as a bare Object instead of AOSP's real
// ServiceState / TelephonyDisplayInfo / CellLocation types (all three already
// exist elsewhere in this package). That is not cosmetic: an app compiled
// against the real SDK implements e.g.
// onServiceStateChanged(Landroid/telephony/ServiceState;)V, a DIFFERENT
// method by JVM erasure than our old onServiceStateChanged(Ljava/lang/Object;)V
// -- javac never generates a bridge method for an overload it never saw at
// compile time. So the app's class did not actually implement our interface's
// abstract method at all, which is the exact "descriptor lie" class of bug
// CameraManager.getCameraCharacteristics and UsbManager.getAccessoryList in
// this tree already document being fixed for. Now that TelephonyManager
// actually delivers an immediate snapshot on registration (see
// TelephonyManager.java), these three types matter even though no radio-state
// CHANGE ever follows the first one.
package android.telephony;

public class TelephonyCallback {

    public TelephonyCallback() {}

    /** Marker for the modern call-state event. AOSP delivers the call state as
     *  an int (the incoming number was removed for privacy in API 31). */
    public interface CallStateListener {
        void onCallStateChanged(int state);
    }

    public interface SignalStrengthsListener {
        void onSignalStrengthsChanged(SignalStrength signalStrength);
    }

    public interface DataConnectionStateListener {
        void onDataConnectionStateChanged(int state, int networkType);
    }

    public interface DataActivityListener {
        void onDataActivity(int direction);
    }

    public interface ServiceStateListener {
        void onServiceStateChanged(ServiceState serviceState);
    }

    public interface CellInfoListener {
        void onCellInfoChanged(java.util.List<CellInfo> cellInfo);
    }

    public interface DisplayInfoListener {
        void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo);
    }

    public interface CellLocationListener {
        void onCellLocationChanged(CellLocation location);
    }

    // ---- API-36 listener interfaces added 2026-08-24 -----------------------
    // Every one is a marker interface an app IMPLEMENTS; declaring them costs
    // nothing and their absence is a hard link failure for any app that
    // implements the real SDK's version. None of them ever fires here (no
    // radio), which is the same as a device in airplane mode.
    // Source: frameworks/base/telephony/java/android/telephony/TelephonyCallback.java

    public interface ActiveDataSubscriptionIdListener {
        void onActiveDataSubscriptionIdChanged(int subId);
    }

    public interface BarringInfoListener {
        void onBarringInfoChanged(BarringInfo barringInfo);
    }

    public interface CallDisconnectCauseListener {
        void onCallDisconnectCauseChanged(int disconnectCause, int preciseDisconnectCause);
    }

    public interface CallForwardingIndicatorListener {
        void onCallForwardingIndicatorChanged(boolean cfi);
    }

    public interface CarrierNetworkListener {
        void onCarrierNetworkChange(boolean active);
    }

    public interface DataActivationStateListener {
        void onDataActivationStateChanged(int state);
    }

    public interface EmergencyNumberListListener {
        void onEmergencyNumberListChanged(
                java.util.Map<Integer, java.util.List<
                        android.telephony.emergency.EmergencyNumber>> emergencyNumberList);
    }

    public interface ImsCallDisconnectCauseListener {
        void onImsCallDisconnectCauseChanged(android.telephony.ims.ImsReasonInfo imsReasonInfo);
    }

    public interface MessageWaitingIndicatorListener {
        void onMessageWaitingIndicatorChanged(boolean mwi);
    }

    public interface PhysicalChannelConfigListener {
        void onPhysicalChannelConfigChanged(java.util.List<PhysicalChannelConfig> configs);
    }

    public interface PreciseDataConnectionStateListener {
        void onPreciseDataConnectionStateChanged(
                PreciseDataConnectionState dataConnectionState);
    }

    public interface RegistrationFailedListener {
        void onRegistrationFailed(CellIdentity cellIdentity, String chosenPlmn, int domain,
                int causeCode, int additionalCauseCode);
    }

    public interface UserMobileDataStateListener {
        void onUserMobileDataStateChanged(boolean enabled);
    }
}
