#!/usr/bin/env python3
"""export_waifu.py up7_photo.json OUT.bin: waifu2x upconv_7 weights in the layout bridge/shell/sharpen_nn.c reads (float32, little endian), per conv layer
(six 3x3: 3->16->32->64->128->128->256, the first input padded to 4 channels) the mat4s [outLayer][tap = ky*3+kx][inLayer], each a column-major mat4
(column j = the four output channels of input channel j), then 4 floats of bias per outLayer; then the 4x4 transposed conv: mat4s [inLayer][tap = ky*4+kx]
(output rgb in the first three rows), then its bias (4 floats). Source: nagadomi/waifu2x models/upconv_7/photo/scale2.0x_model.json (MIT)."""
import json, sys, numpy as np
L = json.load(open(sys.argv[1])); out = []
def pad4(n): return (n + 3) // 4 * 4
for l in L[:-1]:
    no, ni = l['nOutputPlane'], l['nInputPlane']; w = np.array(l['weight'], np.float32).reshape(no, ni, 3, 3)
    wp = np.zeros((pad4(no), pad4(ni), 3, 3), np.float32); wp[:no, :ni] = w
    m = wp.reshape(pad4(no) // 4, 4, pad4(ni) // 4, 4, 3, 3)       # [o4][k][i4][j][ky][kx]
    m = m.transpose(0, 4, 5, 2, 3, 1)                              # [o4][ky][kx][i4][j][k]: column j holds the k outputs
    out.append(np.ascontiguousarray(m).reshape(-1)); b = np.zeros(pad4(no), np.float32); b[:no] = l['bias']; out.append(b)
l = L[-1]; ni, no = l['nInputPlane'], l['nOutputPlane']; w = np.array(l['weight'], np.float32).reshape(ni, no, 4, 4)
wp = np.zeros((ni, 4, 4, 4), np.float32); wp[:, :no] = w                  # [i][k][ky][kx]
m = wp.reshape(ni // 4, 4, 4, 4, 4).transpose(0, 3, 4, 1, 2)             # [i4][ky][kx][j][k]  (the input channel i = 4*i4+j)
out.append(np.ascontiguousarray(m).reshape(-1)); b = np.zeros(4, np.float32); b[:no] = l['bias']; out.append(b)
a = np.concatenate(out).astype('<f4'); a.tofile(sys.argv[2]); print(len(a), 'floats', a.nbytes, 'bytes')
