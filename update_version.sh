#!/usr/bin/env bash
set -euo pipefail

PLUGIN_MAIN_FILE="src/main/java/nl/hauntedmc/proxyfeatures/ProxyFeatures.java"

usage() {
  echo "Usage: $0 <major|minor|patch>" >&2
}

if [[ $# -ne 1 ]]; then
  usage
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "This script must be run inside a git repository." >&2
  exit 1
fi

bump_type="$1"
if [[ "$bump_type" != "major" && "$bump_type" != "minor" && "$bump_type" != "patch" ]]; then
  usage
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven (mvn) is required but was not found in PATH." >&2
  exit 1
fi

if [[ ! -f pom.xml ]]; then
  echo "pom.xml not found." >&2
  exit 1
fi

if [[ ! -f "$PLUGIN_MAIN_FILE" ]]; then
  echo "$PLUGIN_MAIN_FILE not found." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit or stash changes before bumping version." >&2
  exit 1
fi

current_version="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version | tr -d '\r')"
if [[ ! "$current_version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Project version must be a semantic version like 1.2.3." >&2
  exit 1
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
patch="${BASH_REMATCH[3]}"

current_plugin_version="$(
  sed -nE 's/^[[:space:]]*version = "([0-9]+\.[0-9]+\.[0-9]+)",/\1/p' "$PLUGIN_MAIN_FILE" | head -n 1
)"
if [[ -z "$current_plugin_version" ]]; then
  echo "Could not parse plugin annotation version from $PLUGIN_MAIN_FILE." >&2
  exit 1
fi

if [[ "$current_plugin_version" != "$current_version" ]]; then
  echo "Version mismatch: pom.xml has $current_version but $PLUGIN_MAIN_FILE has $current_plugin_version." >&2
  exit 1
fi

case "$bump_type" in
  major)
    major=$((major + 1))
    minor=0
    patch=0
    ;;
  minor)
    minor=$((minor + 1))
    patch=0
    ;;
  patch)
    patch=$((patch + 1))
    ;;
esac

new_version="${major}.${minor}.${patch}"
new_tag="v${new_version}"

if git rev-parse -q --verify "refs/tags/${new_tag}" >/dev/null 2>&1; then
  echo "Tag ${new_tag} already exists." >&2
  exit 1
fi

echo "New version: $new_tag"

mvn -q versions:set -DnewVersion="$new_version" -DgenerateBackupPoms=false
sed -i -E "s/(version = \")[0-9]+\.[0-9]+\.[0-9]+(\",)/\1${new_version}\2/" "$PLUGIN_MAIN_FILE"

git add pom.xml "$PLUGIN_MAIN_FILE"
git commit -m "Bump version to $new_tag for release"
git tag "$new_tag"

echo "Version updated locally. Push the branch and tag when ready:"
echo "  git push && git push origin $new_tag"
