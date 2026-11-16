import sys,re,collections
def load(p):
    d={}
    for l in open(p):
        f=l.rstrip('\n').split('|',2)
        if len(f)<3: continue
        d.setdefault((f[0],f[1]),f[2])
    return d
def norm(op,v):
    m=re.match(r'\w*-?\w*\((\d+)\)$',op)
    if op=='open' or op.startswith('open('):
        return re.sub(r'blocks=\d+','blocks=*',re.sub(r'size=(\d+)',lambda m:'size='+('0' if m.group(1)=='0' else 'N'),v))
    p=v.split('|')
    if m and p[0].lstrip('-').isdigit() and op.split('(')[0] in('read','pread0','svc-read','svc-pread0'):
        n=int(m.group(1)); r=int(p[0]); c='n' if r==n else '0' if r==0 else 'neg' if r<0 else 'short'
        return '|'.join([c]+p[1:])
    if op.startswith(('loop4096','read(1M)','read(st_size)','readv','preadv','fgets','fread','sendfile','splice','copy_file','reopen','FIONREAD')):
        v=re.sub(r'(?<![\w=])\d{2,}(?![\w=])','N',v); v=re.sub(r'(calls|first|max|end|read|st_size|out_size|n)=\d+',r'\1=N',v); return v
    return v
a,b=load(sys.argv[1]),load(sys.argv[2]); names=collections.defaultdict(list)
for k in a:
    if k not in b: names[k[0]].append((k[1],a[k],'<missing>')); continue
    x,y=norm(k[1],a[k]),norm(k[1],b[k])
    if x!=y: names[k[0]].append((k[1],a[k],b[k]))
for f,l in names.items():
    print('##',f,len(l))
    for o,x,y in l[:int(sys.argv[3]) if len(sys.argv)>3 else 6]: print('  ',o,'| real:',x,'| other:',y)
