// android.text.ParcelableSpan -- AOSP marker for framework span types that
// can be written to a Parcel. AOSP:
//   frameworks/base/core/java/android/text/ParcelableSpan.java
//   public interface ParcelableSpan extends Parcelable { ... }
//
// Framework-only; apps don't implement it, but they (and AOSP-faithful span
// classes like TextAppearanceSpan / SuggestionSpan) reference it. We provide
// the exact interface shape so those spans can implement it and any
// `instanceof ParcelableSpan` check round-trips.
package android.text;

import android.os.Parcel;
import android.os.Parcelable;

public interface ParcelableSpan extends Parcelable {
    int getSpanTypeId();
    int getSpanTypeIdInternal();
    void writeToParcelInternal(Parcel dest, int flags);
}
