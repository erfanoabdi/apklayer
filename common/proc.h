// Running programs and reading their rows: shared by the CLI (cli/setup.cpp) and the manager (manager/src), as common/fetch, releases and json are.
// The namespace is `manager` for history: nothing here knows a UI.
#pragma once

#include <string>
#include <sys/types.h>
#include <vector>

namespace manager {

struct Result {
    int status = -1;   // exit status, 128+signal when killed, -1 when it could not start
    std::string out;   // stdout (the rows)
    std::string err;   // stderr (apklayer's messages)
};

using Rows = std::vector<std::vector<std::string>>;
Rows parseRows(const std::string& tsv);

// Any program with arguments to completion (the portal's file chooser, pkexec for the binder setup)
Result runProgram(const std::vector<std::string>& argv);
// exit status of a child (128+signal when killed), -1 on failure; argv as the NULL-terminated pointers execvp wants
int exitStatus(pid_t pid);
std::vector<char*> cArgs(const std::vector<std::string>& argv);
// "apklayer: installed x" -> "Installed x": the last line the CLI printed (stdout on success, its error otherwise)
std::string summary(const Result& r);
// file:///a%20b.apk -> /a b.apk
std::string fileUriPath(const std::string& uri);

}  // namespace manager
