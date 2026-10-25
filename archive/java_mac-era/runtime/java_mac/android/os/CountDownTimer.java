// android.os.CountDownTimer -- schedules onTick() callbacks on a fixed interval
// down to onFinish() after a total duration. Widely used for UI timers, OTP
// resend cooldowns, and quiz countdowns. This is a faithful port of AOSP's
// implementation (a Handler posting delayed self-messages, drift-corrected so
// ticks stay aligned to wall-clock interval boundaries).
//
// AOSP ref: frameworks/base/core/java/android/os/CountDownTimer.java
package android.os;

public abstract class CountDownTimer {
    // Total millis to count down.
    private final long mMillisInFuture;
    // Interval (millis) between onTick callbacks.
    private final long mCountdownInterval;

    private long mStopTimeInFuture;

    // Set when the timer is canceled so a queued tick message is dropped even if
    // it was already in the queue when cancel() ran. Mirrors AOSP's mCancelled.
    private boolean mCancelled = false;

    public CountDownTimer(long millisInFuture, long countDownInterval) {
        mMillisInFuture = millisInFuture;
        mCountdownInterval = countDownInterval;
    }

    public synchronized final void cancel() {
        mCancelled = true;
        mHandler.removeMessages(MSG);
    }

    public synchronized final CountDownTimer start() {
        mCancelled = false;
        if (mMillisInFuture <= 0) {
            onFinish();
            return this;
        }
        mStopTimeInFuture = SystemClock.elapsedRealtime() + mMillisInFuture;
        mHandler.sendMessage(mHandler.obtainMessage(MSG));
        return this;
    }

    public abstract void onTick(long millisUntilFinished);

    public abstract void onFinish();

    private static final int MSG = 1;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            synchronized (CountDownTimer.this) {
                if (mCancelled) {
                    return;
                }

                final long millisLeft = mStopTimeInFuture - SystemClock.elapsedRealtime();

                if (millisLeft <= 0) {
                    onFinish();
                } else {
                    long lastTickStart = SystemClock.elapsedRealtime();
                    onTick(millisLeft);

                    // Take into account user's onTick taking time to execute.
                    long lastTickDuration = SystemClock.elapsedRealtime() - lastTickStart;
                    long delay;

                    if (millisLeft < mCountdownInterval) {
                        // Just delay until done.
                        delay = millisLeft - lastTickDuration;
                        // Special case: user's onTick took more than interval to
                        // complete, trigger onFinish without delay.
                        if (delay < 0) delay = 0;
                    } else {
                        delay = mCountdownInterval - lastTickDuration;
                        // Special case: user's onTick took more than interval to
                        // complete, skip to next interval.
                        while (delay < 0) delay += mCountdownInterval;
                    }

                    sendMessageDelayed(obtainMessage(MSG), delay);
                }
            }
        }
    };
}
