package hello.nativesurface;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.view.Surface;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** MediaCodec output into a SurfaceTexture of another process (TextureService): its frame-available callbacks fire and updateTexImage latches the frames. */
final class TextureCheck {
    interface Decoder { long decode(Surface surface) throws Exception; }

    static void run(Context context, Decoder decoder) throws Exception {
        LinkedBlockingQueue<IBinder> connected = new LinkedBlockingQueue<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) { connected.add(binder); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(context.bindService(new Intent(context, TextureService.class), connection, Context.BIND_AUTO_CREATE), "bind TextureService");
        try {
            IBinder service = connected.poll(15, TimeUnit.SECONDS);
            require(service != null, "TextureService did not connect");
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            Surface surface;
            try {
                require(service.transact(TextureService.SURFACE, data, reply, 0), "SURFACE");
                surface = Surface.CREATOR.createFromParcel(reply);
            } finally { data.recycle(); reply.recycle(); }
            require(surface.isValid(), "the parcelled Surface is not valid");
            long last = decoder.decode(surface);
            int frames = 0; long timestamp = -1; String error = "";
            for (int wait = 0; wait < 40 && timestamp != last * 1000; wait++) {  // the last callback may still be on its way (up to 2 s)
                data = Parcel.obtain(); reply = Parcel.obtain();
                try {
                    require(service.transact(TextureService.FRAMES, data, reply, 0), "FRAMES");
                    frames = reply.readInt(); timestamp = reply.readLong(); error = reply.readString();
                } finally { data.recycle(); reply.recycle(); }
                if (timestamp != last * 1000) Thread.sleep(50);
            }
            require(error.isEmpty(), "updateTexImage failed in the texture's process: " + error);
            require(frames > 0 && timestamp == last * 1000, "the other process latched " + frames + " frames, last timestamp " + timestamp + " ns, expected " + last * 1000);
            surface.release();
            System.out.println("[hello-native-surface] TEXTURE PASS " + frames + " frames reached a SurfaceTexture in another process (last at " + last + " us)");
        } finally { context.unbindService(connection); }
    }

    private static void require(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    private TextureCheck() {}
}
