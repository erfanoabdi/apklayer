#!/usr/bin/env python3
"""Writes cases.inc for neondiff.c: one C(name, mem, "pre", "asm") per arm64 instruction form (NEON, scalar FP, integer, memory).
Every candidate is assembled with the NDK clang first; forms it rejects are dropped. Usage: gen.py > cases.inc   (NDK from ANDROID_HOME)"""
import glob, itertools, os, re, subprocess, sys, tempfile

cases = []  # (group, asm, mem, pre)
def add(group, asm, mem=0, pre=""):
    cases.append((group, asm, mem, pre))

ARR = ["8b", "16b", "4h", "8h", "2s", "4s", "2d"]
def va(a): return {"b": "b", "h": "h", "s": "s", "d": "d"}[a[-1]]

# ---- integer vector 3-same
for op in "add sub mul mla mls cmeq cmgt cmge cmhi cmhs cmtst sshl ushl sqshl uqshl srshl urshl sqrshl uqrshl smax smin umax umin sabd uabd saba uaba shadd uhadd srhadd urhadd shsub uhsub sqadd uqadd sqsub uqsub sqdmulh sqrdmulh addp smaxp sminp umaxp uminp pmul sdot udot".split():
    for a in ARR:
        if op in ("sdot", "udot"):
            if a in ("2s", "4s"): add("int3", f"{op} v0.{a}, v1.{'8b' if a=='2s' else '16b'}, v2.{'8b' if a=='2s' else '16b'}")
            continue
        add("int3", f"{op} v0.{a}, v1.{a}, v2.{a}")
for op in "and bic orr orn eor bsl bit bif".split():
    for a in ("8b", "16b"): add("logic", f"{op} v0.{a}, v1.{a}, v2.{a}")
# ---- integer vector 2-reg misc
for op in "abs neg cls clz cnt mvn not rbit rev16 rev32 rev64 sqabs sqneg suqadd usqadd saddlp uaddlp sadalp uadalp urecpe ursqrte".split():
    for a in ARR: add("int2", f"{op} v0.{a}, v1.{a}")
for op in "cmeq cmgt cmge cmle cmlt".split():
    for a in ARR: add("int2", f"{op} v0.{a}, v1.{a}, #0")
for (n, w, n2) in [("8b", "8h", "16b"), ("4h", "4s", "8h"), ("2s", "2d", "4s")]:
    for op in "xtn sqxtn uqxtn sqxtun".split():
        add("narrow", f"{op} v0.{n}, v1.{w}"); add("narrow", f"{op}2 v0.{n2}, v1.{w}")
    for op in "sxtl uxtl".split():
        add("narrow", f"{op} v0.{w}, v1.{n}"); add("narrow", f"{op}2 v0.{w}, v1.{n2}")
    add("narrow", f"shll v0.{w}, v1.{n}, #{int(n[-2]) if n[-2] != 'b' else 8}")
for a, w in [("8b", "4h"), ("16b", "8h"), ("4h", "2s"), ("8h", "4s"), ("2s", "1d"), ("4s", "2d")]:
    for op in "saddlp uaddlp sadalp uadalp".split(): add("narrow", f"{op} v0.{w}, v1.{a}")
# ---- shifts by immediate
SH = {"b": 8, "h": 16, "s": 32, "d": 64}
for a in ARR:
    e = SH[a[-1]]
    for op in "shl sli sqshl uqshl sqshlu".split():
        for i in sorted({0, 1, e // 2, e - 1}): add("shift", f"{op} v0.{a}, v1.{a}, #{i}")
    for op in "sshr ushr srshr urshr ssra usra srsra ursra sri".split():
        for i in sorted({1, e // 2, e}): add("shift", f"{op} v0.{a}, v1.{a}, #{i}")
for (n, w, n2) in [("8b", "8h", "16b"), ("4h", "4s", "8h"), ("2s", "2d", "4s")]:
    e = SH[n[-1]]
    for op in "shrn rshrn sqshrn uqshrn sqrshrn uqrshrn sqshrun sqrshrun".split():
        for i in sorted({1, e // 2, e}):
            add("shift", f"{op} v0.{n}, v1.{w}, #{i}"); add("shift", f"{op}2 v0.{n2}, v1.{w}, #{i}")
    for op in "sshll ushll".split():
        for i in sorted({0, 1, e // 2, e - 1}):
            add("shift", f"{op} v0.{w}, v1.{n}, #{i}"); add("shift", f"{op}2 v0.{w}, v1.{n2}, #{i}")
for op in "shl sli sshr ushr srshr urshr ssra usra srsra ursra sri sqshl uqshl sqshlu".split():
    for i in (1, 7, 63): add("shift", f"{op} d0, d1, #{i}")
for a in ("2s", "4s", "2d"):
    e = SH[a[-1]]
    for op in "scvtf ucvtf fcvtzs fcvtzu".split():
        for i in (1, e // 2, e): add("fpfix", f"{op} v0.{a}, v1.{a}, #{i}")
# ---- by element
for op in "mul mla mls sqdmulh sqrdmulh".split():
    for a in ("4h", "8h"):
        for i in range(8): add("elem", f"{op} v0.{a}, v1.{a}, v2.h[{i}]")
    for a in ("2s", "4s"):
        for i in range(4): add("elem", f"{op} v0.{a}, v1.{a}, v2.s[{i}]")
for op in "smull umull smlal umlal smlsl umlsl sqdmull sqdmlal sqdmlsl sqdmulh".split():
    for i in range(8): add("elem", f"{op} v0.4s, v1.4h, v2.h[{i}]"); add("elem", f"{op}2 v0.4s, v1.8h, v2.h[{i}]")
    for i in range(4): add("elem", f"{op} v0.2d, v1.2s, v2.s[{i}]"); add("elem", f"{op}2 v0.2d, v1.4s, v2.s[{i}]")
for op in "fmul fmla fmls fmulx".split():
    for a in ("2s", "4s"):
        for i in range(4): add("elem", f"{op} v0.{a}, v1.{a}, v2.s[{i}]")
    for i in range(2): add("elem", f"{op} v0.2d, v1.2d, v2.d[{i}]")
    for i in range(4): add("elem", f"{op} s0, s1, v2.s[{i}]")
    for i in range(2): add("elem", f"{op} d0, d1, v2.d[{i}]")
for i in range(4): add("elem", f"sdot v0.4s, v1.16b, v2.4b[{i}]"); add("elem", f"udot v0.4s, v1.16b, v2.4b[{i}]")
# ---- FP vector
for op in "fadd fsub fmul fdiv fmla fmls fmax fmin fmaxnm fminnm fabd facge facgt fcmeq fcmge fcmgt faddp fmaxp fminp fmaxnmp fminnmp frecps frsqrts fmulx".split():
    for a in ("2s", "4s", "2d"): add("fp3", f"{op} v0.{a}, v1.{a}, v2.{a}")
    for a in ("4h", "8h"): add("fp16", f"{op} v0.{a}, v1.{a}, v2.{a}")
for op in "fabs fneg fsqrt frecpe frsqrte frintn frintm frintp frintz frinta frintx frinti fcvtns fcvtnu fcvtms fcvtmu fcvtps fcvtpu fcvtzs fcvtzu fcvtas fcvtau scvtf ucvtf".split():
    for a in ("2s", "4s", "2d"): add("fp2", f"{op} v0.{a}, v1.{a}")
    for a in ("4h", "8h"): add("fp16", f"{op} v0.{a}, v1.{a}")
for op in "fcmeq fcmge fcmgt fcmle fcmlt".split():
    for a in ("2s", "4s", "2d"): add("fp2", f"{op} v0.{a}, v1.{a}, #0.0")
add("fp2", "fcvtl v0.4s, v1.4h"); add("fp2", "fcvtl2 v0.4s, v1.8h"); add("fp2", "fcvtl v0.2d, v1.2s"); add("fp2", "fcvtl2 v0.2d, v1.4s")
add("fp2", "fcvtn v0.4h, v1.4s"); add("fp2", "fcvtn2 v0.8h, v1.4s"); add("fp2", "fcvtn v0.2s, v1.2d"); add("fp2", "fcvtn2 v0.4s, v1.2d")
add("fp2", "fcvtxn v0.2s, v1.2d"); add("fp2", "fcvtxn2 v0.4s, v1.2d"); add("fp2", "fcvtxn s0, d1")
for op in "fmaxv fminv fmaxnmv fminnmv".split(): add("across", f"{op} s0, v1.4s")
for op in "faddp fmaxp fminp fmaxnmp fminnmp".split(): add("across", f"{op} s0, v1.2s"); add("across", f"{op} d0, v1.2d")
# ---- permute / move
for op in "zip1 zip2 uzp1 uzp2 trn1 trn2".split():
    for a in ARR: add("perm", f"{op} v0.{a}, v1.{a}, v2.{a}")
for i in range(16): add("perm", f"ext v0.16b, v1.16b, v2.16b, #{i}")
for i in range(8): add("perm", f"ext v0.8b, v1.8b, v2.8b, #{i}")
for op in "tbl tbx".split():
    for n in range(1, 5):
        for a in ("8b", "16b"):
            regs = ", ".join(f"v{k}.16b" for k in range(1, n + 1))
            add("perm", f"{op} v0.{a}, {{{regs}}}, v5.{a}")
for a, e, ln in [("16b", "b", 16), ("8h", "h", 8), ("4s", "s", 4), ("2d", "d", 2), ("8b", "b", 8), ("4h", "h", 4), ("2s", "s", 2)]:
    for i in range(ln): add("perm", f"dup v0.{a}, v1.{e}[{i}]")
    add("perm", f"dup v0.{a}, {'x1' if e == 'd' else 'w1'}")
for e, ln in [("b", 16), ("h", 8), ("s", 4), ("d", 2)]:
    for i in range(ln):
        add("perm", f"ins v0.{e}[{i}], v1.{e}[{(i * 3 + 1) % ln}]"); add("perm", f"ins v0.{e}[{i}], {'x1' if e == 'd' else 'w1'}")
        add("perm", f"umov {'x0' if e == 'd' else 'w0'}, v1.{e}[{i}]"); add("perm", f"dup {e}0, v1.{e}[{i}]")
        if e != "d": add("perm", f"smov w0, v1.{e}[{i}]")
        if e != "d" and e != "s": add("perm", f"smov x0, v1.{e}[{i}]")
        if e == "s": add("perm", f"smov x0, v1.s[{i}]")
for im in ("movi v0.4s, #0xab, lsl #8", "movi v0.4s, #0xab, lsl #24", "movi v0.2s, #0x12, lsl #16", "movi v0.4s, #0x34, msl #8", "movi v0.2d, #0xff00ff00ff00ff00", "movi d0, #0xffff0000ffff0000",
           "movi v0.16b, #0x5a", "movi v0.8h, #0x77, lsl #8", "mvni v0.4s, #0x12, msl #8", "mvni v0.8h, #0x21", "mvni v0.4s, #0x33, lsl #16", "fmov v0.4s, #1.5", "fmov v0.2d, #-0.125",
           "fmov v0.2s, #31.0", "orr v0.4s, #0x4, lsl #16", "orr v0.8h, #0x3", "bic v0.8h, #0x3, lsl #8", "bic v0.4s, #0xf0, lsl #24"): add("perm", im)
for op in "mov v0.16b, v1.16b|mov v0.d[1], v1.d[0]|mov x0, v1.d[1]|mov v0.s[2], w1|rev64 v0.4s, v1.4s".split("|"): add("perm", op)
# ---- across lanes
for op in "addv smaxv sminv umaxv uminv".split():
    for a in ("8b", "16b", "4h", "8h", "4s"): add("across", f"{op} {va(a)}0, v1.{a}")
for op in "saddlv uaddlv".split():
    for a, d in [("8b", "h"), ("16b", "h"), ("4h", "s"), ("8h", "s"), ("4s", "d")]: add("across", f"{op} {d}0, v1.{a}")
add("across", "addp d0, v1.2d")
# ---- long / wide / narrow 3-reg
LONG = [("8h", "8b", "16b"), ("4s", "4h", "8h"), ("2d", "2s", "4s")]
for op in "saddl uaddl ssubl usubl sabal uabal sabdl uabdl smlal umlal smlsl umlsl smull umull sqdmull sqdmlal sqdmlsl pmull".split():
    for d, s, s2 in LONG: add("long", f"{op} v0.{d}, v1.{s}, v2.{s}"); add("long", f"{op}2 v0.{d}, v1.{s2}, v2.{s2}")
add("long", "pmull v0.1q, v1.1d, v2.1d"); add("long", "pmull2 v0.1q, v1.2d, v2.2d")
for op in "saddw uaddw ssubw usubw".split():
    for d, s, s2 in LONG: add("long", f"{op} v0.{d}, v1.{d}, v2.{s}"); add("long", f"{op}2 v0.{d}, v1.{d}, v2.{s2}")
for op in "addhn raddhn subhn rsubhn".split():
    for d, s, s2 in LONG: add("long", f"{op} v0.{s}, v1.{d}, v2.{d}"); add("long", f"{op}2 v0.{s2}, v1.{d}, v2.{d}")
# ---- scalar SIMD
for op in "add sub cmeq cmgt cmge cmhi cmhs cmtst sshl ushl srshl urshl sqshl uqshl sqrshl uqrshl sqadd uqadd sqsub uqsub sqdmulh sqrdmulh abs neg sqabs sqneg suqadd usqadd".split():
    for e in "bhsd": add("ssimd", f"{op} {e}0, {e}1, {e}2" if op not in "abs neg sqabs sqneg suqadd usqadd".split() else f"{op} {e}0, {e}1")
for op in "fabd facge facgt fcmeq fcmge fcmgt frecps frsqrts fmulx".split():
    for e in "sd": add("ssimd", f"{op} {e}0, {e}1, {e}2")
for op in "frecpe frsqrte frecpx fcvtns fcvtnu fcvtms fcvtmu fcvtps fcvtpu fcvtzs fcvtzu fcvtas fcvtau scvtf ucvtf".split():
    for e in "sd": add("ssimd", f"{op} {e}0, {e}1")
for op in "sqxtn uqxtn sqxtun".split():
    for n, w in "bh hs sd".split(): add("ssimd", f"{op} {n}0, {w}1")
add("ssimd", "sqdmull s0, h1, h2"); add("ssimd", "sqdmull d0, s1, s2"); add("ssimd", "sqdmlal s0, h1, h2"); add("ssimd", "sqdmlsl d0, s1, s2")
add("ssimd", "cmeq d0, d1, #0"); add("ssimd", "cmlt d0, d1, #0"); add("ssimd", "fcmgt s0, s1, #0.0"); add("ssimd", "fcmle d0, d1, #0.0")
for op in "scvtf ucvtf fcvtzs fcvtzu".split():
    for e in "sd":
        for i in (1, 16, 31): add("ssimd", f"{op} {e}0, {e}1, #{i}")
# ---- scalar FP
for t in "sdh":
    for op in "fabs fneg fsqrt frintn frintm frintp frintz frinta frintx frinti".split(): add("sfp", f"{op} {t}0, {t}1")
    for op in "fadd fsub fmul fdiv fnmul fmax fmin fmaxnm fminnm".split(): add("sfp", f"{op} {t}0, {t}1, {t}2")
    for op in "fmadd fmsub fnmadd fnmsub".split(): add("sfp", f"{op} {t}0, {t}1, {t}2, {t}3")
    add("sfp", f"fcmp {t}1, {t}2"); add("sfp", f"fcmpe {t}1, {t}2"); add("sfp", f"fcmp {t}1, #0.0"); add("sfp", f"fcmpe {t}1, #0.0")
    for cc in "eq ne cs cc mi pl vs vc hi ls ge lt gt le".split():
        add("sfp", f"fcsel {t}0, {t}1, {t}2, {cc}"); add("sfp", f"fccmp {t}1, {t}2, #5, {cc}"); add("sfp", f"fccmpe {t}1, {t}2, #10, {cc}")
for a, b in ("sd", "ds", "sh", "hs", "dh", "hd"): add("sfp", f"fcvt {a}0, {b}1")
for imm in ("2.5", "-0.125", "31.0", "1.0", "0.5"):
    for t in "sd": add("sfp", f"fmov {t}0, #{imm}")
for op in "fmov w0, s1|fmov x0, d1|fmov s0, w1|fmov d0, x1|fmov x0, v1.d[1]|fmov v0.d[1], x1|fmov w0, h1|fmov h0, w1|fmov x0, h1|fmov s0, s1|fmov d0, d1".split("|"): add("sfp", op)
for op in "fcvtns fcvtnu fcvtms fcvtmu fcvtps fcvtpu fcvtzs fcvtzu fcvtas fcvtau".split():
    for r in "wx":
        for t in "sdh": add("sfp", f"{op} {r}0, {t}1")
for op in "scvtf ucvtf".split():
    for r in "wx":
        for t in "sdh":
            add("sfp", f"{op} {t}0, {r}1")
            for i in (1, 8, 31): add("sfp", f"{op} {t}0, {r}1, #{i}")
for op in "fcvtzs fcvtzu".split():
    for r in "wx":
        for t in "sd":
            for i in (1, 8, 31): add("sfp", f"{op} {r}0, {t}1, #{i}")
add("sfp", "fjcvtzs w0, d1")
# ---- scalar integer
for r in "wx":
    n = 32 if r == "w" else 64
    ab = lambda i: f"{r}{i}"
    for lsb, wd in [(0, 8), (3, 5), (8, 16), (0, n - 1), (7, 1), (n - 1, 1), (1, n - 2), (0, n)]:
        if lsb + wd > n: continue
        for op in ("sbfx", "ubfx", "bfxil") + (("bfi",) if lsb else ()): add("int", f"{op} {r}0, {r}1, #{lsb}, #{wd}")
    for op in "sxtb sxth uxtb uxth".split(): add("int", f"{op} {r}0, w1")
    add("int", "sxtw x0, w1")
    for i in (0, 1, 7, n // 2, n - 1):
        for op in "lsl lsr asr ror".split(): add("int", f"{op} {r}0, {r}1, #{i}")
        add("int", f"extr {r}0, {r}1, {r}2, #{i}")
    for op in "lsl lsr asr ror".split(): add("int", f"{op} {r}0, {r}1, {r}2")
    for op in "rbit rev rev16 clz cls".split(): add("int", f"{op} {r}0, {r}1")
    if r == "x": add("int", "rev32 x0, x1")
    for cc in "eq ne cs cc mi pl vs vc hi ls ge lt gt le".split():
        for op in "csel csinc csinv csneg".split(): add("int", f"{op} {r}0, {r}1, {r}2, {cc}")
        add("int", f"cset {r}0, {cc}"); add("int", f"csetm {r}0, {cc}"); add("int", f"cinc {r}0, {r}1, {cc}"); add("int", f"cneg {r}0, {r}1, {cc}")
        for op in "ccmp ccmn".split():
            add("int", f"{op} {r}1, {r}2, #{(len(cases) * 5) % 16}, {cc}"); add("int", f"{op} {r}1, #{(len(cases) * 3) % 32}, #{(len(cases) * 7) % 16}, {cc}")
    for op in "add adds sub subs and ands orr eor bic bics orn eon".split():
        for sh in ("lsl", "lsr", "asr", "ror"):
            if sh == "ror" and op in ("add", "adds", "sub", "subs"): continue
            for amt in (0, 1, 13, n - 1): add("int", f"{op} {r}0, {r}1, {r}2, {sh} #{amt}")
    for op in "adc adcs sbc sbcs".split(): add("int", f"{op} {r}0, {r}1, {r}2")
    for op in "add adds sub subs".split():
        for ext in "uxtb uxth uxtw uxtx sxtb sxth sxtw sxtx".split():
            for sh in (0, 2, 4): add("int", f"{op} {r}0, {r}1, {'x' if ext[-1] == 'x' else 'w'}2, {ext} #{sh}")
        for imm in (0, 1, 0xfff): add("int", f"{op} {r}0, {r}1, #{imm}")
        add("int", f"{op} {r}0, {r}1, #0x7ab, lsl #12")
    for op in "negs ngc ngcs mvn neg cmp cmn tst".split(): add("int", f"{op} {r}0, {r}1") if op not in ("cmp", "cmn", "tst") else add("int", f"{op} {r}1, {r}2")
    for imm in ("0xff", "0xff00ff00", "0x5555", "0x3ffffffc", "0x80000001", "0xf0f0f0f0", "0x1", "0x7ffffffe"):
        for op in "and orr eor ands".split(): add("int", f"{op} {r}0, {r}1, #{imm}")
    for op in "madd msub".split(): add("int", f"{op} {r}0, {r}1, {r}2, {r}3")
    add("int", f"mul {r}0, {r}1, {r}2"); add("int", f"mneg {r}0, {r}1, {r}2")
    for op in "udiv sdiv".split(): add("int", f"{op} {r}0, {r}1, {r}2")
    for sz in "b h w x".split():
        if sz == "x" and r == "w": continue
        if sz != "x" or r == "x": pass
        add("int", f"crc32{sz} w0, w1, {'x' if sz == 'x' else 'w'}2"); add("int", f"crc32c{sz} w0, w1, {'x' if sz == 'x' else 'w'}2")
for op in "smaddl smsubl umaddl umsubl".split(): add("int", f"{op} x0, w1, w2, x3")
for op in "smull umull smnegl umnegl".split(): add("int", f"{op} x0, w1, w2")
for op in "smulh umulh".split(): add("int", f"{op} x0, x1, x2")
for op in ("movz x0, #0x1234, lsl #16", "movn x0, #0x1234, lsl #32", "movk x0, #0xabcd, lsl #48", "movk w0, #0xabcd, lsl #16", "movz w0, #0x7fff", "movn w0, #0", "mov x0, x1", "mov w0, w1", "mov x0, #0xff00ff00ff00ff00",
           "cls x0, x1", "adr x0, 1f\n 1: adr x1, 2f\n 2: sub x0, x1, x0\n mov x1, #0", "adrp x0, 1f\n adrp x1, 1f\n 1: sub x0, x1, x0\n mov x1, #0", "cfinv", "cmp x1, x2\n cinc x0, x0, hi\n csel x1, x2, x3, lt"): add("int", op)
# branches
for cc in "eq ne cs cc mi pl vs vc hi ls ge lt gt le".split():
    add("branch", f"cmp x1, x2\n b.{cc} 1f\n mov x0, #11\n b 2f\n 1: mov x0, #22\n 2:")
    add("branch", f"cmp w1, w2\n b.{cc} 1f\n mov x0, #11\n b 2f\n 1: mov x0, #22\n 2:")
    add("branch", f"fcmp d0, d1\n b.{cc} 1f\n mov x0, #11\n b 2f\n 1: mov x0, #22\n 2:")
    add("branch", f"fcmp s0, s1\n b.{cc} 1f\n mov x0, #11\n b 2f\n 1: mov x0, #22\n 2:")
for r in "wx":
    for ins in ("cbz", "cbnz"): add("branch", f"{ins} {r}1, 1f\n mov x0, #11\n b 2f\n 1: mov x0, #22\n 2:")
for b in (0, 1, 7, 31, 32, 63):
    for ins in ("tbz", "tbnz"): add("branch", f"{ins} x1, #{b}, 1f\n mov x0, #11\n b 2f\n 1: mov x0, #22\n 2:")
add("branch", "adr x5, 1f\n br x5\n mov x0, #11\n 1: mov x0, #22")
# ---- memory (x4 = middle of a 512 byte buffer; x1 reduced to a small index first when "pre" says so)
SMALL = "and x1, x1, #0xf\n"
SIGNED = "and x1, x1, #0xf\n sub x1, x1, #8\n"
def lane_ok(a): return True
for a, nb in [("8b", 8), ("16b", 16), ("4h", 8), ("8h", 16), ("2s", 8), ("4s", 16), ("1d", 8), ("2d", 16)]:
    for n in range(1, 5):
        regs = ", ".join(f"v{k}.{a}" for k in range(n))
        add("ldst", f"ld1 {{{regs}}}, [x4]", 1); add("ldst", f"st1 {{{regs}}}, [x4]", 1)
        add("ldst", f"ld1 {{{regs}}}, [x4], #{nb * n}", 1); add("ldst", f"st1 {{{regs}}}, [x4], #{nb * n}", 1)
        add("ldst", f"ld1 {{{regs}}}, [x4], x1", 1, SMALL); add("ldst", f"st1 {{{regs}}}, [x4], x1", 1, SMALL)
        if a != "1d" and n > 1:
            add("ldst", f"ld{n} {{{regs}}}, [x4]", 1); add("ldst", f"st{n} {{{regs}}}, [x4]", 1)
            add("ldst", f"ld{n} {{{regs}}}, [x4], #{nb * n}", 1); add("ldst", f"st{n} {{{regs}}}, [x4], #{nb * n}", 1)
            add("ldst", f"ld{n} {{{regs}}}, [x4], x1", 1, SMALL)
    for n in range(1, 5):
        regs = ", ".join(f"v{k}.{a}" for k in range(n))
        add("ldst", f"ld{n}r {{{regs}}}, [x4]", 1); add("ldst", f"ld{n}r {{{regs}}}, [x4], #{ {'b':1,'h':2,'s':4,'d':8}[a[-1]] * n }", 1)
for e, ln, nb in [("b", 16, 1), ("h", 8, 2), ("s", 4, 4), ("d", 2, 8)]:
    for n in range(1, 5):
        regs = ", ".join(f"v{k}.{e}" for k in range(n))
        for i in sorted({0, 1, ln - 1}):
            for op in ("ld", "st"):
                add("ldst", f"{op}{n} {{{regs}}}[{i}], [x4]", 1); add("ldst", f"{op}{n} {{{regs}}}[{i}], [x4], #{nb * n}", 1)
for (r, nb) in [("q", 16), ("d", 8), ("s", 4), ("x", 8), ("w", 4)]:
    for off in (-32, 0, 16, 48):
        if nb == 16 or off % nb == 0:
            add("ldst", f"ldp {r}0, {r}1, [x4, #{off}]", 1); add("ldst", f"stp {r}0, {r}1, [x4, #{off}]", 1)
            add("ldst", f"ldp {r}0, {r}1, [x4, #{off}]!", 1); add("ldst", f"stp {r}0, {r}1, [x4, #{off}]!", 1)
            add("ldst", f"ldp {r}0, {r}1, [x4], #{off}", 1); add("ldst", f"stp {r}0, {r}1, [x4], #{off}", 1)
    add("ldst", f"ldnp {r}0, {r}1, [x4, #16]", 1); add("ldst", f"stnp {r}0, {r}1, [x4, #16]", 1)
add("ldst", "ldpsw x0, x1, [x4, #8]", 1); add("ldst", "ldpsw x0, x1, [x4], #-8", 1)
for ins, r in [("ldr", "x"), ("ldr", "w"), ("ldr", "b"), ("ldr", "h"), ("ldr", "s"), ("ldr", "d"), ("ldr", "q"), ("str", "x"), ("str", "w"), ("str", "b"), ("str", "h"), ("str", "s"), ("str", "d"), ("str", "q"),
               ("ldrb", "w"), ("ldrh", "w"), ("ldrsb", "w"), ("ldrsb", "x"), ("ldrsh", "w"), ("ldrsh", "x"), ("ldrsw", "x"), ("strb", "w"), ("strh", "w")]:
    sz = {"x": 8, "w": 4, "b": 1, "h": 2, "s": 4, "d": 8, "q": 16}[r]
    if ins in ("ldrb", "strb", "ldrsb"): sz = 1
    if ins in ("ldrh", "strh", "ldrsh"): sz = 2
    if ins == "ldrsw": sz = 4
    for off in (0, sz, 5 * sz):
        add("ldst", f"{ins} {r}0, [x4, #{off}]", 1)
    for off in (-9, 0, 3): add("ldst", f"{ins.replace('ldr', 'ldur').replace('str', 'stur')} {r}0, [x4, #{off}]", 1)
    for off in (-sz, 5, sz) if sz > 1 else (-1, 1): add("ldst", f"{ins} {r}0, [x4, #{off}]!", 1); add("ldst", f"{ins} {r}0, [x4], #{off}", 1)
    for ext, pre in (("lsl #0", SMALL), (f"lsl #{sz.bit_length() - 1}", SMALL), ("sxtx", SIGNED)):
        add("ldst", f"{ins} {r}0, [x4, x1, {ext}]", 1, pre)
    for ext, pre in (("uxtw", SMALL), ("sxtw", SIGNED), (f"uxtw #{sz.bit_length() - 1}", SMALL), (f"sxtw #{sz.bit_length() - 1}", SIGNED)):
        add("ldst", f"{ins} {r}0, [x4, w1, {ext}]", 1, pre)
for ins in "ldar ldarb ldarh ldaxr ldxr ldxrb ldxrh ldapr".split(): add("ldst", f"{ins} w0, [x4]", 1)
for ins in "stlr stlrb stlrh".split(): add("ldst", f"{ins} w0, [x4]", 1)
for ins in "stxr stlxr".split(): add("ldst", f"ldxr x2, [x4]\n {ins} w3, x0, [x4]", 1)
add("ldst", "ldxp x0, x2, [x4]\n stxp w3, x0, x2, [x4]", 1); add("ldst", "ldaxp x0, x2, [x4]\n stlxp w3, x1, x2, [x4]", 1)
for op in "ldadd ldclr ldset ldeor ldsmax ldsmin ldumax ldumin swp".split():
    for sfx in ("", "a", "l", "al"):
        for r in "wx": add("atomic", f"{op}{sfx} {r}1, {r}0, [x4]", 1)
        add("atomic", f"{op}{sfx}b w1, w0, [x4]", 1); add("atomic", f"{op}{sfx}h w1, w0, [x4]", 1)
for sfx in ("", "a", "l", "al"):
    for r in "wx": add("atomic", f"cas{sfx} {r}0, {r}1, [x4]", 1); add("atomic", f"ldr {r}0, [x4]\n cas{sfx} {r}0, {r}1, [x4]", 1)
    add("atomic", f"casp x0, x1, x2, x3, [x4]", 1)
for op in "dmb ish|dmb ishld|dmb ishst|dsb sy|isb|prfm pldl1keep, [x4]|prfm pstl2strm, [x4, #8]|dc zva, x4|clrex".split("|"): add("ldst", op, 1)
add("ldst", "str x0, [sp, #-16]!\n ldr x1, [sp], #16", 1); add("ldst", "stp x0, x1, [sp, #-16]!\n ldp x2, x3, [sp], #16", 1)
add("ldst", "ldr q0, [x4, #16]\n ldr q1, [x4, #32]\n fmla v0.4s, v1.4s, v2.4s\n str q0, [x4]", 1)

# forms the translators raise SIGILL on (sigforms.txt, first guest SIGILL of a run keeps the heavy tier from gearing up later cases): own group "sig_", run last, never chained
sig = set(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "sigforms.txt")).read().split("\n")) if os.path.exists(os.path.join(os.path.dirname(os.path.abspath(__file__)), "sigforms.txt")) else set()
cases = [("sig", c[1], c[2], c[3]) if c[1] in sig else c for c in cases]
# ---- validate with the NDK assembler, drop rejected forms, emit
def ndk_clang():
    sdk = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk"))
    return sorted(glob.glob(sdk + "/ndk/*/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android26-clang"))[-1]
seen, uniq = set(), []
for c in cases:
    k = (c[1], c[2], c[3])
    if k not in seen: seen.add(k); uniq.append(c)
cases = uniq
ARCH = "-march=armv8.5-a+fp16+fp16fml+aes+sha2+sha3+sm4+dotprod+crc+lse+rcpc"
def bad_lines(cs):
    with tempfile.TemporaryDirectory() as d:
        src, ln, ranges = [], 1, []
        src.append(".text"); ln += 1
        for i, c in enumerate(cs):
            body = (c[3] + c[1]).replace("\n ", "\n").split("\n")
            ranges.append((ln, ln + len(body) - 1)); src += body; ln += len(body)
        open(d + "/t.s", "w").write("\n".join(src) + "\n")
        r = subprocess.run([ndk_clang(), ARCH, "-c", "-x", "assembler", d + "/t.s", "-o", d + "/t.o"], capture_output=True, text=True)
        bad = set()
        for m in re.finditer(r"t\.s:(\d+):\d+: error", r.stderr):
            n = int(m.group(1))
            for i, (a, b) in enumerate(ranges):
                if a <= n <= b: bad.add(i)
        if r.returncode and not bad: sys.exit(r.stderr)
        return bad
while True:
    bad = bad_lines(cases)
    if not bad: break
    print(f"dropped {len(bad)} invalid forms, e.g. {cases[min(bad)][1]!r}", file=sys.stderr)
    cases = [c for i, c in enumerate(cases) if i not in bad]
# ---- sequences: several forms in one region (the heavy tier allocates registers and forwards guest-context loads/stores across them), plus mixed-width accesses to the same V register
import random
rng = random.Random(20261116)
def plain(c):  # no label, no base writeback, no exclusive pairs: safe to chain
    return "\n" not in c[1] or c[0] in ("perm", "int"), not any(t in c[1] for t in ("!", "], #", "], x1", "f\n", "br ", "ldxr", "ldaxr", "stxr", "stlxr", "sp", "ldp", "dc zva", "casp", "ldxp", "ldaxp")) and not c[1].startswith(("b.", "cb", "tb", "adr"))
pool = [c for c in cases if c[0] not in ("branch", "sig") and all(plain(c)) and "\n" not in c[1] and "ld" not in c[1].split()[0][:2] + ""[:0] or (c[2] and all(plain(c)) and "\n" not in c[1])]
pool = [c for c in pool if "1f" not in c[1] and "wfi" not in c[1] and not c[3]]
heavy = [c for c in pool if c[0] in ("int3", "logic", "int2", "narrow", "shift", "fpfix", "elem", "fp3", "fp2", "across", "perm", "long", "ssimd", "sfp", "fp16")]
rest = [c for c in pool if c not in heavy]
for k in range(1500):
    n = rng.randint(3, 10)
    chosen = [rng.choice(heavy) if rng.random() < 0.8 or not rest else rng.choice(rest) for _ in range(n)]
    cases.append(("seq", "\n".join(c[1] for c in chosen), int(any(c[2] for c in chosen)), "".join(dict.fromkeys(c[3] for c in chosen if c[3])) if False else ""))
MW = ["ldr d{a}, [x4, #{o}]", "ldr q{a}, [x4, #{o}]", "ldr s{a}, [x4, #{o}]", "ldr b{a}, [x4, #{o}]", "ldr h{a}, [x4, #{o}]", "str d{a}, [x4, #{o}]", "str q{a}, [x4, #{o}]", "str s{a}, [x4, #{o}]", "ins v{a}.d[1], x1", "ins v{a}.s[2], w2", "ins v{a}.h[5], w3",
      "mov v{a}.d[1], v{b}.d[0]", "mov v{a}.16b, v{b}.16b", "mov v{a}.8b, v{b}.8b", "umov x0, v{a}.d[1]", "umov w0, v{a}.s[3]", "fmov x0, d{a}", "fmov d{a}, x1", "fmov v{a}.d[1], x2", "fmov x3, v{a}.d[1]", "fmov s{a}, w1",
      "dup v{a}.4s, w1", "dup v{a}.2d, x2", "ext v{a}.16b, v{b}.16b, v{c}.16b, #{e}", "ld1 {{v{a}.d}}[1], [x4, #0]" if False else "ld1 {{v{a}.2d}}, [x4]", "ld1r {{v{a}.4s}}, [x4]", "zip1 v{a}.2d, v{b}.2d, v{c}.2d", "zip2 v{a}.4s, v{b}.4s, v{c}.4s",
      "fadd d{a}, d{b}, d{c}", "fadd s{a}, s{b}, s{c}", "fadd v{a}.2d, v{b}.2d, v{c}.2d", "fadd v{a}.4s, v{b}.4s, v{c}.4s", "fmul v{a}.2s, v{b}.2s, v{c}.2s", "add v{a}.8b, v{b}.8b, v{c}.8b", "add v{a}.16b, v{b}.16b, v{c}.16b", "add v{a}.2s, v{b}.2s, v{c}.2s",
      "movi v{a}.2d, #0xff00ff00ff00ff00", "movi d{a}, #0xffff0000ffff0000", "fcvtl v{a}.2d, v{b}.2s", "fcvtn v{a}.2s, v{b}.2d", "fcvtn2 v{a}.4s, v{b}.2d", "xtn v{a}.8b, v{b}.8h", "xtn2 v{a}.16b, v{b}.8h", "sqxtn v{a}.4h, v{b}.4s", "uzp1 v{a}.4s, v{b}.4s, v{c}.4s",
      "scvtf d{a}, x0", "ucvtf s{a}, w1", "fcvtzs x0, d{a}", "fmla v{a}.4s, v{b}.4s, v{c}.s[1]", "fmla d{a}, d{b}, v{c}.d[1]", "fmadd d{a}, d{b}, d{c}, d{e}", "ldp d{a}, d{b}, [x4, #16]", "stp q{a}, q{b}, [x4, #32]", "ldp q{a}, q{b}, [x4, #-32]", "stp d{a}, d{b}, [x4, #8]",
      "ld1 {{v{a}.8b, v{b}.8b}}, [x4]", "st1 {{v{a}.16b, v{b}.16b}}, [x4]", "ld2 {{v{a}.4s, v{b}.4s}}, [x4]", "st2 {{v{a}.2s, v{b}.2s}}, [x4]", "ld1 {{v{a}.16b, v{b}.16b, v{c}.16b, v{e}.16b}}, [x4]" if False else "ld1 {{v{a}.2d, v{b}.2d}}, [x4]",
      "tbl v{a}.16b, {{v{b}.16b}}, v{c}.16b", "tbl v{a}.8b, {{v{b}.16b, v{c}.16b}}, v{e}.8b", "bsl v{a}.16b, v{b}.16b, v{c}.16b", "fmaxnm v{a}.2d, v{b}.2d, v{c}.2d", "addv b{a}, v{b}.16b", "addp d{a}, v{b}.2d", "shl v{a}.2d, v{b}.2d, #3", "ushr d{a}, d{b}, #5", "sshll v{a}.2d, v{b}.2s, #2", "umull v{a}.2d, v{b}.2s, v{c}.2s"]
for k in range(1500):
    n = rng.randint(4, 12); ins = []
    for _ in range(n):
        t = rng.choice(MW); ins.append(t.format(a=rng.randint(0, 3), b=rng.randint(0, 3), c=rng.randint(0, 3), e=rng.randint(0, 3) if "#{e}" not in t else rng.randint(1, 15), o=rng.choice([0, 8, 16, 24, 32, -16, -48])))
    cases.append(("mw", "\n".join(ins), 1, ""))
# ---- loops: the same chains inside an in-region loop (x6 counts), and streaming loops with post-index loads/stores (the shapes the heavy tier hoists and promotes whatever their length)
LD = ["ld1 {v0.4s, v1.4s, v2.4s, v3.4s}, [x4], #64", "ld1 {v0.16b, v1.16b}, [x4], #32", "ld1 {v0.2d}, [x4], #16", "ld2 {v0.4s, v1.4s}, [x4], #32", "ld3 {v0.8h, v1.8h, v2.8h}, [x4], #48", "ld4 {v0.16b, v1.16b, v2.16b, v3.16b}, [x4], #64",
      "ldp q0, q1, [x4], #32", "ldr q0, [x4], #16", "ldr d0, [x4], #8", "ld1 {v0.8b}, [x4], #8", "ld1r {v0.4s}, [x4], #4", "ldp d0, d1, [x4], #16", "ldr s0, [x4], #4", "ld1 {v0.s}[2], [x4], #4", "ld2 {v0.2s, v1.2s}, [x4], #16"]
OP = ["fmla v4.4s, v0.4s, v1.4s", "fadd v5.4s, v4.4s, v2.4s", "fmul v6.4s, v1.4s, v0.s[1]", "add v4.16b, v0.16b, v1.16b", "uzp1 v5.4s, v0.4s, v1.4s", "zip2 v6.8h, v2.8h, v3.8h", "umull v4.8h, v0.8b, v1.8b", "umlal2 v4.8h, v0.16b, v1.16b", "ucvtf v5.4s, v0.4s", "fcvtzs v6.4s, v1.4s",
      "bsl v4.16b, v0.16b, v1.16b", "tbl v5.16b, {v0.16b, v1.16b}, v2.16b", "ext v6.16b, v0.16b, v1.16b, #3", "fmax v4.4s, v4.4s, v0.4s", "fmin v5.4s, v5.4s, v1.4s", "sshr v6.4s, v0.4s, #3", "shl v4.2d, v1.2d, #7", "xtn v5.4h, v0.4s", "xtn2 v5.8h, v1.4s", "sqxtun v6.8b, v0.8h",
      "fcvtn v4.4h, v0.4s", "fcvtl v5.4s, v1.4h", "mov v6.d[1], v0.d[0]", "ins v4.s[1], v1.s[3]", "dup v5.4s, v0.s[2]", "umaxp v6.4s, v0.4s, v1.4s", "addv s4, v0.4s", "faddp v5.4s, v0.4s, v1.4s", "frecpe v6.4s, v0.4s", "frsqrte v4.4s, v1.4s", "rev64 v5.4s, v0.4s", "cmgt v6.4s, v0.4s, v1.4s",
      "mla v4.4s, v0.4s, v1.4s", "fmls v5.2d, v0.2d, v1.2d", "scvtf v6.2d, v0.2d", "fsqrt v4.4s, v1.4s", "movi v5.2d, #0xff00ff00ff00ff00", "add d6, d0, d1", "fadd d4, d0, d1", "fmadd d5, d0, d1, d2"]
ST = ["st1 {v4.4s, v5.4s, v6.4s}, [x5], #48", "st1 {v4.16b}, [x5], #16", "stp q4, q5, [x5], #32", "str q6, [x5], #16", "str d4, [x5], #8", "st2 {v4.4s, v5.4s}, [x5], #32", "st4 {v4.8b, v5.8b, v6.8b, v7.8b}, [x5], #32", "st1 {v5.d}[1], [x5], #8", "stp d4, d5, [x5], #16", "str s6, [x5], #4"]
for k in range(500):
    body = [rng.choice(LD)] + [rng.choice(OP) for _ in range(rng.randint(1, 5))] + [rng.choice(ST)] + ([rng.choice(ST)] if rng.random() < 0.3 else [])
    cases.append(("lm", "sub x5, x4, #256\nmov x6, #4\n1:\n" + "\n".join(body) + "\nsubs x6, x6, #1\nb.ne 1b", 1, ""))
for c in [c for c in cases if c[0] in ("seq", "mw")][::2]:
    cases.append(("lp", "mov x6, #3\n1:\n" + c[1] + "\nsubs x6, x6, #1\nb.ne 1b", 1, ""))
for c in [c for c in cases if c[0] in ("ssimd", "fp3", "int3", "elem", "sfp", "shift", "perm", "long", "int2", "fp2", "narrow")][::3]:
    cases.append(("lp", "mov x6, #5\n1:\n" + c[1] + "\nsubs x6, x6, #1\nb.ne 1b", 0, ""))
# ---- bail prefix: a loop (captured back edge) then an instruction the heavy tier does not translate, then more code: the heavy region is installed up to the bail and the guest state must be complete there
BAIL = ["sqrdmlah v0.4s, v1.4s, v2.4s", "sqrdmlsh v3.8h, v1.8h, v2.8h", "fjcvtzs w0, d1", "fmlal v0.4s, v1.4h, v2.4h", "eor3 v0.16b, v1.16b, v2.16b, v3.16b", "bcax v1.16b, v0.16b, v2.16b, v3.16b", "rax1 v0.2d, v1.2d, v2.2d", "xar v0.2d, v1.2d, v2.2d, #7",
        "uqrshl s0, s1, s2", "urshl v0.4s, v1.4s, v2.4s", "fcmla v0.4s, v1.4s, v2.4s, #90", "fcadd v0.4s, v1.4s, v2.4s, #270", "frint32z v0.4s, v1.4s", "sha512h q0, q1, v2.2d", "sm3ss1 v0.4s, v1.4s, v2.4s, v3.4s", "fcvtn v0.4h, v1.4s", "fcvtl v0.4s, v1.4h", "sqshl v0.4s, v1.4s, v2.4s",
        "sqrshl v0.8h, v1.8h, v2.8h", "ldapur x0, [x4, #1]", "stlur x0, [x4, #1]", "msr nzcv, x1", "mrs x0, nzcv", "msr fpcr, xzr", "mrs x0, fpsr", "cfinv", "setf8 w1", "rmif x1, #3, #5", "dc zva, x4", "ic ivau, x4", "dmb ish", "isb", "clrex"]
for k in range(700):
    pre_ = [rng.choice(heavy)[1] for _ in range(rng.randint(2, 6))]
    post_ = [rng.choice(heavy)[1] for _ in range(rng.randint(2, 6))]
    cases.append(("bp", "mov x6, #4\n1:\n" + "\n".join(pre_) + "\nsubs x6, x6, #1\nb.ne 1b\n" + rng.choice(BAIL) + "\n" + "\n".join(post_), 1, ""))
bad = bad_lines(cases)
if bad: cases = [c for i, c in enumerate(cases) if i not in bad]; print(f"dropped {len(bad)} invalid sequences", file=sys.stderr)
# ---- wide: 40-150 forms in one region over v0-v31 and 24 integer registers (register pressure beyond what x86 has: spills, rematerialisation), half of them inside a loop
BX = [0, 1, 2, 3] + list(range(7, 16)) + list(range(19, 29))
def rename(t, voff, xoff):
    t = re.sub(r"\bv(\d+)\b", lambda m: f"v{(int(m.group(1)) + voff) % 32}", t)
    t = re.sub(r"(?<![\w.#])([bhsdq])(\d+)\b", lambda m: f"{m.group(1)}{(int(m.group(2)) + voff) % 32}", t)
    return re.sub(r"\b([xw])([0-3])\b", lambda m: f"{m.group(1)}{BX[(int(m.group(2)) + xoff) % len(BX)]}", t)
wpool = [c for c in pool if "x1" not in c[1] or "], x1" not in c[1]]
wide = []
for k in range(600):
    body = []
    for _ in range(rng.randint(40, 150)):
        c = rng.choice(wpool if rng.random() < 0.3 else heavy)
        body.append(rename(c[1], rng.randint(0, 24), rng.randint(0, len(BX) - 1)))
    if k % 2: body = ["mov x6, #3", "1:"] + body + ["subs x6, x6, #1", "b.ne 1b"]
    wide.append(("wl" if k % 2 else "wd", "\n".join(body), 1, ""))
cases = [c for c in cases if c[0] != "sig"] + [c for c in cases if c[0] == "sig"]
def check_wide(ws):  # drop single invalid lines (a renamed form may not assemble), keep the rest of the case
    flat = [("w", ln, 0, "") for w in ws for ln in w[1].split("\n")]
    while True:
        bad = bad_lines(flat)
        if not bad: break
        flat = [c for i, c in enumerate(flat) if i not in bad]
    ok = {c[1] for c in flat}
    return [(w[0], "\n".join(l for l in w[1].split("\n") if l in ok), w[2], w[3]) for w in ws]
wide = check_wide(wide)
names = set()
for g, asm, mem, pre in cases:
    nm = g + "_" + re.sub(r"_+", "_", re.sub(r"[^0-9a-zA-Z]", "_", asm.split("\n")[-1] if False else asm.replace("\n", ";")).strip("_"))
    nm = nm[:90]
    if g in ('seq', 'mw'): nm = f"{g}_{sum(1 for x in names if x.startswith(g + '_')):04d}"
    base, k = nm, 1
    while nm in names: k += 1; nm = f"{base}_{k}"
    names.add(nm)
    esc = lambda s: s.replace("\\", "\\\\").replace('"', '\\"').replace("\n ", "\\n").replace("\n", "\\n")
    print(f'C({nm}, {mem}, "{esc(pre)}", "{esc(asm)}")')
esc = lambda t: t.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "cases_wide.inc"), "w") as f:
    for i, (g, asm, mem, pre) in enumerate(wide): f.write(f'W({g}_{i:04d}, "{esc(asm)}")\n')
print(f"{len(cases)} cases, {len(wide)} wide", file=sys.stderr)
