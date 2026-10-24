// android.media.midi.MidiSender -- the abstract "something you can connect a
// MidiReceiver to". MidiOutputPort extends it in the platform, so an app that
// holds a port as a MidiSender (the usual way to wire a device graph) needs the
// base in the chain.
//
// Source (android-30 android.jar):
//   public abstract class android.media.midi.MidiSender {
//     public void connect(MidiReceiver);
//     public void disconnect(MidiReceiver);
//     public abstract void onConnect(MidiReceiver);
//     public abstract void onDisconnect(MidiReceiver);
//   }
// AOSP's connect/disconnect are thin wrappers that null-check and delegate to
// onConnect/onDisconnect; we mirror that exactly. Mac.apk drives no MIDI, so the
// concrete port's callbacks are no-ops.
package android.media.midi;

public abstract class MidiSender {

    public void connect(MidiReceiver receiver) {
        if (receiver == null) throw new NullPointerException("receiver is null");
        onConnect(receiver);
    }

    public void disconnect(MidiReceiver receiver) {
        if (receiver == null) throw new NullPointerException("receiver is null");
        onDisconnect(receiver);
    }

    public abstract void onConnect(MidiReceiver receiver);

    public abstract void onDisconnect(MidiReceiver receiver);
}
