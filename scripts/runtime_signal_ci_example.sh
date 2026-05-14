#!/usr/bin/env bash
set -euo pipefail

WORKSPACE_ROOT="${1:-${GITHUB_WORKSPACE:-$(pwd)}}"
STRICT_MODE="${STRICT_MODE:-false}"
CHECK_LOCAL_FUNCTION="${CHECK_LOCAL_FUNCTION:-true}"
FUNCTION_NAME="${FUNCTION_NAME:-architecture-read}"
FUNCTION_ACTION="${FUNCTION_ACTION:-events}"

SCRIPT_PATH="$WORKSPACE_ROOT/scripts/runtime_signal.ps1"
if [[ ! -f "$SCRIPT_PATH" ]]; then
  echo "runtime_signal.ps1 not found at: $SCRIPT_PATH" >&2
  exit 10
fi

ARGS=(
  -WorkspaceRoot "$WORKSPACE_ROOT"
  -OutputEncodingMode Utf8NoBom
  -CiExitOnNotReady
)

if [[ "$CHECK_LOCAL_FUNCTION" == "true" ]]; then
  ARGS+=(
    -CheckLocalFunction
    -FunctionName "$FUNCTION_NAME"
    -FunctionAction "$FUNCTION_ACTION"
  )
fi

if [[ "$STRICT_MODE" == "true" ]]; then
  ARGS+=(-FailOnYellow)
fi

if command -v pwsh >/dev/null 2>&1; then
  pwsh -NoProfile -File "$SCRIPT_PATH" "${ARGS[@]}"
  EXIT_CODE=$?
elif command -v powershell >/dev/null 2>&1; then
  powershell -NoProfile -ExecutionPolicy Bypass -File "$SCRIPT_PATH" "${ARGS[@]}"
  EXIT_CODE=$?
else
  echo "Neither 'pwsh' nor 'powershell' is available in PATH." >&2
  exit 11
fi

REPORT_PATH="$WORKSPACE_ROOT/runtime_signal.json"
if [[ -f "$REPORT_PATH" ]]; then
  echo "--- runtime_signal.json ---"
  cat "$REPORT_PATH"
fi

exit $EXIT_CODE

