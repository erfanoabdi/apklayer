// The size cap of an app's log file (cli/logs.cpp teeLaunchOutput): a chunk is cut at a line end, what is left out is counted, never lost silently.
#pragma once
#include <cstddef>

namespace apklayer {
// How much of a chunk of n bytes the file takes with `room` bytes left: all of it, or its whole lines that fit (none if the first line does not).
inline size_t capKeep(const char* buf, size_t n, size_t room) {
    if (n <= room) return n;
    while (room && buf[room - 1] != '\n') room--;
    return room;
}
// The lines in a chunk that is left out (a last line without its end counts).
inline size_t capLines(const char* buf, size_t n) {
    size_t lines = 0;
    for (size_t i = 0; i < n; i++) lines += buf[i] == '\n';
    return lines + (n && buf[n - 1] != '\n');
}
}  // namespace apklayer
