package android.util;
/** A statsd atom event. Mac.apk has no statsd; a built event is inert. */
public final class StatsEvent {
    private StatsEvent() {}
    public int getAtomId() { return 0; }
    public byte[] getBytes() { return new byte[0]; }
    public int getNumBytes() { return 0; }
    public void release() {}
    public static Builder newBuilder() { return new Builder(); }
    public static final class Builder {
        Builder() {}
        public Builder setAtomId(int atomId) { return this; }
        public Builder writeInt(int value) { return this; }
        public Builder writeLong(long value) { return this; }
        public Builder writeFloat(float value) { return this; }
        public Builder writeBoolean(boolean value) { return this; }
        public Builder writeString(String value) { return this; }
        public Builder writeByteArray(byte[] value) { return this; }
        public Builder writeIntArray(int[] values) { return this; }
        public Builder writeLongArray(long[] values) { return this; }
        public Builder writeFloatArray(float[] values) { return this; }
        public Builder writeStringArray(String[] values) { return this; }
        public Builder writeBooleanArray(boolean[] values) { return this; }
        public Builder addBooleanAnnotation(byte annotationId, boolean value) { return this; }
        public Builder addIntAnnotation(byte annotationId, int value) { return this; }
        public Builder usePooledBuffer() { return this; }
        public StatsEvent build() { return new StatsEvent(); }
    }
}
