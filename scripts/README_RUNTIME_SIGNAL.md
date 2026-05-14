# Runtime Signal Probe

This probe emits a reliable local runtime signal for Deno + Supabase edge development.

For CI-ready wrapper usage and GitHub Actions snippets, see:

- `scripts/runtime_signal_ci_example.ps1`
- `scripts/README_RUNTIME_SIGNAL_CI.md`

## What it checks

- `deno` CLI presence
- `supabase` CLI presence and version
- `docker` CLI presence
- Docker daemon reachability (`docker version --format '{{.Server.Version}}'`)
- Local Supabase stack status (`supabase status -o json` best-effort)

## Signal semantics

- `GREEN`: toolchain + daemon + local Supabase stack confirmed
- `YELLOW`: toolchain/daemon present, stack not confirmed running
- `RED`: at least one blocking prerequisite missing

## Run

```powershell
Set-Location "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\scripts\runtime_signal.ps1
```

## Run with local edge function HTTP probe

```powershell
Set-Location "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\scripts\runtime_signal.ps1 -CheckLocalFunction -FunctionName "architecture-read" -FunctionAction "events"
```

This mode also probes:

`http://127.0.0.1:54321/functions/v1/<function>?action=<action>`

## CI gate mode (non-zero exit when not ready)

```powershell
Set-Location "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\scripts\runtime_signal.ps1 -CheckLocalFunction -FunctionName "architecture-read" -FunctionAction "events" -CiExitOnNotReady
```

CI-friendly UTF-8 (no BOM) output mode:

```powershell
Set-Location "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\scripts\runtime_signal.ps1 -CheckLocalFunction -FunctionName "architecture-read" -FunctionAction "events" -CiExitOnNotReady -OutputEncodingMode Utf8NoBom
```

Optional stricter mode (also fail on `YELLOW`):

```powershell
Set-Location "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\scripts\runtime_signal.ps1 -CheckLocalFunction -FunctionName "architecture-read" -FunctionAction "events" -CiExitOnNotReady -FailOnYellow
```

Exit codes:

- `0`: pass (always for `GREEN`, and for `YELLOW` unless `-FailOnYellow` is set)
- `2`: fail on `RED` when `-CiExitOnNotReady` is set
- `3`: fail on `YELLOW` when both `-CiExitOnNotReady` and `-FailOnYellow` are set

## Output

- Human-readable lines:
  - `runtime_signal=<GREEN|YELLOW|RED>`
  - `report=<path>`
  - `runtime_summary=signal:<...>;ready:<true|false>;blockers:<k1,k2,...>`
- JSON report:
  - `runtime_signal.json`
  - Includes `local_function_probe` section
  - `-OutputEncodingMode Utf8NoBom` writes report as UTF-8 (no BOM)

