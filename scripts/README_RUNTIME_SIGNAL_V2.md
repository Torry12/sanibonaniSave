# Runtime Signal Consolidated & Upgraded (v2.0)

**Status**: ✅ Refactored, Consolidated, Enhanced  
**Date**: May 13, 2026  
**Version**: 2.0 (Unified Architecture)

## Overview

The runtime signal system has been **consolidated, refined, debugged, and upgraded** with:

- ✅ **Consolidated**: Single unified architecture (platform-agnostic)
- ✅ **Refined**: Enhanced error handling and logging
- ✅ **Debugged**: Structured diagnostics with timestamps and detail levels
- ✅ **Upgraded**: New features (retry logic, timeout control, comprehensive reporting)

## Architecture Changes

### Before (Fragmented)
```
┌─ runtime_signal.ps1              (Core probe)
│
├─ runtime_signal_ci_example.ps1   (PowerShell wrapper)
└─ runtime_signal_ci_example.sh    (Bash wrapper)
     ↓ Complex shell detection logic
     ↓ Different error handling paths
     ↓ Code duplication
```

### After (Unified)
```
┌─ runtime_signal.ps1              (Enhanced core probe)
│    ├─ Better diagnostics metadata
│    ├─ Configurable timeout
│    └─ Verbose logging support
│
└─ Unified Wrappers (Choose based on shell availability)
   ├─ runtime-signal-unified.ps1   (PowerShell entry point)
   │   ├─ Platform-agnostic parameter handling
   │   ├─ Retry logic for transient failures
   │   ├─ Structured console output
   │   └─ Better error messages
   │
   └─ runtime-signal-unified.sh    (Cross-platform shell wrapper)
       ├─ Auto-detects pwsh/powershell
       ├─ Same interface on all platforms
       ├─ Timestamp-based logging
       └─ Consistent diagnostics output
```

## New Files Created

### 1. `runtime-signal-unified.ps1` (NEW)
**Purpose**: Primary PowerShell wrapper with enhanced features

**New Capabilities**:
- Structured logging with timestamps and severity levels
- Retry mechanism for transient failures
- Configurable HTTP timeout
- Enhanced error messages and diagnostics
- Better report formatting and color coding

**Usage**:
```powershell
# Basic usage
.\runtime-signal-unified.ps1 -CheckLocalFunction

# With debugging
.\runtime-signal-unified.ps1 -CheckLocalFunction -Debug

# Strict mode with retries
.\runtime-signal-unified.ps1 -CheckLocalFunction -Strict -Retry 2

# Custom timeout and function
.\runtime-signal-unified.ps1 `
  -CheckLocalFunction `
  -FunctionName "my-function" `
  -FunctionAction "validate" `
  -TimeoutSeconds 15 `
  -Debug
```

### 2. `runtime-signal-unified.sh` (NEW)
**Purpose**: Cross-platform bash wrapper with unified interface

**New Capabilities**:
- Works on Windows, macOS, Linux
- Auto-detects available PowerShell (pwsh or powershell)
- Timestamp-based structured logging
- Same command-line interface across platforms
- Better error diagnostics

**Usage**:
```bash
# Basic usage
./runtime-signal-unified.sh

# With local function check
./runtime-signal-unified.sh --check-function

# Debug mode
./runtime-signal-unified.sh --check-function --debug

# Strict with retries
./runtime-signal-unified.sh \
  --check-function \
  --strict \
  --retry 2 \
  --timeout 15 \
  --debug
```

### 3. Enhanced `runtime_signal.ps1`
**Improvements**:
- Added `-TimeoutSec` parameter (configurable, default 8s)
- Added `-Verbose` flag for detailed diagnostics
- Better error detail handling (truncated to 500 chars max)
- Enhanced JSON output with `diagnostics` metadata
- Improved logging functions for better troubleshooting

## Consolidated Features

### Retry Logic
```powershell
# Retry up to 2 times on transient failures
.\runtime-signal-unified.ps1 -CheckLocalFunction -Retry 2
```

### Configurable Timeout
```powershell
# Set 15-second timeout for HTTP probes
.\runtime-signal-unified.ps1 -CheckLocalFunction -TimeoutSeconds 15
```

### Structured Logging
```
[2026-05-13 14:32:15.237] [INFO] ✓ Pre-flight validation passed
[2026-05-13 14:32:15.238] [DEBUG] Workspace root: C:\workspace
[2026-05-13 14:32:15.501] [INFO] Running runtime probe (attempt 1/1)...
[2026-05-13 14:32:17.892] [INFO] ✓ Runtime environment ready for deployment
```

### Enhanced Error Handling
```
[2026-05-13 14:32:15.500] [WARN] Transient failure detected (exit 2), will retry...
[2026-05-13 14:32:16.000] [ERROR] ✗ FAILED: Critical runtime prerequisites missing (RED signal)
```

## Debug vs. Report Modes

### Standard Output (CI-Friendly)
```
runtime_signal=GREEN
report=C:\workspace\runtime_signal.json
runtime_summary=signal:GREEN;ready:True;blockers:none
```

### Debug Mode Output (Troubleshooting)
```
[14:32:15.237] [INFO] ✓ Pre-flight validation passed
[14:32:15.238] [DEBUG] Workspace root: C:\workspace
[14:32:15.239] [DEBUG] Probe script path: C:\workspace\scripts\runtime_signal.ps1
[14:32:15.500] [DEBUG] Parameters: FunctionName=architecture-read, ...
[14:32:17.892] [INFO] ============================================================
[14:32:17.893] [INFO] RUNTIME SIGNAL REPORT
[14:32:17.894] [INFO] ============================================================
[14:32:17.895] [INFO] Signal:   GREEN
[14:32:17.896] [INFO] Ready:    True
[14:32:17.897] [INFO] Summary:  Local Deno/Supabase runtime is ready.
[14:32:17.898] [INFO] Requirements:
[14:32:17.899] [DEBUG]   ✓ deno_cli
[14:32:17.900] [DEBUG]     Detail: deno 1.40.0
...
```

## Enhanced JSON Report

### Before (Basic)
```json
{
  "generated_at": "2026-05-13T...",
  "signal": "GREEN",
  "runtime_ready": true,
  "requirements": [...],
  "blocking_failures": [...]
}
```

### After (Enhanced with Diagnostics)
```json
{
  "generated_at": "2026-05-13T...",
  "signal": "GREEN",
  "runtime_ready": true,
  "requirements": [...],
  "blocking_failures": [],
  "diagnostics": {
    "probe_version": "2.0",
    "timeout_seconds": 8,
    "check_local_function": true,
    "function_base_url": "http://127.0.0.1:54321/functions/v1",
    "total_requirements": 5,
    "failed_count": 0
  }
}
```

## Migration Guide

### For PowerShell Users

**Old Way**:
```powershell
.\runtime_signal_ci_example.ps1 -CheckLocalFunction -Strict
```

**New Way** (Better):
```powershell
.\runtime-signal-unified.ps1 -CheckLocalFunction -Strict -Debug
```

**Benefits**:
- Cleaner parameter interface
- Better error messages
- Debug mode for troubleshooting
- Retry logic for flaky networks
- Colored output with signal status

### For Bash Users

**Old Way** (Windows only):
```bash
./runtime_signal_ci_example.sh
```

**New Way** (Cross-platform):
```bash
./runtime-signal-unified.sh --check-function --debug
```

**Benefits**:
- Works on all platforms (bash, zsh, sh)
- Auto-detects available PowerShell
- Timestamps on all log lines
- Better formatting
- Consistent exit codes

### For CI/CD Pipelines

**GitHub Actions - Before**:
```yaml
- name: Check runtime
  shell: powershell
  run: .\scripts\runtime_signal_ci_example.ps1 -CheckLocalFunction
```

**GitHub Actions - After** (Enhanced):
```yaml
- name: Check runtime
  shell: powershell
  run: |
    .\scripts\runtime-signal-unified.ps1 `
      -CheckLocalFunction `
      -Strict `
      -Retry 2 `
      -Debug
```

**GitHub Actions - Bash** (New option):
```yaml
- name: Check runtime (cross-platform)
  shell: bash
  run: |
    chmod +x scripts/runtime-signal-unified.sh
    ./scripts/runtime-signal-unified.sh \
      --check-function \
      --strict \
      --retry 2 \
      --debug
```

## Exit Codes

| Code | Meaning | Condition |
|------|---------|-----------|
| 0 | ✅ SUCCESS | GREEN signal (or YELLOW without -Strict) |
| 2 | ❌ CRITICAL | RED signal (missing prerequisites) |
| 3 | ⚠️ WARN | YELLOW signal with -Strict flag |
| 10 | ❌ CONFIG | Missing probe script or workspace |
| 11 | ❌ SHELL | No PowerShell available (bash wrapper) |

## Backward Compatibility

### Old Scripts (Deprecated but Still Work)
- `runtime_signal.ps1` — ✅ Enhanced but backward compatible
- `runtime_signal_ci_example.ps1` — 🟡 Still works but consider migration
- `runtime_signal_ci_example.sh` — 🟡 Still works but consider migration

### When to Keep Old Scripts
- If you have hardcoded CI/CD references and cannot update
- For archival/audit purposes

### When to Migrate
- ✅ All new projects should use unified wrappers
- ✅ Update existing CI/CD pipelines at next maintenance
- ✅ Local development should use unified scripts

## Troubleshooting

### Debug Mode for Issues
```powershell
# Enable verbose output to see what's happening
.\runtime-signal-unified.ps1 -CheckLocalFunction -Debug

# Shows:
# - Timestamp on every operation
# - Detailed diagnostic info
# - Full requirement details
# - Better error context
```

### Retry for Flaky Networks
```powershell
# Retry up to 2 times if first attempt fails
.\runtime-signal-unified.ps1 -CheckLocalFunction -Retry 2
```

### Timeout Configuration
```powershell
# Increase timeout for slow networks
.\runtime-signal-unified.ps1 -CheckLocalFunction -TimeoutSeconds 20
```

### Check the Report
```powershell
# View full diagnostic report
Get-Content ./runtime_signal.json | ConvertFrom-Json | ForEach-Object { $_ | ConvertTo-Json -Depth 8 }
```

## Performance Improvements

- ✅ Early exit on critical failures (RED signal)
- ✅ Parallel requirement checking (existing)
- ✅ Configurable timeouts prevent hanging
- ✅ Retry logic handles network transients
- ✅ Structured logging without overhead

## Summary of Changes

| Aspect | Before | After |
|--------|--------|-------|
| Wrappers | 2 separate scripts | 1 unified interface (2 implementations) |
| Error Handling | Basic try/catch | Structured with retry logic |
| Logging | None in core | Timestamp-based levels (DEBUG/INFO/WARN/ERROR) |
| Timeout | Fixed 8s | Configurable parameter |
| Diagnostics | Minimal | Rich metadata in JSON |
| Debug Mode | None | Full verbose output |
| Exit Codes | 3 codes | 5 codes with better semantics |
| Cross-Platform | Windows/bash separate | Single interface |
| Developer Experience | Manual parameter mapping | Auto-discovery, better messages |

## Next Steps

1. ✅ **Review** the three new/enhanced scripts
2. ✅ **Test** with: `.\runtime-signal-unified.ps1 -Debug`
3. ✅ **Update** CI/CD pipelines to use new wrappers
4. ✅ **Archive** old `runtime_signal_ci_example.*` scripts (keep for reference)
5. ✅ **Document** project-specific customizations

## Files Reference

- `scripts/runtime_signal.ps1` — Core probe (enhanced)
- `scripts/runtime-signal-unified.ps1` — PowerShell unified wrapper (NEW)
- `scripts/runtime-signal-unified.sh` — Bash unified wrapper (NEW)
- `scripts/README_RUNTIME_SIGNAL.md` — Original documentation (reference)
- `scripts/README_RUNTIME_SIGNAL_CI.md` — CI examples (reference)

---

**Version**: 2.0 Consolidated | **Status**: 🟢 Ready for Production  
**Created**: May 13, 2026 | **Team**: SanibonaniSave DevOps

