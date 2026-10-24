// android.telecom.Conferenceable -- the common supertype of Connection and
// Conference, so a call can be told which other calls it may be merged with.
//
// AOSP declares it as an abstract CLASS with a package-private constructor,
// not an interface, specifically so that only android.telecom can subclass it.
// Copied as-is: making it an interface would let app code implement it, which
// is surface AOSP does not offer.
//
// Mirrors telecomm/java/android/telecom/Conferenceable.java. Public API shape
// confirmed against frameworks/base/core/api/current.txt
// ("public abstract class Conferenceable { }" -- no members).
package android.telecom;

public abstract class Conferenceable {
    Conferenceable() {
    }
}
