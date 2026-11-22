#!/usr/bin/env python3
from pathlib import Path
from caseio import pack
import os,sys
if len(sys.argv)<3:sys.exit('Usage: python3 scripts/create_case.py source.spl stdin1.txt [...] > case.in')
os.write(1,pack(Path(sys.argv[1]).read_bytes(),[Path(n).read_bytes() for n in sys.argv[2:]]))
