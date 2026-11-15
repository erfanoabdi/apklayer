// The files an installed app opens, as the desktop's file associations (docs/FILE-ASSOCIATIONS.md): from the app's info.properties (open.N, written at
// install from the manifest's VIEW/EDIT intent filters) the MIME types for its .desktop entry's MimeType=, and a shared-mime-info package for the
// extensions the system has no type for.
#pragma once

#include <string>
#include <vector>

namespace apklayer {

struct FileTypes {
    std::vector<std::string> mimes;  // the entry's MimeType= (concrete types; image/* and the like expanded, */* dropped)
    std::string xml;                 // the shared-mime-info package defining the extensions' types ("" when the system knows them all)
};

FileTypes fileTypes(const std::string& info, const std::string& package, const std::string& label);

}  // namespace apklayer
