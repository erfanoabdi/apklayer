package android.media.effect;

/**
 * Binds an {@link EffectFactory} to the GLES context that is current on the
 * calling thread. API 14.
 *
 * Creating the context succeeds -- it is just a holder, and refusing it would
 * make an app fail before it ever reached the capability check that matters --
 * but the factory it hands back supports no effects; see EffectFactory.
 *
 * Source: frameworks/base/media/java/android/media/effect/EffectContext.java
 */
public class EffectContext {

    private final EffectFactory mFactory = new EffectFactory();

    EffectContext() { }

    /** AOSP: "@throws RuntimeException if no GL context is bound". That check
     *  needs an EGL query this class has no handle for, and getting it wrong in
     *  the throwing direction would break an app on a thread that DOES have a
     *  context -- so the context is created and the honest "no" is delivered by
     *  EffectFactory.isEffectSupported() instead. */
    public static EffectContext createWithCurrentGlContext() { return new EffectContext(); }

    public EffectFactory getFactory() { return mFactory; }

    public void release() { }
}
