package android.app;
import android.content.ComponentName;
/** Metadata describing a searchable activity (parsed from an app's searchable
 *  XML). A value holder read by SearchView / the search dialog. Mac.apk does not
 *  parse searchable.xml into a live registry, so an app that constructs/receives
 *  one gets inert defaults; the accessors must all resolve.
 *  Source: frameworks/base/core/java/android/app/SearchableInfo.java */
public class SearchableInfo implements android.os.Parcelable {
    public SearchableInfo() {}
    public ComponentName getSearchActivity() { return null; }
    public int getHintId() { return 0; }
    public int getInputType() { return android.text.InputType.TYPE_CLASS_TEXT; }
    public int getImeOptions() { return android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH; }
    public boolean shouldRewriteQueryFromData() { return false; }
    public boolean shouldRewriteQueryFromText() { return false; }
    public String getSuggestAuthority() { return null; }
    public String getSuggestPath() { return null; }
    public String getSuggestSelection() { return null; }
    public String getSuggestIntentAction() { return null; }
    public String getSuggestIntentData() { return null; }
    public int getSuggestThreshold() { return 0; }
    public boolean getVoiceSearchEnabled() { return false; }
    public boolean getVoiceSearchLaunchWebSearch() { return false; }
    public boolean getVoiceSearchLaunchRecognizer() { return false; }
    public int getVoiceLanguageModeId() { return 0; }
    public int getVoicePromptTextId() { return 0; }
    public int getVoiceLanguageId() { return 0; }
    public int getVoiceMaxResults() { return 0; }
    public boolean getVoiceSearchLaunchWebSearchEnabled() { return false; }
    public boolean autoUrlDetect() { return false; }

    // ---- AOSP API-36 surface completion ----
    // Same premise as the rest of this class: every field is populated ONLY by
    // parsing an app's searchable.xml, which we do not do, so each accessor
    // returns AOSP's value for the corresponding attribute being ABSENT.

    /** AOSP getSuggestPackage(): "the name of the package where the suggestion
     *  provider lives, or null." */
    public String getSuggestPackage() { return null; }

    /** AOSP getSettingsDescriptionId(): "the resource id of the description
     *  string to use for this source in system search settings, or 0 if none has
     *  been specified" (android:searchSettingsDescription). */
    public int getSettingsDescriptionId() { return 0; }

    /** AOSP shouldIncludeInGlobalSearch(): "the value of the
     *  android:includeInGlobalSearch attribute, or false if the attribute is not
     *  set." */
    public boolean shouldIncludeInGlobalSearch() { return false; }

    /** AOSP queryAfterZeroResults(): android:queryAfterZeroResults, false when
     *  unset -- "whether this searchable activity should be queried for
     *  suggestions if a prefix of the query has returned no results." */
    public boolean queryAfterZeroResults() { return false; }

    // 2026-08-23 conformance sweep: AOSP's SearchableInfo is a ~20-field
    // immutable value class populated ONLY by parsing an app's searchable.xml
    // metadata (SearchableInfo.java's private XML-parsing constructor); its
    // writeToParcel/readFromParcel mirror those fields 1:1
    // (SearchableInfo.java L814-900). This class does not parse
    // searchable.xml (see the class comment above) and has no constructor
    // parameters at all -- every getter returns a fixed default or null
    // regardless of how the instance was built. As with ApplicationStartInfo,
    // there is no per-instance state to lose: an honest round trip writes
    // nothing and CREATOR hands back an equally-stateless new instance.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) { }

    public static final android.os.Parcelable.Creator<SearchableInfo> CREATOR =
            new android.os.Parcelable.Creator<SearchableInfo>() {
        @Override public SearchableInfo createFromParcel(android.os.Parcel in) {
            return new SearchableInfo();
        }
        @Override public SearchableInfo[] newArray(int size) { return new SearchableInfo[size]; }
    };
}
