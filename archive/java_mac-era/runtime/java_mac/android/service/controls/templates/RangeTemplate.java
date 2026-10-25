package android.service.controls.templates;

/**
 * A slider control: min, max, step, current value, and the format string the
 * panel uses to render the number ("%.1f" + a unit).
 *
 * ⚠ The constructor VALIDATES, exactly as AOSP does -- min <= current <= max
 * and step > 0. A smart-home app that ships an inconsistent range gets the
 * IllegalArgumentException it would get on a device, at the point it builds the
 * template, rather than a silently broken slider later.
 *
 * Source: frameworks/base/core/java/android/service/controls/templates/RangeTemplate.java
 */
public final class RangeTemplate extends ControlTemplate {

    private final float mMinValue;
    private final float mMaxValue;
    private final float mCurrentValue;
    private final float mStepValue;
    private final CharSequence mFormatString;

    public RangeTemplate(String templateId, float minValue, float maxValue,
            float currentValue, float stepValue, CharSequence formatString) {
        super(templateId);
        mMinValue = minValue;
        mMaxValue = maxValue;
        mCurrentValue = currentValue;
        mStepValue = stepValue;
        // AOSP defaults the format string to "%.1f" when the caller passes null.
        mFormatString = formatString == null ? "%.1f" : formatString;
        validate();
    }

    public float getMinValue() { return mMinValue; }

    public float getMaxValue() { return mMaxValue; }

    public float getCurrentValue() { return mCurrentValue; }

    public float getStepValue() { return mStepValue; }

    public CharSequence getFormatString() { return mFormatString; }

    @Override public int getTemplateType() { return TYPE_RANGE; }

    private void validate() {
        if (Float.compare(mMinValue, mMaxValue) > 0) {
            throw new IllegalArgumentException("minValue > maxValue");
        }
        if (Float.compare(mMinValue, mCurrentValue) > 0) {
            throw new IllegalArgumentException("minValue > currentValue");
        }
        if (Float.compare(mCurrentValue, mMaxValue) > 0) {
            throw new IllegalArgumentException("currentValue > maxValue");
        }
        if (mStepValue <= 0) {
            throw new IllegalArgumentException("stepValue must be positive");
        }
    }
}
