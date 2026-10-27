package hello.nativecodec;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;

/** Borrowed real JVM descriptor, bounded source window and ownership after release. */
final class ExtractorFdCheck {
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }
    static void run(Context context) throws Exception {
        byte[] mp4;
        try (InputStream asset = context.getAssets().open("red-h264.mp4")) { mp4 = asset.readAllBytes(); }
        byte[] file = new byte[17 + mp4.length + 9];
        file[0] = 0x51;
        System.arraycopy(mp4, 0, file, 17, mp4.length);
        File path = new File(context.getCacheDir(), "native-fd-window.mp4");
        Files.write(path.toPath(), file);
        try (FileInputStream input = new FileInputStream(path)) {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(input.getFD(), 17, mp4.length);
                require(extractor.getTrackCount() == 1, "FD track count");
                require("video/avc".equals(extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME)), "FD track MIME");
                extractor.selectTrack(0);
                require(extractor.readSampleData(ByteBuffer.allocateDirect(1024 * 1024), 0) > 0, "FD sample read");
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                require(extractor.getSampleTime() == 0, "FD seek");
            } finally {
                extractor.release();
            }
            require(input.getFD().valid(), "extractor must preserve caller descriptor ownership");
            input.getChannel().position(0); // AOSP FileSource uses lseek on its shared open-file description.
            require(input.read() == 0x51, "caller descriptor remains usable");
            MediaExtractor detached = new MediaExtractor();
            try {
                try (FileInputStream owner = new FileInputStream(path)) {
                    detached.setDataSource(owner.getFD(), 17, mp4.length);
                } // Android permits closing the original immediately after setDataSource.
                detached.selectTrack(0);
                require(detached.readSampleData(ByteBuffer.allocateDirect(1024 * 1024), 0) > 0, "source survives caller early close");
            } finally { detached.release(); }
            System.out.println("[hello-native-codec] extractor FD PASS bounded offset, track/sample/seek, caller ownership/early close");
        } finally {
            Files.deleteIfExists(path.toPath());
        }
    }
    private ExtractorFdCheck() {}
}
