package apklayer.host;

import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * HotSpot's helpful NullPointerException messages in ART's words (art/runtime/common_throws.cc). The index, cast, arithmetic and
 * NoSuchMethodException messages are rewritten where the exception is made (java.lang.ApklayerHooks.message, JdkIdentityPatch).
 * HotSpot does not know the invoked method's return type nor a field's type, which ART names: those two messages keep the shape without them.
 */
@DoNotInstrument
public final class GuestMessages {
    private GuestMessages() {}

    private static String qualified(String type) {
        return switch (type) {
            case "String", "Object", "Integer", "Long", "Boolean", "Class", "Thread", "Runnable", "Number", "Double", "Float", "Short", "Byte", "Character", "Iterable", "CharSequence", "StringBuilder", "Enum", "Throwable", "Exception" -> "java.lang." + type;
            default -> type;
        };
    }

    static String npe(String m) {
        try {
            if (m.startsWith("Cannot invoke \"")) {  // Cannot invoke "String.length()" because "<local1>" is null
                int end = m.indexOf("\" because");
                String call = m.substring(15, end);
                int dot = call.lastIndexOf('.', call.indexOf('('));
                String owner = qualified(call.substring(0, dot));
                return "Attempt to invoke virtual method '" + owner + call.substring(dot) + "' on a null object reference";
            }
            if (m.startsWith("Cannot read field \"")) return "Attempt to read from field '" + field(m, 19) + "' on a null object reference";
            if (m.startsWith("Cannot assign field \"")) return "Attempt to write to field '" + field(m, 21) + "' on a null object reference";
            if (m.startsWith("Cannot load from ")) return "Attempt to read from null array";
            if (m.startsWith("Cannot store to ")) return "Attempt to write to null array";
            if (m.startsWith("Cannot read the array length")) return "Attempt to get length of null array";
            if (m.startsWith("Cannot enter synchronized block")) return "Attempt to do a synchronize operation on a null object";
            if (m.startsWith("Cannot throw exception")) return "throw with null exception";
        } catch (RuntimeException unparsed) { /* HotSpot's */ }
        return m;
    }

    private static String field(String m, int from) {
        return m.substring(from, m.indexOf('"', from));
    }
}
