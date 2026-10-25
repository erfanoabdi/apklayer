// android.app.ForegroundServiceTypeException -- abstract base of the
// foreground-service-TYPE failures (API 34).
//   AOSP: frameworks/base/core/java/android/app/ForegroundServiceTypeException.java
//         (android14-release), mirrored member for member.
// See ServiceStartNotAllowedException.java for why this family exists here even
// though Mac.apk has no foreground-service-type policy to enforce.
package android.app;

public abstract class ForegroundServiceTypeException extends ServiceStartNotAllowedException {
    public ForegroundServiceTypeException(String message) {
        super(message);
    }
}
