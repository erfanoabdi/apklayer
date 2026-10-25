// android.nfc.tech.TagTechnology -- the interface every NFC tag-technology
// wrapper implements (NfcA, IsoDep, Ndef, MifareClassic, ...).
//
// A pure interface, mirrored exactly from
// packages/modules/Nfc framework/java/android/nfc/tech/TagTechnology.java.
// It is here because implementing an interface is a class-STRUCTURE
// dependency: the JVM resolves it when the implementing class is DEFINED, so
// a bundled SDK carrying an `implements TagTechnology` class would
// NoClassDefFoundError on load even though it never touches a tag.
package android.nfc.tech;

import android.nfc.Tag;

import java.io.Closeable;
import java.io.IOException;

public interface TagTechnology extends Closeable {

    Tag getTag();

    void connect() throws IOException;

    @Override
    void close() throws IOException;

    boolean isConnected();
}
