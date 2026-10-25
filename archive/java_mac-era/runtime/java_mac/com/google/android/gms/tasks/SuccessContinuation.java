package com.google.android.gms.tasks;
public interface SuccessContinuation<T, R> { Task<R> then(T result) throws Exception; }
