#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend-plugin"

DEFAULT_JBR_HOME="/Applications/PyCharm CE.app/Contents/jbr/Contents/Home"
export JAVA_HOME="${JAVA_HOME:-$DEFAULT_JBR_HOME}"
export PATH="$JAVA_HOME/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO_ROOT/.gradle-local/split-mode}"

CLIENT_PROFILES_ROOT="${CLIENT_PROFILES_ROOT:-$HOME/Library/Application Support/JetBrains}"

ensure_java_home() {
    if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
        echo "JAVA_HOME is not usable: $JAVA_HOME" >&2
        echo "Set JAVA_HOME to a JetBrains Runtime, for example:" >&2
        echo "  export JAVA_HOME=\"$DEFAULT_JBR_HOME\"" >&2
        exit 1
    fi
}

ensure_gradle_home() {
    mkdir -p "$GRADLE_USER_HOME"
}

list_client_profiles() {
    if [[ ! -d "$CLIENT_PROFILES_ROOT" ]]; then
        return 0
    fi

    find "$CLIENT_PROFILES_ROOT" -maxdepth 1 -mindepth 1 -type d -name 'JetBrainsClient*' | sort
}

run_frontend_gradle() {
    ensure_java_home
    ensure_gradle_home
    (
        cd "$FRONTEND_DIR"
        ./gradlew --no-daemon "$@"
    )
}

run_root_gradle() {
    ensure_java_home
    ensure_gradle_home
    (
        cd "$REPO_ROOT"
        ./gradlew --no-daemon "$@"
    )
}
