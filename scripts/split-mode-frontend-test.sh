#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/split-mode-common.sh"

if [[ $# -eq 0 ]]; then
    set -- test
fi

echo "Frontend plugin test run"
echo "  JAVA_HOME: $JAVA_HOME"
echo "  GRADLE_USER_HOME: $GRADLE_USER_HOME"
echo "  tasks: $*"

run_frontend_gradle "$@"
