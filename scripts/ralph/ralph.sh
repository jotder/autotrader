#!/bin/bash
# Ralph Wiggum - Long-running AI agent loop (Customized for AutoTrader)
# Usage: ./ralph.sh [--tool amp|claude] [max_iterations]
#
# Modifications from upstream (snarktank/ralph):
#   A. Default tool = claude (not amp)
#   B. Post-iteration Maven quality gate (compile + test; revert on failure)
#   C. SemVer + build number auto-increment after successful story
#   D. PROJECT_ROOT for AutoTrader working directory

set -e

# ── Mod D: Project root ──────────────────────────────────────────────
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

# ── Parse arguments ───────────────────────────────────────────────────
TOOL="claude"  # Mod A: default to claude
MAX_ITERATIONS=10
VERSION_PREFIX="v1.0.0"  # SemVer prefix for P1

while [[ $# -gt 0 ]]; do
  case $1 in
    --tool)
      TOOL="$2"
      shift 2
      ;;
    --tool=*)
      TOOL="${1#*=}"
      shift
      ;;
    --version-prefix)
      VERSION_PREFIX="$2"
      shift 2
      ;;
    --version-prefix=*)
      VERSION_PREFIX="${1#*=}"
      shift
      ;;
    *)
      if [[ "$1" =~ ^[0-9]+$ ]]; then
        MAX_ITERATIONS="$1"
      fi
      shift
      ;;
  esac
done

# Validate tool choice
if [[ "$TOOL" != "amp" && "$TOOL" != "claude" ]]; then
  echo "Error: Invalid tool '$TOOL'. Must be 'amp' or 'claude'."
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRD_FILE="$SCRIPT_DIR/prd.json"
PROGRESS_FILE="$SCRIPT_DIR/progress.txt"
ARCHIVE_DIR="$SCRIPT_DIR/archive"
LAST_BRANCH_FILE="$SCRIPT_DIR/.last-branch"
BUILD_FILE="$SCRIPT_DIR/build-number.txt"

# ── Mod C: Initialize build number file ──────────────────────────────
if [ ! -f "$BUILD_FILE" ]; then
  echo "0" > "$BUILD_FILE"
fi

# Archive previous run if branch changed
if [ -f "$PRD_FILE" ] && [ -f "$LAST_BRANCH_FILE" ]; then
  CURRENT_BRANCH=$(jq -r '.branchName // empty' "$PRD_FILE" 2>/dev/null || echo "")
  LAST_BRANCH=$(cat "$LAST_BRANCH_FILE" 2>/dev/null || echo "")

  if [ -n "$CURRENT_BRANCH" ] && [ -n "$LAST_BRANCH" ] && [ "$CURRENT_BRANCH" != "$LAST_BRANCH" ]; then
    DATE=$(date +%Y-%m-%d)
    FOLDER_NAME=$(echo "$LAST_BRANCH" | sed 's|^ralph/||')
    ARCHIVE_FOLDER="$ARCHIVE_DIR/$DATE-$FOLDER_NAME"

    echo "Archiving previous run: $LAST_BRANCH"
    mkdir -p "$ARCHIVE_FOLDER"
    [ -f "$PRD_FILE" ] && cp "$PRD_FILE" "$ARCHIVE_FOLDER/"
    [ -f "$PROGRESS_FILE" ] && cp "$PROGRESS_FILE" "$ARCHIVE_FOLDER/"
    echo "   Archived to: $ARCHIVE_FOLDER"

    echo "# Ralph Progress Log" > "$PROGRESS_FILE"
    echo "Started: $(date)" >> "$PROGRESS_FILE"
    echo "---" >> "$PROGRESS_FILE"
  fi
fi

# Track current branch
if [ -f "$PRD_FILE" ]; then
  CURRENT_BRANCH=$(jq -r '.branchName // empty' "$PRD_FILE" 2>/dev/null || echo "")
  if [ -n "$CURRENT_BRANCH" ]; then
    echo "$CURRENT_BRANCH" > "$LAST_BRANCH_FILE"
  fi
fi

# Initialize progress file if it doesn't exist
if [ ! -f "$PROGRESS_FILE" ]; then
  echo "# Ralph Progress Log" > "$PROGRESS_FILE"
  echo "Started: $(date)" >> "$PROGRESS_FILE"
  echo "---" >> "$PROGRESS_FILE"
fi

# ── Mod C: Capture initial commit hash for change detection ──────────
PREV_COMMIT=$(git log -1 --format="%H" 2>/dev/null || echo "")

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  Ralph — AutoTrader Autonomous Development Loop              ║"
echo "║  Tool: $TOOL | Max iterations: $MAX_ITERATIONS | Version: $VERSION_PREFIX  ║"
echo "║  Build: $(cat "$BUILD_FILE") | Project: $(basename "$PROJECT_ROOT")                    ║"
echo "╚═══════════════════════════════════════════════════════════════╝"

for i in $(seq 1 $MAX_ITERATIONS); do
  echo ""
  echo "==============================================================="
  echo "  Ralph Iteration $i of $MAX_ITERATIONS ($TOOL)"
  echo "  Current build: $(cat "$BUILD_FILE")"
  echo "==============================================================="

  # Run the selected tool with the ralph prompt
  if [[ "$TOOL" == "amp" ]]; then
    OUTPUT=$(cat "$SCRIPT_DIR/prompt.md" | amp --dangerously-allow-all 2>&1 | tee /dev/stderr) || true
  else
    OUTPUT=$(claude --dangerously-skip-permissions --print < "$SCRIPT_DIR/CLAUDE.md" 2>&1 | tee /dev/stderr) || true
  fi

  # ── Mod B: Post-iteration Maven quality gate ─────────────────────
  LATEST_COMMIT=$(git log -1 --format="%H" 2>/dev/null || echo "")
  if [ "$LATEST_COMMIT" != "$PREV_COMMIT" ]; then
    echo ""
    echo "--- Post-iteration quality gate ---"
    cd "$PROJECT_ROOT"

    if ! mvn compile -q 2>&1; then
      echo "QUALITY GATE FAILED: compilation error after iteration $i"
      echo "Reverting last commit..."
      git revert --no-edit HEAD
      echo "Reverted. Continuing to next iteration..."
      sleep 2
      continue
    fi

    if ! mvn test -q 2>&1; then
      echo "QUALITY GATE FAILED: tests failed after iteration $i"
      echo "Reverting last commit..."
      git revert --no-edit HEAD
      echo "Reverted. Continuing to next iteration..."
      sleep 2
      continue
    fi

    echo "Quality gate PASSED (compile + test)"

    # ── Mod C: Increment build number and tag ────────────────────
    BUILD_NUM=$(cat "$BUILD_FILE" 2>/dev/null || echo "0")
    BUILD_NUM=$((BUILD_NUM + 1))
    echo "$BUILD_NUM" > "$BUILD_FILE"

    VERSION="${VERSION_PREFIX}+build.${BUILD_NUM}"
    git tag -a "$VERSION" -m "Ralph build $BUILD_NUM - iteration $i" 2>/dev/null || true
    echo "Tagged: $VERSION (build $BUILD_NUM)"

    PREV_COMMIT="$LATEST_COMMIT"
  else
    echo "No new commit detected in iteration $i."
  fi

  # Check for completion signal
  if echo "$OUTPUT" | grep -q "<promise>COMPLETE</promise>"; then
    echo ""
    echo "╔═══════════════════════════════════════════════════════════╗"
    echo "║  Ralph completed all tasks!                              ║"
    echo "║  Final build: $(cat "$BUILD_FILE") | Version: ${VERSION_PREFIX}+build.$(cat "$BUILD_FILE")  ║"
    echo "║  Completed at iteration $i of $MAX_ITERATIONS                       ║"
    echo "╚═══════════════════════════════════════════════════════════╝"
    exit 0
  fi

  echo "Iteration $i complete. Continuing..."
  sleep 2
done

echo ""
echo "Ralph reached max iterations ($MAX_ITERATIONS) without completing all tasks."
echo "Current build: $(cat "$BUILD_FILE")"
echo "Check $PROGRESS_FILE for status."
exit 1
