// MediaStats -- env-gated PLAYBACK-ATTEMPT counters for MediaCodec and
// AudioTrack (MACAPK_MEDIA_PROBE=1), so a harness can tell "the app never tried
// to play anything" (REACH: the checker never got to a player) from "it tried
// and the pipeline produced nothing" (OUR layer) -- owner-issue class (h).
//
// WHY (2026-10-01, docs/owner-issue-detectors_2026-09-29.md OPEN). The owner's
// two class-h findings (AntennaPod "will not stream or play", NewPipe "videos
// do not play") were invisible to the checker: fleet_ab's audio column reads
// the HOST mixer ([audio-stats], audio_backend.mm), which only says whether
// sound came out. A decoder that was opened and fed but never produced a frame,
// or a player that decoded but never wrote to an AudioTrack, left no line at
// all. The questions this answers, per object, in the order a player walks
// them (AOSP MediaCodec synchronous/asynchronous processing, android14
// MediaCodec.java "Data Processing" javadoc: configure -> start ->
// queueInputBuffer ... dequeueOutputBuffer -> releaseOutputBuffer(render)):
//   codec#N start mime=M surface=0|1     start() succeeded (an ATTEMPT)
//   codec#N first-output after in=K      the first real output buffer (index
//                                        >= 0; the INFO_* codes are not output)
//   codec#N no-output for 5.0s in=K polls=P   once: fed >= 2 input buffers, and 5 s
//                                        after the first one still no output (a
//                                        decoder holds a few frames of latency,
//                                        never seconds)
//   codec#N end in=A out=B rendered=R polls=P   stop()/reset()/release()
//   track#N first-write bytes=B written=W         first AudioTrack.write with data
//   <secs>s: codec_in=.. codec_out=.. rendered=.. polls=.. track_writes=.. track_bytes=..
//                                        a 5 s window, flushed from the hit path
//                                        (no thread), only when something moved
// A decoder starved of input still reads as ATTEMPT + polls>0 + in=0, and one
// that swallows input for 5 s reads "no-output for 5.0s": debug/owner_issue_scan.py
// keys MEDIA-NOOUTPUT on that line ONLY. An end line with out=0 is NOT evidence: a
// healthy decoder needs ~8 inputs before its first output (NewPipe, measured
// 2026-10-01), so a codec released early (a format switch, a seek, the user
// leaving) legitimately ends in=K out=0. codecEnd runs stall() first, so a codec
// released after the 5 s window with no output still produces the line.
//
// GATE. Default OFF (a diagnostic, like MACAPK_AUDIO_PROBE / MACAPK_AUDIO_TRACE):
// with the variable unset every hook is one static-final boolean test and the
// runtime's behaviour and output are unchanged. debug/fleet_ab.sh sets it for
// our side. Offline gate: debug/mediastats_gate.sh.
package com.macapk.loader;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class MediaStats {
    public static final boolean ON = "1".equals(System.getenv("MACAPK_MEDIA_PROBE"));
    private static final long WINDOW_MS = 5000L;

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final AtomicLong W_IN = new AtomicLong(), W_OUT = new AtomicLong(),
            W_RENDER = new AtomicLong(), W_POLL = new AtomicLong(),
            W_TWRITE = new AtomicLong(), W_TBYTES = new AtomicLong();
    private static final AtomicLong W0 = new AtomicLong();

    private MediaStats() {}

    /** One codec's counters. Created only when ON, so a null check gates every hook. */
    public static final class Codec {
        final int id = NEXT_ID.incrementAndGet();
        final AtomicLong in = new AtomicLong(), out = new AtomicLong(),
                rendered = new AtomicLong(), polls = new AtomicLong();
        volatile boolean started, firstOut, ended, stallSaid;
        volatile long firstInMs;
    }

    /** One AudioTrack's counters. */
    public static final class Track {
        final int id = NEXT_ID.incrementAndGet();
        final AtomicLong writes = new AtomicLong(), bytes = new AtomicLong();
        volatile boolean firstWrite;
    }

    public static Codec newCodec() { return ON ? new Codec() : null; }
    public static Track newTrack() { return ON ? new Track() : null; }

    public static void codecStart(Codec c, String mime, boolean surface) {
        if (c == null) return;
        c.started = true; c.ended = false; c.firstInMs = 0; c.stallSaid = false;
        emit("codec#" + c.id + " start mime=" + mime + " surface=" + (surface ? 1 : 0));
    }

    /** queueInputBuffer with a payload (codec-config and empty EOS buffers are not input). */
    public static void codecIn(Codec c) {
        if (c == null) return;
        c.in.incrementAndGet(); W_IN.incrementAndGet();
        if (c.firstInMs == 0) c.firstInMs = nowMs();
        stall(c); tick();
    }

    private static void stall(Codec c) {
        if (c.stallSaid || c.out.get() > 0 || c.in.get() < 2 || c.firstInMs == 0) return;
        long dt = nowMs() - c.firstInMs;
        if (dt < WINDOW_MS) return;
        c.stallSaid = true;
        emit(String.format(java.util.Locale.ROOT, "codec#%d no-output for %.1fs in=%d polls=%d",
                c.id, dt / 1000.0, c.in.get(), c.polls.get()));
    }

    private static long nowMs() { return System.nanoTime() / 1_000_000L; }

    /** One dequeue attempt (sync dequeueOutputBuffer or the async dispatch poll);
     *  index >= 0 is a real output buffer, a negative value an INFO_* code. */
    public static void codecPoll(Codec c, int index) {
        if (c == null) return;
        c.polls.incrementAndGet(); W_POLL.incrementAndGet();
        if (index >= 0) {
            long n = c.out.incrementAndGet(); W_OUT.incrementAndGet();
            if (n == 1 && !c.firstOut) {
                c.firstOut = true;
                emit("codec#" + c.id + " first-output after in=" + c.in.get());
            }
        } else {
            stall(c);
        }
        tick();
    }

    /** releaseOutputBuffer(render=true) of a frame that reached the Surface. */
    public static void codecRendered(Codec c) {
        if (c == null) return;
        c.rendered.incrementAndGet(); W_RENDER.incrementAndGet(); tick();
    }

    public static void codecEnd(Codec c, String how) {
        if (c == null || !c.started || c.ended) return;
        c.ended = true;
        // A codec released >= 5 s after its first input with no output is the same
        // stall the next dequeue/queue would have reported; a release is the last
        // chance to say so (an app that gives up on a silent decoder stops polling).
        stall(c);
        emit("codec#" + c.id + " end(" + how + ") in=" + c.in.get() + " out=" + c.out.get()
                + " rendered=" + c.rendered.get() + " polls=" + c.polls.get());
    }

    /** AudioTrack.write that handed data to the track; written = the call's result. */
    public static void trackWrite(Track t, int bytes, int written) {
        if (t == null || bytes <= 0) return;
        t.writes.incrementAndGet(); W_TWRITE.incrementAndGet();
        if (written > 0) { t.bytes.addAndGet(written); W_TBYTES.addAndGet(written); }
        if (!t.firstWrite) {
            t.firstWrite = true;
            emit("track#" + t.id + " first-write bytes=" + bytes + " written=" + written);
        }
        tick();
    }

    private static void tick() {
        long now = nowMs();
        long w0 = W0.get();
        if (w0 == 0) { W0.compareAndSet(0, now); return; }
        if (now - w0 < WINDOW_MS || !W0.compareAndSet(w0, now)) return;
        emit(String.format(java.util.Locale.ROOT,
                "%.1fs: codec_in=%d codec_out=%d rendered=%d polls=%d track_writes=%d track_bytes=%d",
                (now - w0) / 1000.0, W_IN.getAndSet(0), W_OUT.getAndSet(0), W_RENDER.getAndSet(0),
                W_POLL.getAndSet(0), W_TWRITE.getAndSet(0), W_TBYTES.getAndSet(0)));
    }

    private static void emit(String s) {
        // System.err, like [audio-trace]: one println is one write, and it needs
        // no native method (an offline gate can load this class bare).
        System.err.println("[media-stats] " + s);
    }
}
