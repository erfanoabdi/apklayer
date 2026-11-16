# Line classes of two /proc/self/maps texts (addresses and numbers normalised): maps-classes.py <native maps> <translated served maps (BERBERIS_DUMP_MAPS)>
import re,sys,collections
def cls(l):
    p=l.split(None,5)
    perms=p[1]; off=p[2]; dev=p[3]; ino=p[4]; name=p[5].strip() if len(p)>5 else ''
    n=re.sub(r'/data/(app|data|user)[^ ]*/', r'/data/\1/<..>/', name) 
    n=re.sub(r'\d+','N',n)
    n=re.sub(r'(/home/[^/]+)/.*/([^/]*)$', r'\1/../\2', n)
    if not name: n='<anon>'
    return (perms, n)
def load(p): return collections.Counter(cls(l) for l in open(p) if l.strip())
N=load(sys.argv[1]); T=load(sys.argv[2])
print('native',sum(N.values()),'translated',sum(T.values()))
rows=[]
for k in set(N)|set(T):
    rows.append((T[k]-N[k],k,N[k],T[k]))
rows.sort(key=lambda r:-abs(r[0]))
for d,k,n,t in rows[:45]: print('%+6d  native %5d  trans %5d  %s %s'%(d,n,t,k[0],k[1][:90]))
