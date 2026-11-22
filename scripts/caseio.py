#!/usr/bin/env python3
"""CS323 P5 readable line-delimited testcase files.

Input file:
  CASES N
  === SOURCE ===
  <SPL source>
  === INPUT 1 ===
  <stdin 1>
  ...
  === END ===

Expected / actual output:
  CASES N
  === OUTPUT 1 ===
  <stdout 1>
  ...
  === END ===

Payloads are UTF-8, LF-only; a nonempty payload must end with LF.
All delimiter lines are reserved and cannot appear as payload lines.
"""
from pathlib import Path
import re

MAX_CASES = 100
MAX_SOURCE = 1024 * 1024
MAX_INPUT = 512 * 1024
MAX_OUTPUT = 1024 * 1024
MARKER = re.compile(rb"=== (?:SOURCE|INPUT [0-9]+|OUTPUT [0-9]+|END|ERROR [A-Z]+) ===")


def _valid_payload(payload: bytes, cap: int, name: str):
    if len(payload) > cap:
        raise ValueError(f"{name} is too large")
    if b'\r' in payload:
        raise ValueError(f"{name} must use LF newlines")
    payload.decode('utf-8')
    if payload and not payload.endswith(b'\n'):
        raise ValueError(f"{name} must end with LF")
    if any(MARKER.fullmatch(line) for line in payload.split(b'\n')):
        raise ValueError(f"{name} contains a reserved delimiter line")


def _pack_sections(kind: str, prefix: bytes | None, payloads: list[bytes]) -> bytes:
    if not (1 <= len(payloads) <= MAX_CASES):
        raise ValueError('CASES must be in [1, 100]')
    if prefix is not None:
        _valid_payload(prefix, MAX_SOURCE, 'SPL source')
    chunks = [f'CASES {len(payloads)}\n'.encode('ascii')]
    if prefix is not None:
        chunks += [b'=== SOURCE ===\n', prefix]
    cap = MAX_INPUT if kind == 'INPUT' else MAX_OUTPUT
    for i, payload in enumerate(payloads, 1):
        _valid_payload(payload, cap, f'{kind} {i}')
        chunks += [f'=== {kind} {i} ===\n'.encode('ascii'), payload]
    chunks.append(b'=== END ===\n')
    return b''.join(chunks)


def pack(source: bytes, inputs: list[bytes]) -> bytes:
    return _pack_sections('INPUT', source, inputs)


def pack_output(outputs: list[bytes]) -> bytes:
    return _pack_sections('OUTPUT', None, outputs)


def _unpack(data: bytes, kind: str) -> tuple[bytes | None, list[bytes]]:
    if len(data) > MAX_SOURCE + MAX_CASES * (max(MAX_INPUT,MAX_OUTPUT) + 50):
        raise ValueError('test file too large')
    if b'\r' in data:
        raise ValueError('test file must use LF newlines')
    text=data.decode('utf-8')
    if not text.endswith('\n'):
        raise ValueError('test file missing final newline')
    lines=text.splitlines(keepends=True)
    idx=0
    def next_line():
        nonlocal idx
        if idx == len(lines):
            raise ValueError('unexpected end of test file')
        result=lines[idx]; idx+=1
        return result
    def payload_until(marker: str, cap: int) -> bytes:
        nonlocal idx
        content=[]
        while True:
            if idx >= len(lines):
                raise ValueError(f'missing marker {marker!r}')
            line=next_line()
            if line == marker+'\n':
                raw=''.join(content).encode('utf-8')
                _valid_payload(raw,cap,'payload')
                return raw
            if MARKER.fullmatch(line[:-1].encode('utf-8')):
                raise ValueError(f'unexpected marker before {marker}: {line.strip()}')
            content.append(line)
    head=next_line()
    m=re.fullmatch(r'CASES ([1-9][0-9]*)\n', head)
    if not m:
        raise ValueError('expected CASES N')
    count=int(m.group(1))
    if not 1 <= count <= MAX_CASES:
        raise ValueError('case count out of range')
    source=None
    if kind=='INPUT':
        if next_line() != '=== SOURCE ===\n':
            raise ValueError('missing SOURCE marker')
        source=payload_until('=== INPUT 1 ===', MAX_SOURCE)
    else:
        if next_line() != '=== OUTPUT 1 ===\n':
            raise ValueError('missing first OUTPUT marker')
    output=[]
    for i in range(1,count+1):
        following=f'=== {kind} {i+1} ===' if i<count else '=== END ==='
        output.append(payload_until(following, MAX_INPUT if kind=='INPUT' else MAX_OUTPUT))
    if idx != len(lines):
        raise ValueError('trailing bytes after END')
    return source, output


def unpack(data: bytes) -> tuple[bytes, list[bytes]]:
    source, inputs=_unpack(data,'INPUT')
    assert source is not None
    return source,inputs


def unpack_output(data: bytes) -> list[bytes]:
    _,outputs=_unpack(data,'OUTPUT')
    return outputs


def source_path(root: Path, n: str):
    return root/'reference/spl'/f'{n}.spl'

if __name__=='__main__':
    import sys
    if len(sys.argv)<3:
        raise SystemExit('Usage: caseio.py source.spl stdin1.txt [...]')
    sys.stdout.buffer.write(pack(Path(sys.argv[1]).read_bytes(), [Path(p).read_bytes() for p in sys.argv[2:]]))
