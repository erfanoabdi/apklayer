package android.hardware;

/**
 * Consumer infrared blaster (the "use your phone as a TV remote" API).
 *
 * No Mac has IR emitter hardware, so AOSP's own contract for a device without
 * one is the honest answer here: hasIrEmitter() false, an empty carrier
 * frequency list, and transmit() doing nothing. Apps are required to check
 * hasIrEmitter() before transmitting (AOSP documents transmit as undefined
 * otherwise), so this is a legitimate absent-hardware report rather than a
 * silent no-op.
 *
 * Source: frameworks/base/core/java/android/hardware/ConsumerIrManager.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/hardware/ConsumerIrManager.java
 */
public final class ConsumerIrManager {

    public ConsumerIrManager() { }

    public boolean hasIrEmitter() { return false; }

    /** AOSP returns null when there is no emitter. */
    public CarrierFrequencyRange[] getCarrierFrequencies() { return null; }

    /** No emitter: nothing to drive. */
    public void transmit(int carrierFrequency, int[] pattern) { }

    public final class CarrierFrequencyRange {
        private final int mMinFrequency;
        private final int mMaxFrequency;

        public CarrierFrequencyRange(int min, int max) {
            mMinFrequency = min;
            mMaxFrequency = max;
        }

        public int getMinFrequency() { return mMinFrequency; }
        public int getMaxFrequency() { return mMaxFrequency; }
    }
}
