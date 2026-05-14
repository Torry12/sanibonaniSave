# 🔐 GitHub Secrets Setup — SanibonaniSave

Go to: **GitHub → Your Repo → Settings → Secrets and variables → Actions → New repository secret**

## Required Secrets

| Secret Name | How to Get | Notes |
|-------------|------------|-------|
| `KEYSTORE_B64` | `base64 -i release.keystore` (macOS/Linux) or `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` (PowerShell) | Generate keystore first (see below) |
| `KEYSTORE_PASSWORD` | Password you chose when generating the keystore | |
| `KEY_PASSWORD` | Key password (can be same as keystore password) | |
| `SUPABASE_URL` | Supabase Dashboard → Project Settings → API | e.g. `https://xxx.supabase.co` |
| `SUPABASE_ANON_KEY` | Supabase Dashboard → Project Settings → API → anon public | |
| `YOCO_PUBLIC_KEY` | dashboard.yoco.com → Settings → API Keys | Use `pk_live_...` for production |
| `WHATSAPP_PHONE_NUMBER_ID` | Meta Developer Console | Non-secret phone number ID |
| `GEOAPIFY_API_KEY` | myprojects.geoapify.com | |
| `PLATFORM_ADMIN_EMAIL` | Your admin email | e.g. `torrymsimango@gmail.com` |
| `FIREBASE_CONFIG_JSON` | `base64 -i app/google-services.json` | Base64-encoded google-services.json |
| `GOOGLE_PLAY_KEY_JSON` | Google Play Console → Setup → API access → Service account | For automated Play Store upload |

---

## Generate Release Keystore (One-time Setup)

Run in your terminal:

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias sanibonani_release \
  -dname "CN=SanibonaniSave, OU=Mobile, O=SanibonaniSave, L=Johannesburg, S=Gauteng, C=ZA"
```

**Windows PowerShell:**
```powershell
keytool -genkey -v `
  -keystore release.keystore `
  -keyalg RSA -keysize 2048 `
  -validity 10000 `
  -alias sanibonani_release `
  -dname "CN=SanibonaniSave, OU=Mobile, O=SanibonaniSave, L=Johannesburg, S=Gauteng, C=ZA"
```

Then encode it for GitHub Secrets:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Clipboard
# Now paste the clipboard value as the KEYSTORE_B64 secret
```

> ⚠️ Store `release.keystore` and its passwords in a secure vault (e.g., 1Password, Bitwarden).
> **Never commit the keystore to git.**

---

## Trigger a Build

After adding secrets, trigger the workflow by pushing a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Or use **GitHub → Actions → Build & Deploy → Run workflow** (manual dispatch).

