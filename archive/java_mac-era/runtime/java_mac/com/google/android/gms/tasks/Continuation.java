package com.google.android.gms.tasks;
public interface Continuation<T, R> { R then(Task<T> task) throws Exception; }
