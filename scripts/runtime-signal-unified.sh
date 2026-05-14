#!/usr/bin/env bash
#
# Runtime Signal Unified Wrapper
# Cross-platform orchestrator for runtime readiness checks
#
# Features:
#  - Auto-detects available shell (pwsh, powershell, bash)
#  - Consolidates bash + PowerShell wrapper logic
#  - Enhanced error handling and diagnostics
#  - Structured logging with timestamps and levels
#
# Usage: ./runtime-signal-unified.sh [OPTIONS]
#   -w, --workspace ROOT        Workspace root (default: $CI_WORKSPACE or pwd)
#   -c, --check-function        Enable local function HTTP probe
#   -f, --function NAME         Function name (default: architecture-read)
#   -a, --action ACTION         Function action (default: events)
#   -s, --strict                Exit code 3 on YELLOW (not just RED)
#   -d, --debug                 Enable verbose debug output
#   -r, --retry N               Retry attempts for transient failures (default: 0)
#   -t, --timeout SEC           HTTP timeout in seconds (default: 8)
#   -h, --help                  Show this help message
#
# Examples:
#   ./runtime-signal-unified.sh
#   ./runtime-signal-unified.sh -c -d
#   ./runtime-signal-unified.sh -c -s -w /path/to/workspace

set -euo pipefail

# ============================================================================
# Configuration
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="${CI_WORKSPACE:-${GITHUB_WORKSPACE:-$(pwd)}}"
CHECK_LOCAL_FUNCTION=false
FUNCTION_NAME="architecture-read"
FUNCTION_ACTION="events"
STRICT_MODE=false
DEBUG_MODE=false
RETRY_COUNT=0
TIMEOUT_SECONDS=8

# ============================================================================
# Logging Functions
# ============================================================================

log_timestamp() {
    date "+%Y-%m-%d %H:%M:%S.%3N"
}

log_debug() {
    if [[ "$DEBUG_MODE" == "true" ]]; then
        echo "[$(log_timestamp)] [DEBUG] $*" >&2
    fi
}

log_info() {
    echo "[$(log_timestamp)] [INFO] $*" >&2
}

log_warn() {
    echo "[$(log_timestamp)] [WARN] $*" >&2 | tee /dev/stderr
}

log_error() {
    echo "[$(log_timestamp)] [ERROR] $*" >&2
}

# ============================================================================
# Helper Functions
# ============================================================================

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

show_usage() {
    head -n 30 "$0" | tail -n 28 | grep "^#" | sed 's/^# //'
    exit 0
}

# ============================================================================
# Argument Parsing
# ============================================================================

while [[ $# -gt 0 ]]; do
    case "$1" in
        -w|--workspace)
            WORKSPACE_ROOT="$2"
            shift 2
            ;;
        -c|--check-function)
            CHECK_LOCAL_FUNCTION=true
            shift
            ;;
        -f|--function)
            FUNCTION_NAME="$2"
            shift 2
            ;;
        -a|--action)
            FUNCTION_ACTION="$2"
            shift 2
            ;;
        -s|--strict)
            STRICT_MODE=true
            shift
            ;;
        -d|--debug)
            DEBUG_MODE=true
            shift
            ;;
        -r|--retry)
            RETRY_COUNT="$2"
            shift 2
            ;;
        -t|--timeout)
            TIMEOUT_SECONDS="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            ;;
        *)
            log_error "Unknown option: $1"
            echo "Run with -h or --help for usage information."
            exit 1
            ;;
    esac
done

# ============================================================================
# Pre-flight Validation
# ============================================================================

if [[ ! -d "$WORKSPACE_ROOT" ]]; then
    log_error "Workspace root not found: $WORKSPACE_ROOT"
    exit 10
fi

PROBE_SCRIPT="$WORKSPACE_ROOT/scripts/runtime_signal.ps1"
if [[ ! -f "$PROBE_SCRIPT" ]]; then
    log_error "Probe script not found: $PROBE_SCRIPT"
    exit 10
fi

log_debug "Workspace: $WORKSPACE_ROOT"
log_debug "Probe script: $PROBE_SCRIPT"
log_info "✓ Pre-flight validation passed"

# ============================================================================
# Shell Detection & Execution
# ============================================================================

SHELL_COMMAND=""
EXIT_CODE=0

if command_exists pwsh; then
    log_debug "Using: pwsh (modern PowerShell)"
    SHELL_COMMAND="pwsh"
elif command_exists powershell; then
    log_debug "Using: powershell (legacy PowerShell)"
    SHELL_COMMAND="powershell"
else
    log_error "Neither 'pwsh' nor 'powershell' found in PATH"
    log_info "Install PowerShell Core (pwsh) or Windows PowerShell (powershell)"
    exit 11
fi

# ============================================================================
# Build Probe Arguments
# ============================================================================

PROBE_ARGS=(
    "-WorkspaceRoot" "$WORKSPACE_ROOT"
    "-OutputEncodingMode" "Utf8NoBom"
    "-CiExitOnNotReady"
    "-TimeoutSec" "$TIMEOUT_SECONDS"
)

if [[ "$CHECK_LOCAL_FUNCTION" == "true" ]]; then
    PROBE_ARGS+=(
        "-CheckLocalFunction"
        "-FunctionName" "$FUNCTION_NAME"
        "-FunctionAction" "$FUNCTION_ACTION"
    )
fi

if [[ "$STRICT_MODE" == "true" ]]; then
    PROBE_ARGS+=("-FailOnYellow")
fi

if [[ "$DEBUG_MODE" == "true" ]]; then
    PROBE_ARGS+=("-Verbose")
fi

# ============================================================================
# Execute Probe with Retry
# ============================================================================

attempt=1
max_attempts=$((RETRY_COUNT + 1))

while [[ $attempt -le $max_attempts ]]; do
    if [[ $attempt -gt 1 ]]; then
        log_warn "Retry attempt $attempt/$max_attempts..."
        sleep 2
    fi

    log_info "Running runtime probe (attempt $attempt/$max_attempts)..."
    log_debug "Command: $SHELL_COMMAND -NoProfile -ExecutionPolicy Bypass -File \"$PROBE_SCRIPT\" ${PROBE_ARGS[@]}"

    if "$SHELL_COMMAND" -NoProfile -ExecutionPolicy Bypass -File "$PROBE_SCRIPT" "${PROBE_ARGS[@]}"; then
        EXIT_CODE=0
        break
    else
        EXIT_CODE=$?

        # Transient failure (exit code 2 typically means RED - not transient)
        # Retry only if not a hard failure
        if [[ $attempt -lt $max_attempts && $EXIT_CODE -ne 2 ]]; then
            log_warn "Transient failure detected (exit $EXIT_CODE), will retry..."
        fi
    fi

    attempt=$((attempt + 1))
done

# ============================================================================
# Report Output
# ============================================================================

REPORT_PATH="$WORKSPACE_ROOT/runtime_signal.json"

if [[ -f "$REPORT_PATH" ]]; then
    log_debug "Report found at: $REPORT_PATH"
    if [[ "$DEBUG_MODE" == "true" ]]; then
        log_info "=== RUNTIME SIGNAL REPORT ==="
        cat "$REPORT_PATH"
        log_info "============================"
    fi
else
    log_warn "Report file not found at expected location: $REPORT_PATH"
fi

# ============================================================================
# Finalization
# ============================================================================

log_debug "Probe completed with exit code: $EXIT_CODE"

case $EXIT_CODE in
    0)
        log_info "✓ Runtime environment ready for deployment"
        ;;
    2)
        log_error "✗ FAILED: Critical runtime prerequisites missing (RED signal)"
        ;;
    3)
        log_error "✗ FAILED: Runtime environment degraded (YELLOW signal with --strict)"
        ;;
    10)
        log_error "✗ FAILED: Wrapper misconfiguration (missing probe script)"
        ;;
    11)
        log_error "✗ FAILED: No PowerShell shell available"
        ;;
    *)
        log_error "✗ FAILED: Unknown error (exit $EXIT_CODE)"
        ;;
esac

exit $EXIT_CODE

