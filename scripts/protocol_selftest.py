#!/usr/bin/env python3
"""Round-trip, malformed-input and delimiter-collision tests."""
from caseio import pack, unpack, pack_output, unpack_output
cases=[
  (b'int main0(){return 0;}\n', [b'',b'12\n']),
  ('// 中文\nint main0() { return 0; }\n'.encode('utf-8'), [b'\n\n', b'  1  2\n']),
  (b'int main0(){write(1);return 0;}\n', [b'-1\n',b'0\n']),
]
for source,inputs in cases:
    raw=pack(source,inputs)
    assert unpack(raw)==(source,inputs)
    for extra in (b'EXTRA', b'\n'):
        try:unpack(raw+extra)
        except ValueError:pass
        else:raise AssertionError('accepted trailing bytes')
for outs in ([b'10\n',b'20\n'], [b'',b'0\n'], [b'1\n2\n',b'-5\n']):
    assert unpack_output(pack_output(outs))==outs
bad=[(b'int main0(){return 0;}\n', [b'=== INPUT 2 ===\n']),
     (b'=== INPUT 1 ===\n', [b'1\n']),
     (b'int main0(){}\n', [b'hello'])]
for src,inp in bad:
    try:pack(src,inp)
    except ValueError:pass
    else:raise AssertionError('accepted invalid payload')
print('[PASS] delimiter-based CASES N protocol: UTF-8, multiline stdout, empty stdin, reserved markers')
