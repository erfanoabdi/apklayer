package com.google.android.play.core.assetpacks;
/** Thrown by the Play asset-delivery API. On a de-Googled device asset packs
 *  resolve locally (bundled install-time packs), so this is defined for the
 *  catch-surface but not thrown; getErrorCode() reports the AssetPackErrorCode. */
public class AssetPackException extends RuntimeException {
    private final int errorCode;
    public AssetPackException() { super(); this.errorCode = 0; }
    public AssetPackException(String message) { super(message); this.errorCode = 0; }
    public AssetPackException(String message, int errorCode) { super(message); this.errorCode = errorCode; }
    public int getErrorCode() { return errorCode; }
}
