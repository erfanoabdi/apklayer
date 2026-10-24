// AudioTrace -- env-gated diagnostic for the audio subsystem.
//
// PHASE 0 of the "make sound work" effort (docs/future_implementations.md
// #2): before building any real CoreAudio backend we need to learn WHICH
// Android audio API each test app actually drives -- Java AudioTrack.write?
// SoundPool.play? MediaPlayer.start? (the native OpenSL/AAudio dlsym stubs
// already log themselves in bionic_shim.cpp). This helper records per-tag
// hit counts and prints the first few hits + a periodic heartbeat so a
// per-frame writer (libGDX music) is distinguishable from a one-shot
// SoundPool.load.
//
// Default-off: set MACAPK_AUDIO_TRACE=1 to enable. Mirrors the
// MACAPK_NET_TRACE convention -- a permanent, env-gated diagnostic, not
// temporary instrumentation to be deleted.
package com.macapk.loader;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class AudioTrace {
    public static final boolean ON = System.getenv("MACAPK_AUDIO_TRACE") != null;
    /** MACAPK_AUDIO_TRACE=all: log EVERY hit, not just #1-3 and every 500th.
     *  A harness that counts events (one SoundPool.play per game action, e.g.
     *  a flap) needs every one; the sampled default is for busy audio paths. */
    private static final boolean ALL = "all".equals(System.getenv("MACAPK_AUDIO_TRACE"));
    private static final ConcurrentHashMap<String, AtomicLong> COUNTS =
        new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> CALLER_LOGGED =
        new ConcurrentHashMap<>();

    private AudioTrace() {}

    // Record a hit on `tag`. Logs the first 3 occurrences (with a caller
    // hint on the very first so we can see which engine drives the API)
    // and then every 500th to confirm a streaming writer without flooding.
    public static void hit(String tag) {
        if (!ON) return;
        long n = COUNTS.computeIfAbsent(tag, k -> new AtomicLong())
                       .incrementAndGet();
        if (n == 1) {
            System.err.println("[audio-trace] " + tag + " #1  from "
                               + caller());
        } else if (ALL || n <= 3 || n % 500 == 0) {
            System.err.println("[audio-trace] " + tag + " #" + n);
        }
    }

    // Like hit() but appends a detail string (sampleRate/format/bytes) on
    // the first few calls only.
    public static void hit(String tag, String detail) {
        if (!ON) return;
        long n = COUNTS.computeIfAbsent(tag, k -> new AtomicLong())
                       .incrementAndGet();
        if (n == 1) {
            System.err.println("[audio-trace] " + tag + " #1  " + detail
                               + "  from " + caller());
        } else if (ALL || n <= 3 || n % 500 == 0) {
            System.err.println("[audio-trace] " + tag + " #" + n + "  " + detail);
        }
    }

    // First app/engine frame up the stack that is NOT our framework
    // (android.* / com.macapk.*), so the log names the real caller.
    private static String caller() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.startsWith("com.macapk.loader.AudioTrace")
                || c.startsWith("java.lang.Thread")) continue;
            if (c.startsWith("android.media.")) continue;
            return c + "." + e.getMethodName() + "(" + e.getFileName()
                   + ":" + e.getLineNumber() + ")";
        }
        return "(unknown)";
    }
}
