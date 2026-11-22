#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from pathlib import PurePosixPath
root=Path('src/main/java/impl')
files=sorted(p for p in root.rglob('*.java') if p.is_file())
if not Path('Splc.g4').is_file() or root/'IRGenerator.java' not in files:
    raise SystemExit('Missing Splc.g4 or src/main/java/impl/IRGenerator.java')
with ZipFile('submission.zip','w',ZIP_DEFLATED) as zip:
    zip.write('Splc.g4')
    for path in files:zip.write(path,path.as_posix())
print(f'[HANDIN] submission.zip: Splc.g4 + {len(files)} Java file(s)')
PY
