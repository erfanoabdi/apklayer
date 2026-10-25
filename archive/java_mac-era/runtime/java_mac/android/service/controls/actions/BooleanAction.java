package android.service.controls.actions;

/**
 * The user flipped a ToggleTemplate / ToggleRangeTemplate switch.
 *
 * Source: frameworks/base/core/java/android/service/controls/actions/BooleanAction.java
 */
public final class BooleanAction extends ControlAction {

    private final boolean mNewState;

    public BooleanAction(String templateId, boolean newState) {
        this(templateId, newState, null);
    }

    public BooleanAction(String templateId, boolean newState, String challengeValue) {
        super(templateId, challengeValue);
        mNewState = newState;
    }

    public boolean getNewState() { return mNewState; }

    @Override public int getActionType() { return TYPE_BOOLEAN; }
}
