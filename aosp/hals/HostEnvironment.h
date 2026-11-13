#pragma once
#include <cstdlib>

/* Backend controls belong to the host runtime; app environment changes must not reconfigure HALs. */
extern "C" const char* libc_bio_host_getenv(const char*) __attribute__((weak));
namespace apklayer {
inline const char* hostGetenv(const char* name) {
    return libc_bio_host_getenv ? libc_bio_host_getenv(name) : std::getenv(name);
}
}
