// android.drm.DrmUtils -- file helpers the DRM framework uses internally, plus
// the one public entry point, getExtendedMetadataParser.
//
// Mirrors frameworks/base/drm/java/android/drm/DrmUtils.java (android16-release).
//
// SCOPE NOTE: ExtendedMetadataParser decodes a length-prefixed key/value blob
// that a plug-in returns under DrmStore.ConstraintsColumns.EXTENDED_METADATA.
// It is a plain byte-stream parser -- it does no decryption, evaluates no
// licence, and unlocks nothing -- so implementing it faithfully is ordinary
// interoperability work. It is implemented in full for that reason: an app
// that gets a metadata blob from somewhere else and parses it here gets the
// right answer. What it will never receive from US is a blob, because
// getConstraints always answers null (see DrmManagerClient).
package android.drm;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

@Deprecated
public class DrmUtils {

    public DrmUtils() {}

    /* package */ static byte[] readBytes(String path) throws IOException {
        return readBytes(new File(path));
    }

    /* package */ static byte[] readBytes(File file) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
        byte[] data = null;
        try {
            int length = bufferedStream.available();
            if (length > 0) {
                data = new byte[length];
                // AOSP ignores the return of read() here. Kept as-is: the
                // short-read case it glosses over is real but changing it
                // would change what DrmRights/DrmInfo consider "valid data",
                // and those validity checks are observable API behaviour.
                bufferedStream.read(data);
            }
        } finally {
            quietlyDispose(bufferedStream);
            quietlyDispose(inputStream);
        }
        return data;
    }

    /* package */ static void writeToFile(final String path, byte[] data) throws IOException {
        FileOutputStream outputStream = null;
        if (null != path && null != data) {
            try {
                outputStream = new FileOutputStream(path);
                outputStream.write(data);
            } finally {
                quietlyDispose(outputStream);
            }
        }
    }

    /* package */ static void removeFile(String path) throws IOException {
        new File(path).delete();
    }

    private static void quietlyDispose(Closeable closable) {
        try {
            if (null != closable) closable.close();
        } catch (IOException e) {
            // AOSP: "no need to care, at least as of now"
        }
    }

    public static ExtendedMetadataParser getExtendedMetadataParser(byte[] extendedMetadata) {
        return new ExtendedMetadataParser(extendedMetadata);
    }

    public static class ExtendedMetadataParser {
        HashMap<String, String> mMap = new HashMap<String, String>();

        private int readByte(byte[] constraintData, int arrayIndex) {
            return (int) constraintData[arrayIndex];
        }

        private String readMultipleBytes(byte[] constraintData, int numberOfBytes,
                int arrayIndex) {
            byte[] returnBytes = new byte[numberOfBytes];
            for (int j = arrayIndex, i = 0; j < arrayIndex + numberOfBytes; j++, i++) {
                returnBytes[i] = constraintData[j];
            }
            return new String(returnBytes);
        }

        /* package */ ExtendedMetadataParser(byte[] constraintData) {
            int index = 0;
            while (index < constraintData.length) {
                int keyLength = readByte(constraintData, index);
                index++;
                int valueLength = readByte(constraintData, index);
                index++;
                String strKey = readMultipleBytes(constraintData, keyLength, index);
                index += keyLength;
                String strValue = readMultipleBytes(constraintData, valueLength, index);
                // AOSP: a single space encodes the empty value.
                if (strValue.equals(" ")) strValue = "";
                index += valueLength;
                mMap.put(strKey, strValue);
            }
        }

        public Iterator<String> iterator() { return mMap.values().iterator(); }
        public Iterator<String> keyIterator() { return mMap.keySet().iterator(); }
        public String get(String key) { return mMap.get(key); }
    }
}
