#!/usr/bin/env bash

set -euo pipefail

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

confirm_yes_no() {
  local prompt="$1"
  local default_answer="${2:-n}"
  local answer=""
  local suffix="[y/N]"

  if [[ "$default_answer" == "y" ]]; then
    suffix="[Y/n]"
  fi

  while true; do
    read -r -p "$prompt $suffix " answer || exit 1
    if [[ -z "$answer" ]]; then
      answer="$default_answer"
    fi

    case "$answer" in
      y|Y|yes|YES|Yes)
        return 0
        ;;
      n|N|no|NO|No)
        return 1
        ;;
      *)
        echo "Please enter y or n."
        ;;
    esac
  done
}

read_gradle_version_name() {
  awk -F= '
    $1 == "application.version.name" {
      value = $2
      sub(/\r$/, "", value)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      print value
      exit
    }
  ' "$1"
}

create_note_template() {
  local note_file="$1"
  local tag_name="$2"
  local today="$3"

  mkdir -p "$(dirname "$note_file")"

  if [[ -f "$note_file" ]]; then
    echo "Release note already exists, keeping current file: ${note_file#$REPO_ROOT/}"
    return 0
  fi

  cat >"$note_file" <<EOF
发布日期：$today

## 新特性
- 

## 修复
- 
EOF

  echo "Created release note template: ${note_file#$REPO_ROOT/}"
}

require_command git
require_command awk
require_command date

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null || true)"

if [[ -z "$REPO_ROOT" ]]; then
  echo "This script must be run inside a Git repository." >&2
  exit 1
fi

cd "$REPO_ROOT"

GRADLE_FILE="$REPO_ROOT/gradle.properties"

if [[ ! -f "$GRADLE_FILE" ]]; then
  echo "gradle.properties not found: $GRADLE_FILE" >&2
  exit 1
fi

VERSION_NAME="$(read_gradle_version_name "$GRADLE_FILE")"
if [[ -z "$VERSION_NAME" ]]; then
  echo "Failed to read application.version.name from gradle.properties." >&2
  exit 1
fi

TAG_NAME="v$VERSION_NAME"
NOTE_FILE_REL="docs/release/note/$TAG_NAME.md"
NOTE_FILE="$REPO_ROOT/$NOTE_FILE_REL"
TODAY="$(date +%F)"

if ! confirm_yes_no "Release version $TAG_NAME?" "n"; then
  echo "Release cancelled."
  exit 0
fi

CURRENT_BRANCH="$(git branch --show-current)"
if [[ -z "$CURRENT_BRANCH" ]]; then
  echo "Detached HEAD detected. Cannot push the current branch automatically." >&2
  exit 1
fi

UPSTREAM_REF="$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null || true)"
REMOTE_NAME="origin"
REMOTE_BRANCH="$CURRENT_BRANCH"

if [[ -n "$UPSTREAM_REF" ]]; then
  REMOTE_NAME="${UPSTREAM_REF%%/*}"
  REMOTE_BRANCH="${UPSTREAM_REF#*/}"
fi

if ! git remote get-url "$REMOTE_NAME" >/dev/null 2>&1; then
  echo "Remote not found: $REMOTE_NAME" >&2
  exit 1
fi

if git rev-parse -q --verify "refs/tags/$TAG_NAME" >/dev/null 2>&1; then
  echo "Local tag already exists: $TAG_NAME" >&2
  exit 1
fi

create_note_template "$NOTE_FILE" "$TAG_NAME" "$TODAY"

echo "Edit $NOTE_FILE_REL and fill in the release notes."
while true; do
  read -r -p "Enter y to continue after editing, or n to cancel this release: " answer || exit 1
  case "$answer" in
    y|Y)
      break
      ;;
    n|N)
      echo "Release cancelled."
      exit 0
      ;;
    *)
      echo "Please enter y or n."
      ;;
  esac
done

CURRENT_VERSION_NAME="$(read_gradle_version_name "$GRADLE_FILE")"
CURRENT_TAG_NAME="v$CURRENT_VERSION_NAME"
if [[ "$CURRENT_TAG_NAME" != "$TAG_NAME" ]]; then
  echo "gradle.properties version changed to $CURRENT_TAG_NAME. Re-run the script." >&2
  exit 1
fi

if [[ ! -f "$NOTE_FILE" ]]; then
  echo "Release note file not found: $NOTE_FILE_REL" >&2
  exit 1
fi

GRADLE_IS_DIRTY=0
if [[ -n "$(git status --porcelain -- gradle.properties)" ]]; then
  GRADLE_IS_DIRTY=1
fi

echo
echo "About to release:"
echo "  Version: $TAG_NAME"
echo "  Release note: $NOTE_FILE_REL"
if [[ "$GRADLE_IS_DIRTY" -eq 1 ]]; then
  echo "  Extra file: gradle.properties"
else
  echo "  Extra file: no local gradle.properties changes"
fi
echo "  Commit: chore(release): prepare $TAG_NAME"
echo "  Push target: $REMOTE_NAME/$REMOTE_BRANCH"
echo

if ! confirm_yes_no "Commit and push this release?" "n"; then
  echo "Release cancelled. No commit or tag was created."
  exit 0
fi

REMOTE_TAG_OUTPUT=""
if ! REMOTE_TAG_OUTPUT="$(git ls-remote --tags "$REMOTE_NAME" "refs/tags/$TAG_NAME" 2>/dev/null)"; then
  echo "Failed to query the remote tag. Check network access and repository permissions." >&2
  exit 1
fi

if [[ -n "$REMOTE_TAG_OUTPUT" ]]; then
  echo "Remote tag already exists: $TAG_NAME" >&2
  exit 1
fi

git add -- "$NOTE_FILE_REL"

COMMIT_PATHS=("$NOTE_FILE_REL")
if [[ "$GRADLE_IS_DIRTY" -eq 1 ]]; then
  git add -- "gradle.properties"
  COMMIT_PATHS+=("gradle.properties")
fi

if git diff --cached --quiet -- "${COMMIT_PATHS[@]}"; then
  echo "No release changes to commit. Edit $NOTE_FILE_REL or update gradle.properties." >&2
  exit 1
fi

COMMIT_MESSAGE="chore(release): prepare $TAG_NAME"
git commit --only -m "$COMMIT_MESSAGE" -- "${COMMIT_PATHS[@]}"
git tag -a "$TAG_NAME" -m "Release $TAG_NAME"
git push "$REMOTE_NAME" "HEAD:$REMOTE_BRANCH"
git push "$REMOTE_NAME" "refs/tags/$TAG_NAME"

echo
echo "Release preparation completed:"
echo "  Commit: $COMMIT_MESSAGE"
echo "  Tag: $TAG_NAME"
echo "  Pushed to: $REMOTE_NAME/$REMOTE_BRANCH"
