package com.google.android.gms.tasks;

// com.google.android.gms.tasks.OnTokenCanceledListener -- invoked when the
// CancellationTokenSource backing a CancellationToken is canceled. Single
// abstract method (functional interface) so apps can pass a lambda.
public interface OnTokenCanceledListener {
    void onCanceled();
}
