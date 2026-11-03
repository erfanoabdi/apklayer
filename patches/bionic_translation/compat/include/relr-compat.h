/* glibc before 2.36 (Ubuntu 22.04) has no RELR types: bionic_translation's linker (and Android's libraries, which are linked with --pack-dyn-relocs=android+relr) uses them. */
#include <elf.h>
#ifndef DT_RELR
typedef Elf32_Word Elf32_Relr;
typedef Elf64_Xword Elf64_Relr;
#define DT_RELRSZ 35
#define DT_RELR 36
#define DT_RELRENT 37
#endif
