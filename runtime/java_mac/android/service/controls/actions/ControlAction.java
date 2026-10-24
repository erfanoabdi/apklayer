package android.service.controls.actions;

/**
 * A user interaction with one Control, handed to
 * ControlsProviderService.performControlAction.
 *
 * AOSP declares it abstract with a closed set of subclasses. See the package
 * note on android.service.controls.DeviceTypes for why nothing binds the
 * service here.
 *
 * ⚠ Constants EXTRACTED MECHANICALLY from /tmp/aosp_api/android16.txt. Two
 * runs again: RESPONSE_* (0..5) is the reply an app sends back, TYPE_* is which
 * subclass this is -- and TYPE_ERROR is -1 while there is no public TYPE_ = 3.
 *
 * Source: frameworks/base/core/java/android/service/controls/actions/ControlAction.java
 */
public abstract class ControlAction {

    public static final int RESPONSE_CHALLENGE_ACK = 3;
    public static final int RESPONSE_CHALLENGE_PASSPHRASE = 5;
    public static final int RESPONSE_CHALLENGE_PIN = 4;
    public static final int RESPONSE_FAIL = 2;
    public static final int RESPONSE_OK = 1;
    public static final int RESPONSE_UNKNOWN = 0;
    public static final int TYPE_BOOLEAN = 1;
    public static final int TYPE_COMMAND = 5;
    public static final int TYPE_ERROR = -1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_MODE = 4;

    private static final ControlAction ERROR_ACTION = new ControlAction("", null) {
        @Override public int getActionType() { return TYPE_ERROR; }
    };

    private final String mTemplateId;
    private final String mChallengeValue;

    ControlAction(String templateId, String challengeValue) {
        if (templateId == null) throw new NullPointerException("templateId");
        mTemplateId = templateId;
        mChallengeValue = challengeValue;
    }

    public abstract int getActionType();

    public String getTemplateId() { return mTemplateId; }

    /** The PIN/passphrase the user supplied after a RESPONSE_CHALLENGE_*, or
     *  null on the first attempt. */
    public String getChallengeValue() { return mChallengeValue; }

    /** AOSP's singleton handed back when an action could not be parsed. */
    public static ControlAction getErrorAction() { return ERROR_ACTION; }

    public static final boolean isValidResponse(int response) {
        return response >= RESPONSE_UNKNOWN && response <= RESPONSE_CHALLENGE_PASSPHRASE;
    }
}
