package android.app;
/** A structured crash/ANR report. Mac.apk never files one to a handler, but the
 *  type + public fields resolve.
 *  Source: frameworks/base/core/java/android/app/ApplicationErrorReport.java
 *
 *  2026-08-23 conformance sweep: added Parcelable (AOSP's writeToParcel /
 *  readFromParcel dispatch on `type` and (de)serialize whichever *Info is
 *  active for that type -- L233-282) plus the AnrInfo / BatteryInfo /
 *  RunningServiceInfo siblings CrashInfo already had counterparts for.
 *  Those three are NOT independently Parcelable in AOSP either -- they are
 *  plain classes with a Parcel constructor + writeToParcel(Parcel,int)
 *  called directly by the containing report, mirrored exactly here. */
public class ApplicationErrorReport implements android.os.Parcelable {
    public static final int TYPE_NONE = 0, TYPE_CRASH = 1, TYPE_ANR = 2, TYPE_BATTERY = 3, TYPE_RUNNING_SERVICE = 5;
    public int type;
    public String packageName;
    public String installerPackageName;
    public String processName;
    public long time;
    public boolean systemApp;
    public ApplicationErrorReport() {}

    public CrashInfo crashInfo;
    public AnrInfo anrInfo;
    public BatteryInfo batteryInfo;
    public RunningServiceInfo runningServiceInfo;

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(type);
        dest.writeString(packageName);
        dest.writeString(installerPackageName);
        dest.writeString(processName);
        dest.writeLong(time);
        dest.writeInt(systemApp ? 1 : 0);
        dest.writeInt(crashInfo != null ? 1 : 0);
        switch (type) {
            case TYPE_CRASH:
                if (crashInfo != null) crashInfo.writeToParcel(dest, flags);
                break;
            case TYPE_ANR:
                anrInfo.writeToParcel(dest, flags);
                break;
            case TYPE_BATTERY:
                batteryInfo.writeToParcel(dest, flags);
                break;
            case TYPE_RUNNING_SERVICE:
                runningServiceInfo.writeToParcel(dest, flags);
                break;
        }
    }

    public void readFromParcel(android.os.Parcel in) {
        type = in.readInt();
        packageName = in.readString();
        installerPackageName = in.readString();
        processName = in.readString();
        time = in.readLong();
        systemApp = in.readInt() == 1;
        boolean hasCrashInfo = in.readInt() == 1;
        switch (type) {
            case TYPE_CRASH:
                crashInfo = hasCrashInfo ? new CrashInfo(in) : null;
                anrInfo = null; batteryInfo = null; runningServiceInfo = null;
                break;
            case TYPE_ANR:
                anrInfo = new AnrInfo(in);
                crashInfo = null; batteryInfo = null; runningServiceInfo = null;
                break;
            case TYPE_BATTERY:
                batteryInfo = new BatteryInfo(in);
                anrInfo = null; crashInfo = null; runningServiceInfo = null;
                break;
            case TYPE_RUNNING_SERVICE:
                runningServiceInfo = new RunningServiceInfo(in);
                batteryInfo = null; anrInfo = null; crashInfo = null;
                break;
        }
    }

    public static final android.os.Parcelable.Creator<ApplicationErrorReport> CREATOR =
            new android.os.Parcelable.Creator<ApplicationErrorReport>() {
        @Override public ApplicationErrorReport createFromParcel(android.os.Parcel source) {
            ApplicationErrorReport r = new ApplicationErrorReport();
            r.readFromParcel(source);
            return r;
        }
        @Override public ApplicationErrorReport[] newArray(int size) {
            return new ApplicationErrorReport[size];
        }
    };

    public static class CrashInfo {
        /** AOSP CrashInfo.dump(Printer, String): one "field: value" line per
         *  field, in AOSP's order. Verbatim -- a crash reporter that scrapes the
         *  dump depends on both the labels and the order. */
        public void dump(android.util.Printer pw, String prefix) {
            if (pw == null) return;
            pw.println(prefix + "exceptionHandlerClassName: " + exceptionHandlerClassName);
            pw.println(prefix + "exceptionClassName: " + exceptionClassName);
            pw.println(prefix + "exceptionMessage: " + exceptionMessage);
            pw.println(prefix + "throwFileName: " + throwFileName);
            pw.println(prefix + "throwClassName: " + throwClassName);
            pw.println(prefix + "throwMethodName: " + throwMethodName);
            pw.println(prefix + "throwLineNumber: " + throwLineNumber);
            pw.println(prefix + "stackTrace: " + stackTrace);
        }

        public String exceptionHandlerClassName;
        public String exceptionClassName;
        public String exceptionMessage;
        public String throwFileName;
        public String throwClassName;
        public String throwMethodName;
        public int throwLineNumber;
        public String stackTrace;
        public String crashTag;

        public CrashInfo() {}

        // AOSP CrashInfo(Throwable): walks to the root cause for the class
        // name / throw site, keeps the ORIGINAL throwable's full printed
        // stack trace, and records whatever default UncaughtExceptionHandler
        // is currently installed (ApplicationErrorReport.java L354-396).
        public CrashInfo(Throwable tr) {
            if (tr == null) return;
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            tr.printStackTrace(pw);
            pw.flush();
            stackTrace = sanitizeString(sw.toString());
            exceptionMessage = tr.getMessage();

            Throwable rootTr = tr;
            Throwable cursor = tr;
            while (cursor.getCause() != null) {
                cursor = cursor.getCause();
                if (cursor.getStackTrace() != null && cursor.getStackTrace().length > 0) {
                    rootTr = cursor;
                }
                String msg = cursor.getMessage();
                if (msg != null && msg.length() > 0) exceptionMessage = msg;
            }

            Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
            exceptionHandlerClassName = (handler != null) ? handler.getClass().getName() : "unknown";

            exceptionClassName = rootTr.getClass().getName();
            if (rootTr.getStackTrace().length > 0) {
                StackTraceElement trace = rootTr.getStackTrace()[0];
                throwFileName = trace.getFileName();
                throwClassName = trace.getClassName();
                throwMethodName = trace.getMethodName();
                throwLineNumber = trace.getLineNumber();
            } else {
                throwFileName = "unknown";
                throwClassName = "unknown";
                throwMethodName = "unknown";
                throwLineNumber = 0;
            }
        }

        public CrashInfo(android.os.Parcel in) {
            exceptionHandlerClassName = in.readString();
            exceptionClassName = in.readString();
            exceptionMessage = in.readString();
            throwFileName = in.readString();
            throwClassName = in.readString();
            throwMethodName = in.readString();
            throwLineNumber = in.readInt();
            stackTrace = in.readString();
            crashTag = in.readString();
        }

        public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeString(exceptionHandlerClassName);
            dest.writeString(exceptionClassName);
            dest.writeString(exceptionMessage);
            dest.writeString(throwFileName);
            dest.writeString(throwClassName);
            dest.writeString(throwMethodName);
            dest.writeInt(throwLineNumber);
            dest.writeString(stackTrace);
            dest.writeString(crashTag);
        }

        /** AOSP: truncate from the middle rather than the end, so both the
         *  start and the end of a huge stack trace survive. */
        private String sanitizeString(String s) {
            int prefixLength = 10 * 1024;
            int suffixLength = 10 * 1024;
            int acceptableLength = prefixLength + suffixLength;
            if (s != null && s.length() > acceptableLength) {
                String replacement = "\n[TRUNCATED " + (s.length() - acceptableLength) + " CHARS]\n";
                StringBuilder sb = new StringBuilder(acceptableLength + replacement.length());
                sb.append(s.substring(0, prefixLength));
                sb.append(replacement);
                sb.append(s.substring(s.length() - suffixLength));
                return sb.toString();
            }
            return s;
        }
    }

    public static class AnrInfo {
        /** AOSP AnrInfo.dump(Printer, String), verbatim. */
        public void dump(android.util.Printer pw, String prefix) {
            if (pw == null) return;
            pw.println(prefix + "activity: " + activity);
            pw.println(prefix + "cause: " + cause);
            pw.println(prefix + "info: " + info);
        }

        public String activity;
        public String cause;
        public String info;
        public AnrInfo() {}
        public AnrInfo(android.os.Parcel in) {
            activity = in.readString();
            cause = in.readString();
            info = in.readString();
        }
        public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeString(activity);
            dest.writeString(cause);
            dest.writeString(info);
        }
    }

    public static class BatteryInfo {
        /** AOSP BatteryInfo.dump(Printer, String), verbatim. */
        public void dump(android.util.Printer pw, String prefix) {
            if (pw == null) return;
            pw.println(prefix + "usagePercent: " + usagePercent);
            pw.println(prefix + "durationMicros: " + durationMicros);
            pw.println(prefix + "usageDetails: " + usageDetails);
            pw.println(prefix + "checkinDetails: " + checkinDetails);
        }

        public int usagePercent;
        public long durationMicros;
        public String usageDetails;
        public String checkinDetails;
        public BatteryInfo() {}
        public BatteryInfo(android.os.Parcel in) {
            usagePercent = in.readInt();
            durationMicros = in.readLong();
            usageDetails = in.readString();
            checkinDetails = in.readString();
        }
        public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeInt(usagePercent);
            dest.writeLong(durationMicros);
            dest.writeString(usageDetails);
            dest.writeString(checkinDetails);
        }
    }

    public static class RunningServiceInfo {
        /** AOSP RunningServiceInfo.dump(Printer, String), verbatim. */
        public void dump(android.util.Printer pw, String prefix) {
            if (pw == null) return;
            pw.println(prefix + "durationMillis: " + durationMillis);
            pw.println(prefix + "serviceDetails: " + serviceDetails);
        }

        public long durationMillis;
        public String serviceDetails;
        public RunningServiceInfo() {}
        public RunningServiceInfo(android.os.Parcel in) {
            durationMillis = in.readLong();
            serviceDetails = in.readString();
        }
        public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeLong(durationMillis);
            dest.writeString(serviceDetails);
        }
    }

    /** AOSP ApplicationErrorReport.dump(Printer, String): the six header fields
     *  then the *Info for the active `type`. Verbatim, including the switch --
     *  AOSP dereferences the info unguarded, but a report with type=TYPE_CRASH
     *  and a null crashInfo is a caller bug we should not turn into an NPE from
     *  inside the framework, so each branch null-checks. */
    public void dump(android.util.Printer pw, String prefix) {
        if (pw == null) return;
        pw.println(prefix + "type: " + type);
        pw.println(prefix + "packageName: " + packageName);
        pw.println(prefix + "installerPackageName: " + installerPackageName);
        pw.println(prefix + "processName: " + processName);
        pw.println(prefix + "time: " + time);
        pw.println(prefix + "systemApp: " + systemApp);

        switch (type) {
            case TYPE_CRASH:
                if (crashInfo != null) crashInfo.dump(pw, prefix);
                break;
            case TYPE_ANR:
                if (anrInfo != null) anrInfo.dump(pw, prefix);
                break;
            case TYPE_BATTERY:
                if (batteryInfo != null) batteryInfo.dump(pw, prefix);
                break;
            case TYPE_RUNNING_SERVICE:
                if (runningServiceInfo != null) runningServiceInfo.dump(pw, prefix);
                break;
        }
    }

    /** AOSP getErrorReportReceiver(Context, String, int): finds the
     *  ACTION_APP_ERROR receiver an error report should be sent to. AOSP's FIRST
     *  act is
     *  {@code if (Settings.Global.getInt(cr, Settings.Global.SEND_ACTION_APP_ERROR, 0) == 0)
     *  return null;} -- error reporting is OFF by default, and that is the state
     *  here: Mac.apk has no error-report receiver installed and no crash-upload
     *  path. null is what a caller checks for before offering a "report" button,
     *  so answering it correctly suppresses a button that would do nothing. */
    public static android.content.ComponentName getErrorReportReceiver(
            android.content.Context context, String packageName, int appFlags) {
        return null;
    }
}
