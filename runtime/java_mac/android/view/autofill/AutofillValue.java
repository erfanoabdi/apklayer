// android.view.autofill.AutofillValue -- a typed value the autofill framework
// passes to/from a view. Compose's semantics/autofill (SemanticsModifier ->
// AndroidComposeView autofill) constructs these via the static factories during
// the measure pass; the missing factories threw NoSuchMethodError there. We have
// no autofill service, so the values are inert holders -- the methods just need
// to exist + round-trip their kind. v1.0.429.
// Source: frameworks/base/core/java/android/view/autofill/AutofillValue.java
package android.view.autofill;

// AOSP declares `public final class AutofillValue implements Parcelable`; the
// missing `implements` had the same silent-type-check consequence described on
// AutofillId.
public final class AutofillValue implements android.os.Parcelable {
    private final int type;          // 0=text 1=toggle 2=list 3=date
    private final CharSequence text;
    private final boolean toggle;
    private final int listIndex;
    private final long date;

    private AutofillValue(int type, CharSequence text, boolean toggle, int listIndex, long date) {
        this.type = type; this.text = text; this.toggle = toggle; this.listIndex = listIndex; this.date = date;
    }

    public static AutofillValue forText(CharSequence value) { return new AutofillValue(0, value, false, 0, 0); }
    public static AutofillValue forToggle(boolean value)    { return new AutofillValue(1, null, value, 0, 0); }
    public static AutofillValue forList(int value)          { return new AutofillValue(2, null, false, value, 0); }
    public static AutofillValue forDate(long value)         { return new AutofillValue(3, null, false, 0, value); }

    public boolean isText()   { return type == 0; }
    public boolean isToggle() { return type == 1; }
    public boolean isList()   { return type == 2; }
    public boolean isDate()   { return type == 3; }

    public CharSequence getTextValue() { return text; }
    public boolean getToggleValue()    { return toggle; }
    public int getListValue()          { return listIndex; }
    public long getDateValue()         { return date; }

    // Parcelable. Every field is a plain value, so this marshals and rebuilds
    // for real. AOSP writes the kind first and then only the payload that kind
    // uses; the same shape is kept here.
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(type);
        switch (type) {
            case 0: dest.writeCharSequence(text); break;
            case 1: dest.writeBoolean(toggle); break;
            case 2: dest.writeInt(listIndex); break;
            case 3: dest.writeLong(date); break;
        }
    }
    public static final android.os.Parcelable.Creator<AutofillValue> CREATOR =
            new android.os.Parcelable.Creator<AutofillValue>() {
        @Override public AutofillValue createFromParcel(android.os.Parcel source) {
            final int type = source.readInt();
            switch (type) {
                case 0:  return forText(source.readCharSequence());
                case 1:  return forToggle(source.readBoolean());
                case 2:  return forList(source.readInt());
                case 3:  return forDate(source.readLong());
                default: return forText(null);
            }
        }
        @Override public AutofillValue[] newArray(int size) { return new AutofillValue[size]; }
    };
}
