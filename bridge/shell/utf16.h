#include <stddef.h>

/* A UTF-16 unit offset into UTF-8 text (as the host counts: a 4-byte character is two units) as a byte offset. */
static inline size_t utf16_to_byte(const char *t, size_t len, int units)
{
    size_t i = 0;
    for (int n = 0; n < units && i < len; ) {
        n += ((unsigned char)t[i] & 0xf8) == 0xf0 ? 2 : 1;
        for (i++; i < len && ((unsigned char)t[i] & 0xc0) == 0x80; i++);
    }
    return i;
}

/* The UTF-16 units of the UTF-8 bytes t[from, to) (a 4-byte character is two units; a cut inside a character counts it once). */
static inline int utf8_units(const char *t, size_t from, size_t to)
{
    int n = 0;
    for (size_t i = from; i < to; i++) {
        unsigned char c = (unsigned char)t[i];
        if ((c & 0xc0) != 0x80) n += (c & 0xf8) == 0xf0 ? 2 : 1;
    }
    return n;
}
