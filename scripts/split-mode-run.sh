#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/split-mode-common.sh"

if [[ $# -ne 1 ]]; then
    echo "Usage: bash scripts/split-mode-run.sh /absolute/path/to/project" >&2
    exit 1
fi

PROJECT_PATH="$1"
if [[ ! -d "$PROJECT_PATH" ]]; then
    echo "Project path does not exist: $PROJECT_PATH" >&2
    exit 1
fi

echo "Launching split mode"
echo "  project: $PROJECT_PATH"
echo "  JAVA_HOME: $JAVA_HOME"
echo "  GRADLE_USER_HOME: $GRADLE_USER_HOME"

run_root_gradle runIdeSplitMode "-PsplitModeProjectPath=$PROJECT_PATH"
