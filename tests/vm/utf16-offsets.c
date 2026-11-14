/* textinput.c's UTF-16 -> byte offsets (what the host sends, what text-input-v3 wants): ASCII, 2/3-byte and astral characters. */
#include <assert.h>
#include <string.h>
#include "../../bridge/shell/utf16.h"

int main(void)
{
    const char *a = "abc", *cjk = "\xe4\xbd\xa0\xe5\xa5\xbd", *astral = "a\xf0\x9f\x98\x80z\xc3\xa9";  /* a, U+1F600, z, e-acute */
    assert(utf16_to_byte(a, 3, 0) == 0 && utf16_to_byte(a, 3, 2) == 2 && utf16_to_byte(a, 3, 3) == 3);
    assert(utf16_to_byte(cjk, 6, 1) == 3 && utf16_to_byte(cjk, 6, 2) == 6);
    assert(utf16_to_byte(astral, strlen(astral), 1) == 1);  /* after a */
    assert(utf16_to_byte(astral, strlen(astral), 3) == 5);  /* after the pair */
    assert(utf16_to_byte(astral, strlen(astral), 4) == 6);  /* after z */
    assert(utf16_to_byte(astral, strlen(astral), 5) == 8);  /* the end */
    assert(utf16_to_byte(astral, strlen(astral), 99) == 8);  /* clamped */
    return 0;
}
