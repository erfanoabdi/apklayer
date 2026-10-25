package android.media.effect;

/**
 * Notified when an {@link Effect} produces an intermediate result. API 14.
 * Source: frameworks/base/media/java/android/media/effect/EffectUpdateListener.java
 */
public interface EffectUpdateListener {
    void onEffectUpdated(Effect effect, Object info);
}
