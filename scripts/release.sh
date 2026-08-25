#!/bin/bash
set -euo pipefail

# Release both JetBrains plugins from their owning repository.
# Usage:
#   ./scripts/release.sh          — test, build, tag, push, and publish
#   ./scripts/release.sh --check  — test, build, and validate without publishing
# Requires: gh CLI, Git, a suitable JDK, and unzip

REPO="albertwujj/intellij-navigator"
cd "$(git rev-parse --show-toplevel)"

die() {
  echo "Error: $*" >&2
  exit 1
}

case "${1:-}" in
  --check|"")
    ;;
  *)
    echo "Usage: ./scripts/release.sh [--check]" >&2
    exit 1
    ;;
esac

for command_name in git gh java unzip; do
  command -v "$command_name" >/dev/null 2>&1 || die "Required command not found: $command_name"
done

[ "$(git branch --show-current)" = "main" ] || die "Releases must be created from main."
[ -z "$(git status --porcelain)" ] || die "The worktree must be clean before releasing."

echo "Checking origin/main..."
git fetch origin main --quiet
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] \
  || die "Local main must exactly match origin/main before releasing."

read_plugin_version() {
  local properties_file="$1"
  awk -F= '$1 ~ /^[[:space:]]*pluginVersion[[:space:]]*$/ { gsub(/[[:space:]]/, "", $2); print $2 }' "$properties_file"
}

BACKEND_VERSION=$(read_plugin_version gradle.properties)
FRONTEND_VERSION=$(read_plugin_version frontend-plugin/gradle.properties)

[ -n "$BACKEND_VERSION" ] || die "Backend pluginVersion is missing."
[ "$BACKEND_VERSION" = "$FRONTEND_VERSION" ] \
  || die "Plugin versions must match (backend ${BACKEND_VERSION}, frontend ${FRONTEND_VERSION})."
[[ "$BACKEND_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
  || die "Plugin version is not a simple semantic version: $BACKEND_VERSION"

TAG="v${BACKEND_VERSION}"
BACKEND_ZIP="build/distributions/intellij-navigator-${BACKEND_VERSION}.zip"
FRONTEND_ZIP="frontend-plugin/build/distributions/intellij-navigator-frontend-${FRONTEND_VERSION}.zip"

git rev-parse --verify --quiet "refs/tags/${TAG}" >/dev/null \
  && die "Local tag already exists: ${TAG}"
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  die "GitHub release already exists: ${TAG}"
fi

echo "Testing and building backend plugin ${BACKEND_VERSION}..."
./gradlew test buildPlugin

echo "Testing and building frontend plugin ${FRONTEND_VERSION}..."
(
  cd frontend-plugin
  ./gradlew test buildPlugin
)

[ -f "$BACKEND_ZIP" ] || die "Backend plugin ZIP was not produced: $BACKEND_ZIP"
[ -f "$FRONTEND_ZIP" ] || die "Frontend plugin ZIP was not produced: $FRONTEND_ZIP"
unzip -tq "$BACKEND_ZIP" >/dev/null
unzip -tq "$FRONTEND_ZIP" >/dev/null
[ -z "$(git status --porcelain)" ] || die "Release checks modified the worktree."

echo "Validated plugin assets:"
echo "  $(basename "$BACKEND_ZIP")"
echo "  $(basename "$FRONTEND_ZIP")"

if [ "${1:-}" = "--check" ]; then
  echo ""
  echo "✅ Release checks passed for ${TAG}."
  exit 0
fi

git tag "$TAG"
git push origin main "$TAG"

RELEASE_NOTES="## Downloads

Install both plugin ZIPs from this release:

- **$(basename "$BACKEND_ZIP")** — Host/backend plugin for file and symbol resolution
- **$(basename "$FRONTEND_ZIP")** — Client/frontend plugin for editor scrolling, caret reporting, and the read-only editor guard

## Installation

For JetBrains Remote Development, install the backend ZIP on the **Host** and the frontend ZIP on the **Client**. For a local IDE, install both ZIPs into the same IDE through **Settings → Plugins → ⚙ → Install Plugin from Disk**.

See the [installation guide](https://github.com/${REPO}/blob/${TAG}/INSTALL.md) for details."

echo "Creating plugin release ${TAG}..."
gh release create "$TAG" \
  --repo "$REPO" \
  --verify-tag \
  --title "IntelliJ Navigator Plugins ${TAG}" \
  --notes "$RELEASE_NOTES" \
  --latest \
  "$BACKEND_ZIP" \
  "$FRONTEND_ZIP"

echo ""
echo "✅ ${TAG}: https://github.com/${REPO}/releases/tag/${TAG}"
