#include "datamove.h"

#include <cstdio>
#include <pwd.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <unistd.h>

#include "proc.h"
#include "stack.h"

namespace apklayer {

namespace {
const char* kUserdata = "/userdata/apklayer";
const long long kSmall = 16000000000LL;

dev_t device(const std::string& path) { struct stat st{}; return stat(path.c_str(), &st) == 0 ? st.st_dev : 0; }
}  // namespace

std::string bigDataDir(const Layout& layout) {
    if (!isDir(kUserdata) || access(kUserdata, W_OK) != 0 || device(kUserdata) == device(layout.dataHome)) return "";
    passwd* pw = getpwuid(getuid());
    return join(join(kUserdata, pw ? pw->pw_name : std::to_string(getuid())), "share");
}

bool smallDataHome(const Layout& layout) {
    struct statvfs st{};
    return statvfs(layout.dataHome.c_str(), &st) == 0 && static_cast<long long>(st.f_blocks) * static_cast<long long>(st.f_frsize) < kSmall && !bigDataDir(layout).empty();
}

std::string moveData(const Layout& layout, const std::string& dir) {
    const std::string src = join(layout.dataHome, "apklayer");
    struct stat st{};
    if (lstat(src.c_str(), &st) == 0 && S_ISLNK(st.st_mode)) return src + " is already a link to " + realPath(src);
    if (dir.empty() || dir[0] != '/') return "not an absolute directory: " + dir;
    if (exists(dir) && !listDir(dir).empty()) return dir + " is not empty";
    for (const std::string& id : listDir(join(layout.stateHome, "apklayer"))) {
        auto pid = readFile(join(layout.stateHome, "apklayer/" + id + "/stack.pid"));
        if (pid && alive(atoi(pid->c_str()))) return "instance " + id + " is running: apklayer --instance " + id + " stop, then again";
    }
    makeDirs(dirName(dir));
    rmdir(dir.c_str());  // an empty one: replaced by the copy
    if (!exists(src)) makeDirs(src);
    // copied beside its place, renamed into it: an interrupted copy leaves the data where it was
    const std::string part = dir + ".part", old = src + ".moving";
    removeTree(part);
    manager::Result cp = manager::runProgram({"cp", "-a", "--", src, part});
    if (cp.status != 0) { removeTree(part); return "cannot copy " + src + " to " + dir + ": " + trim(cp.err).substr(0, 200); }
    if (rename(part.c_str(), dir.c_str()) != 0) { removeTree(part); return "cannot create " + dir; }
    if (rename(src.c_str(), old.c_str()) != 0) return "cannot replace " + src + " (the copy is in " + dir + ")";
    if (symlink(dir.c_str(), src.c_str()) != 0) { rename(old.c_str(), src.c_str()); return "cannot link " + src + " to " + dir; }
    removeTree(old);
    return "";
}

}  // namespace apklayer
