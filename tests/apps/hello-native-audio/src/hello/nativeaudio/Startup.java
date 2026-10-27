package hello.nativeaudio;
import android.app.Application;
import android.media.AudioFormat;
import android.media.AudioTrack;
public class Startup extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            String implementation = (String) Class.forName("android.os.MessageQueue").getMethod("getImplName").invoke(null);
            System.out.println("[hello-native-audio] MessageQueue=" + implementation);
            if (implementation.equals("deli:true")) throw new AssertionError("queue incompatible with API36 scheduler");
        } catch (NoSuchMethodException olderFramework) {
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        if (!Boolean.getBoolean("apklayer.nativeaudio")) return;
        int minimum = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        try {
            Class<?> system = Class.forName("android.media.AudioSystem");
            System.out.println("[hello-native-audio] startup uid=" + android.os.Process.myUid()
                    + " pid=" + android.os.Process.myPid() + " format limits="
                    + AudioFormat.class.getField("SAMPLE_RATE_HZ_MIN").get(null) + "/"
                    + AudioFormat.class.getField("SAMPLE_RATE_HZ_MAX").get(null)
                    + " system channels=" + system.getField("OUT_CHANNEL_COUNT_MAX").get(null)
                    + " rates=" + system.getField("SAMPLE_RATE_HZ_MIN").get(null) + "/"
                    + system.getField("SAMPLE_RATE_HZ_MAX").get(null));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        if (minimum <= 0) throw new AssertionError("native AudioTrack startup buffer " + minimum);
        System.out.println("[hello-native-audio] startup AudioTrack PASS min=" + minimum);
    }
}
