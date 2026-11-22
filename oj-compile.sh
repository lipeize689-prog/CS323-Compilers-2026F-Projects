#!/usr/bin/env bash
# CS323 Project 5. Compatible with the already deployed cs323project language:
#   ./oj-compile.sh submission.zip /usr/local/share/antlr/antlr.jar
# Target artifact: project.jar. No Hydro language configuration changes.
set -euo pipefail
cd "$(dirname "$(realpath "$0")")"

case "$#" in
    0)
        # Local development: compile the sources currently in this directory.
        submission=""
        antlr_jar="${ANTLR_JAR:-/usr/local/share/antlr/antlr.jar}"
        ;;
    1)
        # Convenience for local testing of a handin ZIP.
        submission="$1"
        antlr_jar="${ANTLR_JAR:-/usr/local/share/antlr/antlr.jar}"
        ;;
    2)
        # Fixed, deployed Hydro cs323project language interface.
        submission="$1"
        antlr_jar="$2"
        ;;
    *)
        echo 'Usage: ./oj-compile.sh [submission.zip [antlr.jar]]' >&2
        exit 2
        ;;
esac

if [[ -n "$submission" ]]; then
    if [[ ! -f "$submission" ]]; then
        echo "[P5] ERROR: missing submission archive: $submission" >&2
        exit 2
    fi
    python3 scripts/install_submission.py "$submission" "$PWD"
fi

if [[ ! -f "$antlr_jar" ]]; then
    echo "[P5] ERROR: ANTLR complete JAR not found: $antlr_jar" >&2
    exit 2
fi
make build ANTLR_JAR="$antlr_jar"
if [[ ! -f target/project.jar ]]; then
    echo '[P5] ERROR: make build did not produce target/project.jar' >&2
    exit 2
fi
cp target/project.jar project.jar
printf '[P5] OK: project.jar (%s bytes)\n' "$(wc -c < project.jar)"
