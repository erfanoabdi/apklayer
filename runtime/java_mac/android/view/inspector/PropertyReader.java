package android.view.inspector;
/** Receives inspectable property values during
 *  InspectionCompanion.readProperties. Mac.apk has no inspector consuming these,
 *  so the reads are accepted and discarded. API 29.
 *  Source: frameworks/base/core/java/android/view/inspector/PropertyReader.java */
public interface PropertyReader {
    void readBoolean(int id, boolean value);
    void readByte(int id, byte value);
    void readChar(int id, char value);
    void readDouble(int id, double value);
    void readFloat(int id, float value);
    void readInt(int id, int value);
    void readLong(int id, long value);
    void readShort(int id, short value);
    void readObject(int id, Object value);
    void readColor(int id, int value);
    void readColor(int id, long value);
    void readColor(int id, android.graphics.Color value);
    void readGravity(int id, int value);
    void readResourceId(int id, int value);
    void readIntEnum(int id, int value);
    void readIntFlag(int id, int value);

    /** AOSP PropertyReader.PropertyTypeMismatchException: a companion read a
     *  property with the wrong typed overload. */
    class PropertyTypeMismatchException extends RuntimeException {
        public PropertyTypeMismatchException(int id, String expectedPropertyType,
                String actualPropertyType, String propertyName) {
            super(formatMessage(id, expectedPropertyType, actualPropertyType, propertyName));
        }
        public PropertyTypeMismatchException(int id, String expectedPropertyType,
                String actualPropertyType) {
            this(id, expectedPropertyType, actualPropertyType, null);
        }
        private static String formatMessage(int id, String expectedPropertyType,
                String actualPropertyType, String propertyName) {
            final String name = propertyName == null
                    ? String.format("with ID 0x%08X", id)
                    : String.format("\"%s\"", propertyName);
            return String.format("Attempted to read property %s as type %s, but it is type %s.",
                    name, actualPropertyType, expectedPropertyType);
        }
    }
}
