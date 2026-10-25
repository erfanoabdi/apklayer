package android.service.controls.actions;

/**
 * The user moved a RangeTemplate slider.
 *
 * Source: frameworks/base/core/java/android/service/controls/actions/FloatAction.java
 */
public final class FloatAction extends ControlAction {

    private final float mNewValue;

    public FloatAction(String templateId, float newValue) {
        this(templateId, newValue, null);
    }

    public FloatAction(String templateId, float newValue, String challengeValue) {
        super(templateId, challengeValue);
        mNewValue = newValue;
    }

    public float getNewValue() { return mNewValue; }

    @Override public int getActionType() { return TYPE_FLOAT; }
}
