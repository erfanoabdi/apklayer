package android.renderscript;

import java.util.HashMap;

/**
 * Sampler state: the filtering and wrap modes a kernel uses when it reads a
 * texture Allocation with rsSample.
 *
 * Everything about this class is state. AOSP's version has getters, a Builder,
 * and nine cached preset factories, and no behaviour at all beyond validation,
 * because the sampling itself happens inside a kernel. So this is a complete
 * implementation rather than a stand-in: the only thing that could consume a
 * Sampler is a ScriptC kernel, and those cannot run here (see
 * {@link ScriptC}), but the object an app builds is fully correct.
 *
 * Source: frameworks/rs/java/android/renderscript/Sampler.java
 */
public class Sampler extends BaseObj {

    public enum Value {
        NEAREST(0),
        LINEAR(1),
        LINEAR_MIP_LINEAR(2),
        LINEAR_MIP_NEAREST(5),
        WRAP(3),
        CLAMP(4),
        MIRRORED_REPEAT(6);

        int mID;

        Value(int id) {
            mID = id;
        }
    }

    Value mMin;
    Value mMag;
    Value mWrapS;
    Value mWrapT;
    Value mWrapR;
    float mAniso;

    Sampler(RenderScript rs) {
        super(rs);
    }

    /** @return the minification filter. */
    public Value getMinification() {
        return mMin;
    }

    /** @return the magnification filter. */
    public Value getMagnification() {
        return mMag;
    }

    /** @return the wrap mode for the S (horizontal) coordinate. */
    public Value getWrapS() {
        return mWrapS;
    }

    /** @return the wrap mode for the T (vertical) coordinate. */
    public Value getWrapT() {
        return mWrapT;
    }

    /** @return the anisotropy setting. */
    public float getAnisotropy() {
        return mAniso;
    }

    /**
     * Per-context cache of the nine presets, mirroring AOSP's one field per
     * preset. Two calls to CLAMP_LINEAR(rs) must return the same object.
     */
    private static final HashMap<RenderScript, HashMap<String, Sampler>> sPresets =
            new HashMap<RenderScript, HashMap<String, Sampler>>();

    private static Sampler preset(RenderScript rs, String key, Value min, Value mag,
                                  Value wrap) {
        if (rs == null) {
            return build(null, min, mag, wrap);
        }
        synchronized (sPresets) {
            HashMap<String, Sampler> m = sPresets.get(rs);
            if (m == null) {
                m = new HashMap<String, Sampler>();
                sPresets.put(rs, m);
            }
            Sampler s = m.get(key);
            if (s == null) {
                s = build(rs, min, mag, wrap);
                m.put(key, s);
            }
            return s;
        }
    }

    private static Sampler build(RenderScript rs, Value min, Value mag, Value wrap) {
        Builder b = new Builder(rs);
        b.setMinification(min);
        b.setMagnification(mag);
        b.setWrapS(wrap);
        b.setWrapT(wrap);
        return b.create();
    }

    /** Point sampled, clamped to the edge. */
    public static Sampler CLAMP_NEAREST(RenderScript rs) {
        return preset(rs, "CLAMP_NEAREST", Value.NEAREST, Value.NEAREST, Value.CLAMP);
    }

    /** Bilinear, clamped to the edge. */
    public static Sampler CLAMP_LINEAR(RenderScript rs) {
        return preset(rs, "CLAMP_LINEAR", Value.LINEAR, Value.LINEAR, Value.CLAMP);
    }

    /** Trilinear, clamped to the edge. */
    public static Sampler CLAMP_LINEAR_MIP_LINEAR(RenderScript rs) {
        return preset(rs, "CLAMP_LINEAR_MIP_LINEAR", Value.LINEAR_MIP_LINEAR, Value.LINEAR, Value.CLAMP);
    }

    /** Point sampled, wrapping. */
    public static Sampler WRAP_NEAREST(RenderScript rs) {
        return preset(rs, "WRAP_NEAREST", Value.NEAREST, Value.NEAREST, Value.WRAP);
    }

    /** Bilinear, wrapping. */
    public static Sampler WRAP_LINEAR(RenderScript rs) {
        return preset(rs, "WRAP_LINEAR", Value.LINEAR, Value.LINEAR, Value.WRAP);
    }

    /** Trilinear, wrapping. */
    public static Sampler WRAP_LINEAR_MIP_LINEAR(RenderScript rs) {
        return preset(rs, "WRAP_LINEAR_MIP_LINEAR", Value.LINEAR_MIP_LINEAR, Value.LINEAR, Value.WRAP);
    }

    /** Point sampled, mirrored repeat. */
    public static Sampler MIRRORED_REPEAT_NEAREST(RenderScript rs) {
        return preset(rs, "MIRRORED_REPEAT_NEAREST", Value.NEAREST, Value.NEAREST, Value.MIRRORED_REPEAT);
    }

    /** Bilinear, mirrored repeat. */
    public static Sampler MIRRORED_REPEAT_LINEAR(RenderScript rs) {
        return preset(rs, "MIRRORED_REPEAT_LINEAR", Value.LINEAR, Value.LINEAR, Value.MIRRORED_REPEAT);
    }

    /** Trilinear, mirrored repeat. */
    public static Sampler MIRRORED_REPEAT_LINEAR_MIP_LINEAR(RenderScript rs) {
        return preset(rs, "MIRRORED_REPEAT_LINEAR_MIP_LINEAR", Value.LINEAR_MIP_LINEAR, Value.LINEAR, Value.MIRRORED_REPEAT);
    }

    public static class Builder {

        RenderScript mRS;
        Value mMin = Value.NEAREST;
        Value mMag = Value.NEAREST;
        Value mWrapS = Value.WRAP;
        Value mWrapT = Value.WRAP;
        Value mWrapR = Value.WRAP;
        float mAniso = 1.0f;

        public Builder(RenderScript rs) {
            if (rs != null) {
                rs.validate();
            }
            mRS = rs;
        }

        /**
         * NOTE these setters return void, not the Builder. That breaks the
         * chaining every other Builder in this package supports, and it is the
         * platform's signature, so it cannot be improved.
         *
         * @param v NEAREST, LINEAR, LINEAR_MIP_LINEAR or LINEAR_MIP_NEAREST.
         */
        public void setMinification(Value v) {
            if (v == Value.NEAREST || v == Value.LINEAR
                    || v == Value.LINEAR_MIP_LINEAR || v == Value.LINEAR_MIP_NEAREST) {
                mMin = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        /** @param v NEAREST or LINEAR; there is no mipmap magnification. */
        public void setMagnification(Value v) {
            if (v == Value.NEAREST || v == Value.LINEAR) {
                mMag = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        /** @param v CLAMP, WRAP or MIRRORED_REPEAT. */
        public void setWrapS(Value v) {
            if (v == Value.WRAP || v == Value.CLAMP || v == Value.MIRRORED_REPEAT) {
                mWrapS = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        /** @param v CLAMP, WRAP or MIRRORED_REPEAT. */
        public void setWrapT(Value v) {
            if (v == Value.WRAP || v == Value.CLAMP || v == Value.MIRRORED_REPEAT) {
                mWrapT = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public void setAnisotropy(float v) {
            if (v >= 0.0f) {
                mAniso = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public Sampler create() {
            if (mRS != null) {
                mRS.validate();
            }
            Sampler sampler = new Sampler(mRS);
            sampler.mMin = mMin;
            sampler.mMag = mMag;
            sampler.mWrapS = mWrapS;
            sampler.mWrapT = mWrapT;
            sampler.mWrapR = mWrapR;
            sampler.mAniso = mAniso;
            return sampler;
        }
    }
}
