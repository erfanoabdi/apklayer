// The composer's HW vsync: a steady timeline at the output's period, phase-locked to the shell's real frames (header only).
// SurfaceFlinger's VSyncPredictor needs evenly spaced vsync timestamps on one grid. The shell has real frames only while one of its windows is
// mapped (frame-callback times, whole milliseconds, one per request); this thread ticks at the output's period on the grid, and each real sample
// nudges the grid's phase. With no window mapped no sample comes and the grid just keeps running.
#pragma once

#include <time.h>

#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <mutex>
#include <thread>

namespace apklayer::composer {

inline int64_t monotonicNs() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

class VsyncTimeline {
  public:
    using Tick = std::function<void(int64_t timestampNs, int64_t periodNs)>;  // a vsync for SurfaceFlinger
    using Want = std::function<void()>;                                       // ask the shell for a real sample

    VsyncTimeline(int64_t periodNs, Tick tick, Want want) : mTick(std::move(tick)), mWant(std::move(want)), mPeriod(periodNs) {
        mThread = std::thread([this] { run(); });
    }
    ~VsyncTimeline() {
        {
            std::lock_guard<std::mutex> l(mLock);
            mQuit = true;
        }
        mCond.notify_all();
        mThread.join();
    }

    void setEnabled(bool enabled) {
        {
            std::lock_guard<std::mutex> l(mLock);
            mEnabled = enabled;
            if (enabled) mLastSample = 0;  // a fresh sample soon
        }
        mCond.notify_all();
    }
    void setPeriod(int64_t periodNs) {
        if (periodNs <= 0) return;
        std::lock_guard<std::mutex> l(mLock);
        mPeriod = periodNs;
    }
    int64_t period() {
        std::lock_guard<std::mutex> l(mLock);
        return mPeriod;
    }
    // A real frame of the compositor (CLOCK_MONOTONIC ns, millisecond precision): moves the grid's phase a fraction of the way to it.
    void addSample(int64_t timestampNs) {
        std::lock_guard<std::mutex> l(mLock);
        const int64_t now = monotonicNs();
        mLastSample = now;
        mAsked = 0;
        if (mAnchor == 0) {
            mAnchor = timestampNs;
            return;
        }
        int64_t err = (timestampNs - mAnchor) % mPeriod;
        if (err > mPeriod / 2) err -= mPeriod;
        if (err < -mPeriod / 2) err += mPeriod;
        mAnchor += err / 8;
    }
    // The latest vsync on the grid (getDisplayKnownVsyncSample).
    void lastVsync(int64_t* timestampNs, int64_t* periodNs) {
        std::lock_guard<std::mutex> l(mLock);
        const int64_t now = monotonicNs();
        if (mAnchor == 0) mAnchor = now;
        *timestampNs = mAnchor + (now - mAnchor) / mPeriod * mPeriod - (now < mAnchor ? mPeriod : 0);
        *periodNs = mPeriod;
    }

  private:
    // the first grid time after `after`
    int64_t nextAfter(int64_t after) const {
        if (after < mAnchor) return mAnchor;
        return mAnchor + ((after - mAnchor) / mPeriod + 1) * mPeriod;
    }

    void run() {
        std::unique_lock<std::mutex> l(mLock);
        int64_t last = 0;
        while (!mQuit) {
            if (!mEnabled) {
                last = 0;
                mCond.wait(l);
                continue;
            }
            const int64_t now = monotonicNs();
            if (mAnchor == 0) mAnchor = now;
            const int64_t next = nextAfter(std::max(last, now - mPeriod / 2));  // never a tick older than half a period
            if (now < next) {
                mCond.wait_until(l, std::chrono::steady_clock::time_point(std::chrono::nanoseconds(next)));
                continue;
            }
            last = next;
            const int64_t period = mPeriod;
            const bool ask = mAsked == 0 || now - mAsked > 500000000LL;
            const bool want = ask && now - mLastSample > 100000000LL;  // a real sample about every 100 ms
            if (want) mAsked = now;
            l.unlock();
            mTick(next, period);
            if (want) mWant();
            l.lock();
        }
    }

    Tick mTick;
    Want mWant;
    std::mutex mLock;
    std::condition_variable mCond;
    int64_t mPeriod;
    int64_t mAnchor = 0;      // a time on the grid (CLOCK_MONOTONIC ns), 0 = none yet
    int64_t mLastSample = 0;  // when the last real sample came
    int64_t mAsked = 0;       // when a sample was last asked for (0 = none outstanding)
    bool mEnabled = false, mQuit = false;
    std::thread mThread;
};

}  // namespace apklayer::composer
