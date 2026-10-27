/* apklayer patch: minimal uncompressed-header/index checks adapted from
 * libvpx vp9_decodeframe.c and vp9_decoder.c (BSD-3-Clause, docs/THIRD_PARTY.md).
 * Decoding and bitstream validation remain the host decoder's responsibility. */
#pragma once
#include <cstddef>
#include <cstdint>

namespace apklayer {
inline int vp9VisibleFrames(const uint8_t *data, size_t size) {
    if (!size) return -1;
    uint8_t marker = data[size - 1];
    size_t sizes[8] = {size};
    unsigned count = 1;
    size_t payload = size;
    if ((marker & 0xe0) == 0xc0) {
        count = (marker & 7) + 1;
        unsigned magnitude = ((marker >> 3) & 3) + 1;
        size_t indexSize = 2 + count * magnitude;
        if (size < indexSize || data[size - indexSize] != marker) return -1;
        payload -= indexSize;
        const uint8_t *index = data + payload + 1;
        for (unsigned i = 0; i < count; i++) {
            sizes[i] = 0;
            for (unsigned b = 0; b < magnitude; b++) sizes[i] |= size_t(*index++) << (8 * b);
        }
    }
    int visible = 0;
    for (unsigned i = 0; i < count; i++) {
        if (!sizes[i] || sizes[i] > payload || (data[0] & 0xf0) != 0x80) return -1;
        // Profile 0: frame marker=10, profile=00, show_existing, frame_type, show_frame.
        if ((data[0] & 0x08) || (data[0] & 0x02)) visible++;
        data += sizes[i];
        payload -= sizes[i];
    }
    return payload ? -1 : visible;
}
}
