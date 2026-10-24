// android.content.res.ObbScanner -- reads an OBB expansion file's footer.
//
// AOSP ref: frameworks/base/core/java/android/content/res/ObbScanner.java
// (android16-release). AOSP's getObbInfo() does the argument checks in Java
// and hands the canonical path to getObbInfo_native, which is
// libs/androidfw/ObbFile.cpp's ObbFile::parseObbFile. That parser is ported
// here in Java -- it is pure FILE PARSING with no kernel or HAL involvement,
// so there is nothing about it a single-process macOS host cannot do.
//
// Footer layout, from ObbFile.cpp (android16-release), which is the
// authoritative definition:
//   ... payload ...
//   [footer body, `footerSize` bytes, laid out as]
//       +0   uint32 LE  signature version   (must be 1, kSigVersion :48)
//       +4   uint32 LE  package version     (kPackageVersionOffset :51)
//       +8   uint32 LE  flags               (kFlagsOffset          :52)
//       +12  8 bytes    salt                (kSaltOffset           :53)
//       +20  uint32 LE  package name length (kPackageNameLenOffset :54)
//       +24  UTF-8      package name        (kPackageNameOffset    :55)
//   [footer tag, 8 bytes (kFooterTagSize :32)]
//       +0   uint32 LE  footerSize
//       +4   uint32 LE  0x01059983          (kSignature :46)
// with a whole-file minimum of 33 bytes (kFooterMinSize :34) and a footer
// capped at 32768 (kMaxBufSize :44).
//
// NOTE this is independent of StorageManager.mountObb(), which cannot mount an
// OBB here (no loop-mount / vold equivalent) and says so. Reading the metadata
// and mounting the filesystem are different operations, and only the second is
// blocked.
package android.content.res;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class ObbScanner {
    private ObbScanner() {}

    // ObbFile.cpp constants, transcribed.
    private static final int  FOOTER_TAG_SIZE   = 8;
    private static final int  FOOTER_MIN_SIZE   = 33;
    private static final int  MAX_BUF_SIZE      = 32768;
    private static final long SIGNATURE         = 0x01059983L;
    private static final int  SIG_VERSION       = 1;
    private static final int  PACKAGE_VERSION_OFFSET  = 4;
    private static final int  FLAGS_OFFSET            = 8;
    private static final int  SALT_OFFSET             = 12;
    private static final int  SALT_SIZE               = 8;
    private static final int  PACKAGE_NAME_LEN_OFFSET = 20;
    private static final int  PACKAGE_NAME_OFFSET     = 24;

    /**
     * AOSP's argument checks are verbatim (IllegalArgumentException for a null
     * path or a missing file); a footer that does not parse becomes an
     * IOException, which is what AOSP's native path throws for the same
     * conditions.
     */
    public static ObbInfo getObbInfo(String filePath) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("file path cannot be null");
        }
        final File obbFile = new File(filePath);
        if (!obbFile.exists()) {
            throw new IllegalArgumentException("OBB file does not exist: " + filePath);
        }
        final String canonicalFilePath = obbFile.getCanonicalPath();

        ObbInfo obbInfo = new ObbInfo();
        obbInfo.filename = canonicalFilePath;
        parseObbFile(new File(canonicalFilePath), obbInfo);
        return obbInfo;
    }

    private static void parseObbFile(File file, ObbInfo out) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            final long fileLength = raf.length();
            if (fileLength < FOOTER_MIN_SIZE) {
                throw new IOException("file is only " + fileLength
                        + " (less than " + FOOTER_MIN_SIZE + " minimum)");
            }

            byte[] tag = new byte[FOOTER_TAG_SIZE];
            raf.seek(fileLength - FOOTER_TAG_SIZE);
            raf.readFully(tag);
            ByteBuffer tb = ByteBuffer.wrap(tag).order(ByteOrder.LITTLE_ENDIAN);
            long footerSize = tb.getInt(0) & 0xffffffffL;
            long fileSig    = tb.getInt(4) & 0xffffffffL;
            if (fileSig != SIGNATURE) {
                throw new IOException(String.format(
                        "footer didn't match magic string (expected 0x%08x; got 0x%08x)",
                        SIGNATURE, fileSig));
            }
            if (footerSize > fileLength - FOOTER_TAG_SIZE || footerSize > MAX_BUF_SIZE) {
                throw new IOException("claimed footer size is too large (" + footerSize
                        + "; file size is " + fileLength + ")");
            }
            if (footerSize < (FOOTER_MIN_SIZE - FOOTER_TAG_SIZE)) {
                throw new IOException("claimed footer size is too small (" + footerSize
                        + "; minimum size is " + (FOOTER_MIN_SIZE - FOOTER_TAG_SIZE) + ")");
            }

            byte[] buf = new byte[(int) footerSize];
            raf.seek(fileLength - footerSize - FOOTER_TAG_SIZE);
            raf.readFully(buf);
            ByteBuffer fb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

            int sigVersion = fb.getInt(0);
            if (sigVersion != SIG_VERSION) {
                throw new IOException("Unsupported ObbFile version " + sigVersion);
            }

            out.version = fb.getInt(PACKAGE_VERSION_OFFSET);
            out.flags   = fb.getInt(FLAGS_OFFSET);
            out.salt    = new byte[SALT_SIZE];
            System.arraycopy(buf, SALT_OFFSET, out.salt, 0, SALT_SIZE);

            long nameLen = fb.getInt(PACKAGE_NAME_LEN_OFFSET) & 0xffffffffL;
            if (nameLen == 0 || nameLen > (footerSize - PACKAGE_NAME_OFFSET)) {
                throw new IOException("bad ObbFile package name length (" + nameLen
                        + "; " + (footerSize - PACKAGE_NAME_OFFSET) + " possible)");
            }
            out.packageName = new String(buf, PACKAGE_NAME_OFFSET, (int) nameLen,
                                         StandardCharsets.UTF_8);
        }
    }
}
