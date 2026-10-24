package android.icu.text;

/**
 * Formatting-context settings: how a formatted name should be capitalized and
 * whether to prefer standard or dialect display names.
 *
 * Source: https://developer.android.com/reference/android/icu/text/DisplayContext
 */
public enum DisplayContext {

    STANDARD_NAMES(Type.DIALECT_HANDLING, 0),
    DIALECT_NAMES(Type.DIALECT_HANDLING, 1),

    CAPITALIZATION_NONE(Type.CAPITALIZATION, 0),
    CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE(Type.CAPITALIZATION, 1),
    CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE(Type.CAPITALIZATION, 2),
    CAPITALIZATION_FOR_UI_LIST_OR_MENU(Type.CAPITALIZATION, 3),
    CAPITALIZATION_FOR_STANDALONE(Type.CAPITALIZATION, 4),

    LENGTH_FULL(Type.DISPLAY_LENGTH, 0),
    LENGTH_SHORT(Type.DISPLAY_LENGTH, 1),

    SUBSTITUTE(Type.SUBSTITUTE_HANDLING, 0),
    NO_SUBSTITUTE(Type.SUBSTITUTE_HANDLING, 1);

    public enum Type { DIALECT_HANDLING, CAPITALIZATION, DISPLAY_LENGTH, SUBSTITUTE_HANDLING }

    private final Type mType;
    private final int mValue;

    DisplayContext(Type type, int value) { mType = type; mValue = value; }

    public Type type() { return mType; }

    public int value() { return mValue; }
}
