package android.os.health;
import java.util.Collections;
import java.util.Map;
/** A snapshot of a uid's resource-usage metrics. Mac.apk collects none, so an
 *  empty snapshot is returned (0 keys). API 24. */
// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class HealthStats {
    public HealthStats() {}
    public String getDataType() { return ""; }
    public int getMeasurementKeyCount() { return 0; }
    public int getMeasurementKeyAt(int index) { throw new IndexOutOfBoundsException(); }
    public boolean hasMeasurement(int key) { return false; }
    public long getMeasurement(int key) { return 0L; }
    public int getMeasurementsKeyCount() { return 0; }
    public int getMeasurementsKeyAt(int index) { throw new IndexOutOfBoundsException(); }
    public boolean hasMeasurements(int key) { return false; }
    public Map<String, Long> getMeasurements(int key) { return Collections.emptyMap(); }
    public int getTimerKeyCount() { return 0; }
    public int getTimerKeyAt(int index) { throw new IndexOutOfBoundsException(); }
    public boolean hasTimer(int key) { return false; }
    public int getTimerCount(int key) { return 0; }
    public long getTimerTime(int key) { return 0L; }
    public TimerStat getTimer(int key) { return new TimerStat(); }
    public int getTimersKeyCount() { return 0; }
    public int getTimersKeyAt(int index) { throw new IndexOutOfBoundsException(); }
    public boolean hasTimers(int key) { return false; }
    public Map<String, TimerStat> getTimers(int key) { return Collections.emptyMap(); }
    public int getStatsKeyCount() { return 0; }
    public int getStatsKeyAt(int index) { throw new IndexOutOfBoundsException(); }
    public boolean hasStats(int key) { return false; }
    public Map<String, HealthStats> getStats(int key) { return Collections.emptyMap(); }
}
