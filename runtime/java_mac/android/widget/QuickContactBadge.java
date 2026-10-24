// android.widget.QuickContactBadge -- an ImageView that, when tapped, opens
// the QuickContact card for an assigned contact. Was Absent. We keep the
// contact-assignment surface; opening the system contact card has no backend.
// AOSP: frameworks/base/core/java/android/widget/QuickContactBadge.java
//   public class QuickContactBadge extends ImageView implements View.OnClickListener
package android.widget;

import android.content.Context;
import android.net.Uri;
import android.view.View;

public class QuickContactBadge extends ImageView implements View.OnClickListener {
    public static final int MODE_SMALL = 1;
    public static final int MODE_MEDIUM = 2;
    public static final int MODE_LARGE = 3;

    private Uri mContactUri;
    private String mContactEmail;
    private String mContactPhone;

    public QuickContactBadge(Context context) { super(context); }
    public QuickContactBadge(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public QuickContactBadge(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public QuickContactBadge(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void assignContactUri(Uri contactUri) { mContactUri = contactUri; }
    public void assignContactFromEmail(String emailAddress, boolean lazyLookup) {
        mContactEmail = emailAddress;
    }
    public void assignContactFromPhone(String phoneNumber, boolean lazyLookup) {
        mContactPhone = phoneNumber;
    }
    public void setMode(int size) {}
    /** AOSP setExcludeMimes: stores into the protected mExcludeMimes field
     *  below, which subclasses read. It used to discard the array. */
    public void setExcludeMimes(String[] excludeMimes) { mExcludeMimes = excludeMimes; }
    public void setImageToDefault() {}

    public void setOverlay(android.graphics.drawable.Drawable overlay) {}

    @Override public void onClick(View v) { /* no QuickContact backend */ }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.QuickContactBadge had
    // 4 public members missing, one of them a protected FIELD). AOSP:
    // frameworks/base/core/java/android/widget/QuickContactBadge.java
    // (android16-release).
    //
    // Standing caveat: there is no QuickContact backend here (see onClick
    // above), and no Contacts provider to run the email/phone lookup against,
    // so AOSP's `!lazyLookup` branch -- which fires an async ContentResolver
    // query -- has nothing to query. AOSP's OWN `lazyLookup` branch is
    // "remember the address and clear the resolved contact uri", and that is
    // what happens here for both branches.
    // ==================================================================

    /** AOSP :57 -- protected, and read by subclasses that filter which contact
     *  MIME types the QuickContact sheet may show. */
    protected String[] mExcludeMimes = null;

    private String mPrioritizedMimeType;
    private android.os.Bundle mExtras;

    /** AOSP :233 / :273 -- the Bundle-carrying overloads (API 23). The extras
     *  ride along to the QuickContact activity. */
    public void assignContactFromEmail(String emailAddress, boolean lazyLookup,
            android.os.Bundle extras) {
        mExtras = extras;
        assignContactFromEmail(emailAddress, lazyLookup);
    }
    public void assignContactFromPhone(String phoneNumber, boolean lazyLookup,
            android.os.Bundle extras) {
        mExtras = extras;
        assignContactFromPhone(phoneNumber, lazyLookup);
    }

    /** AOSP :143 -- verbatim. */
    public void setPrioritizedMimeType(String prioritizedMimeType) {
        mPrioritizedMimeType = prioritizedMimeType;
    }
}
