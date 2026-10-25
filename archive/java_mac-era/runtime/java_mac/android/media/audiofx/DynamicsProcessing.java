package android.media.audiofx;

/**
 * The multi-stage dynamics processor (input gain -> pre-EQ -> multi-band
 * compressor -> post-EQ -> limiter) an equalizer app configures per channel.
 * API 28.
 *
 * Like every other class in this package, the ENGINE is absent: Mac.apk's mixer
 * has no per-session effect insertion path (see AudioEffect's class comment), so
 * no audio is actually processed. What IS fully implemented is the
 * CONFIGURATION MODEL -- and that matters more here than in the simpler effects,
 * because DynamicsProcessing is the one audiofx class an app drives as a data
 * structure: it builds a Config, reads bands back, mutates single bands, and
 * renders its own UI from what it reads. A model that dropped values (returning
 * a fresh zeroed band each time, say) would break that UI even on a device where
 * the effect legitimately does nothing.
 *
 * So every setter really stores and every getter really reads back, per channel
 * and per band; only the audio is unchanged.
 *
 * Source: frameworks/base/media/java/android/media/audiofx/DynamicsProcessing.java
 * (android16-release); constructor defaults and the Config.Builder shape follow
 * that file.
 */
public final class DynamicsProcessing extends AudioEffect {

    public static final int VARIANT_FAVOR_FREQUENCY_RESOLUTION = 0;
    public static final int VARIANT_FAVOR_TIME_RESOLUTION = 1;

    /** AOSP's own defaults for the convenience constructor
     *  (DynamicsProcessing.java, CONFIG_DEFAULT_*). */
    private static final int CHANNEL_COUNT_DEFAULT = 1;
    private static final int VARIANT_DEFAULT = VARIANT_FAVOR_FREQUENCY_RESOLUTION;
    private static final boolean PREEQ_IN_USE_DEFAULT = true;
    private static final int PREEQ_BAND_COUNT_DEFAULT = 6;
    private static final boolean MBC_IN_USE_DEFAULT = true;
    private static final int MBC_BAND_COUNT_DEFAULT = 6;
    private static final boolean POSTEQ_IN_USE_DEFAULT = true;
    private static final int POSTEQ_BAND_COUNT_DEFAULT = 6;
    private static final boolean LIMITER_IN_USE_DEFAULT = true;

    private Config mConfig;

    public DynamicsProcessing(int audioSession) {
        this(0 /* priority */, audioSession, null);
    }

    public DynamicsProcessing(int priority, int audioSession, Config cfg) {
        super(EFFECT_TYPE_DYNAMICS_PROCESSING, EFFECT_TYPE_NULL, priority, audioSession);
        mConfig = (cfg != null) ? cfg : new Config.Builder(
                VARIANT_DEFAULT, CHANNEL_COUNT_DEFAULT,
                PREEQ_IN_USE_DEFAULT, PREEQ_BAND_COUNT_DEFAULT,
                MBC_IN_USE_DEFAULT, MBC_BAND_COUNT_DEFAULT,
                POSTEQ_IN_USE_DEFAULT, POSTEQ_BAND_COUNT_DEFAULT,
                LIMITER_IN_USE_DEFAULT).build();
    }

    public Config getConfig() { return mConfig; }
    public int getChannelCount() { return mConfig.getChannelCount(); }

    public Channel getChannelByChannelIndex(int channelIndex) {
        return mConfig.getChannelByChannelIndex(channelIndex);
    }
    public void setChannelTo(int channelIndex, Channel channel) {
        mConfig.setChannelTo(channelIndex, channel);
    }
    public void setAllChannelsTo(Channel channel) { mConfig.setAllChannelsTo(channel); }

    public float getInputGainByChannelIndex(int channelIndex) {
        return mConfig.getInputGainByChannelIndex(channelIndex);
    }
    /** AOSP's spelling of this one really is "setInputGainbyChannel" (lower-case
     *  b); it is public API and cannot be corrected. */
    public void setInputGainbyChannel(int channelIndex, float inputGain) {
        mConfig.setInputGainByChannelIndex(channelIndex, inputGain);
    }
    public void setInputGainAllChannelsTo(float inputGain) {
        mConfig.setInputGainAllChannelsTo(inputGain);
    }

    public Eq getPreEqByChannelIndex(int channelIndex) {
        return mConfig.getPreEqByChannelIndex(channelIndex);
    }
    public void setPreEqByChannelIndex(int channelIndex, Eq preEq) {
        mConfig.setPreEqByChannelIndex(channelIndex, preEq);
    }
    public void setPreEqAllChannelsTo(Eq preEq) { mConfig.setPreEqAllChannelsTo(preEq); }
    public EqBand getPreEqBandByChannelIndex(int channelIndex, int band) {
        return mConfig.getPreEqBandByChannelIndex(channelIndex, band);
    }
    public void setPreEqBandByChannelIndex(int channelIndex, int band, EqBand preEqBand) {
        mConfig.setPreEqBandByChannelIndex(channelIndex, band, preEqBand);
    }
    public void setPreEqBandAllChannelsTo(int band, EqBand preEqBand) {
        mConfig.setPreEqBandAllChannelsTo(band, preEqBand);
    }

    public Mbc getMbcByChannelIndex(int channelIndex) {
        return mConfig.getMbcByChannelIndex(channelIndex);
    }
    public void setMbcByChannelIndex(int channelIndex, Mbc mbc) {
        mConfig.setMbcByChannelIndex(channelIndex, mbc);
    }
    public void setMbcAllChannelsTo(Mbc mbc) { mConfig.setMbcAllChannelsTo(mbc); }
    public MbcBand getMbcBandByChannelIndex(int channelIndex, int band) {
        return mConfig.getMbcBandByChannelIndex(channelIndex, band);
    }
    public void setMbcBandByChannelIndex(int channelIndex, int band, MbcBand mbcBand) {
        mConfig.setMbcBandByChannelIndex(channelIndex, band, mbcBand);
    }
    public void setMbcBandAllChannelsTo(int band, MbcBand mbcBand) {
        mConfig.setMbcBandAllChannelsTo(band, mbcBand);
    }

    public Eq getPostEqByChannelIndex(int channelIndex) {
        return mConfig.getPostEqByChannelIndex(channelIndex);
    }
    public void setPostEqByChannelIndex(int channelIndex, Eq postEq) {
        mConfig.setPostEqByChannelIndex(channelIndex, postEq);
    }
    public void setPostEqAllChannelsTo(Eq postEq) { mConfig.setPostEqAllChannelsTo(postEq); }
    public EqBand getPostEqBandByChannelIndex(int channelIndex, int band) {
        return mConfig.getPostEqBandByChannelIndex(channelIndex, band);
    }
    public void setPostEqBandByChannelIndex(int channelIndex, int band, EqBand postEqBand) {
        mConfig.setPostEqBandByChannelIndex(channelIndex, band, postEqBand);
    }
    public void setPostEqBandAllChannelsTo(int band, EqBand postEqBand) {
        mConfig.setPostEqBandAllChannelsTo(band, postEqBand);
    }

    public Limiter getLimiterByChannelIndex(int channelIndex) {
        return mConfig.getLimiterByChannelIndex(channelIndex);
    }
    public void setLimiterByChannelIndex(int channelIndex, Limiter limiter) {
        mConfig.setLimiterByChannelIndex(channelIndex, limiter);
    }
    public void setLimiterAllChannelsTo(Limiter limiter) {
        mConfig.setLimiterAllChannelsTo(limiter);
    }

    // ---- the model ---------------------------------------------------------

    /** Base for a processing stage: enabled + in-use. */
    public static class Stage {
        private boolean mInUse;
        private boolean mEnabled;
        public Stage(boolean inUse, boolean enabled) { mInUse = inUse; mEnabled = enabled; }
        public boolean isInUse() { return mInUse; }
        public boolean isEnabled() { return mEnabled; }
        public void setEnabled(boolean enabled) { mEnabled = enabled; }
    }

    /** A stage split into frequency bands. */
    public static class BandStage extends Stage {
        private final int mBandCount;
        public BandStage(boolean inUse, boolean enabled, int bandCount) {
            super(inUse, enabled);
            mBandCount = bandCount;
        }
        public int getBandCount() { return mBandCount; }
    }

    /** Base for one band: enabled + cutoff frequency. */
    public static class BandBase {
        private boolean mEnabled;
        private float mCutoffFrequency;
        public BandBase(boolean enabled, float cutoffFrequency) {
            mEnabled = enabled;
            mCutoffFrequency = cutoffFrequency;
        }
        public boolean isEnabled() { return mEnabled; }
        public void setEnabled(boolean enabled) { mEnabled = enabled; }
        public float getCutoffFrequency() { return mCutoffFrequency; }
        public void setCutoffFrequency(float frequency) { mCutoffFrequency = frequency; }
    }

    /** One equalizer band: a cutoff plus a gain. */
    public static final class EqBand extends BandBase {
        private float mGain;
        public EqBand(boolean enabled, float cutoffFrequency, float gain) {
            super(enabled, cutoffFrequency);
            mGain = gain;
        }
        public EqBand(EqBand cfg) {
            this(cfg.isEnabled(), cfg.getCutoffFrequency(), cfg.getGain());
        }
        public float getGain() { return mGain; }
        public void setGain(float gain) { mGain = gain; }
    }

    /** An equalizer stage: N EqBands. */
    public static final class Eq extends BandStage {
        private final EqBand[] mBands;
        public Eq(boolean inUse, boolean enabled, int bandCount) {
            super(inUse, enabled, bandCount);
            mBands = new EqBand[bandCount];
            for (int i = 0; i < bandCount; i++) {
                // AOSP seeds the bands across the audible range so an app that
                // never sets one still reads a sane cutoff rather than 0 Hz.
                mBands[i] = new EqBand(true, DEFAULT_CUTOFFS[i % DEFAULT_CUTOFFS.length], 0.0f);
            }
        }
        public Eq(Eq cfg) {
            this(cfg.isInUse(), cfg.isEnabled(), cfg.getBandCount());
            for (int i = 0; i < mBands.length; i++) mBands[i] = new EqBand(cfg.getBand(i));
        }
        public EqBand getBand(int band) { return mBands[band]; }
        public void setBand(int band, EqBand bandCfg) {
            if (bandCfg != null) mBands[band] = new EqBand(bandCfg);
        }
    }

    /** One multi-band-compressor band. */
    public static final class MbcBand extends BandBase {
        private float mAttackTime, mReleaseTime, mRatio, mThreshold, mKneeWidth;
        private float mNoiseGateThreshold, mExpanderRatio, mPreGain, mPostGain;

        public MbcBand(boolean enabled, float cutoffFrequency, float attackTime,
                float releaseTime, float ratio, float threshold, float kneeWidth,
                float noiseGateThreshold, float expanderRatio, float preGain, float postGain) {
            super(enabled, cutoffFrequency);
            mAttackTime = attackTime;
            mReleaseTime = releaseTime;
            mRatio = ratio;
            mThreshold = threshold;
            mKneeWidth = kneeWidth;
            mNoiseGateThreshold = noiseGateThreshold;
            mExpanderRatio = expanderRatio;
            mPreGain = preGain;
            mPostGain = postGain;
        }

        public MbcBand(MbcBand cfg) {
            this(cfg.isEnabled(), cfg.getCutoffFrequency(), cfg.getAttackTime(),
                    cfg.getReleaseTime(), cfg.getRatio(), cfg.getThreshold(), cfg.getKneeWidth(),
                    cfg.getNoiseGateThreshold(), cfg.getExpanderRatio(), cfg.getPreGain(),
                    cfg.getPostGain());
        }

        public float getAttackTime() { return mAttackTime; }
        public void setAttackTime(float attackTime) { mAttackTime = attackTime; }
        public float getReleaseTime() { return mReleaseTime; }
        public void setReleaseTime(float releaseTime) { mReleaseTime = releaseTime; }
        public float getRatio() { return mRatio; }
        public void setRatio(float ratio) { mRatio = ratio; }
        public float getThreshold() { return mThreshold; }
        public void setThreshold(float threshold) { mThreshold = threshold; }
        public float getKneeWidth() { return mKneeWidth; }
        public void setKneeWidth(float kneeWidth) { mKneeWidth = kneeWidth; }
        public float getNoiseGateThreshold() { return mNoiseGateThreshold; }
        public void setNoiseGateThreshold(float t) { mNoiseGateThreshold = t; }
        public float getExpanderRatio() { return mExpanderRatio; }
        public void setExpanderRatio(float expanderRatio) { mExpanderRatio = expanderRatio; }
        public float getPreGain() { return mPreGain; }
        public void setPreGain(float preGain) { mPreGain = preGain; }
        public float getPostGain() { return mPostGain; }
        public void setPostGain(float postGain) { mPostGain = postGain; }
    }

    /** A multi-band-compressor stage: N MbcBands. */
    public static final class Mbc extends BandStage {
        private final MbcBand[] mBands;
        public Mbc(boolean inUse, boolean enabled, int bandCount) {
            super(inUse, enabled, bandCount);
            mBands = new MbcBand[bandCount];
            for (int i = 0; i < bandCount; i++) {
                mBands[i] = new MbcBand(true, DEFAULT_CUTOFFS[i % DEFAULT_CUTOFFS.length],
                        3.0f /* attack ms */, 80.0f /* release ms */, 1.0f /* ratio */,
                        -45.0f /* threshold dB */, 0.0f /* knee */, -90.0f /* gate dB */,
                        1.0f /* expander ratio */, 0.0f /* pre gain */, 0.0f /* post gain */);
            }
        }
        public Mbc(Mbc cfg) {
            this(cfg.isInUse(), cfg.isEnabled(), cfg.getBandCount());
            for (int i = 0; i < mBands.length; i++) mBands[i] = new MbcBand(cfg.getBand(i));
        }
        public MbcBand getBand(int band) { return mBands[band]; }
        public void setBand(int band, MbcBand bandCfg) {
            if (bandCfg != null) mBands[band] = new MbcBand(bandCfg);
        }
    }

    /** The output limiter stage. */
    public static final class Limiter extends Stage {
        private int mLinkGroup;
        private float mAttackTime, mReleaseTime, mRatio, mThreshold, mPostGain;

        public Limiter(boolean inUse, boolean enabled, int linkGroup, float attackTime,
                float releaseTime, float ratio, float threshold, float postGain) {
            super(inUse, enabled);
            mLinkGroup = linkGroup;
            mAttackTime = attackTime;
            mReleaseTime = releaseTime;
            mRatio = ratio;
            mThreshold = threshold;
            mPostGain = postGain;
        }

        public Limiter(Limiter cfg) {
            this(cfg.isInUse(), cfg.isEnabled(), cfg.getLinkGroup(), cfg.getAttackTime(),
                    cfg.getReleaseTime(), cfg.getRatio(), cfg.getThreshold(), cfg.getPostGain());
        }

        public int getLinkGroup() { return mLinkGroup; }
        public void setLinkGroup(int linkGroup) { mLinkGroup = linkGroup; }
        public float getAttackTime() { return mAttackTime; }
        public void setAttackTime(float attackTime) { mAttackTime = attackTime; }
        public float getReleaseTime() { return mReleaseTime; }
        public void setReleaseTime(float releaseTime) { mReleaseTime = releaseTime; }
        public float getRatio() { return mRatio; }
        public void setRatio(float ratio) { mRatio = ratio; }
        public float getThreshold() { return mThreshold; }
        public void setThreshold(float threshold) { mThreshold = threshold; }
        public float getPostGain() { return mPostGain; }
        public void setPostGain(float postGain) { mPostGain = postGain; }
    }

    /** One channel's whole chain. */
    public static final class Channel {
        private float mInputGain;
        private Eq mPreEq;
        private Mbc mMbc;
        private Eq mPostEq;
        private Limiter mLimiter;

        public Channel(float inputGain, boolean preEqInUse, int preEqBandCount,
                boolean mbcInUse, int mbcBandCount, boolean postEqInUse, int postEqBandCount,
                boolean limiterInUse) {
            mInputGain = inputGain;
            mPreEq = new Eq(preEqInUse, preEqInUse, preEqBandCount);
            mMbc = new Mbc(mbcInUse, mbcInUse, mbcBandCount);
            mPostEq = new Eq(postEqInUse, postEqInUse, postEqBandCount);
            mLimiter = new Limiter(limiterInUse, limiterInUse, 0 /* linkGroup */,
                    1.0f /* attack ms */, 60.0f /* release ms */, 10.0f /* ratio */,
                    -2.0f /* threshold dB */, 0.0f /* post gain */);
        }

        public Channel(Channel cfg) {
            mInputGain = cfg.getInputGain();
            mPreEq = new Eq(cfg.getPreEq());
            mMbc = new Mbc(cfg.getMbc());
            mPostEq = new Eq(cfg.getPostEq());
            mLimiter = new Limiter(cfg.getLimiter());
        }

        public float getInputGain() { return mInputGain; }
        public void setInputGain(float inputGain) { mInputGain = inputGain; }
        public Eq getPreEq() { return mPreEq; }
        public void setPreEq(Eq preEq) { if (preEq != null) mPreEq = new Eq(preEq); }
        public EqBand getPreEqBand(int band) { return mPreEq.getBand(band); }
        public void setPreEqBand(int band, EqBand preEqBand) { mPreEq.setBand(band, preEqBand); }
        public Mbc getMbc() { return mMbc; }
        public void setMbc(Mbc mbc) { if (mbc != null) mMbc = new Mbc(mbc); }
        public MbcBand getMbcBand(int band) { return mMbc.getBand(band); }
        public void setMbcBand(int band, MbcBand mbcBand) { mMbc.setBand(band, mbcBand); }
        public Eq getPostEq() { return mPostEq; }
        public void setPostEq(Eq postEq) { if (postEq != null) mPostEq = new Eq(postEq); }
        public EqBand getPostEqBand(int band) { return mPostEq.getBand(band); }
        public void setPostEqBand(int band, EqBand postEqBand) { mPostEq.setBand(band, postEqBand); }
        public Limiter getLimiter() { return mLimiter; }
        public void setLimiter(Limiter limiter) {
            if (limiter != null) mLimiter = new Limiter(limiter);
        }
    }

    /** The whole processor's configuration. */
    public static final class Config {
        private final int mVariant;
        private final int mPreEqBandCount;
        private final int mMbcBandCount;
        private final int mPostEqBandCount;
        private final boolean mPreEqInUse;
        private final boolean mMbcInUse;
        private final boolean mPostEqInUse;
        private final boolean mLimiterInUse;
        private final float mPreferredFrameDuration;
        private final Channel[] mChannels;

        private Config(Builder b) {
            mVariant = b.mVariant;
            mPreEqInUse = b.mPreEqInUse;
            mPreEqBandCount = b.mPreEqBandCount;
            mMbcInUse = b.mMbcInUse;
            mMbcBandCount = b.mMbcBandCount;
            mPostEqInUse = b.mPostEqInUse;
            mPostEqBandCount = b.mPostEqBandCount;
            mLimiterInUse = b.mLimiterInUse;
            mPreferredFrameDuration = b.mPreferredFrameDuration;
            mChannels = new Channel[b.mChannels.length];
            for (int i = 0; i < mChannels.length; i++) mChannels[i] = new Channel(b.mChannels[i]);
        }

        int getChannelCount() { return mChannels.length; }

        public int getVariant() { return mVariant; }
        public float getPreferredFrameDuration() { return mPreferredFrameDuration; }
        public boolean isPreEqInUse() { return mPreEqInUse; }
        public int getPreEqBandCount() { return mPreEqBandCount; }
        public boolean isMbcInUse() { return mMbcInUse; }
        public int getMbcBandCount() { return mMbcBandCount; }
        public boolean isPostEqInUse() { return mPostEqInUse; }
        public int getPostEqBandCount() { return mPostEqBandCount; }
        public boolean isLimiterInUse() { return mLimiterInUse; }

        public Channel getChannelByChannelIndex(int channelIndex) {
            return mChannels[channelIndex];
        }
        public void setChannelTo(int channelIndex, Channel channel) {
            if (channel != null) mChannels[channelIndex] = new Channel(channel);
        }
        public void setAllChannelsTo(Channel channel) {
            for (int i = 0; i < mChannels.length; i++) setChannelTo(i, channel);
        }

        public float getInputGainByChannelIndex(int channelIndex) {
            return mChannels[channelIndex].getInputGain();
        }
        public void setInputGainByChannelIndex(int channelIndex, float inputGain) {
            mChannels[channelIndex].setInputGain(inputGain);
        }
        public void setInputGainAllChannelsTo(float inputGain) {
            for (Channel c : mChannels) c.setInputGain(inputGain);
        }

        public Eq getPreEqByChannelIndex(int channelIndex) {
            return mChannels[channelIndex].getPreEq();
        }
        public void setPreEqByChannelIndex(int channelIndex, Eq preEq) {
            mChannels[channelIndex].setPreEq(preEq);
        }
        public void setPreEqAllChannelsTo(Eq preEq) {
            for (Channel c : mChannels) c.setPreEq(preEq);
        }
        public EqBand getPreEqBandByChannelIndex(int channelIndex, int band) {
            return mChannels[channelIndex].getPreEqBand(band);
        }
        public void setPreEqBandByChannelIndex(int channelIndex, int band, EqBand preEqBand) {
            mChannels[channelIndex].setPreEqBand(band, preEqBand);
        }
        public void setPreEqBandAllChannelsTo(int band, EqBand preEqBand) {
            for (Channel c : mChannels) c.setPreEqBand(band, preEqBand);
        }

        public Mbc getMbcByChannelIndex(int channelIndex) {
            return mChannels[channelIndex].getMbc();
        }
        public void setMbcByChannelIndex(int channelIndex, Mbc mbc) {
            mChannels[channelIndex].setMbc(mbc);
        }
        public void setMbcAllChannelsTo(Mbc mbc) {
            for (Channel c : mChannels) c.setMbc(mbc);
        }
        public MbcBand getMbcBandByChannelIndex(int channelIndex, int band) {
            return mChannels[channelIndex].getMbcBand(band);
        }
        public void setMbcBandByChannelIndex(int channelIndex, int band, MbcBand mbcBand) {
            mChannels[channelIndex].setMbcBand(band, mbcBand);
        }
        public void setMbcBandAllChannelsTo(int band, MbcBand mbcBand) {
            for (Channel c : mChannels) c.setMbcBand(band, mbcBand);
        }

        public Eq getPostEqByChannelIndex(int channelIndex) {
            return mChannels[channelIndex].getPostEq();
        }
        public void setPostEqByChannelIndex(int channelIndex, Eq postEq) {
            mChannels[channelIndex].setPostEq(postEq);
        }
        public void setPostEqAllChannelsTo(Eq postEq) {
            for (Channel c : mChannels) c.setPostEq(postEq);
        }
        public EqBand getPostEqBandByChannelIndex(int channelIndex, int band) {
            return mChannels[channelIndex].getPostEqBand(band);
        }
        public void setPostEqBandByChannelIndex(int channelIndex, int band, EqBand postEqBand) {
            mChannels[channelIndex].setPostEqBand(band, postEqBand);
        }
        public void setPostEqBandAllChannelsTo(int band, EqBand postEqBand) {
            for (Channel c : mChannels) c.setPostEqBand(band, postEqBand);
        }

        public Limiter getLimiterByChannelIndex(int channelIndex) {
            return mChannels[channelIndex].getLimiter();
        }
        public void setLimiterByChannelIndex(int channelIndex, Limiter limiter) {
            mChannels[channelIndex].setLimiter(limiter);
        }
        public void setLimiterAllChannelsTo(Limiter limiter) {
            for (Channel c : mChannels) c.setLimiter(limiter);
        }

        /** AOSP's Builder takes the whole topology up front, because the band
         *  counts are fixed for the life of the Config. */
        public static final class Builder {
            private final int mVariant;
            private final boolean mPreEqInUse;
            private final int mPreEqBandCount;
            private final boolean mMbcInUse;
            private final int mMbcBandCount;
            private final boolean mPostEqInUse;
            private final int mPostEqBandCount;
            private final boolean mLimiterInUse;
            private float mPreferredFrameDuration = 10.0f;  // AOSP default, ms
            private final Channel[] mChannels;

            public Builder(int variant, int channelCount, boolean preEqInUse, int preEqBandCount,
                    boolean mbcInUse, int mbcBandCount, boolean postEqInUse, int postEqBandCount,
                    boolean limiterInUse) {
                mVariant = variant;
                mPreEqInUse = preEqInUse;
                mPreEqBandCount = preEqBandCount;
                mMbcInUse = mbcInUse;
                mMbcBandCount = mbcBandCount;
                mPostEqInUse = postEqInUse;
                mPostEqBandCount = postEqBandCount;
                mLimiterInUse = limiterInUse;
                mChannels = new Channel[Math.max(1, channelCount)];
                for (int i = 0; i < mChannels.length; i++) {
                    mChannels[i] = new Channel(0.0f /* input gain dB */,
                            preEqInUse, preEqBandCount, mbcInUse, mbcBandCount,
                            postEqInUse, postEqBandCount, limiterInUse);
                }
            }

            public Builder setPreferredFrameDuration(float frameDuration) {
                mPreferredFrameDuration = frameDuration; return this;
            }
            public Builder setChannelTo(int channelIndex, Channel channel) {
                if (channel != null) mChannels[channelIndex] = new Channel(channel);
                return this;
            }
            public Builder setAllChannelsTo(Channel channel) {
                for (int i = 0; i < mChannels.length; i++) setChannelTo(i, channel);
                return this;
            }
            public Builder setInputGainByChannelIndex(int channelIndex, float inputGain) {
                mChannels[channelIndex].setInputGain(inputGain); return this;
            }
            public Builder setInputGainAllChannelsTo(float inputGain) {
                for (Channel c : mChannels) c.setInputGain(inputGain);
                return this;
            }
            public Builder setPreEqByChannelIndex(int channelIndex, Eq preEq) {
                mChannels[channelIndex].setPreEq(preEq); return this;
            }
            public Builder setPreEqAllChannelsTo(Eq preEq) {
                for (Channel c : mChannels) c.setPreEq(preEq);
                return this;
            }
            public Builder setMbcByChannelIndex(int channelIndex, Mbc mbc) {
                mChannels[channelIndex].setMbc(mbc); return this;
            }
            public Builder setMbcAllChannelsTo(Mbc mbc) {
                for (Channel c : mChannels) c.setMbc(mbc);
                return this;
            }
            public Builder setPostEqByChannelIndex(int channelIndex, Eq postEq) {
                mChannels[channelIndex].setPostEq(postEq); return this;
            }
            public Builder setPostEqAllChannelsTo(Eq postEq) {
                for (Channel c : mChannels) c.setPostEq(postEq);
                return this;
            }
            public Builder setLimiterByChannelIndex(int channelIndex, Limiter limiter) {
                mChannels[channelIndex].setLimiter(limiter); return this;
            }
            public Builder setLimiterAllChannelsTo(Limiter limiter) {
                for (Channel c : mChannels) c.setLimiter(limiter);
                return this;
            }
            public Config build() { return new Config(this); }
        }
    }

    /** Seed cutoffs spanning the audible range, so a freshly built stage reads
     *  back plausible per-band frequencies instead of 0 Hz for every band. */
    private static final float[] DEFAULT_CUTOFFS =
            { 120.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 12000.0f, 16000.0f };
}
