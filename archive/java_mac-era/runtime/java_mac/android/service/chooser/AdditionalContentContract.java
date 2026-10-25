package android.service.chooser;

/**
 * API 35 contract between an app that shares content and the system chooser's
 * "additional content" ContentProvider: the column the provider must expose,
 * the cursor extra it may set, and the ContentProvider.call() method name the
 * chooser uses to report a selection change.
 *
 * Pure naming contract -- there is no behaviour here even in AOSP, which is why
 * the outer type is an empty interface. Mac.apk shows no system chooser, so
 * nothing on our side calls the provider, but an app that IMPLEMENTS the
 * provider references these names and must resolve them.
 *
 * ⚠ These are compile-time String constants, so a compiled app has them
 * INLINED and cannot miss them at runtime. They are still declared because a
 * missing field breaks reflection (and breaks compiling against us), and
 * because getting the spelling right once here is cheaper than three apps
 * getting it wrong.
 *
 * Values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   Columns.URI = "uri"
 *   CursorExtraKeys.POSITION = "position"
 *   MethodNames.ON_SELECTION_CHANGED = "onSelectionChanged"
 *
 * Source: frameworks/base/core/java/android/service/chooser/AdditionalContentContract.java
 */
public interface AdditionalContentContract {

    interface Columns {
        String URI = "uri";
    }

    interface CursorExtraKeys {
        String POSITION = "position";
    }

    interface MethodNames {
        String ON_SELECTION_CHANGED = "onSelectionChanged";
    }
}
