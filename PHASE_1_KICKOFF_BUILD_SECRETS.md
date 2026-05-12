# 🚀 Phase 1 Kickoff: Build & Secrets (Weeks 1-2)

**Status**: READY TO START  
**Duration**: 2 weeks  
**Goal**: Production-ready build with secure secrets management

---

## ✅ Phase 1 Checklist

### Week 1: Release Signing & Environment Setup

#### Day 1-2: Generate Release Keystore
- [ ] Generate keystore:
  ```bash
  keytool -genkey -v -keystore release.keystore \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias sanibonani_release
  ```
- [ ] Store keystore **securely** (not in git, use encrypted vault)
- [ ] Document keystore password safely
- [ ] Create backup copy

#### Day 3: Update Gradle Build Config
- [ ] Edit `app/build.gradle.kts`:
  ```kotlin
  signingConfigs {
      release {
          storeFile = file("release.keystore")
          storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
          keyAlias = "sanibonani_release"
          keyPassword = System.getenv("KEY_PASSWORD") ?: ""
      }
  }
  ```
- [ ] Configure ProGuard rules for minification
- [ ] Test minified build locally

#### Day 4: GitHub Secrets Setup
- [ ] Go to GitHub repo → Settings → Secrets → Actions
- [ ] Add these secrets:
  ```
  KEYSTORE_B64           (base64 encoded keystore)
  KEYSTORE_PASSWORD      (keystore password)
  KEY_PASSWORD           (key password)
  SUPABASE_URL           (from .env)
  SUPABASE_ANON_KEY      (from .env)
  SUPABASE_SERVICE_ROLE  (if needed)
  YOCO_PUBLIC_KEY        (payment key)
  WHATSAPP_TOKEN         (messaging)
  GEOAPIFY_API_KEY       (maps)
  FIREBASE_CONFIG_JSON   (google-services.json base64)
  ```

#### Day 5: CI/CD Pipeline Setup
- [ ] Create `.github/workflows/deploy.yml` (use template provided)
- [ ] Test workflow with tag: `git tag v0.1.0-test && git push origin v0.1.0-test`
- [ ] Verify workflow runs successfully

---

### Week 2: Testing & Performance Verification

#### Day 1-2: Build Release APK
- [ ] Build release variant locally:
  ```bash
  ./gradlew bundleRelease
  ```
- [ ] Verify APK size: **Target < 60 MB**
- [ ] Sign with release keystore
- [ ] Test on device/emulator

#### Day 3: Run Complete Test Suite
- [ ] Run all unit tests:
  ```bash
  ./gradlew test --continue
  ```
- [ ] Run integration tests:
  ```bash
  ./gradlew connectedAndroidTest
  ```
- [ ] Verify **3 consecutive clean runs** (no flaky failures)
- [ ] Fix any failing tests immediately

#### Day 4: Performance Baseline
- [ ] Measure startup time (target: < 2 sec)
- [ ] Check memory footprint (target: < 300 MB average)
- [ ] Test offline functionality
- [ ] Verify Firebase Crashlytics integration

#### Day 5: Pre-Flight Review
- [ ] Verify no secrets in APK:
  ```bash
  strings app/release/app.aab | grep -i "key\|token\|password" # should be empty
  ```
- [ ] Review ProGuard rules for libraries
- [ ] Test obfuscated code paths
- [ ] Document any breaking changes

---

## 🎯 Phase 1 Success Criteria

### Build Quality
- ✅ Release APK/AAB builds without warnings
- ✅ ProGuard minification enabled with 0 errors
- ✅ APK size < 60 MB (uncompressed)
- ✅ No hardcoded secrets in APK

### Test Coverage
- ✅ All unit tests pass (3 consecutive runs)
- ✅ Integration tests pass
- ✅ Flaky tests stabilized (if any)
- ✅ Crash rate baseline established

### Infrastructure
- ✅ GitHub Secrets configured
- ✅ CI/CD pipeline working (tested with tag)
- ✅ Firebase Crashlytics live
- ✅ APK signing verified

---

## 📋 Configuration Templates

### `.github/workflows/deploy.yml`

```yaml
name: Build & Deploy

on:
  push:
    tags:
      - 'v[0-9]+.[0-9]+.[0-9]+'

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Decode Keystore
        run: |
          echo "${{ secrets.KEYSTORE_B64 }}" | base64 -d > release.keystore
      
      - name: Decode google-services.json
        run: |
          echo "${{ secrets.FIREBASE_CONFIG_JSON }}" | base64 -d > app/google-services.json
      
      - name: Create local.properties
        run: |
          cat > local.properties << EOF
          sdk.dir=$ANDROID_HOME
          SUPABASE_URL=${{ secrets.SUPABASE_URL }}
          SUPABASE_ANON_KEY=${{ secrets.SUPABASE_ANON_KEY }}
          YOCO_PUBLIC_KEY=${{ secrets.YOCO_PUBLIC_KEY }}
          WHATSAPP_TOKEN=${{ secrets.WHATSAPP_TOKEN }}
          GEOAPIFY_API_KEY=${{ secrets.GEOAPIFY_API_KEY }}
          EOF
      
      - name: Run Tests
        run: ./gradlew test --continue
      
      - name: Build Release Bundle
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew bundleRelease
      
      - name: Upload to Play Store (Internal)
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.GOOGLE_PLAY_KEY_JSON }}
          packageName: com.sanibonani.save
          releaseFiles: app/release/app.aab
          track: internal
          status: completed
```

### `build.gradle.kts` (Release Config)

```kotlin
android {
    // ...existing code...
    
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "sanibonani_release"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            buildConfigField("boolean", "RELEASE_BUILD", "true")
            
            // Enable crash reporting
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotesFile = "release-notes.txt"
                groupsFile = "groups.txt"
            }
        }
    }
}
```

### `proguard-rules.pro` (Additions for libraries)

```proguard
# Hilt
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class dagger.hilt.android.internal.lifecycle.DefaultActivityViewModelFactory
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# Supabase
-keep class com.supabase.** { *; }
-keep interface com.supabase.** { *; }
-keepclassmembers class com.supabase.** { *; }

# Serialization (kotlinx.serialization)
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.SerializableKt *;
}

# Keep custom data models
-keep class com.sanibonani.save.domain.model.** { *; }
-keep @kotlinx.serialization.Serializable class ** { *; }
-keep @android.os.Parcelable class ** { *; }

# Room
-keep @androidx.room.Entity class **
-keep @androidx.room.Dao interface **
```

---

## 🔧 Quick Commands for Phase 1

### Build Release Locally
```bash
# Set environment variables
export KEYSTORE_PASSWORD="your_password"
export KEY_PASSWORD="your_key_password"

# Build bundle
./gradlew bundleRelease

# Build APK (for local testing)
./gradlew assembleRelease
```

### Test Everything
```bash
# Run all tests
./gradlew test --continue

# Run specific test class
./gradlew :app:testDebugUnitTest --tests "com.sanibonani.save.GroupViewModelTest"

# Check for crashes
./gradlew :app:testDebugUnitTest --info
```

### Verify APK
```bash
# Check size
ls -lh app/release/app.aab

# Verify signing
jarsigner -verify -verbose -certs app/release/app-release.apk

# Check for secrets (should be empty)
strings app/release/app.aab | grep -iE "key|token|password|secret" | head -20
```

### Prepare for CI/CD
```bash
# Test keystore encoding
base64 -i release.keystore > keystore.b64

# Test with GitHub (local)
export KEYSTORE_PASSWORD="test"
export KEY_PASSWORD="test"
./gradlew bundleRelease
```

---

## 📊 Progress Tracking

### Week 1 Progress
- [ ] Day 1-2: Keystore generated ______%
- [ ] Day 3: Gradle config updated ______%
- [ ] Day 4: GitHub secrets added ______%
- [ ] Day 5: CI/CD pipeline tested ______%

**Week 1 Target**: 100% by Friday EOD

### Week 2 Progress
- [ ] Day 1-2: Release build verified ______%
- [ ] Day 3: All tests passing ______%
- [ ] Day 4: Performance baseline ______%
- [ ] Day 5: Pre-flight review ______%

**Week 2 Target**: 100% by Friday EOD

---

## 🚨 Troubleshooting

### Issue: Keystore password not working
```bash
# Verify keystore
keytool -list -v -keystore release.keystore

# Test in build (locally)
KEYSTORE_PASSWORD="correct_password" ./gradlew bundleRelease
```

### Issue: Tests fail on CI/CD
```bash
# Run same tests locally with CI environment
./gradlew test --continue --info

# Check for flaky tests
for i in {1..3}; do ./gradlew test --continue || exit; done
```

### Issue: APK too large (> 60 MB)
```bash
# Analyze APK size
./gradlew bundleRelease
# Download: https://github.com/google/bundletool
bundletool analyze-bundle --bundle=app/release/app.aab

# Common culprits:
# - Unused resources (enableResourceStripping)
# - Unoptimized images
# - Duplicate dependencies
```

### Issue: Secrets visible in APK
```bash
# Rebuild with ProGuard
./gradlew assembleRelease

# Verify again
strings app/release/app-release.apk | grep -i "supabase"  # should be empty
```

---

## ✉️ Handoff to Phase 2

Once Phase 1 is complete (end of Week 2):
- [ ] Document any issues/learnings
- [ ] Generate Phase 1 completion report
- [ ] Brief Phase 2 team on any blockers
- [ ] Schedule Phase 2 kickoff (Week 3)

**Phase 2 starts**: Monday, Week 3
**Phase 2 lead**: Backend/DevOps engineer
**Phase 2 focus**: Supabase production setup, database optimization

---

## 📞 Support During Phase 1

- **Questions**: Check `DEPLOYMENT_MCP_AGENT_ROADMAP.md` Phase 1 section
- **Blockers**: Create GitHub issue with `phase-1` label
- **Weekly Sync**: Tuesday 10am UTC

---

**Phase 1 Status**: 🟢 READY TO KICKOFF

**Start Date**: This Week  
**Expected Completion**: End of Week 2  
**Next Phase**: Week 3 (Backend Hardening)

