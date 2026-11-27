#!/usr/bin/env python3
"""export_student.py STUDENT.pt OUT.bin: a distill.Student in the file bridge/shell/sharpen_nn.c reads: u32 magic 'NNS1', u32 n (valid 3x3 convs, the last one 12 channels
= pixel shuffle), n pairs of u32 (in4, out4: channels / 4, the first input padded to 4), then per layer float32 mat4s [outLayer][tap = ky*3+kx][inLayer] (column j = the four
output channels of input channel j) and 4 floats of bias per outLayer."""
import sys, struct, numpy as np, torch
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from distill import Student
d = torch.load(sys.argv[1]); net = Student(d['ch']); net.load_state_dict(d['sd'])
p4 = lambda n: (n + 3) // 4 * 4; head = [0x3153534e, len(net.convs)]; body = []
for c in net.convs:
    w = c.weight.detach().numpy().astype(np.float32); b = c.bias.detach().numpy().astype(np.float32); no, ni = w.shape[:2]
    head += [p4(ni) // 4, p4(no) // 4]
    wp = np.zeros((p4(no), p4(ni), 3, 3), np.float32); wp[:no, :ni] = w
    m = wp.reshape(p4(no) // 4, 4, p4(ni) // 4, 4, 3, 3).transpose(0, 4, 5, 2, 3, 1)
    bp = np.zeros(p4(no), np.float32); bp[:no] = b; body += [np.ascontiguousarray(m).reshape(-1), bp]
with open(sys.argv[2], 'wb') as f:
    f.write(struct.pack('<%dI' % len(head), *head)); f.write(np.concatenate(body).astype('<f4').tobytes())
print('layers', len(net.convs), 'macs/px', net.macs())
