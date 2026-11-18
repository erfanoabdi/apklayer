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
    interface Decoder { void decode(Surface surface, FrameCheck frameCheck) throws Exception; }
    interface FrameCheck { void await(long presentationTimeUs) throws Exception; }

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
                require(reply.readInt() != android.os.Process.myPid(), "TextureService must be in another process");
                surface = Surface.CREATOR.createFromParcel(reply);
            } finally { data.recycle(); reply.recycle(); }
            try {
                require(surface.isValid(), "the parcelled Surface is not valid");
                FrameProgress progress = new FrameProgress(service);
                decoder.decode(surface, progress::await);
                require(progress.frames >= 3, "expected at least three latched VP9 frames, got " + progress.frames);
                System.out.println("[hello-native-surface] TEXTURE PASS VP9 latched " + progress.frames
                        + " frames with advancing timestamps in another process and reached EOS");
            } finally { surface.release(); }
        } finally { context.unbindService(connection); }
    }

    private static final class FrameProgress {
        private final IBinder service;
        private int frames;
        private long timestampNs = Long.MIN_VALUE;

        FrameProgress(IBinder service) { this.service = service; }

        void await(long presentationTimeUs) throws Exception {
            long expectedNs = TimeUnit.MICROSECONDS.toNanos(presentationTimeUs);
            require(expectedNs > timestampNs, "fixture PTS did not advance");
            long deadline = android.os.SystemClock.elapsedRealtime() + 2000;
            // Wait before releasing the next output so SurfaceTexture cannot coalesce our test frames.
            do {
                Parcel data = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    require(service.transact(TextureService.FRAMES, data, reply, 0), "FRAMES");
                    int latched = reply.readInt();
                    long actualNs = reply.readLong();
                    String error = reply.readString();
                    require(error.isEmpty(), "updateTexImage failed in the texture process: " + error);
                    if (actualNs == expectedNs && latched > frames) {
                        timestampNs = actualNs;
                        frames = latched;
                        return;
                    }
                    require(actualNs <= expectedNs, "unexpected SurfaceTexture timestamp " + actualNs);
                } finally { data.recycle(); reply.recycle(); }
                Thread.sleep(20);
            } while (android.os.SystemClock.elapsedRealtime() < deadline);
            throw new AssertionError("SurfaceTexture did not latch VP9 PTS " + expectedNs + " ns");
        }
    }

    private static void require(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    private TextureCheck() {}
}
