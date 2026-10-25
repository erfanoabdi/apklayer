package com.google.android.gms.wallet;

import android.app.Activity;
import com.google.android.gms.tasks.Task;

// com.google.android.gms.wallet.PaymentsClient -- the Google Pay client. On a
// de-Googled device Google Pay is unavailable, so isReadyToPay always resolves
// false (honest negative) and loadPaymentData never launches a real sheet. NEVER
// fabricates a payment token or a successful purchase. Mirrors the published
// method set. Ref: developers.google.com/android/reference/.../wallet/PaymentsClient
public interface PaymentsClient {

    Task<Boolean> isReadyToPay(IsReadyToPayRequest request);

    Task<Object> loadPaymentData(PaymentDataRequest request);

    // The AutoResolvableTask launch path: with no Pay backend nothing resolves.
    void loadPaymentData(PaymentDataRequest request, Activity activity, int requestCode);
}
