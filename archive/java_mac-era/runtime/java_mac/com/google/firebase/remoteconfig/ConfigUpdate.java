package com.google.firebase.remoteconfig;
import java.util.Set;
public final class ConfigUpdate {
    private final Set<String> updatedKeys;
    public ConfigUpdate(Set<String> updatedKeys) { this.updatedKeys = updatedKeys; }
    public Set<String> getUpdatedKeys() { return updatedKeys; }
}
