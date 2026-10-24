// android.util.AttributeSet -- read-only handle to XML attributes
// passed in to View constructors. v2 doesn't inflate layout XML, so
// real apps that walk this interface get empty results.
package android.util;

public interface AttributeSet {
    int getAttributeCount();
    String getAttributeName(int index);
    /**
     * The namespace URI of the attribute at {@code index}, or null when the
     * implementation does not track namespaces.
     *
     * AOSP declares this DEFAULT (API 36, core/api/current.txt:
     * "method public default String getAttributeNamespace(int)"), precisely so
     * that adding it did not break the many third-party AttributeSet
     * implementations that predate it. Mirrored as a default for the same
     * reason -- making it abstract here would break every implementor in the
     * tree and in apps.
     */
    default String getAttributeNamespace(int index) { return null; }
    String getAttributeValue(int index);
    String getAttributeValue(String namespace, String name);
    String getPositionDescription();
    int getAttributeNameResource(int index);
    int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue);
    boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue);
    int getAttributeResourceValue(String namespace, String attribute, int defaultValue);
    int getAttributeIntValue(String namespace, String attribute, int defaultValue);
    int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue);
    float getAttributeFloatValue(String namespace, String attribute, float defaultValue);
    int getAttributeListValue(int index, String[] options, int defaultValue);
    boolean getAttributeBooleanValue(int index, boolean defaultValue);
    int getAttributeResourceValue(int index, int defaultValue);
    int getAttributeIntValue(int index, int defaultValue);
    int getAttributeUnsignedIntValue(int index, int defaultValue);
    float getAttributeFloatValue(int index, float defaultValue);
    String getIdAttribute();
    String getClassAttribute();
    int getIdAttributeResourceValue(int defaultValue);
    int getStyleAttribute();
}
