package apklayer.host;

import android.os.Binder;
import android.os.ServiceSpecificException;
import android.security.keystore.IKeyAttestationApplicationIdProvider;
import android.security.keystore.KeyAttestationApplicationId;
import android.security.keystore.KeyAttestationPackageInfo;
import android.security.keystore.Signature;
import java.util.List;

/**
 * "sec_key_att_app_id_provider" (system_server's KeyAttestationApplicationIdProviderService, as it is): keystore2 asks it which packages and signing
 * certificates a uid has before KeyMint attests a key (the attestation's application id); without it every attested key generation waits for it.
 * Callers: keystore2 runs as the stack's Linux uid with no work source, which counts as a system uid; app JVMs carry their app uid and are refused.
 */
final class HubAttestationIds extends IKeyAttestationApplicationIdProvider.Stub {
    static final String SERVICE = "sec_key_att_app_id_provider";
    private final HubPackages packages;
    private HubAttestationIds(HubPackages packages) { this.packages = packages; }

    static boolean publish(HubPackages packages) { return RealBinder.publish(SERVICE, new HubAttestationIds(packages)); }

    @Override public KeyAttestationApplicationId getKeyAttestationApplicationId(int uid) {
        if (android.os.UserHandle.getAppId(Binder.getCallingUid()) >= android.os.Process.FIRST_APPLICATION_UID)
            throw new SecurityException("This service can only be used by Keystore or Credstore");
        List<String> names = packages.installed().stream().filter(name -> packages.uid(name) == uid).toList();
        if (names.isEmpty())
            throw new ServiceSpecificException(ERROR_GET_ATTESTATION_APPLICATION_ID_FAILED, "No package for uid: " + uid);
        KeyAttestationPackageInfo[] infos = new KeyAttestationPackageInfo[names.size()];
        for (int i = 0; i < infos.length; i++) {
            String name = names.get(i);
            KeyAttestationPackageInfo info = infos[i] = new KeyAttestationPackageInfo();
            info.packageName = name;
            var parsed = packages.get(name);
            info.versionCode = parsed == null ? 0 : ((long) parsed.mVersionCodeMajor << 32) | (parsed.mVersionCode & 0xffffffffL);
            android.content.pm.Signature[] signatures = HostCertificate.legacy(HostCertificate.details(name));
            info.signatures = new Signature[signatures.length];
            for (int s = 0; s < signatures.length; s++) { info.signatures[s] = new Signature(); info.signatures[s].data = signatures[s].toByteArray(); }
        }
        KeyAttestationApplicationId id = new KeyAttestationApplicationId();
        id.packageInfos = infos;
        return id;
    }
}
