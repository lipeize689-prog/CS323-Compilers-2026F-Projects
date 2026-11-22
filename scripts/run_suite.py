#!/usr/bin/env python3
"""Run local public/full tests and score each independent execution."""
import argparse, os, subprocess, sys
from pathlib import Path
from caseio import unpack_output
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--scope',choices=['public','all'],required=True)
p.add_argument('--examples',action='store_true')
args=p.parse_args()
jar=os.environ.get('ANTLR_JAR','/usr/local/share/antlr/antlr.jar')
cmd=['java','-Xmx128m','-cp',f'{root}/target/project.jar:{jar}','Main']
passed=0;total=0
if args.examples:
    for source in sorted((root/'examples').glob('*.spl')):
        inp=(root/'examples'/f'{source.stem}.stdin').read_bytes()
        expected=(root/'examples'/f'{source.stem}.stdout').read_bytes()
        try:
            proc=subprocess.run(cmd+['--run-spl',str(source)],input=inp,capture_output=True,timeout=35,cwd=root)
            good=proc.returncode==0 and proc.stdout==expected
            detail=proc.stderr.decode('utf-8','replace')[:200].strip()
        except subprocess.TimeoutExpired:
            good=False; detail='timeout'
        print(f'[{"PASS" if good else "FAIL"}] example {source.stem}' + (f' :: {detail}' if not good and detail else ''))
        total+=1;passed+=int(good)
ids=['001','002','005'] + (['003','004','006','007','008','009','010'] if args.scope=='all' else [])
for n in ids:
    part=root/'testcases/project5'/('' if n in ('001','002','005') else 'hidden')
    inp=part/'in'/f'{n}.in'; out=part/'out'/f'{n}.out'
    if not inp.is_file() or not out.is_file():
        print(f'[MISSING] official {n}',file=sys.stderr)
        total+=10;continue
    expected=unpack_output(out.read_bytes())
    try:
        # Allow 10 runs of up to 5 seconds, plus LLVM compilation and JVM startup.
        proc=subprocess.run(cmd,input=inp.read_bytes(),capture_output=True,timeout=90,cwd=root)
        actual=unpack_output(proc.stdout) if proc.returncode==0 else []
        ok=sum(a==e for a,e in zip(actual,expected)) if len(actual)==len(expected) else 0
        detail=proc.stderr.decode('utf-8','replace')[:200].strip()
    except (subprocess.TimeoutExpired,ValueError,UnicodeDecodeError) as e:
        ok=0;detail=str(e)[:200]
    total+=len(expected);passed+=ok
    print(f'[{"PASS" if ok==len(expected) else "FAIL"}] official {n}: {ok}/{len(expected)}' + (f' :: {detail}' if ok<len(expected) and detail else ''))
print(f'Local score: {passed}/{total}')
sys.exit(0 if passed==total else 1)
