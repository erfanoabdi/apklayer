#!/usr/bin/env python3
"""apklayer-codec: decodes the buffers an app queues into one android.media.MediaCodec (PLAN P5.2).

GStreamer decoder in its own process. Protocol on stdin/stdout (binary):
  in:  "cfg MIME W H TW TH RATE CHANNELS NCSD\\n" once, NCSD times "csd SIZE\\n" + bytes (codec-specific data),
       then per input buffer "in SIZE\\n" + SIZE bytes
  out: per input buffer "out SIZE W H\\n" + SIZE bytes (decoded RGBA frame, or interleaved S16LE PCM; SIZE 0: nothing yet)
Video is scaled to TW x TH (the host's output buffers are sized for that).
"""
import sys
import gi
gi.require_version("Gst", "1.0")
gi.require_version("GstApp", "1.0")
from gi.repository import Gst, GstApp  # noqa: F401  (GstApp provides appsrc/appsink methods)

Gst.init(None)
rd, wr = sys.stdin.buffer, sys.stdout.buffer
_, mime, w, h, tw, th, rate, channels, ncsd = rd.readline().decode().split()


def read_exact(n):
    data = b""
    while len(data) < n:
        chunk = rd.read(n - len(data))
        if not chunk:
            raise SystemExit
        data += chunk
    return data


csd = [read_exact(int(rd.readline().split()[1])) for _ in range(int(ncsd))]
RAW_AUDIO = "audio/x-raw,format=S16LE,layout=interleaved,rate=%s,channels=%s" % (rate, channels)
VIDEO_OUT = "videoconvert ! videoscale ! video/x-raw,format=RGBA,width=%s,height=%s" % (tw, th)
PIPES = {
    "video/x-vnd.on2.vp9": ("video/x-vp9", "vp9dec ! " + VIDEO_OUT),
    "video/x-vnd.on2.vp8": ("video/x-vp8", "vp8dec ! " + VIDEO_OUT),
    "video/avc": ("video/x-h264,stream-format=byte-stream,alignment=au", "h264parse ! avdec_h264 ! " + VIDEO_OUT),
    "video/av01": ("video/x-av1,stream-format=obu-stream,alignment=tu", "av1dec ! " + VIDEO_OUT),
    "audio/mp4a-latm": ("audio/mpeg,mpegversion=4,stream-format=raw,rate=%s,channels=%s%s" % (
        rate, channels, ",codec_data=(buffer)" + csd[0].hex() if csd else ""),
        "avdec_aac ! audioconvert ! audioresample ! " + RAW_AUDIO),
    "audio/opus": ("audio/x-opus,rate=%s,channels=%s,channel-mapping-family=0" % (rate, channels),
                   "opusdec ! audioconvert ! audioresample ! " + RAW_AUDIO),
    "audio/mpeg": ("audio/mpeg,mpegversion=1,layer=3,rate=%s,channels=%s" % (rate, channels),
                   "mpg123audiodec ! audioconvert ! audioresample ! " + RAW_AUDIO),
}
caps, chain = PIPES[mime]
pipe = Gst.parse_launch('appsrc name=src format=time caps="%s" ! %s ! appsink name=sink sync=false max-buffers=8' % (caps, chain))
src, sink = pipe.get_by_name("src"), pipe.get_by_name("sink")
pipe.set_state(Gst.State.PLAYING)
is_video = mime.startswith("video/")
pts = 0
if mime == "video/avc":  # SPS/PPS in byte-stream form go in first
    for blob in csd:
        src.push_buffer(Gst.Buffer.new_wrapped(blob))

while True:
    header = rd.readline()
    if not header:
        break
    size = int(header.split()[1])
    data = read_exact(size) if size else b""
    out, ow, oh = b"", 0, 0
    if size:
        buf = Gst.Buffer.new_wrapped(data)
        buf.pts = pts
        pts += 1000
        src.push_buffer(buf)
        sample = sink.try_pull_sample(300 * Gst.MSECOND)
        if sample:
            ok, info = sample.get_buffer().map(Gst.MapFlags.READ)
            if ok:
                out = bytes(info.data)
                sample.get_buffer().unmap(info)
                if is_video:
                    ow, oh = int(tw), int(th)
    wr.write(b"out %d %d %d\n" % (len(out), ow, oh))
    wr.write(out)
    wr.flush()
pipe.set_state(Gst.State.NULL)
