package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

/**
 * Media classes whose Robolectric shadows only record calls (no metadata, no output file): the original classes over libmedia_jni and the
 * instance's media services, like MediaExtractor and MediaCodec. Their class initializers need the media JNI registered first.
 */
final class HostMediaShadows {
    private HostMediaShadows() {}

    /** Duration, tags, cover art and frames from the tree's StagefrightMetadataRetriever (Robolectric's answered null for every key). */
    @Implements(className = "android.media.MediaMetadataRetriever", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static final class MediaMetadataRetriever {
        @Implementation protected static void __staticInitializer__() {
            NativeMedia.ensureInitialized();
            ReflectionHelpers.callStaticMethod(org.robolectric.shadow.api.Shadow.class.getClassLoader(), "android.media.MediaMetadataRetriever", "__staticInitializer__");
        }
    }

    /** MP4/WebM/OGG files from the tree's writers (Robolectric's copied raw samples into a file no player reads). */
    @Implements(className = "android.media.MediaMuxer", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static final class MediaMuxer {
        @Implementation protected static void __staticInitializer__() { NativeMedia.ensureInitialized(); }  // the original only loads media_jni
    }
}
