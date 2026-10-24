package android.view;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AOSP-faithful android.view.ViewDebug. Real Android uses this for HierarchyViewer
 * / systrace introspection. The annotations (@ExportedProperty etc.) are the part
 * that matters at our layer: View subclasses across the framework and apps declare
 * them on fields/methods, so they must exist with RUNTIME retention and the exact
 * member set or class-load fails. The trace/dump/count methods have no debug server
 * to talk to, so they are faithful no-ops. Mirrors
 * frameworks/base/core/java/android/view/ViewDebug.java.
 */
public class ViewDebug {
    public static final boolean TRACE_HIERARCHY = false;
    public static final boolean TRACE_RECYCLER = false;
    public static final boolean DEBUG_DRAG = false;
    public static final boolean DEBUG_POSITIONING = false;

    @Target({ ElementType.FIELD, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ExportedProperty {
        boolean resolveId() default false;
        IntToString[] mapping() default { };
        IntToString[] indexMapping() default { };
        FlagToString[] flagMapping() default { };
        boolean deepExport() default false;
        String prefix() default "";
        String category() default "";
        boolean formatToHexString() default false;
        boolean hasAdjacentMapping() default false;
    }

    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IntToString {
        int from();
        String to();
    }

    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface FlagToString {
        int mask();
        int equals();
        String name();
        boolean outputIf() default true;
    }

    @Target({ ElementType.FIELD, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CapturedViewProperty {
        boolean retrieveReturn() default false;
    }

    public interface HierarchyHandler {
        void dumpViewHierarchyWithProperties(java.io.BufferedWriter out, int level);
        View findHierarchyView(String className, int hashCode);
    }

    public enum HierarchyTraceType {
        INVALIDATE,
        INVALIDATE_CHILD,
        INVALIDATE_CHILD_IN_PARENT,
        REQUEST_LAYOUT,
        ON_LAYOUT,
        ON_MEASURE,
        DRAW,
        BUILD_CACHE
    }

    @Deprecated
    public enum RecyclerTraceType {
        NEW_VIEW,
        BIND_VIEW,
        RECYCLE_FROM_ACTIVE_HEAP,
        RECYCLE_FROM_SCRAP_HEAP,
        MOVE_TO_SCRAP_HEAP,
        MOVE_FROM_ACTIVE_TO_SCRAP_HEAP
    }

    public static long getViewInstanceCount() { return 0; }
    public static long getViewRootImplCount() { return 0; }

    public static void trace(View view, RecyclerTraceType type, int... parameters) {}
    public static void startRecyclerTracing(String prefix, View view) {}
    public static void stopRecyclerTracing() {}
    public static void trace(View view, HierarchyTraceType type) {}
    public static void startHierarchyTracing(String prefix, View view) {}
    public static void stopHierarchyTracing() {}

    public static String resolveId(android.content.Context context, int id) {
        return String.valueOf(id);
    }

    /** AOSP ViewDebug.dumpCapturedView(String, Object) writes the object's
     *  @ExportedProperty fields to the view-server socket for HierarchyViewer.
     *  Mac.apk runs no view server (there is no ViewDebug socket and no
     *  ddmlib client to read it), so AOSP's own early-out for "no capture
     *  target" is what happens -- the call is accepted and produces nothing. */
    public static void dumpCapturedView(String tag, Object view) { }
}
