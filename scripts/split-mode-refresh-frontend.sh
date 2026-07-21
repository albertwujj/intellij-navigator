#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/split-mode-common.sh"

echo "Refreshing frontend plugin for split mode"
echo "  JAVA_HOME: $JAVA_HOME"
echo "  GRADLE_USER_HOME: $GRADLE_USER_HOME"

run_frontend_gradle buildPlugin
run_root_gradle installFrontendPluginForSplitMode

cat <<EOF

Frontend plugin refreshed into the local JetBrains Client profile.
Restart the split-mode client to load the new build.

Next step:
  bash scripts/split-mode-run.sh /absolute/path/to/project

EOF
