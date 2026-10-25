package com.google.android.gms.fitness;

import android.content.Context;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// com.google.android.gms.fitness.Fitness -- entry point for Google Fit. There is
// no Fit store on a de-Googled device, so the History/Sessions clients fail every
// read/write cleanly (API_NOT_CONNECTED) and NEVER fabricate fitness data. The
// account-overload signatures mirror the published API (the GoogleSignInAccount
// arg threads consent state). Ref: developers.google.com/android/reference/.../Fitness
public final class Fitness {
    private Fitness() {}

    public static HistoryClient getHistoryClient(Context context, GoogleSignInAccount account) {
        return HISTORY;
    }

    public static SessionsClient getSessionsClient(Context context, GoogleSignInAccount account) {
        return SESSIONS;
    }

    private static <T> Task<T> notConnected() {
        return Tasks.forException(new ApiException(
                new Status(CommonStatusCodes.API_NOT_CONNECTED, "Google Fit unavailable")));
    }

    private static final HistoryClient HISTORY = new HistoryClient() {
        @Override public Task<Object> readData(Object dataReadRequest) { return notConnected(); }
        @Override public Task<Object> readDailyTotal(DataType dataType) { return notConnected(); }
        @Override public Task<Void> insertData(Object dataSet) { return notConnected(); }
        @Override public Task<Void> deleteData(Object dataDeleteRequest) { return notConnected(); }
    };

    private static final SessionsClient SESSIONS = new SessionsClient() {
        @Override public Task<Void> startSession(Object session) { return notConnected(); }
        @Override public Task<Object> stopSession(String identifier) { return notConnected(); }
        @Override public Task<Object> readSession(Object sessionReadRequest) { return notConnected(); }
        @Override public Task<Void> insertSession(Object sessionInsertRequest) { return notConnected(); }
    };
}
