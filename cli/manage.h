// The managing commands behind apklayer-manager (manager/): apps with their state, instances, the instance's Android property overrides and
// what it sees of the host. Output is tab-separated rows (no field holds a tab or a newline), for scripts as much as for the manager.
#pragma once

#include "apps.h"

#include <map>

namespace apklayer {

// The launches of this instance (run.<pid> records: the JVM first, then the window sink, 0 for a component host, whose launcher became the JVM),
// by package: the JVM's command line names it (-Dapklayer.package=), or once the runtime renamed the process as Android does, it is the process name.
std::map<std::string, std::vector<std::string>> launches(const Layout& layout, const Instance& instance);
// `list --tsv`: package, label, version, app directory, uid, running ("1" when a window or host of it runs in this instance), system app, in the app drawer
void listAppsTsv(const Layout& layout, const Instance& instance);
// `info <package>`: key<TAB>value rows (info.properties' description, code and data size in bytes, directories)
int appInfo(const Layout& layout, const Instance& instance, const std::string& package);
// `clear-data <package>`: the app's files in this instance (stopped first)
int clearData(const Layout& layout, const Instance& instance, const std::string& package);
// `stop <package>`: that app's launches in this instance (the window JVMs and their sinks, component hosts)
int stopApp(const Layout& layout, const Instance& instance, const std::string& package);
// `instances`: id, running, binder devices present, instance root
void listInstances(const Layout& layout);
// `instance create|remove <id>`
int instanceCommand(const Layout& layout, const std::string& action, const std::string& id);
// `prop [list] | set KEY VALUE | unset KEY`: the instance's overrides, applied at its stack's next start
int propCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);
// `settings [list] | set KEY VALUE | unset KEY`: the instance's host-settings overrides (night, accent, contrast, animations, font-scale, locale,
// timezone, time-format), laid over the desktop's by the hub; a running instance applies them at once
int settingsCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);
// `permissions <package>`: permission, protection level, granted ("1"/"0", blank: another app defines it) of what the app requests;
// `permissions grant|revoke <package> <permission>`: a runtime (dangerous) permission in this instance (a revoke stops the app, as on Android)
int permissionsCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);
// `status`: key<TAB>value rows: the stack, running apps, GPU, audio, battery, location
int status(const Layout& layout, const Instance& instance);

}  // namespace apklayer
