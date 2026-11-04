// The manager's backend against the real CLI, in the instance $APKLAYER_INSTANCE (never the default one): instance create/remove, install,
// info, permissions, property overrides, open (detached launch) and stop, clear data, uninstall. Usage: backend-test <app.apk>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <mutex>

#include "../src/backend.h"

using namespace manager;

#define CHECK(cond, what) do { if (!(cond)) { fprintf(stderr, "FAIL %s\n", what); return 1; } printf("ok   %s\n", what); } while (0)

static bool hasRow(const Rows& rows, const std::string& first, size_t col = 0, const std::string& value = "") {
    for (const auto& r : rows)
        if (!r.empty() && r[0] == first && (value.empty() || (r.size() > col && r[col] == value))) return true;
    return false;
}

int main(int argc, char** argv) {
    const char* instance = getenv("APKLAYER_INSTANCE");
    if (argc != 2 || !instance || std::string(instance) == "default") { fputs("usage: APKLAYER_INSTANCE=<not default> backend-test <app.apk>\n", stderr); return 2; }
    Cli cli(Cli::locate());
    cli.instance = instance;
    printf("cli %s, instance %s\n", cli.exe().c_str(), instance);

    const std::string spare = cli.instance + "-spare";
    CHECK(hasRow(cli.rows({"instance", "create", spare}), "created"), "instance create");
    CHECK(hasRow(cli.rows({"instances"}), spare), "the new instance is listed");
    CHECK(cli.run({"instance", "remove", spare}).status == 0 && !hasRow(cli.rows({"instances"}), spare), "instance remove");
    CHECK(cli.run({"instance", "remove", "apps"}).status != 0, "the apps directory is no instance");

    Rows before = cli.rows({"list", "--tsv"});
    Result install = cli.run({"install", argv[1]});
    CHECK(install.status == 0, "install");
    std::string pkg;
    for (const auto& r : cli.rows({"list", "--tsv"}))
        if (r.size() >= 6 && (!hasRow(before, r[0]) || install.out.find(" " + r[0] + " ") != std::string::npos)) pkg = r[0];
    CHECK(!pkg.empty(), ("the installed package is listed: " + pkg).c_str());
    Rows info = cli.rows({"info", pkg});
    CHECK(hasRow(info, "package", 1, pkg) && hasRow(info, "running", 1, "0"), "info");
    // the app drawer: a user app has a launcher there, a system app (install --system) none unless shown (`launcher show|hide`)
    const char* dataHome = getenv("XDG_DATA_HOME");
    std::string desktop = (dataHome && *dataHome ? std::string(dataHome) : std::string(getenv("HOME")) + "/.local/share") + "/applications/apklayer-" + pkg + ".desktop";
    auto hidden = [&] { Result r = runProgram({"grep", "-qx", "NoDisplay=true", desktop}); return r.status == 0; };
    CHECK(hasRow(cli.rows({"list", "--tsv"}), pkg, 6, "0") && hasRow(cli.rows({"list", "--tsv"}), pkg, 7, "1") && !hidden(), "a user app: not system, in the app drawer");
    CHECK(cli.run({"launcher", "hide", pkg}).status == 0 && hasRow(cli.rows({"list", "--tsv"}), pkg, 7, "0") && hidden(), "launcher hide: NoDisplay=true");
    CHECK(cli.run({"launcher", "show", pkg}).status == 0 && !hidden(), "launcher show");
    CHECK(cli.run({"launcher", "remove", pkg}).status != 0, "launcher takes show or hide");
    CHECK(cli.run({"uninstall", pkg}).status == 0 && cli.run({"install", "--system", argv[1]}).status == 0 && hasRow(cli.rows({"list", "--tsv"}), pkg, 6, "1") &&
          hasRow(cli.rows({"info", pkg}), "system", 1, "1"), "install --system: a system app");
    CHECK(hidden() && hasRow(cli.rows({"list", "--tsv"}), pkg, 7, "0"), "a system app is out of the app drawer by default: NoDisplay=true");
    CHECK(cli.run({"install", argv[1]}).status == 0 && hasRow(cli.rows({"list", "--tsv"}), pkg, 6, "1") && hidden(), "an update keeps the system mark");
    CHECK(cli.run({"launcher", "show", pkg}).status == 0 && !hidden() && hasRow(cli.rows({"list", "--tsv"}), pkg, 7, "1"), "launcher show flips a system app into the drawer");
    Result perms = cli.run({"permissions", pkg});
    bool levels = perms.status == 0;  // every row: permission, level, granted (a normal one granted)
    for (const auto& r : parseRows(perms.out)) levels = levels && r.size() == 3 && (r[1] != "normal" || r[2] == "1");
    CHECK(levels, "permissions lists what the app requests");
    CHECK(cli.run({"permissions", "revoke", pkg, "android.permission.INTERNET"}).status != 0, "only a requested runtime permission is revoked");

    CHECK(cli.run({"prop", "set", "debug.apklayer.manager", "on"}).status == 0 && hasRow(cli.rows({"prop"}), "debug.apklayer.manager", 2, "instance"), "prop set");
    CHECK(cli.run({"prop", "set", "bad key", "1"}).status != 0, "prop set refuses a bad name");
    CHECK(cli.run({"stop"}).status == 0, "stop the instance (install started it): overrides apply at the next start");

    std::mutex m;
    std::condition_variable cv;
    bool started = false, ended = false;
    int exitStatus = 0;
    cli.launch(pkg, [&](const std::string& line) { if (line.find("app jvm spawned") != std::string::npos) { std::lock_guard g(m); started = true; cv.notify_all(); } },
               [&](int st) { std::lock_guard g(m); ended = true; exitStatus = st; cv.notify_all(); });
    {
        std::unique_lock lock(m);
        cv.wait_for(lock, std::chrono::minutes(2), [&] { return started || ended; });  // a guard, not a pace: the event is the launcher's trace line
    }
    CHECK(started && hasRow(cli.rows({"list", "--tsv"}), pkg, 5, "1"), "open: the app runs in the instance");
    // the override reached the stack the launch started
    CHECK(hasRow(cli.rows({"prop"}), "debug.apklayer.manager", 3, "on"), "the override is live");
    CHECK(cli.run({"stop", pkg}).status == 0, "stop the app");
    {
        std::unique_lock lock(m);
        cv.wait_for(lock, std::chrono::seconds(30), [&] { return ended; });
    }
    CHECK(ended && hasRow(cli.rows({"list", "--tsv"}), pkg, 5, "0"), ("the launch ended (status " + std::to_string(exitStatus) + ")").c_str());
    CHECK(cli.run({"prop", "unset", "debug.apklayer.manager"}).status == 0 && !hasRow(cli.rows({"prop"}), "debug.apklayer.manager"), "prop unset");
    CHECK(cli.run({"clear-data", pkg}).status == 0, "clear data");
    CHECK(cli.run({"uninstall", pkg}).status == 0 && !hasRow(cli.rows({"list", "--tsv"}), pkg), "uninstall");
    CHECK(cli.run({"stop"}).status == 0, "stop the instance");
    puts("PASS manager backend");
    return 0;
}
