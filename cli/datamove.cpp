#include "datamove.h"

#include <cstdio>
#include <ftw.h>
#include <climits>
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

std::string gOldBase, gNewBase;  // nftw's callback has no user data

// absolute links inside the copy that point under the old real location now point under the new one
int relinkOne(const char* path, const struct stat*, int type, struct FTW*) {
    if (type != FTW_SL) return 0;
    char buf[PATH_MAX];
    ssize_t n = readlink(path, buf, sizeof buf - 1);
    if (n <= 0) return 0;
    std::string t(buf, static_cast<size_t>(n));
    if (t.compare(0, gOldBase.size(), gOldBase) != 0 || (t.size() > gOldBase.size() && t[gOldBase.size()] != '/')) return 0;
    std::string fresh = gNewBase + t.substr(gOldBase.size()), tmp = std::string(path) + ".relink";
    if (symlink(fresh.c_str(), tmp.c_str()) == 0 && rename(tmp.c_str(), path) != 0) unlink(tmp.c_str());
    return 0;
}

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
    // already moved once (src is a link): the data is copied from where the link points, the link then points at dir (drive to drive)
    const bool linked = lstat(src.c_str(), &st) == 0 && S_ISLNK(st.st_mode);
    const std::string from = linked ? realPath(src) : src;
    if (dir.empty() || dir[0] != '/') return "not an absolute directory: " + dir;
    if (linked && realPath(dir) == from) return src + " is already a link to " + from;
    if (exists(dir) && !listDir(dir).empty()) return dir + " is not empty";
    for (const std::string& id : listDir(join(layout.stateHome, "apklayer"))) {
        auto pid = readFile(join(layout.stateHome, "apklayer/" + id + "/stack.pid"));
        if (pid && alive(atoi(pid->c_str()))) return "instance " + id + " is running: apklayer --instance " + id + " stop, then again";
    }
    makeDirs(dirName(dir));
    rmdir(dir.c_str());  // an empty one: replaced by the copy
    if (!exists(from)) makeDirs(from);
    // copied beside its place, renamed into it: an interrupted copy leaves the data where it was
    const std::string part = dir + ".part", old = src + ".moving";
    const std::string oldData = linked ? from : "";
    removeTree(part);
    manager::Result cp = manager::runProgram({"cp", "-a", "--", from, part});
    if (cp.status != 0) { removeTree(part); return "cannot copy " + from + " to " + dir + ": " + trim(cp.err).substr(0, 200); }
    if (rename(part.c_str(), dir.c_str()) != 0) { removeTree(part); return "cannot create " + dir; }
    if (rename(src.c_str(), old.c_str()) != 0) return "cannot replace " + src + " (the copy is in " + dir + ")";
    if (symlink(dir.c_str(), src.c_str()) != 0) { rename(old.c_str(), src.c_str()); return "cannot link " + src + " to " + dir; }
    if (linked) {
        gOldBase = from;
        gNewBase = dir;
        nftw(dir.c_str(), relinkOne, 16, FTW_PHYS);
    }
    removeTree(old);  // a link: just the link
    if (linked) removeTree(oldData);
    return "";
}

}  // namespace apklayer
