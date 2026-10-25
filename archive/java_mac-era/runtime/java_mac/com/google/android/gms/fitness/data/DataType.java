package com.google.android.gms.fitness.data;

// com.google.android.gms.fitness.data.DataType -- identifies a kind of fitness
// data (steps, heart rate, etc.). Apps reference the public TYPE_* constants when
// building FitnessOptions / read requests. Mac.apk has no Fitness backend so no
// data is ever read, but the constants and type must link. Each constant is a
// distinct DataType instance carrying its canonical name string.
// Ref: developers.google.com/android/reference/.../fitness/data/DataType
public class DataType {

    private final String name;

    public DataType(String name) { this.name = name; }

    public String getName() { return name; }

    @Override public String toString() { return "DataType{" + name + "}"; }

    public static final DataType TYPE_STEP_COUNT_DELTA =
            new DataType("com.google.step_count.delta");
    public static final DataType TYPE_STEP_COUNT_CUMULATIVE =
            new DataType("com.google.step_count.cumulative");
    public static final DataType TYPE_CALORIES_EXPENDED =
            new DataType("com.google.calories.expended");
    public static final DataType TYPE_DISTANCE_DELTA =
            new DataType("com.google.distance.delta");
    public static final DataType TYPE_HEART_RATE_BPM =
            new DataType("com.google.heart_rate.bpm");
    public static final DataType TYPE_ACTIVITY_SEGMENT =
            new DataType("com.google.activity.segment");
    public static final DataType AGGREGATE_STEP_COUNT_DELTA =
            new DataType("com.google.step_count.delta");
}
