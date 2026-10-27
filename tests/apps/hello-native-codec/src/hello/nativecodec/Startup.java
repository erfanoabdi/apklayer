package hello.nativecodec;

import android.app.Application;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

/** Codec discovery must work during Application startup, before the Activity host loop. */
public class Startup extends Application {
    @Override public void onCreate() {
        super.onCreate();
        boolean found = false;
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (info.getName().equals("c2.apklayer.vp8.decoder")) {
                if (info.isHardwareAccelerated()) throw new AssertionError("unexpected hardware claim");
                found = true;
            }
        }
        if (!found) throw new AssertionError("native VP8 absent during Application.onCreate");
        System.out.println("[hello-native-codec] startup discovery PASS");
    }
}
