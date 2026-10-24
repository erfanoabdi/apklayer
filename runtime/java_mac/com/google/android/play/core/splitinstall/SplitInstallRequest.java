package com.google.android.play.core.splitinstall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// com.google.android.play.core.splitinstall.SplitInstallRequest -- the request
// bag passed to SplitInstallManager.startInstall(). We honour the published
// builder shape so apps compile and configure it normally; since no real install
// is ever performed the contents are only used to populate the failed Task's
// reporting. Mirrors the published Play Core SplitInstallRequest.
public final class SplitInstallRequest {
    private final List<String> moduleNames;
    private final List<String> languages;

    private SplitInstallRequest(List<String> moduleNames, List<String> languages) {
        this.moduleNames = Collections.unmodifiableList(moduleNames);
        this.languages = Collections.unmodifiableList(languages);
    }

    public List<String> getModuleNames() { return moduleNames; }
    public List<String> getLanguages() { return languages; }

    public static Builder newBuilder() { return new Builder(); }

    @Override
    public String toString() {
        return "SplitInstallRequest{modules=" + moduleNames + ", languages=" + languages + "}";
    }

    public static final class Builder {
        private final List<String> moduleNames = new ArrayList<String>();
        private final List<String> languages = new ArrayList<String>();

        Builder() {}

        public Builder addModule(String moduleName) {
            moduleNames.add(moduleName);
            return this;
        }

        public Builder addLanguage(Locale locale) {
            if (locale != null) languages.add(locale.getLanguage());
            return this;
        }

        public SplitInstallRequest build() {
            return new SplitInstallRequest(moduleNames, languages);
        }
    }
}
