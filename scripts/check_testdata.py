#!/usr/bin/env python3
"""Check public/hidden split, readable case framing and independent runs."""
from pathlib import Path
from caseio import unpack, unpack_output, pack, pack_output
root=Path(__file__).resolve().parents[1]
teacher=(root/'reference').is_dir()
public=['001','002','005']; hidden=['003','004','006','007','008','009','010']
ids=public+hidden if teacher else public
for n in ids:
    part=root/'testcases/project5'/('' if n in public else 'hidden')
    a=part/'in'/f'{n}.in'; b=part/'out'/f'{n}.out'
    assert a.is_file() and b.is_file(), f'missing case {n}'
    source,inputs=unpack(a.read_bytes())
    outputs=unpack_output(b.read_bytes())
    assert source.strip() and len(inputs)==len(outputs)==10
    assert pack(source,inputs)==a.read_bytes(),f'noncanonical input {n}'
    assert pack_output(outputs)==b.read_bytes(), f'noncanonical output {n}'
    if teacher:
        assert source==(root/'reference/spl'/f'{n}.spl').read_bytes()
        for i,inp in enumerate(inputs,1):
            assert inp==(root/'reference/stdin'/n/f'{i:02}.txt').read_bytes()
    print(f'[DATA] {n} {"public" if n in public else "hidden"}: 10/10, readable SPL/stdin/stdout')
if not teacher:
    assert not (root/'testcases/project5/hidden').exists(),'hidden cases leaked to starter'
    assert not (root/'reference').exists(),'teacher reference leaked to starter'
print(f'[PASS] {len(ids)} SPL programs / {10*len(ids)} runs checked')
