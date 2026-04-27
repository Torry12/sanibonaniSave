# DEVELOPERS.md — SanibonaniSave Development Setup Guide

## Prerequisites

- **Android Studio** Panda (2024.2.1) or later
- **Java 17** (managed by Android Studio)
- **Gradle 8.11.1** (managed by Gradle Wrapper)
- **Git** for version control
- **Supabase Account** (create at supabase.com)
- **Firebase Project** (create at console.firebase.google.com)

---

## 🚀 Getting Started (5 minutes)

### 1. Clone & Open Project
```bash
git clone <repo-url>
cd SanibonaniSave_Full
open -a "Android Studio" .  # macOS
# or File → Open in Android Studio
```

### 2. Configure Secrets

Copy `local.properties.template` → `local.properties`:
```bash
cp local.properties.template local.properties
```

Edit `local.properties` with your credentials:

```properties
# SDK (auto-set by Android Studio on first run)
sdk.dir=/Users/YOUR_NAME/Library/Android/sdk

# Supabase (get from your Supabase project settings)
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=eyJhbGci... (Anon key, safe to expose)
SUPABASE_SERVICE_ROLE_KEY=eyJhbGci... (Service key, KEEP SECRET)

# YoCo Payment Gateway (get from YoCo dashboard)
YOCO_PUBLIC_KEY=pk_live_... (or pk_test_ for sandbox)
YOCO_WEBHOOK_SECRET=whsec_... (for webhook validation)

# WhatsApp Business API (optional, for notifications)
WHATSAPP_TOKEN=your_meta_business_token
WHATSAPP_PHONE_NUMBER_ID=your_whatsapp_phone_id

# Firebase signing (for release builds only)
KEYSTORE_PATH=/path/to/release.keystore
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=sanibonani
KEY_PASSWORD=your_key_password
```

**⚠️ Important**: 
- `local.properties` is GITIGNORED — never commit secrets
- Different values needed for dev/staging/production
- Ask team lead for actual credentials

### 3. Add Firebase Config

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create new project or use existing
3. Add Android app:
   - Package: `com.sanibonani.save`
   - SHA-1: Run `./gradlew signingReport` to get fingerprints
4. Download `google-services.json`
5. Place in `app/google-services.json`

### 4. Sync Gradle

Android Studio should auto-sync. If not:
```bash
./gradlew clean build
```

Expected download: ~300MB dependencies (one-time)

### 5. Run on Emulator/Device

1. **Emulator**: 
   - Tools → Device Manager → Create Virtual Device (Pixel 5, API 30+)
   - Select emulator, click ▶ Run

2. **Real Device**:
   - Enable USB Debugging: Settings → Developer Options → USB Debugging
   - Plug in via USB
   - Device appears in IDE: Run → Select Device

---

## 📂 Project Structure

```
SanibonaniSave_Full/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/sanibonani/save/
│   │   │   │   ├── MainActivity.kt           ← App entry
│   │   │   │   ├── SanibonaniApp.kt          ← Hilt setup
│   │   │   │   ├── di/AppModule.kt           ← DI configuration
│   │   │   │   ├── data/
│   │   │   │   │   ├── Constants.kt          ← Magic numbers
│   │   │   │   │   ├── model/Models.kt       ← Data classes
│   │   │   │   │   ├── local/
│   │   │   │   │   │   └── SanibonaniDatabase.kt  ← Room DB
│   │   │   │   │   ├── remote/
│   │   │   │   │   │   └── SupabaseManager.kt    ← Auth
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   └── Repositories.kt  ← 6 repos
│   │   │   │   │   ├── validation/
│   │   │   │   │   │   └── InputValidator.kt ← Input checks
│   │   │   │   │   ├── logging/
│   │   │   │   │   │   └── AppLogger.kt     ← Logging
│   │   │   │   │   └── errors/
│   │   │   │   │       └── ErrorMessageMapper.kt
│   │   │   │   ├── viewmodel/ViewModels.kt  ← 5 ViewModels
│   │   │   │   ├── ui/
│   │   │   │   │   ├── navigation/NavGraph.kt
│   │   │   │   │   ├── theme/Theme.kt
│   │   │   │   │   ├── components/
│   │   │   │   │   │   └── SharedComponents.kt
│   │   │   │   │   └── screens/
│   │   │   │   │       ├── auth/
│   │   │   │   │       ├── landing/
│   │   │   │   │       ├── browse/
│   │   │   │   │       ├── group/
│   │   │   │   │       ├── member/
│   │   │   │   │       ├── admin/
│   │   │   │   │       └── payment/
│   │   │   │   ├── service/
│   │   │   │   │   └── SanibonaniFirebaseService.kt
│   │   │   │   └── worker/
│   │   │   │       └── FeeEnforcementWorker.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── res/
│   │   │       ├── values/
│   │   │       │   ├── strings.xml
│   │   │       │   └── colors.xml
│   │   │       ├── drawable/
│   │   │       └── layout/
│   │   ├── test/                             ← Unit tests
│   │   │   └── java/com/sanibonani/save/
│   │   └── androidTest/                      ← Instrumented tests
│   │       └── java/com/sanibonani/save/
│   ├── build.gradle.kts                      ← App-level build
│   ├── proguard-rules.pro                    ← ProGuard config
│   └── google-services.json                  ← Firebase (not in git)
├── gradle/
│   └── libs.versions.toml                    ← Version catalog
├── supabase/
│   ├── schema.sql                            ← Database schema
│   └── functions/                            ← Edge functions
├── build.gradle.kts                          ← Root build
├── settings.gradle.kts                       ← Project settings
├── local.properties                          ← Secrets (NOT in git)
├── local.properties.template                 ← Template for secrets
├── README.md                                 ← Project overview
├── AGENTS.md                                 ← AI agent guide
├── SHORTCOMINGS.md                           ← Known issues
└── DEVELOPERS.md                             ← This file
```

---

## 🔧 Common Development Tasks

### Building & Running

```bash
# Clean build (removes old artifacts)
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Run on connected device/emulator
./gradlew installDebugAndRunTests

# Build release APK (requires signing config)
./gradlew assembleRelease
```

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run tests with coverage
./gradlew testDebugUnitTest --coverage
```

### Linting & Code Quality

```bash
# Run ktlint (Kotlin formatter)
./gradlew ktlintFormat

# Check ktlint without auto-fix
./gradlew ktlintCheck

# Build lint report
./gradlew lint
```

### Database Management

```bash
# View Room schema (compiled on build)
cat app/schemas/com.sanibonani.save.data.local.SanibonaniDatabase/5.json

# Access Room DB in emulator:
# 1. Install app
# 2. Android Studio → Tools → Device File Explorer
# 3. Navigate: data/data/com.sanibonani.save/databases/sanibonani.db
# 4. Download, open in DB Browser for SQLite
```

---

## 🔐 Managing Secrets Securely

### Development
```properties
# local.properties (NEVER commit)
SUPABASE_URL=https://dev-project.supabase.co
SUPABASE_ANON_KEY=eyJhbGci... (dev anon key)
YOCO_PUBLIC_KEY=pk_test_... (YoCo test key)
```

### Staging / Production
- Credentials stored in CI/CD secrets (GitHub Actions / GitLab CI)
- Never store in code, config files, or documentation
- Use environment variables or secret management service

### Retrieving Credentials

1. **Supabase**: Project Settings → API
2. **Firebase**: Project Settings → Service Accounts
3. **YoCo**: Dashboard → API Keys
4. **WhatsApp**: Meta Business Manager → Settings

---

## 🐛 Debugging

### Logcat Filtering
```bash
# Show only app logs
./gradlew --stacktrace -q build

# Filter by tag in Android Studio:
# Logcat → Filter → "SanibonaniSave" or "GroupRepository"
```

### Network Requests

Add to `local.properties` for verbose Supabase logging:
```properties
# Enable network logging (debug builds only)
OKHTTP_LOG_LEVEL=BODY
```

Then in code:
```kotlin
// AppModule.kt — add to Ktor client config:
install(HttpClientFeature) {
    install(JsonFeature)
    install(Logging) {
        level = LogLevel.BODY  // DEVELOPMENT ONLY
    }
}
```

### Database Inspection

```kotlin
// In any Activity/Fragment:
val groupDao = db.groupDao()
viewModelScope.launch {
    val allGroups = groupDao.observePublicGroups().first()
    Log.d("DB_DEBUG", "Cached groups: ${allGroups.size}")
}
```

---

## 📊 Version Management

### Current Versions
- **Kotlin**: 2.1.0
- **Gradle**: 8.11.1
- **AGP**: 8.7.3
- **Supabase**: 3.1.4
- **Compose BOM**: 2024.12.01
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)

### Updating Dependencies

Edit `gradle/libs.versions.toml`:
```toml
[versions]
kotlin = "2.2.0"  # Update version
# ...

[libraries]
kotlin-gradle = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
```

Then sync Gradle.

---

## 🚀 Deployment

### Debug Build (Development)
```bash
./gradlew installDebug
# App installs: com.sanibonani.save (title: "SanibonaniSave [DEV]")
```

### Release Build (Production)
```bash
# Requires signing config in local.properties
./gradlew assembleRelease

# APK output: app/build/outputs/apk/release/app-release.apk
```

### Firebase Deployment
```bash
# Upload to Firebase App Distribution (testers)
./gradlew appDistributionUploadRelease

# Or use Firebase Console for Play Store deployment
```

---

## 📝 Code Conventions

### Naming
- **Packages**: `com.sanibonani.save.{feature}.{submodule}`
- **Classes**: `PascalCase` (e.g., `GroupRepository`)
- **Functions**: `camelCase` (e.g., `getGroupById`)
- **Constants**: `UPPER_SNAKE_CASE` (in `Constants.kt`)
- **Private vars**: `_prefixed` (e.g., `_state`)

### File Organization
```kotlin
// 1. Imports
import ...

// 2. Package constant/helper classes
object Mappers { ... }
fun Group.toEntity() { ... }

// 3. Interfaces
interface GroupRepository { ... }

// 4. Implementations (with Hilt @Inject)
class GroupRepositoryImpl(...) : GroupRepository { ... }
```

### Comment Style
```kotlin
// Single-line comments for quick notes

/**
 * Docstring comments for public APIs
 * Explain what, why, and usage
 */
fun publicFunction() { ... }

// ─────────────────────────────────────────────────────────────────────────────
//  SECTION HEADERS (use for major sections)
// ─────────────────────────────────────────────────────────────────────────────
```

### State Management
```kotlin
// GOOD: Immutable state updates
_state.update { it.copy(loading = true) }

// BAD: Direct mutation
_state.value.loading = true  // DON'T DO THIS

// GOOD: Coroutine scope for async
viewModelScope.launch {
    val data = repo.fetch()
}

// BAD: Global scope
GlobalScope.launch { ... }  // DON'T DO THIS
```

---

## 🤝 Contributing

### Before Submitting PR

1. **Format code**:
   ```bash
   ./gradlew ktlintFormat
   ```

2. **Run tests**:
   ```bash
   ./gradlew test
   ```

3. **Check lint**:
   ```bash
   ./gradlew lint
   ```

4. **Update documentation** if adding features

### PR Checklist
- [ ] Builds without errors
- [ ] Tests pass locally
- [ ] Code formatted with ktlint
- [ ] No hardcoded secrets
- [ ] No debug logs left in
- [ ] No commented-out code
- [ ] Meaningful commit messages

---

## 📞 Getting Help

### Common Issues

**Issue**: `Unable to resolve dependency for ':app@debug/compileClasspath': Could not find org.jetbrains.kotlin:kotlin-stdlib`

**Solution**:
```bash
./gradlew clean
./gradlew build
```

---

**Issue**: `local.properties not found`

**Solution**:
```bash
cp local.properties.template local.properties
# Edit with your credentials
```

---

**Issue**: `FirebaseApp is not initialized`

**Solution**: 
- Ensure `google-services.json` is in `app/` directory
- Rebuild project

---

### Contact

- **Lead Dev**: [Team Slack]
- **Architecture Issues**: Check AGENTS.md
- **Known Issues**: See SHORTCOMINGS.md

---

*Last Updated: March 2026 | Version: 1.0*

