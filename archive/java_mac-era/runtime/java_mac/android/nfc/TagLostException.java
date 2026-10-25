// android.nfc.TagLostException -- thrown when a tag goes out of range during
// an I/O operation.
//
// An IOException subclass, mirrored exactly from
// packages/modules/Nfc framework/java/android/nfc/TagLostException.java. The
// type must exist so an app's `catch (TagLostException)` clause resolves --
// a catch type is resolved when the handler is verified, so a missing class
// breaks the method whether or not a tag was ever involved.
package android.nfc;

import java.io.IOException;

public class TagLostException extends IOException {

    public TagLostException() { super(); }

    public TagLostException(String message) { super(message); }
}
