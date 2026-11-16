# Agent Q: parses the S lines of ace-q5.bt (ACE net thread packets): the 237/232-byte report headers and the 133-byte device blob (identical on native runs).
import re,sys
def parse(s):
    out=bytearray(); i=0
    while i<len(s):
        if s[i]=='\\' and i+1<len(s):
            c=s[i+1]
            if c=='x': out.append(int(s[i+2:i+4],16)); i+=4; continue
            m={'n':10,'r':13,'t':9,'\\':92,'"':34}
            if c in m: out.append(m[c]); i+=2; continue
        out.append(ord(s[i])); i+=1
    return bytes(out)
for f in sys.argv[1:]:
    for l in open(f,errors='replace'):
        p=l.rstrip('\n').split(' ',4)
        if len(p)>=5 and p[1]=='S' and p[3] in('237','232'):
            b=parse(p[4]); 
            print(f.split('extra-')[-1], p[0], p[3], len(b), 'id=',b[0x5f:0x5f+26], 'blob@' )
            # blob starts after TLV 01 0a 00 09 ... find marker 00 01 00 00 then blocks
            k=b.find(bytes([0x84,0,0,0,1,0,0x84,1,0x0a]))
            print('  hdr', b[:24].hex(), 'k', k, 'tail', len(b)-k)
            t=b[k:] ; print('  ', t.hex())
