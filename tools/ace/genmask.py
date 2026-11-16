#!/usr/bin/env python3
# genmask.py: assemble sample instructions of each absent-on-A55 feature with varied operands, derive (mask, value) as the bits constant across the samples.
# Output: C++ table for decoder.h (IsAbsentFeatureInsn). usage: genmask.py > table.inc
import glob, os, subprocess, sys, tempfile
CC = sorted(glob.glob(os.path.expanduser('~/Library/Android/sdk/ndk/*/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android26-clang')))[-1]
BIN = os.path.dirname(CC)
X = ['x0', 'x1', 'x5', 'x17', 'x30']
W = ['w0', 'w1', 'w5', 'w17', 'w30']
def regs(fmt, n=5):
    out = []
    for i in range(n):
        out.append(fmt.replace('$x', X[i]).replace('$y', X[(i + 1) % 5]).replace('$z', X[(i + 2) % 5])
                      .replace('$w', W[i]).replace('$u', W[(i + 1) % 5]).replace('$v', W[(i + 2) % 5])
                      .replace('$q', 'q%d' % (i * 7)).replace('$d', 'd%d' % (i * 7)).replace('$s', 's%d' % (i * 7)).replace('$h', 'h%d' % (i * 7)))
    return out
def vec(fmt, arr):  # fmt with $a (arrangement) and v registers
    out = []
    regs3 = [(0, 1, 2), (31, 30, 29), (5, 17, 9), (3, 3, 3), (12, 0, 31)]
    for a in arr:
        for (d, n, m) in regs3:
            out.append(fmt.replace('$a', a).replace('$0', str(d)).replace('$1', str(n)).replace('$2', str(m)))
    return out
G = {}
G['fcma'] = vec('fcmla v$0.$a, v$1.$a, v$2.$a, #0', ['4s', '2d', '8h', '4h', '2s']) + vec('fcmla v$0.$a, v$1.$a, v$2.$a, #270', ['4s']) + vec('fcadd v$0.$a, v$1.$a, v$2.$a, #90', ['4s', '2d', '8h']) + \
    vec('fcadd v$0.$a, v$1.$a, v$2.$a, #270', ['4s']) + ['fcmla v0.4s, v1.4s, v2.s[1], #90', 'fcmla v31.8h, v1.8h, v2.h[3], #0', 'fcmla v5.4s, v6.4s, v7.s[0], #270', 'fcmla v1.4h, v2.4h, v3.h[0], #180']
G['jscvt'] = regs('fjcvtzs $w, d3')
G['flagm'] = ['cfinv', 'setf8 w0', 'setf16 w5', 'setf8 w30', 'rmif x0, #3, #5', 'rmif x30, #63, #15', 'rmif x5, #0, #0']
G['flagm2'] = ['axflag', 'xaflag']
G['frint3264'] = vec('frint32z v$0.$a, v$1.$a', ['4s', '2s', '2d']) + vec('frint64x v$0.$a, v$1.$a', ['4s']) + vec('frint32x v$0.$a, v$1.$a', ['4s']) + vec('frint64z v$0.$a, v$1.$a', ['4s']) + \
    ['frint32z s0, s1', 'frint32z d3, d4', 'frint32x s5, s30', 'frint64z d0, d31', 'frint64x s17, s9', 'frint64z s1, s2']
G['bf16'] = vec('bfdot v$0.$a, v$1.8h, v$2.8h', ['4s', '2s']) + ['bfdot v0.4s, v1.8h, v2.2h[1]', 'bfdot v31.2s, v30.4h, v29.2h[3]', 'bfmmla v0.4s, v1.8h, v2.8h', 'bfmmla v31.4s, v5.8h, v17.8h',
    'bfmlalb v0.4s, v1.8h, v2.8h', 'bfmlalt v31.4s, v5.8h, v17.8h', 'bfmlalb v0.4s, v1.8h, v2.h[3]', 'bfmlalt v3.4s, v1.8h, v2.h[7]', 'bfcvtn v0.4h, v1.4s', 'bfcvtn2 v31.8h, v30.4s', 'bfcvt h0, s1', 'bfcvt h31, s17']
G['i8mm'] = vec('smmla v$0.4s, v$1.16b, v$2.16b', ['x']) + vec('ummla v$0.4s, v$1.16b, v$2.16b', ['x']) + vec('usmmla v$0.4s, v$1.16b, v$2.16b', ['x']) + vec('usdot v$0.$a, v$1.16b, v$2.16b', ['4s']) + vec('usdot v$0.2s, v$1.8b, v$2.8b', ['x']) + \
    ['usdot v0.4s, v1.16b, v2.4b[2]', 'sudot v0.4s, v1.16b, v2.4b[1]', 'usdot v31.2s, v30.8b, v29.4b[3]', 'sudot v5.2s, v6.8b, v7.4b[0]']
G['sb'] = ['sb']
G['ssbs'] = ['msr ssbs, #0', 'msr ssbs, #1', 'mrs x0, ssbs', 'msr ssbs, x5', 'mrs x30, ssbs', 'msr ssbs, x17']
G['dit'] = ['msr dit, #0', 'msr dit, #1', 'mrs x0, dit', 'msr dit, x5', 'mrs x30, dit']
G['pauth_dp'] = []
for op in ['pacia', 'pacib', 'pacda', 'pacdb', 'autia', 'autib', 'autda', 'autdb']:
    G['pauth_dp'] += regs(op + ' $x, $y')
for op in ['paciza', 'pacizb', 'pacdza', 'pacdzb', 'autiza', 'autizb', 'autdza', 'autdzb', 'xpaci', 'xpacd']:
    G['pauth_dp'] += regs(op + ' $x')
G['pauth_dp'] += regs('pacga $x, $y, $z')
G['pauth_br'] = ['retaa', 'retab', 'eretaa', 'eretab'] + regs('braa $x, $y') + regs('brab $x, $y') + regs('blraa $x, $y') + regs('blrab $x, $y') + regs('braaz $x') + regs('brabz $x') + regs('blraaz $x') + regs('blrabz $x')
G['pauth_ld'] = regs('ldraa $x, [$y]') + regs('ldrab $x, [$y]') + regs('ldraa $x, [$y, #8]!') + regs('ldrab $x, [$y, #-16]!') + regs('ldraa $x, [$y, #4088]')
G['mte'] = regs('irg $x, $y') + regs('irg $x, $y, $z') + regs('gmi $x, $y, $z') + regs('subp $x, $y, $z') + regs('subps $x, $y, $z') + regs('addg $x, $y, #16, #3') + regs('subg $x, $y, #32, #15') + \
    regs('ldg $x, [$y]') + regs('ldg $x, [$y, #16]') + regs('stg $x, [$y]') + regs('stg $x, [$y, #32]') + regs('stg $x, [$y, #16]!') + regs('stg $x, [$y], #16') + regs('stzg $x, [$y]') + regs('stzg $x, [$y, #-16]!') + regs('st2g $x, [$y]') + regs('st2g $x, [$y], #32') + \
    regs('stz2g $x, [$y, #32]') + regs('stz2g $x, [$y, #48]!') + regs('stgp $x, $y, [$z]') + regs('stgp $x, $y, [$z, #16]!') + regs('stgp $x, $y, [$z], #16') + regs('ldgm $x, [$y]') + regs('stgm $x, [$y]') + regs('stzgm $x, [$y]') + \
    regs('dc gva, $x') + regs('dc gzva, $x') + regs('dc cgvac, $x') + regs('dc cgdvac, $x') + regs('dc cigvac, $x') + regs('dc cigdvac, $x') + regs('dc igvac, $x') + regs('dc igsw, $x') + regs('dc cgsw, $x')
G['mte_sysreg'] = ['mrs x0, tco', 'msr tco, #1', 'msr tco, x3', 'mrs x30, gmid_el1', 'mrs x1, rgsr_el1', 'mrs x1, gcr_el1']
G['sha512'] = vec('sha512h q$0, q$1, v$2.2d', ['x']) + vec('sha512h2 q$0, q$1, v$2.2d', ['x']) + vec('sha512su0 v$0.2d, v$1.2d', ['x']) + vec('sha512su1 v$0.2d, v$1.2d, v$2.2d', ['x'])
G['sha3'] = vec('eor3 v$0.16b, v$1.16b, v$2.16b, v$2.16b', ['x']) + vec('bcax v$0.16b, v$1.16b, v$2.16b, v$0.16b', ['x']) + vec('rax1 v$0.2d, v$1.2d, v$2.2d', ['x']) + ['xar v0.2d, v1.2d, v2.2d, #0', 'xar v31.2d, v30.2d, v29.2d, #63', 'xar v5.2d, v6.2d, v7.2d, #17', 'xar v1.2d, v2.2d, v3.2d, #32']
G['sm3'] = vec('sm3ss1 v$0.4s, v$1.4s, v$2.4s, v$0.4s', ['x']) + vec('sm3partw1 v$0.4s, v$1.4s, v$2.4s', ['x']) + vec('sm3partw2 v$0.4s, v$1.4s, v$2.4s', ['x']) + \
    ['sm3tt1a v0.4s, v1.4s, v2.s[0]', 'sm3tt1a v31.4s, v30.4s, v29.s[3]', 'sm3tt1b v0.4s, v1.4s, v2.s[1]', 'sm3tt2a v5.4s, v6.4s, v7.s[2]', 'sm3tt2b v5.4s, v6.4s, v7.s[3]']
G['sm4'] = vec('sm4e v$0.4s, v$1.4s', ['x']) + vec('sm4ekey v$0.4s, v$1.4s, v$2.4s', ['x'])
G['fhm'] = vec('fmlal v$0.$a, v$1.$b, v$2.$b'.replace('$b', '2h'), ['2s']) + vec('fmlal v$0.4s, v$1.4h, v$2.4h', ['x']) + vec('fmlsl v$0.2s, v$1.2h, v$2.2h', ['x']) + vec('fmlsl2 v$0.4s, v$1.4h, v$2.4h', ['x']) + vec('fmlal2 v$0.2s, v$1.2h, v$2.2h', ['x']) + \
    ['fmlal v0.4s, v1.4h, v2.h[7]', 'fmlsl v31.2s, v30.2h, v15.h[0]', 'fmlal2 v0.4s, v1.4h, v2.h[3]', 'fmlsl2 v5.2s, v6.2h, v7.h[5]']
G['lrcpc2'] = regs('ldapur $x, [$y]') + regs('ldapur $x, [$y, #-8]') + regs('ldapur $w, [$y, #255]') + regs('ldapurb $w, [$y, #1]') + regs('ldapurh $w, [$y, #2]') + regs('ldapursb $x, [$y, #3]') + regs('ldapursh $w, [$y, #4]') + regs('ldapursw $x, [$y, #8]') + \
    regs('stlur $x, [$y]') + regs('stlur $w, [$y, #-4]') + regs('stlurb $w, [$y, #5]') + regs('stlurh $w, [$y, #6]')
G['rcpc3_lse128'] = regs('ldiapp $x, $y, [$z]') + regs('stilp $x, $y, [$z]') + regs('ldiapp $w, $u, [$z], #8') + regs('stilp $x, $y, [$z, #-16]!') + regs('ldapr $x, [$y, #-8]!') + regs('stlr $x, [$y, #-8]!') + regs('ldapr $w, [$y], #4') + \
    regs('swpp $x, $y, [$z]') + regs('swppa $x, $y, [$z]') + regs('swppal $x, $y, [$z]') + regs('swppl $x, $y, [$z]') + regs('ldclrp $x, $y, [$z]') + regs('ldclrppal $x, $y, [$z]'.replace('ldclrppal', 'ldclrpal')) + regs('ldsetp $x, $y, [$z]') + regs('ldsetpa $x, $y, [$z]')
G['hbc'] = ['bc.eq #8', 'bc.ne #-16', 'bc.hi #1024', 'bc.lt #4', 'bc.al #12']
G['mops'] = regs('cpyfp [$x]!, [$y]!, $z!') + regs('cpyfm [$x]!, [$y]!, $z!') + regs('cpyfe [$x]!, [$y]!, $z!') + regs('cpyp [$x]!, [$y]!, $z!') + regs('cpyfpwn [$x]!, [$y]!, $z!') + regs('cpyfprn [$x]!, [$y]!, $z!') + regs('cpyfpn [$x]!, [$y]!, $z!') + \
    regs('setp [$x]!, $y!, $z') + regs('setm [$x]!, $y!, $z') + regs('sete [$x]!, $y!, $z') + regs('setgp [$x]!, $y!, $z') + regs('setpn [$x]!, $y!, $z') + regs('setgm [$x]!, $y!, $z') + regs('setge [$x]!, $y!, $z')
G['cssc'] = regs('abs $x, $y') + regs('abs $w, $u') + regs('cnt $x, $y') + regs('cnt $w, $u') + regs('ctz $x, $y') + regs('ctz $w, $u') + regs('smax $x, $y, $z') + regs('smin $w, $u, $v') + regs('umax $x, $y, $z') + regs('umin $w, $u, $v') + \
    ['smax x0, x1, #-128', 'smax x5, x17, #127', 'smin w0, w1, #3', 'umax x30, x0, #255', 'umin w5, w6, #0']
G['wfxt'] = regs('wfet $x') + regs('wfit $x')
G['dpb2'] = regs('dc cvadp, $x')
G['priv_sys'] = regs('dc ivac, $x') + regs('dc isw, $x') + regs('dc csw, $x') + regs('dc cisw, $x') + ['ic iallu', 'ic ialluis', 'msr daifset, #0', 'msr daifset, #15', 'msr daifclr, #3', 'msr daifclr, #8', 'msr spsel, #1', 'msr spsel, #0',
    'msr pan, #1', 'msr pan, #0', 'msr uao, #1', 'msr uao, #0']
G['dc_zva'] = regs('dc zva, $x')
G['sys_bad'] = regs('sys #3, c7, c11, #7, $x') + regs('sys #3, c7, c11, #6, $x') + regs('sys #3, c7, c3, #1, $x') + regs('sys #3, c7, c10, #6, $x')
G['lse_ldst_unalloc'] = []

def assemble(lines):
    words = []
    with tempfile.TemporaryDirectory() as d:
        s = os.path.join(d, 'a.s'); o = os.path.join(d, 'a.o'); b = os.path.join(d, 'a.bin')
        open(s, 'w').write('.arch armv9.5-a+sha3+sm4+memtag+lse128+rcpc3+fp16fml\n' + '\n'.join(lines) + '\n')
        r = subprocess.run([CC, '-c', s, '-o', o], capture_output=True, text=True)
        if r.returncode:
            return None, r.stderr
        subprocess.run([BIN + '/llvm-objcopy', '-O', 'binary', '--only-section=.text', o, b], check=True)
        data = open(b, 'rb').read()
        return [int.from_bytes(data[i:i + 4], 'little') for i in range(0, len(data), 4)], ''

import re
out = {}
for name, lines in G.items():
    for ln in lines:
        w, err = assemble([ln])
        if w is None:
            sys.stderr.write('skip %s | %s | %s\n' % (name, ln, err.strip().splitlines()[0][-70:] if err.strip() else ''))
            continue
        keep = ln.split()[0] in ('sys', 'sysl', 'dc', 'ic', 'msr', 'mrs')
        key = (name, re.sub(r'\b[xwvqdsh]\d+', 'R', ln) if keep else re.sub(r'#-?\d+', '#I', re.sub(r'\b[xwvqdsh]\d+', 'R', ln)))
        if keep and ln.split()[0] == 'msr' and '#' in ln:
            key = (name, re.sub(r'#\d+', '#I', key[1]))
        out.setdefault(key, []).extend(w)
res = {}
for key, ws in out.items():
    mask = 0xffffffff
    for w in ws:
        mask &= ~(w ^ ws[0]) & 0xffffffff
    res.setdefault((mask, ws[0] & mask), []).append(key[0] + ':' + key[1])
for (mask, val), names in sorted(res.items(), key=lambda kv: kv[1][0]):
    print('    {0x%08x, 0x%08x},  // %s' % (mask, val, ' '.join(sorted(set(names)))[:100]))
