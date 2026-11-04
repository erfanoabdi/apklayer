// Desktop entries of installed apps (apklayer-<package>.desktop in the data home): system apps stay out of the app drawer unless shown,
// `launcher show|hide` overrides that per app (drawer= in its info.properties), and installs staged before the system mark get it.
#pragma once

#include "layout.h"

namespace apklayer {

// key=value in a properties file: the line replaced, or appended
void setProperty(const std::string& file, const std::string& key, const std::string& value);
bool isSystemApp(const std::string& info);
// Whether the app has a launcher in the drawer: drawer=show|hide, else user apps yes, system apps no
bool inDrawer(const std::string& info);
// (Re)writes the package's entry from its info.properties (a system app without a launcher activity has none) and the .apk handler
void writeDesktop(const Layout& layout, const std::string& package);
// `launcher show|hide <package>`
int launcherCommand(const Layout& layout, const std::string& action, const std::string& package);
// Installs of the packages tools/sysapps.list names that carry no system= yet are marked system apps, their entries rewritten
void markSystemApps(const Layout& layout);

}  // namespace apklayer
