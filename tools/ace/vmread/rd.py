import struct,glob,sys
def load(d):
    base={}
    for p in sorted(glob.glob(d+'/maps-*.txt')):
        b=None
        for l in open(p):
            if 'libanogs.so' in l and int(l.split()[2],16)==0:
                a=int(l.split('-')[0],16); b=a if b is None else min(b,a)
        if b: base[int(p.split('-')[-2])]=b
    recs=[];names={};B=min(base.values())
    for p in sorted(glob.glob(d+'/gate-*.bin')):
        pid=int(p.split('-')[-2]); B=base.get(pid) or B
        data=open(p,'rb').read()
        for i in range(0,len(data)-127,128):
            ts,kind,tid,x0,x1,x2,x3,x30,x29=struct.unpack_from('<QIIQQQQQQ',data,i); mem=data[i+64:i+128]
            recs.append((ts/1e9,kind,tid,x0,x1-B,x2,x3,x30-B,x29,mem))
    return recs,B
if __name__=='__main__':
    recs,B=load(sys.argv[1]); pc=int(sys.argv[2],16)
    E=[(i,r) for i,r in enumerate(recs) if r[1]==0 and r[6]==pc]
    print(len(recs),hex(B),len(E))
    for i,r in E[:5]: print(i,r[0],hex(r[4]),hex(r[2]))
