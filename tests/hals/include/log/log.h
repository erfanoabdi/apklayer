// host stand-in for Android's log/log.h (tests of header-only HAL code)
#pragma once
#include <cstdio>
#define ALOGI(...) (fprintf(stderr, "I " __VA_ARGS__), fputc('\n', stderr))
#define ALOGW(...) (fprintf(stderr, "W " __VA_ARGS__), fputc('\n', stderr))
