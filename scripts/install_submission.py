#!/usr/bin/env python3
"""Install only Splc.g4 and src/main/java/impl/**/*.java. Reject unsafe ZIP members."""
from pathlib import Path, PurePosixPath
from zipfile import ZipFile, BadZipFile
import shutil,sys
if len(sys.argv)!=3:sys.exit('Usage: install_submission.py <submission.zip> <framework-root>')
archive=Path(sys.argv[1]).resolve();root=Path(sys.argv[2]).resolve()
if not archive.is_file():sys.exit(f'Missing submission: {archive}')
selected={}
with ZipFile(archive) as z:
    for item in z.infolist():
        if item.is_dir():continue
        name=item.filename; p=PurePosixPath(name)
        is_java=(len(p.parts)>=5 and p.parts[:4]==('src','main','java','impl') and p.suffix=='.java')
        if name!='Splc.g4' and not is_java:sys.exit(f'Unexpected submission member: {name}')
        if p.is_absolute() or '..' in p.parts or '\\' in name or name in selected:sys.exit(f'Unsafe/duplicate member: {name}')
        if item.file_size>1024*1024:sys.exit(f'Submitted file too large: {name}')
        selected[name]=item
    if 'Splc.g4' not in selected or 'src/main/java/impl/IRGenerator.java' not in selected:sys.exit('Missing student grammar or IRGenerator.java')
    if len(selected)>65 or sum(i.file_size for i in selected.values())>4*1024*1024:sys.exit('Submission source size/count exceeded')
    # Read every member before changing the framework; corruption fails atomically.
    contents={name:z.read(item) for name,item in selected.items()}
    impl=root/'src/main/java/impl'
    if impl.is_dir():shutil.rmtree(impl)
    for name,data in contents.items():
        path=root.joinpath(*PurePosixPath(name).parts)
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
print(f'[INSTALL] Student Splc.g4 + {len(selected)-1} Java file(s); fixed files preserved')
