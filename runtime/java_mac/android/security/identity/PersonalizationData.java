// android.security.identity.PersonalizationData -- the namespaced data
// elements plus access-control profiles handed to
// WritableIdentityCredential.personalize().
//
// A REAL implementation: it is a pure in-memory container, so it behaves
// identically here and on a device. Only the personalize() call that CONSUMES
// it needs a secure element, and that is gated at IdentityCredentialStore.
//
// LinkedHashMap/LinkedList are AOSP's choice, not an incidental one: the CBOR
// encoding personalize() produces is order-sensitive, so insertion order has
// to be preserved.
//
// Mirrors identity/java/android/security/identity/PersonalizationData.java
// (android11-release, API 30), including the package-private accessors and
// nested NamespaceData/EntryData shape.
package android.security.identity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;

public class PersonalizationData {

    private PersonalizationData() {
    }

    private LinkedList<AccessControlProfile> mProfiles = new LinkedList<>();
    private LinkedHashMap<String, NamespaceData> mNamespaces = new LinkedHashMap<>();

    Collection<AccessControlProfile> getAccessControlProfiles() {
        return Collections.unmodifiableCollection(mProfiles);
    }

    Collection<String> getNamespaces() {
        return Collections.unmodifiableCollection(mNamespaces.keySet());
    }

    NamespaceData getNamespaceData(String namespace) {
        return mNamespaces.get(namespace);
    }

    static class NamespaceData {
        private String mNamespace;
        private LinkedHashMap<String, EntryData> mEntries = new LinkedHashMap<>();

        private NamespaceData(String namespace) {
            this.mNamespace = namespace;
        }

        String getNamespaceName() {
            return mNamespace;
        }

        Collection<String> getEntryNames() {
            return Collections.unmodifiableCollection(mEntries.keySet());
        }

        Collection<AccessControlProfileId> getAccessControlProfileIds(String name) {
            EntryData value = mEntries.get(name);
            if (value != null) {
                return value.mAccessControlProfileIds;
            }
            return null;
        }

        byte[] getEntryValue(String name) {
            EntryData value = mEntries.get(name);
            if (value != null) {
                return value.mValue;
            }
            return null;
        }
    }

    private static class EntryData {
        byte[] mValue;
        Collection<AccessControlProfileId> mAccessControlProfileIds;

        EntryData(byte[] value, Collection<AccessControlProfileId> accessControlProfileIds) {
            this.mValue = value;
            this.mAccessControlProfileIds = accessControlProfileIds;
        }
    }

    public static final class Builder {
        private PersonalizationData mData;

        public Builder() {
            this.mData = new PersonalizationData();
        }

        public Builder putEntry(String namespace, String name,
                Collection<AccessControlProfileId> accessControlProfileIds,
                byte[] value) {
            NamespaceData namespaceData = mData.mNamespaces.get(namespace);
            if (namespaceData == null) {
                namespaceData = new NamespaceData(namespace);
                mData.mNamespaces.put(namespace, namespaceData);
            }
            namespaceData.mEntries.put(name, new EntryData(value, accessControlProfileIds));
            return this;
        }

        public Builder addAccessControlProfile(AccessControlProfile profile) {
            mData.mProfiles.add(profile);
            return this;
        }

        public PersonalizationData build() {
            return mData;
        }
    }
}
