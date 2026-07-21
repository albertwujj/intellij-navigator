#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/split-mode-common.sh"

echo "Split-mode bootstrap"
echo "  repo: $REPO_ROOT"
echo "  JAVA_HOME: $JAVA_HOME"
echo "  GRADLE_USER_HOME: $GRADLE_USER_HOME"
echo
echo "Warming shared Gradle state..."
run_frontend_gradle help >/dev/null
run_root_gradle help >/dev/null

profiles="$(list_client_profiles || true)"
if [[ -z "$profiles" ]]; then
    cat <<EOF

No JetBrains Client profile exists yet.

One-time next step:
  bash scripts/split-mode-run.sh /absolute/path/to/project

The first launch creates the JetBrains Client profile. After that, install the frontend plugin with:
  bash scripts/split-mode-refresh-frontend.sh

EOF
    exit 0
fi

echo
echo "JetBrains Client profiles:"
while IFS= read -r profile; do
    [[ -n "$profile" ]] && echo "  $profile"
done <<< "$profiles"

cat <<EOF

Bootstrap finished.

Repeat after frontend code changes:
  bash scripts/split-mode-frontend-test.sh
  bash scripts/split-mode-refresh-frontend.sh
  bash scripts/split-mode-run.sh /absolute/path/to/project

Optional one-time local preference:
  See LOCAL_SPLIT_MODE.md for disabling JetBrains Client language packs to keep menus in English.

EOF
