# Runtime Signal CI Example

This file provides ready-to-paste CI usage for `scripts/runtime_signal.ps1`.

## PowerShell invocation (local or CI runner)

```powershell
Set-Location "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\scripts\runtime_signal_ci_example.ps1 -CheckLocalFunction
```

Strict mode (fail on `YELLOW` too):

```powershell
Set-Location "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\scripts\runtime_signal_ci_example.ps1 -CheckLocalFunction -Strict
```

## GitHub Actions step (Windows runner)

```yaml
- name: Runtime readiness gate
  shell: powershell
  run: |
    Set-Location "${{ github.workspace }}"
    .\scripts\runtime_signal_ci_example.ps1 -CheckLocalFunction
```

Strict mode:

```yaml
- name: Runtime readiness gate (strict)
  shell: powershell
  run: |
    Set-Location "${{ github.workspace }}"
    .\scripts\runtime_signal_ci_example.ps1 -CheckLocalFunction -Strict
```

## Exit codes

- `0`: pass
- `2`: fail on `RED`
- `3`: fail on `YELLOW` (only with `-Strict`)
- `10`: wrapper misconfiguration (missing base script)

