#!/usr/bin/env python3
"""apklayer-media: audio/video playback engine for one android.media.MediaPlayer (PLAN P5.2).

GStreamer playbin in its own process. Commands on stdin, one per line:
  load URI | play | pause | stop | seek MS | volume F | loop 0|1
Events on stdout: "position MS" (about every 500 ms while playing), "eos".
"""
import os, sys
import gi
gi.require_version("Gst", "1.0")
from gi.repository import GLib, Gst

Gst.init(None)
player = Gst.ElementFactory.make("playbin", "player")
player.set_property("video-sink", Gst.ElementFactory.make("fakesink", None))  # video: later (frames to the Surface)
loop_playback = False
loop = GLib.MainLoop()


def say(*parts):
    print(*parts, flush=True)


def on_message(bus, msg):
    if msg.type == Gst.MessageType.EOS:
        if loop_playback:
            player.seek_simple(Gst.Format.TIME, Gst.SeekFlags.FLUSH, 0)
        else:
            say("eos")
    elif msg.type == Gst.MessageType.STATE_CHANGED and msg.src == player:
        new = msg.parse_state_changed()[1]
        print("[media] state", new.value_nick, file=sys.stderr, flush=True)
    elif msg.type == Gst.MessageType.ERROR:
        err, dbg = msg.parse_error()
        print("media error:", err.message, file=sys.stderr, flush=True)


bus = player.get_bus()
bus.add_signal_watch()
bus.connect("message", on_message)


def command(line):
    global loop_playback
    c, _, arg = line.partition(" ")
    if c == "load":
        player.set_state(Gst.State.NULL)
        player.set_property("uri", arg)
        player.set_state(Gst.State.PAUSED)
    elif c == "play":
        player.set_state(Gst.State.PLAYING)
    elif c == "pause":
        player.set_state(Gst.State.PAUSED)
    elif c == "stop":
        player.set_state(Gst.State.READY)
    elif c == "seek":
        player.seek_simple(Gst.Format.TIME, Gst.SeekFlags.FLUSH | Gst.SeekFlags.KEY_UNIT, int(arg) * Gst.MSECOND)
    elif c == "volume":
        player.set_property("volume", float(arg))
    elif c == "loop":
        loop_playback = arg == "1"


pending = b""


def on_stdin(fd, cond):
    global pending
    chunk = os.read(fd, 65536)
    if not chunk:
        player.set_state(Gst.State.NULL)
        loop.quit()
        return False
    pending += chunk
    *lines, pending = pending.split(b"\n")
    for line in lines:
        command(line.decode())
    return True


def tick():
    ok, pos = player.query_position(Gst.Format.TIME)
    if ok and player.get_state(0)[1] == Gst.State.PLAYING:
        say("position", pos // Gst.MSECOND)
    return True


GLib.io_add_watch(sys.stdin.fileno(), GLib.IO_IN | GLib.IO_HUP, on_stdin)
GLib.timeout_add(500, tick)
loop.run()
