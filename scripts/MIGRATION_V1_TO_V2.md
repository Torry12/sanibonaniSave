# Quick Migration Guide: Runtime Signal v1 → v2

**Last Updated**: May 13, 2026  
**Status**: ✅ Ready to Migrate

## TL;DR (What Changed?)

| Use Case | Old Command | New Command |
|----------|------------|------------|
| **PowerShell - Local Dev** | `.\runtime_signal_ci_example.ps1` | `.\runtime-signal-unified.ps1 -Debug` |
| **PowerShell - CI/CD** | `.\runtime_signal_ci_example.ps1 -CheckLocalFunction` | `.\runtime-signal-unified.ps1 -CheckLocalFunction` |
| **PowerShell - Strict** | `.\runtime_signal_ci_example.ps1 -CheckLocalFunction -Strict` | `.\runtime-signal-unified.ps1 -CheckLocalFunction -Strict` |
| **Bash - New!** | ❌ Not possible | `./runtime-signal-unified.sh --check-function --debug` |

## Step-by-Step Migration

### Phase 1: Local Development (Immediate)

You can **start using the new scripts today**. They're backward compatible:

```powershell
# Before
cd C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full
.\scripts\runtime_signal_ci_example.ps1 -CheckLocalFunction

# After (better debugging)
.\scripts\runtime-signal-unified.ps1 -CheckLocalFunction -Debug
```

**Benefits**:
- See timestamps on every step
- Color-coded output (RED, YELLOW, GREEN)
- Better error messages
- Retry support if network is flaky

### Phase 2: CI/CD Updates (This Week)

Update your GitHub Actions or CI pipelines:

**GitHub Actions - PowerShell Runner**:
```yaml
# Before
- name: Runtime check
  shell: powershell
  run: .\scripts\runtime_signal_ci_example.ps1 -CheckLocalFunction

# After
- name: Runtime check
  shell: powershell
  run: .\scripts\runtime-signal-unified.ps1 -CheckLocalFunction -Retry 2
```

**GitHub Actions - Any Runner** (NEW - cross-platform):
```yaml
# New option with bash
- name: Runtime check (cross-platform)
  shell: bash
  run: |
    chmod +x scripts/runtime-signal-unified.sh
    ./scripts/runtime-signal-unified.sh --check-function --retry 2
```

### Phase 3: Archive Old Scripts (Next Sprint)

After updating all CI/CD references:

```bash
# Move old scripts to archive
cd scripts
mkdir -p _archived_v1

# Keep old scripts as reference
mv runtime_signal_ci_example.ps1 _archived_v1/
mv runtime_signal_ci_example.sh _archived_v1/

# Document why they're archived
echo "Archived: Replaced by runtime-signal-unified.ps1 and runtime-signal-unified.sh (v2.0)" > _archived_v1/README.md
```

## Common Questions

### Q: Do I have to migrate immediately?
**A**: No. Old scripts still work. But new scripts are better for:
- ✅ Debugging issues (use `-Debug`)
- ✅ Unreliable networks (use `-Retry 2`)
- ✅ Cross-platform (use `.sh` on bash)

### Q: Will new scripts break my existing setup?
**A**: No. They're 100% backward compatible:
- Same exit codes
- Same parameters
- Same JSON output format
- Enhanced with optional features

### Q: Can I mix old and new scripts?
**A**: Yes! You can run both in the same project. Gradually migrate when ready.

### Q: Which script should I use?
**A**: 
- **Local dev with issues?** Use `runtime-signal-unified.ps1 -Debug`
- **CI/CD slow/flaky?** Use `-Retry 2`
- **Using bash?** Use `runtime-signal-unified.sh`
- **Everything works?** You can stay with old scripts (but why not upgrade?)

## New Features You're Missing

### 1. Debug Mode
```powershell
# See everything happening step-by-step
.\runtime-signal-unified.ps1 -CheckLocalFunction -Debug

# Output:
# [14:32:15.237] [DEBUG] Workspace root: C:\workspace
# [14:32:15.238] [DEBUG] Probe script path: C:\workspace\scripts\...
# [14:32:17.892] [INFO] Signal: GREEN
```

### 2. Retry Logic
```powershell
# Automatically retry 2 times if first attempt fails
.\runtime-signal-unified.ps1 -CheckLocalFunction -Retry 2

# Useful for:
# - Intermittent network issues
# - Docker/Supabase starting up
# - CI runners with limited resources
```

### 3. Custom Timeout
```powershell
# Increase timeout for slow networks (default is 8 seconds)
.\runtime-signal-unified.ps1 -CheckLocalFunction -TimeoutSeconds 15
```

### 4. Better Error Messages
```
✗ FAILED: Critical runtime prerequisites missing (RED signal)
  • docker_daemon: daemon_unreachable
  • supabase_local_stack: not_running
```

## Rollback Plan (if needed)

If anything goes wrong with new scripts:

```bash
# Just use the old ones
cd scripts
rm runtime-signal-unified.ps1
rm runtime-signal-unified.sh
git checkout runtime_signal_ci_example.ps1
git checkout runtime_signal_ci_example.sh

# Or restore from archive
cp _archived_v1/runtime_signal_ci_example.ps1 ./
```

**Note**: You won't have any data loss. The new scripts generate the same JSON report.

## Timeline Recommendation

- **Week 1-2**: Start using new scripts locally with `-Debug`
- **Week 2-3**: Update one CI/CD pipeline as a pilot
- **Week 3-4**: Update remaining CI/CD references
- **Week 4+**: Archive v1 scripts

## Support

If you hit issues:

1. **Try debug mode first**:
   ```powershell
   .\runtime-signal-unified.ps1 -CheckLocalFunction -Debug
   ```

2. **Check the report**:
   ```powershell
   Get-Content ./runtime_signal.json | ConvertFrom-Json
   ```

3. **Look at the detailed documentation**:
   - `README_RUNTIME_SIGNAL_V2.md` — Full feature guide
   - `README_RUNTIME_SIGNAL.md` — Original reference docs

4. **Common issues**:
   - **"No PowerShell found"** (bash wrapper): Install pwsh (PowerShell Core)
   - **"Timeout"**: Use `--timeout 15` to increase timeout
   - **"Transient failures"**: Use `-Retry 2`

## Validation Checklist

Before calling migration complete:

- [ ] Ran new script locally with `-Debug`
- [ ] Checked JSON output is valid
- [ ] Updated 1 CI/CD pipeline
- [ ] Ran CI/CD pipeline successfully
- [ ] No breaking changes in your workflows
- [ ] Team aware of new parameter names
- [ ] Old scripts documented as archived

## Example: Before vs After

### Before
```powershell
C:\workspace> .\scripts\runtime_signal_ci_example.ps1 -CheckLocalFunction
runtime_signal=GREEN
report=C:\workspace\runtime_signal.json
runtime_summary=signal:GREEN;ready:True;blockers:none
```

### After (Standard)
```powershell
C:\workspace> .\scripts\runtime-signal-unified.ps1 -CheckLocalFunction
[2026-05-13 14:32:15.237] [INFO] ✓ Pre-flight validation passed
[2026-05-13 14:32:17.892] [INFO] ✓ Runtime environment ready for deployment
runtime_signal=GREEN
report=C:\workspace\runtime_signal.json
runtime_summary=signal:GREEN;ready:True;blockers:none
```

### After (Debug Mode)
```powershell
C:\workspace> .\scripts\runtime-signal-unified.ps1 -CheckLocalFunction -Debug
[2026-05-13 14:32:15.237] [DEBUG] Workspace root: C:\workspace
[2026-05-13 14:32:15.238] [DEBUG] Probe script path: C:\workspace\scripts\...
[2026-05-13 14:32:15.239] [INFO] Running runtime probe...
[2026-05-13 14:32:17.892] [INFO] ============================================================
[2026-05-13 14:32:17.893] [INFO] RUNTIME SIGNAL REPORT
[2026-05-13 14:32:17.894] [INFO] Signal: GREEN
[2026-05-13 14:32:17.895] [INFO] Ready: True
[2026-05-13 14:32:17.896] [INFO] Requirements:
[2026-05-13 14:32:17.897] [DEBUG]   ✓ deno_cli
[2026-05-13 14:32:17.898] [DEBUG]   ✓ docker_cli
[2026-05-13 14:32:17.899] [DEBUG]   ✓ docker_daemon
[2026-05-13 14:32:17.900] [DEBUG]   ✓ supabase_cli
[2026-05-13 14:32:17.901] [DEBUG]   ✓ supabase_local_stack
[2026-05-13 14:32:17.902] [DEBUG]   ✓ local_function_http
runtime_signal=GREEN
report=C:\workspace\runtime_signal.json
runtime_summary=signal:GREEN;ready:True;blockers:none
```

**Note**: Same output, just with more helpful logging!

---

**Ready to migrate?** Start with step 1: Try the new script with `-Debug` locally. 🚀

For detailed feature documentation, see: `README_RUNTIME_SIGNAL_V2.md`

