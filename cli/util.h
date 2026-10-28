// Small POSIX helpers shared by the apklayer tool: files, paths, processes, hashing. No dependencies beyond libc.
#pragma once

#include <map>
#include <optional>
#include <string>
#include <vector>

namespace apklayer {

using Env = std::map<std::string, std::string>;

[[noreturn]] void die(const std::string& message);

std::string join(const std::string& dir, const std::string& name);
std::string homeDir();
std::string getenvOr(const char* name, const std::string& fallback);
bool exists(const std::string& path);
bool isDir(const std::string& path);
bool isExecutable(const std::string& path);
std::string realPath(const std::string& path);
std::string dirName(const std::string& path);
std::string baseName(const std::string& path);
void makeDirs(const std::string& path);
void removeTree(const std::string& path);
std::optional<std::string> readFile(const std::string& path);
bool writeFile(const std::string& path, const std::string& content);
std::vector<std::string> listDir(const std::string& path);
std::vector<std::string> split(const std::string& text, char separator);
std::string trim(const std::string& text);

// key=value files (info.properties, the config file)
std::string property(const std::string& file, const std::string& key);

std::string sha256File(const std::string& path);
std::string sha256Text(const std::string& text);

struct Spawn {
    std::vector<std::string> argv;
    Env env;                       // the complete environment of the child
    std::string cwd;               // empty: inherit
    std::string logFile;           // stdout and stderr go here (appended); empty: inherit
    bool newSession = false;       // setsid(): the child leads its own process group
    bool detach = false;           // double fork: nobody waits for it
};

// Starts a process; returns its pid (0 for a detached one). The caller owns waiting for it.
int spawn(const Spawn& spec);
// Runs to completion; returns the exit status (128+signal when killed).
int runAndWait(const Spawn& spec);
// Runs and returns what it printed on stdout.
std::string runCapture(const Spawn& spec, int* status = nullptr);
// pidfd for a child: readable when it exits (no polling).
int pidfdOpen(int pid);

char** toEnvp(const Env& env, std::vector<std::string>& storage, std::vector<char*>& pointers);
Env currentEnv();

}  // namespace apklayer
