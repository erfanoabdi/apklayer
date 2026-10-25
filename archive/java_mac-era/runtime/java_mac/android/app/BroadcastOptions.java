package android.app;

import android.os.Bundle;

/**
 * Options bundle for sendBroadcast. Apps build one and pass toBundle() as the
 * broadcast options argument; on a single-process runtime with in-process
 * delivery the options (temporary allowlist duration, identity sharing) have no
 * cross-process effect, but the builder must round-trip so the send call works.
 *
 * Source: frameworks/base/core/java/android/app/BroadcastOptions.java
 */
public class BroadcastOptions {

    public static final int TEMPORARY_WHITELIST_TYPE_FOREGROUND_SERVICE_ALLOWED = 0;
    public static final int TEMPORARY_WHITELIST_TYPE_NOT_ALLOWED = -1;

    private final Bundle mBundle = new Bundle();

    // ---- AOSP API-36 surface completion ----
    // AOSP ref: frameworks/base/core/java/android/app/BroadcastOptions.java
    // (android16-release). Delivery-group and deferral policies are hints the
    // system's broadcast queue uses to COLLAPSE or DELAY queued broadcasts
    // across processes. Mac.apk delivers in-process and immediately, so there is
    // no queue to collapse -- which is the same end state DELIVERY_GROUP_POLICY_ALL
    // (deliver everything) and DEFERRAL_POLICY_NONE (defer nothing) describe.
    // The VALUES still round-trip, because an app that sets a policy and reads it
    // back (or builds options, bundles them, and rebuilds via fromBundle) must
    // see what it set.

    /** AOSP DELIVERY_GROUP_POLICY_*: how to handle earlier broadcasts in the
     *  same delivery group. */
    public static final int DELIVERY_GROUP_POLICY_ALL = 0;
    public static final int DELIVERY_GROUP_POLICY_MOST_RECENT = 1;
    public static final int DELIVERY_GROUP_POLICY_MERGED = 2;

    /** AOSP DEFERRAL_POLICY_*: whether the broadcast may be deferred until the
     *  receiving process is active. */
    public static final int DEFERRAL_POLICY_DEFAULT = 0;
    public static final int DEFERRAL_POLICY_NONE = 1;
    public static final int DEFERRAL_POLICY_UNTIL_ACTIVE = 2;

    // AOSP's Bundle keys, so toBundle()/fromBundle() round-trip through the
    // SAME representation an app could inspect.
    private static final String KEY_SHARE_IDENTITY = "android:broadcast.shareIdentity";
    private static final String KEY_DELIVERY_GROUP_POLICY =
            "android:broadcast.deliveryGroupPolicy";
    private static final String KEY_DELIVERY_GROUP_NAMESPACE_FRAGMENT =
            "android:broadcast.deliveryGroupMatchingNamespaceFragment";
    private static final String KEY_DELIVERY_GROUP_KEY_FRAGMENT =
            "android:broadcast.deliveryGroupMatchingKeyFragment";
    private static final String KEY_DEFERRAL_POLICY = "android:broadcast.deferralPolicy";

    private BroadcastOptions() { }

    /** AOSP's private Bundle constructor behind fromBundle(). */
    private BroadcastOptions(Bundle options) {
        if (options != null) mBundle.putAll(options);
    }

    public static BroadcastOptions makeBasic() { return new BroadcastOptions(); }

    /** AOSP fromBundle(Bundle): {@code return new BroadcastOptions(options);} --
     *  rebuilds an options object from a bundle produced by toBundle(). AOSP
     *  requires non-null. */
    public static BroadcastOptions fromBundle(Bundle options) {
        if (options == null) throw new NullPointerException("options cannot be null");
        return new BroadcastOptions(options);
    }

    /** AOSP isShareIdentityEnabled(): {@code (mFlags & FLAG_SHARE_IDENTITY) != 0}
     *  -- reads back what setShareIdentityEnabled stored. */
    public boolean isShareIdentityEnabled() {
        return mBundle.getBoolean(KEY_SHARE_IDENTITY, false);
    }

    /** AOSP setDeliveryGroupPolicy(int): stores the policy. */
    public BroadcastOptions setDeliveryGroupPolicy(int policy) {
        mBundle.putInt(KEY_DELIVERY_GROUP_POLICY, policy);
        return this;
    }
    /** AOSP getDeliveryGroupPolicy(): the stored policy; DELIVERY_GROUP_POLICY_ALL
     *  when never set (AOSP's field initialiser). */
    public int getDeliveryGroupPolicy() {
        return mBundle.getInt(KEY_DELIVERY_GROUP_POLICY, DELIVERY_GROUP_POLICY_ALL);
    }
    /** AOSP clearDeliveryGroupPolicy(): {@code mDeliveryGroupPolicy =
     *  DELIVERY_GROUP_POLICY_ALL;} -- resets to the default, it does not merely
     *  forget. */
    public void clearDeliveryGroupPolicy() {
        mBundle.putInt(KEY_DELIVERY_GROUP_POLICY, DELIVERY_GROUP_POLICY_ALL);
    }

    /** AOSP setDeliveryGroupMatchingKey(String namespace, String key): both
     *  fragments are Objects.requireNonNull'd, then joined with ':' by the
     *  getter. */
    public BroadcastOptions setDeliveryGroupMatchingKey(String namespace, String key) {
        if (namespace == null) throw new NullPointerException("namespace cannot be null");
        if (key == null) throw new NullPointerException("key cannot be null");
        mBundle.putString(KEY_DELIVERY_GROUP_NAMESPACE_FRAGMENT, namespace);
        mBundle.putString(KEY_DELIVERY_GROUP_KEY_FRAGMENT, key);
        return this;
    }
    /** AOSP getDeliveryGroupMatchingKey(): {@code String.join(":", namespace,
     *  key)}, or null when either fragment is unset. */
    public String getDeliveryGroupMatchingKey() {
        String ns = mBundle.getString(KEY_DELIVERY_GROUP_NAMESPACE_FRAGMENT);
        String key = mBundle.getString(KEY_DELIVERY_GROUP_KEY_FRAGMENT);
        if (ns == null || key == null) return null;
        return String.join(":", ns, key);
    }
    /** AOSP clearDeliveryGroupMatchingKey(): nulls both fragments. */
    public void clearDeliveryGroupMatchingKey() {
        mBundle.remove(KEY_DELIVERY_GROUP_NAMESPACE_FRAGMENT);
        mBundle.remove(KEY_DELIVERY_GROUP_KEY_FRAGMENT);
    }

    /** AOSP setDeferralPolicy(int). */
    public BroadcastOptions setDeferralPolicy(int deferralPolicy) {
        mBundle.putInt(KEY_DEFERRAL_POLICY, deferralPolicy);
        return this;
    }
    /** AOSP getDeferralPolicy(): DEFERRAL_POLICY_DEFAULT when never set. */
    public int getDeferralPolicy() {
        return mBundle.getInt(KEY_DEFERRAL_POLICY, DEFERRAL_POLICY_DEFAULT);
    }
    /** AOSP clearDeferralPolicy(): {@code mDeferralPolicy = DEFERRAL_POLICY_DEFAULT;} */
    public void clearDeferralPolicy() {
        mBundle.putInt(KEY_DEFERRAL_POLICY, DEFERRAL_POLICY_DEFAULT);
    }

    public BroadcastOptions setTemporaryAppAllowlist(long durationMs, int type, int reasonCode,
            String reason) {
        mBundle.putLong("android:broadcast.temporaryAppAllowlistDuration", durationMs);
        return this;
    }

    public BroadcastOptions setShareIdentityEnabled(boolean enable) {
        mBundle.putBoolean(KEY_SHARE_IDENTITY, enable);
        return this;
    }

    public BroadcastOptions setBackgroundActivityStartsAllowed(boolean allowed) { return this; }

    public BroadcastOptions setMaxManifestReceiverApiLevel(int apiLevel) { return this; }

    public Bundle toBundle() { return mBundle.isEmpty() ? null : mBundle; }
}
