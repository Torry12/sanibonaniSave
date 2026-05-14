# Runtime Signal v2.0 - Testing & Validation Guide

**Date**: May 13, 2026  
**Purpose**: Verify all improvements work correctly

## 🧪 Test Suite

### Test 1: Basic Probe Execution (v2.0)

**Command**:
```powershell
cd C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full
.\scripts\runtime-signal-unified.ps1
```

**Expected Output**:
```
[14:32:15.237] [INFO] ✓ Pre-flight validation passed
[14:32:17.892] [INFO] ✓ Runtime environment ready for deployment
```

**Validation**:
- ✅ Script runs without errors
- ✅ Exit code is 0, 2, or 3 (not 10 or 11)
- ✅ runtime_signal.json is created

**Pass/Fail**: ___________

---

### Test 2: Debug Mode

**Command**:
```powershell
.\scripts\runtime-signal-unified.ps1 -Debug
```

**Expected Output**:
```
[14:32:15.237] [DEBUG] Workspace root: C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full
[14:32:15.238] [DEBUG] Probe script path: ...
[14:32:15.500] [INFO] Running runtime probe...
[14:32:17.892] [INFO] ============================================================
[14:32:17.893] [INFO] RUNTIME SIGNAL REPORT
[14:32:17.900] [INFO] Requirements:
[14:32:17.901] [DEBUG]   ✓ deno_cli
...
```

**Validation**:
- ✅ Timestamps on every line
- ✅ Colored output (INFO=green, DEBUG=cyan, ERROR=red)
- ✅ Shows requirement status

**Pass/Fail**: ___________

---

### Test 3: Local Function Probe

**Command** (if local Supabase is running):
```powershell
.\scripts\runtime-signal-unified.ps1 -CheckLocalFunction
```

**Expected Behavior**:
- ✅ Probes local function endpoint
- ✅ Returns YELLOW if can't reach or RED if required tools missing
- ✅ JSON report includes `local_function_probe` section

**Pass/Fail**: ___________

---

### Test 4: Strict Mode

**Command**:
```powershell
.\scripts\runtime-signal-unified.ps1 -CheckLocalFunction -Strict
```

**Expected**:
- ✅ Exit code 3 on YELLOW signals (when using -Strict)
- ✅ Exit code 2 on RED signals
- ✅ Exit code 0 on GREEN signals

**Pass/Fail**: ___________

---

### Test 5: Retry Logic

**Command** (simulate with network issue):
```powershell
.\scripts\runtime-signal-unified.ps1 -CheckLocalFunction -Retry 2 -Debug
```

**Expected Output**:
```
[timestamp] [INFO] Running runtime probe (attempt 1/3)...
[timestamp] [INFO] Retry attempt 2/3...
[timestamp] [INFO] Running runtime probe (attempt 2/3)...
```

**Validation**:
- ✅ Shows retry attempts
- ✅ Continues on transient failures
- ✅ Eventually succeeds or fails with proper exit code

**Pass/Fail**: ___________

---

### Test 6: Timeout Configuration

**Command**:
```powershell
.\scripts\runtime-signal-unified.ps1 -CheckLocalFunction -TimeoutSeconds 5 -Debug
```

**Expected**:
- ✅ Times out after 5 seconds (shorter than default 8)
- ✅ Shows timeout in debug output
- ✅ Handled gracefully with proper error message

**Pass/Fail**: ___________

---

### Test 7: JSON Report Format

**Command**:
```powershell
# After running a test, check the report
$report = Get-Content .\runtime_signal.json | ConvertFrom-Json
$report | ConvertTo-Json -Depth 8 | Out-String
```

**Expected Structure**:
```json
{
  "generated_at": "2026-05-13T14:32:17...",
  "workspace": "C:\\Users\\...",
  "signal": "GREEN",
  "runtime_ready": true,
  "requirements": [...],
  "blocking_failures": [],
  "diagnostics": {
    "probe_version": "2.0",
    "timeout_seconds": 8,
    "check_local_function": false,
    "total_requirements": 5,
    "failed_count": 0
  }
}
```

**Validation**:
- ✅ Has `diagnostics` section (NEW in v2.0)
- ✅ All required fields present
- ✅ Valid JSON format

**Pass/Fail**: ___________

---

### Test 8: Bash Wrapper (if bash available)

**Command**:
```bash
cd /c/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full  # Git Bash
chmod +x scripts/runtime-signal-unified.sh
./scripts/runtime-signal-unified.sh --debug
```

**Expected**:
- ✅ Auto-detects PowerShell
- ✅ Runs unified script via PowerShell
- ✅ Same timestamped output format
- ✅ Same exit codes

**Pass/Fail**: ___________

---

### Test 9: Backward Compatibility

**Command** (run old script):
```powershell
.\scripts\runtime_signal_ci_example.ps1 -CheckLocalFunction
```

**Expected**:
- ✅ Old scripts still work
- ✅ Same output format
- ✅ Same exit codes
- ✅ No errors

**Pass/Fail**: ___________

---

### Test 10: Error Handling

**Command** (intentional error - remove probe script temporarily):
```powershell
# Test missing probe script
.\scripts\runtime-signal-unified.ps1 -WorkspaceRoot "C:\invalid"
```

**Expected**:
- ✅ Exit code 10 (config error)
- ✅ Clear error message
- ✅ No cryptic PowerShell errors

**Pass/Fail**: ___________

---

## 📊 Test Results Summary

| Test # | Name | Status | Notes |
|--------|------|--------|-------|
| 1 | Basic Execution | PASS / FAIL | |
| 2 | Debug Mode | PASS / FAIL | |
| 3 | Function Probe | PASS / FAIL | |
| 4 | Strict Mode | PASS / FAIL | |
| 5 | Retry Logic | PASS / FAIL | |
| 6 | Timeout Config | PASS / FAIL | |
| 7 | JSON Report | PASS / FAIL | |
| 8 | Bash Wrapper | PASS / FAIL | |
| 9 | Backward Compat | PASS / FAIL | |
| 10 | Error Handling | PASS / FAIL | |

**Overall Status**: PASS / FAIL / PARTIAL

---

## 🔍 Quick Diagnostic Commands

If tests fail, run these to diagnose:

```powershell
# Check script syntax
powershell -NoProfile -File .\scripts\runtime-signal-unified.ps1 -Verbose

# Check core probe
.\scripts\runtime_signal.ps1 -Verbose

# Verify report generation
Test-Path .\runtime_signal.json

# Check exit codes
.\scripts\runtime-signal-unified.ps1; $LASTEXITCODE

# See full report
Get-Content .\runtime_signal.json | ConvertFrom-Json | ConvertTo-Json -Depth 8
```

---

## ✅ Approval Checklist

- [ ] All 10 tests pass
- [ ] No unexpected errors
- [ ] Output format matches expectations
- [ ] Exit codes correct
- [ ] JSON reports valid
- [ ] Backward compatible
- [ ] Ready for CI/CD migration

**Approved By**: ___________________  
**Date**: _____________________  
**Notes**: _________________________________________________________________

---

## 🚀 Next Steps After Passing

1. **Update CI/CD Pipelines**
   ```yaml
   run: .\scripts\runtime-signal-unified.ps1 -CheckLocalFunction -Retry 2
   ```

2. **Archive Old Scripts** (after migration complete)
   ```bash
   mkdir scripts/_archived_v1
   mv scripts/runtime_signal_ci_example.* scripts/_archived_v1/
   ```

3. **Update Documentation**
   - Link to `README_RUNTIME_SIGNAL_V2.md`
   - Update project wiki
   - Share migration guide with team

4. **Monitor**
   - Watch CI/CD pipeline runs
   - No regressions or new issues
   - Team feedback on new features

---

**Testing Guide Version**: 1.0  
**Status**: Ready for Testing  
**Date**: May 13, 2026

