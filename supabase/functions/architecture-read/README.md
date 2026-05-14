# architecture-read (Supabase Edge Function)

Read-only endpoint handlers for architecture blueprint discovery.

## Actions

- `action=blueprint` (default): full blueprint response
- `action=models`: compact model summary list
- `action=model&model=<id>`: full model detail
- `action=operations[&model=<id>]`: API operations (all or per model)
- `action=ai[&model=<id>]`: AI opportunities (all or per model)
- `action=events`: event architecture + core event registry

Valid `model` ids:

- `rosca`
- `asca`
- `investment_group`
- `emergency_fund`
- `burial_society`
- `grocery_group`
- `business_capital_group`
- `education_savings_group`
- `social_credit_system`
- `hybrid_financial_group`

## Optional access key

If you set the secret `ARCHITECTURE_READ_KEY`, callers must pass it in `x-architecture-key`.

## Deploy

```powershell
Set-Location "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase functions deploy architecture-read
```

## Invoke examples

```powershell
# Full blueprint
Invoke-WebRequest -Method Get "https://<project-ref>.supabase.co/functions/v1/architecture-read?action=blueprint"

# Model list
Invoke-WebRequest -Method Get "https://<project-ref>.supabase.co/functions/v1/architecture-read?action=models"

# ROSCA model detail
Invoke-WebRequest -Method Get "https://<project-ref>.supabase.co/functions/v1/architecture-read?action=model&model=rosca"

# API operations for one model
Invoke-WebRequest -Method Get "https://<project-ref>.supabase.co/functions/v1/architecture-read?action=operations&model=investment_group"

# AI opportunities across all models
Invoke-WebRequest -Method Get "https://<project-ref>.supabase.co/functions/v1/architecture-read?action=ai"

# Event registry
Invoke-WebRequest -Method Get "https://<project-ref>.supabase.co/functions/v1/architecture-read?action=events"
```

