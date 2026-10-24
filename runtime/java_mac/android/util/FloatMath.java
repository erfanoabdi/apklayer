package android.util;
/** Deprecated float-precision math helpers (removed from public API at level 23,
 *  but old APKs still link them). Delegate to java.lang.Math. */
public class FloatMath {
    private FloatMath() {}
    public static float sqrt(float value) { return (float) Math.sqrt(value); }
    public static float cos(float angle)  { return (float) Math.cos(angle); }
    public static float sin(float angle)  { return (float) Math.sin(angle); }
    public static float floor(float value){ return (float) Math.floor(value); }
    public static float ceil(float value) { return (float) Math.ceil(value); }
    public static float hypot(float x, float y) { return (float) Math.hypot(x, y); }
    public static float exp(float value)  { return (float) Math.exp(value); }
    public static float pow(float x, float y) { return (float) Math.pow(x, y); }
}
