package android.icu.util;
/** A number paired with a MeasureUnit. */
public class Measure {
    private final Number mNumber; private final MeasureUnit mUnit;
    public Measure(Number number, MeasureUnit unit) { mNumber = number; mUnit = unit; }
    public Number getNumber() { return mNumber; }
    public MeasureUnit getUnit() { return mUnit; }
    @Override public boolean equals(Object o) { return o instanceof Measure && java.util.Objects.equals(mNumber, ((Measure)o).mNumber) && java.util.Objects.equals(mUnit, ((Measure)o).mUnit); }
    @Override public int hashCode() { return java.util.Objects.hash(mNumber, mUnit); }
    @Override public String toString() { return mNumber + " " + mUnit; }
}
