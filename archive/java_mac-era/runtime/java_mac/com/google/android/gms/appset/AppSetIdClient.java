package com.google.android.gms.appset;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
public interface AppSetIdClient {
    default Task<AppSetIdInfo> getAppSetIdInfo() {
        return Tasks.forResult(new AppSetIdInfo(
            java.util.UUID.randomUUID().toString(), AppSetIdInfo.SCOPE_APP));
    }
}
