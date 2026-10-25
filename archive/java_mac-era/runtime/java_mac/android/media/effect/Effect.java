package android.media.effect;

/**
 * One GLES image effect, applied from an input texture to an output texture.
 * API 14 (and unchanged since).
 *
 * The class is abstract in AOSP and every concrete implementation lives in the
 * platform's own private mediaframework filter graph -- an app never subclasses
 * it, it obtains one from {@link EffectFactory}. Mac.apk ships none of those
 * implementations (see EffectFactory), so no instance is ever produced; the type
 * exists so an app's `Effect effect = factory.createEffect(...)` compiles and
 * links, and so the null it gets back is a value it already has to handle.
 *
 * Source: frameworks/base/media/java/android/media/effect/Effect.java
 */
public abstract class Effect {

    public Effect() { }

    public abstract String getName();

    public abstract void apply(int inputTexId, int width, int height, int outputTexId);

    public abstract void setParameter(String parameterKey, Object value);

    /** AOSP's base implementation stores the listener; concrete effects invoke
     *  it. Concrete rather than abstract, exactly as AOSP's is. */
    public void setUpdateListener(EffectUpdateListener listener) { mUpdateListener = listener; }

    protected EffectUpdateListener mUpdateListener;

    public abstract void release();
}
