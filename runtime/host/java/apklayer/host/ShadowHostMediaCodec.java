package apklayer.host;

import android.media.MediaCodec;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowMediaCodec;

/** Robolectric's MediaCodec plus: releasing an output buffer with render = true shows its frame (HostCodecs.render). */
@Implements(MediaCodec.class)
public class ShadowHostMediaCodec extends ShadowMediaCodec {
    @RealObject private MediaCodec real;

    @Implementation
    @Override
    protected void releaseOutputBuffer(int index, boolean render) {
        if (render) HostCodecs.render(real, index);
        super.releaseOutputBuffer(index, render);
    }

    @Implementation
    @Override
    protected void releaseOutputBuffer(int index, long renderTimestampNs) {
        HostCodecs.render(real, index);
        super.releaseOutputBuffer(index, renderTimestampNs);
    }
}
