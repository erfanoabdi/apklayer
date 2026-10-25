package android.content.pm;

// Minimal SigningInfo (API 28+). GMS's GoogleSignatureVerifier reads
// PackageInfo.signingInfo via JNI GetFieldID during Firebase init; without
// the field (and its declared type) GetFieldID throws NoSuchFieldError
// uncaught on the Unity main thread and stalls Crossy's GameSceneBoot. We
// report no signers -- consistent with Mac.apk returning null PackageInfo
// .signatures (see MacApkPackageManager.getPackageInfo): GMS can't verify a
// Google-platform signature, so signature-gated features degrade gracefully
// rather than mis-authenticate. App-agnostic.
public final class SigningInfo implements android.os.Parcelable {
    /** AOSP SignatureSchemeVersion (frameworks/base .../SigningInfo.java:47-62).
     *  UNKNOWN is @hide upstream but IS the value SigningDetails.UNKNOWN
     *  reports, so getSchemeVersion() can legitimately return 0. */
    public static final int VERSION_JAR              = 1;
    public static final int VERSION_SIGNING_BLOCK_V2 = 2;
    public static final int VERSION_SIGNING_BLOCK_V3 = 3;
    public static final int VERSION_SIGNING_BLOCK_V4 = 4;

    private final Signature[] signers;
    /** AOSP SigningDetails.pastSigningCertificates -- the rotation lineage.
     *  Empty unless a caller supplied one through the 4-arg constructor. */
    private final Signature[] pastSigners;
    /** AOSP SigningDetails.publicKeys. @NonNull on getPublicKeys(), so this is
     *  never null; an empty set means "we do not know the keys", which is what
     *  SigningDetails.UNKNOWN reports. */
    private final java.util.Collection<java.security.PublicKey> publicKeys;
    private final int schemeVersion;

    public SigningInfo() {
        this.signers = new Signature[0];
        this.pastSigners = new Signature[0];
        this.publicKeys = java.util.Collections.emptySet();
        this.schemeVersion = 0;   // AOSP SigningDetails.UNKNOWN
    }

    // v1.0.580: carry the app's REAL signing certificate(s) so
    // getApkContentsSigners() reports the actual signer -- a real installed
    // APK always has >=1 signer. MacApkPackageManager reads the APK's v1
    // signature and passes it here; GoogleSignatureVerifier then correctly
    // computes a non-Google hash (the app isn't Google-platform-signed) and
    // reports "not Google-signed" instead of seeing zero signers.
    //
    // ⚠ This overload cannot know WHICH signing scheme produced the
    // certificates, so getSchemeVersion() answers 0 (AOSP's UNKNOWN) rather
    // than naming a scheme we did not verify. A caller that knows the scheme
    // -- MacApkPackageManager.readApkSignatures does, it walks v3.1/v3/v2 then
    // v1 -- should use the AOSP 4-arg constructor below instead, which is
    // public API and needs no Mac.apk-specific surface.
    public SigningInfo(Signature[] signers) {
        this.signers = signers != null ? signers : new Signature[0];
        this.pastSigners = new Signature[0];
        this.publicKeys = java.util.Collections.emptySet();
        this.schemeVersion = 0;
    }

    /** AOSP SigningInfo(int, Collection&lt;Signature&gt;, Collection&lt;PublicKey&gt;,
     *  Collection&lt;Signature&gt;) (API 35, frameworks/base
     *  core/java/android/content/pm/SigningInfo.java:98-119). Upstream:
     *  a non-positive schemeVersion or null apkContentsSigners degrades the
     *  whole object to SigningDetails.UNKNOWN, and a history that exactly
     *  matches the current signers is dropped (it is not a rotation). Both
     *  rules are reproduced here. */
    public SigningInfo(int schemeVersion,
            java.util.Collection<Signature> apkContentsSigners,
            java.util.Collection<java.security.PublicKey> publicKeys,
            java.util.Collection<Signature> signingCertificateHistory) {
        if (schemeVersion <= 0 || apkContentsSigners == null) {
            this.signers = new Signature[0];
            this.pastSigners = new Signature[0];
            this.publicKeys = java.util.Collections.emptySet();
            this.schemeVersion = 0;
            return;
        }
        this.signers = apkContentsSigners.isEmpty()
                ? new Signature[0]
                : apkContentsSigners.toArray(new Signature[0]);
        Signature[] past = (signingCertificateHistory == null
                    || signingCertificateHistory.isEmpty())
                ? new Signature[0]
                : signingCertificateHistory.toArray(new Signature[0]);
        if (areExactMatch(this.signers, past)) past = new Signature[0];
        this.pastSigners = past;
        this.publicKeys = (publicKeys == null || publicKeys.isEmpty())
                ? java.util.Collections.<java.security.PublicKey>emptySet()
                : new java.util.LinkedHashSet<>(publicKeys);
        this.schemeVersion = schemeVersion;
    }

    /** AOSP SigningInfo(SigningInfo orig) -- upstream copies the SigningDetails,
     *  which is a deep copy of the arrays and a shallow copy of the entries. */
    public SigningInfo(SigningInfo orig) {
        if (orig == null) {
            this.signers = new Signature[0];
            this.pastSigners = new Signature[0];
            this.publicKeys = java.util.Collections.emptySet();
            this.schemeVersion = 0;
            return;
        }
        this.signers = orig.signers.clone();
        this.pastSigners = orig.pastSigners.clone();
        this.publicKeys = orig.publicKeys;
        this.schemeVersion = orig.schemeVersion;
    }

    public Signature[] getApkContentsSigners() { return signers; }

    /** AOSP SigningInfo.getSigningCertificateHistory():179-190 -- note the
     *  NULL return for a multiply-signed package, which is documented ("This
     *  method returns null if the package is signed by multiple signing
     *  certificates") and which callers branch on via hasMultipleSigners().
     *  Returning the signer array there instead, as this class used to, made a
     *  multiply-signed package look like a rotating one. */
    public Signature[] getSigningCertificateHistory() {
        if (hasMultipleSigners()) return null;
        if (!hasPastSigningCertificates()) return signers;
        return pastSigners;
    }

    public boolean hasMultipleSigners() { return signers.length > 1; }
    public boolean hasPastSigningCertificates() { return pastSigners.length > 0; }

    /** AOSP SigningInfo.getSchemeVersion() (API 35). */
    public int getSchemeVersion() { return schemeVersion; }

    /** AOSP SigningInfo.getPublicKeys() (API 35). @NonNull upstream. */
    public java.util.Collection<java.security.PublicKey> getPublicKeys() {
        return publicKeys;
    }

    /** AOSP SigningInfo.signersMatchExactly(SigningInfo) (API 35), which
     *  delegates to SigningDetails.signaturesMatchExactly ->
     *  Signature.areExactMatch: same COUNT and same SET of certificates, order
     *  independent. */
    public boolean signersMatchExactly(SigningInfo other) {
        if (other == null) return false;
        return areExactMatch(signers, other.signers);
    }

    /** AOSP Signature.areExactMatch(Signature[], Signature[]): equal length and
     *  each array contains all of the other's entries. */
    private static boolean areExactMatch(Signature[] a, Signature[] b) {
        if (a == null || b == null) return a == b;
        if (a.length != b.length) return false;
        for (Signature s : a) if (!contains(b, s)) return false;
        for (Signature s : b) if (!contains(a, s)) return false;
        return true;
    }
    private static boolean contains(Signature[] arr, Signature want) {
        for (Signature s : arr) {
            if (s == null ? want == null : s.equals(want)) return true;
        }
        return false;
    }

    // ---- Parcelable --------------------------------------------------------
    // AOSP: `SigningInfo implements Parcelable` (android11-release
    // SigningInfo.java:36). Its writeToParcel (:123-125) delegates to
    // `mSigningDetails.writeToParcel(dest, parcelableFlags)`, and the (Parcel)
    // constructor (:47-49) to PackageParser.SigningDetails.CREATOR.
    //
    // DIVERGENCE: this tree does not model SigningDetails -- it holds the
    // Signature[] directly (see the class comment for why), so there is nothing
    // to delegate to. SigningDetails' own flattening leads with its signature
    // array, and that array is the ONLY state this class exposes
    // (getApkContentsSigners / getSigningCertificateHistory / hasMultipleSigners
    // all read it), so a typed Signature[] here carries everything an observer
    // can see. If SigningDetails ever lands, this becomes a delegation and the
    // wire form changes with it.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        dest.writeTypedArray(signers, parcelableFlags);
        dest.writeInt(schemeVersion);
        dest.writeTypedArray(pastSigners, parcelableFlags);
    }

    /** `signers` is final and the class is documented as never exposing null,
     *  so a null array on the wire becomes the empty array, exactly as both
     *  public constructors do. */
    private SigningInfo(android.os.Parcel source) {
        Signature[] read = source.createTypedArray(Signature.CREATOR);
        this.signers = read != null ? read : new Signature[0];
        // Written by writeToParcel below, in the same order.
        this.schemeVersion = source.readInt();
        Signature[] past = source.createTypedArray(Signature.CREATOR);
        this.pastSigners = past != null ? past : new Signature[0];
        // PublicKey is not Parcelable and AOSP flattens the keys inside
        // SigningDetails as their encoded form; this tree has no SigningDetails
        // to delegate to, and no caller round-trips a SigningInfo through a
        // parcel here, so the key set does not travel. It reads back empty,
        // which is the same answer SigningDetails.UNKNOWN gives.
        this.publicKeys = java.util.Collections.emptySet();
    }

    public static final android.os.Parcelable.Creator<SigningInfo> CREATOR =
        new android.os.Parcelable.Creator<SigningInfo>() {
            @Override public SigningInfo createFromParcel(android.os.Parcel source) {
                return new SigningInfo(source);
            }
            @Override public SigningInfo[] newArray(int size) {
                return new SigningInfo[size];
            }
        };
}
