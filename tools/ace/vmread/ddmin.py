import subprocess, sys, re, os
def rng(S):
    S = sorted(S); out = []; i = 0
    while i < len(S):
        j = i
        while j + 1 < len(S) and S[j + 1] == S[j] + 1: j += 1
        out.append('%d-%d' % (S[i], S[j])); i = j + 1
    return ','.join(out)
def test(S):
    spec = rng(S); tag = 'dd%d' % abs(hash(spec))
    env = dict(os.environ, PROBE='asl:' + spec, RUNMS='30000', TS='30')
    r = subprocess.run(['flock', '-n', os.path.expanduser('~/pgr-run.lock'), 'bash', 'run-aws-v.sh', 'base', tag], capture_output=True, text=True, env=env, cwd=os.path.expanduser('~/pgr-vmread'))
    ok = 'storm_lines=0' not in r.stdout
    print('test', spec[:80], len(S), 'STORM' if ok else 'ok', flush=True)
    return ok
S = list(range(int(sys.argv[1]), int(sys.argv[2])))
assert test(S)
n = 2
while len(S) >= 2:
    chunk = max(1, len(S) // n); subsets = [S[i:i + chunk] for i in range(0, len(S), chunk)]; reduced = False
    for sub in subsets:
        comp = [x for x in S if x not in sub]
        if comp and test(comp): S = comp; n = max(n - 1, 2); reduced = True; break
    if not reduced:
        if chunk == 1: break
        n = min(len(S), n * 2)
print('MINIMAL', rng(S))
