package com.google.android.gms.fitness;

import com.google.android.gms.fitness.data.DataType;
import java.util.ArrayList;
import java.util.List;

// com.google.android.gms.fitness.FitnessOptions -- declares which data types and
// access levels an app needs; passed to the permission/consent flow and to the
// Fitness clients. Pure config built via its Builder. Mac.apk grants no Fit
// access (no backend), but the type must link for apps that construct it.
// Mirrors the published ACCESS_* constants and Builder.
// Ref: developers.google.com/android/reference/.../fitness/FitnessOptions
public class FitnessOptions {

    public static final int ACCESS_READ = 0;
    public static final int ACCESS_WRITE = 1;

    private final List<DataType> dataTypes;

    private FitnessOptions(List<DataType> dataTypes) { this.dataTypes = dataTypes; }

    public List<DataType> getDataTypes() { return dataTypes; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final List<DataType> dataTypes = new ArrayList<DataType>();
        public Builder addDataType(DataType dataType) { dataTypes.add(dataType); return this; }
        public Builder addDataType(DataType dataType, int access) { dataTypes.add(dataType); return this; }
        public FitnessOptions build() { return new FitnessOptions(dataTypes); }
    }
}
