#!/bin/bash
# Phase 1 Bootstrap Script - Automated Setup for Deployment Preparation
# Run this to get Phase 1 environment ready

set -e  # Exit on error

echo "🚀 SanibonaniSave Phase 1 Bootstrap"
echo "===================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Functions
log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️ $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check prerequisites
echo "📋 Checking prerequisites..."
if ! command -v keytool &> /dev/null; then
    log_error "keytool not found. Install JDK."
    exit 1
fi
log_success "keytool available"

if ! command -v gradle &> /dev/null && ! [ -f "./gradlew" ]; then
    log_error "Gradle not found."
    exit 1
fi
log_success "Gradle available"

echo ""
echo "🔑 Step 1: Generate Release Keystore"
echo "======================================"

if [ -f "release.keystore" ]; then
    log_warning "release.keystore already exists. Skipping generation."
else
    echo "Enter keystore password (min 6 chars): "
    read -s KEYSTORE_PASSWORD
    echo "Confirm password: "
    read -s KEYSTORE_PASSWORD_CONFIRM

    if [ "$KEYSTORE_PASSWORD" != "$KEYSTORE_PASSWORD_CONFIRM" ]; then
        log_error "Passwords don't match!"
        exit 1
    fi

    echo "Enter your name: "
    read NAME

    keytool -genkey -v -keystore release.keystore \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -alias sanibonani_release \
        -dname "CN=$NAME, O=SanibonaniSave, L=South Africa, C=ZA" \
        -storepass "$KEYSTORE_PASSWORD" \
        -keypass "$KEYSTORE_PASSWORD"

    log_success "Keystore generated: release.keystore"
    log_warning "IMPORTANT: Save this password securely. Add to GitHub Secrets."
    echo "Password: $KEYSTORE_PASSWORD"
fi

echo ""
echo "🔐 Step 2: Verify Gradle Signing Config"
echo "========================================"

if grep -q "signingConfigs" app/build.gradle.kts; then
    log_success "Signing config already in build.gradle.kts"
else
    log_warning "Adding signing config to app/build.gradle.kts"
    cat >> app/build.gradle.kts << 'EOF'

// Release signing (added by Phase 1 bootstrap)
signingConfigs {
    create("release") {
        storeFile = file("release.keystore")
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
        keyAlias = "sanibonani_release"
        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
    }
}
EOF
fi

echo ""
echo "🧪 Step 3: Run Unit Tests"
echo "========================="

echo "Running test suite (this may take 2-3 minutes)..."
if ./gradlew test --continue; then
    log_success "All tests passed!"
else
    log_warning "Some tests failed. Review and fix before proceeding."
fi

echo ""
echo "📦 Step 4: Build Release Bundle"
echo "================================"

if [ -z "$KEYSTORE_PASSWORD" ]; then
    echo "Enter keystore password (for release build): "
    read -s KEYSTORE_PASSWORD
fi

export KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export KEY_PASSWORD="$KEYSTORE_PASSWORD"

if ./gradlew bundleRelease; then
    APK_SIZE=$(du -h app/release/app.aab | cut -f1)
    log_success "Release bundle created: app/release/app.aab (Size: $APK_SIZE)"

    if [ $(stat -f%z app/release/app.aab 2>/dev/null || stat -c%s app/release/app.aab 2>/dev/null) -lt 62914560 ]; then
        log_success "APK size is within limits (< 60 MB)"
    else
        log_warning "APK size exceeds 60 MB. Consider optimization."
    fi
else
    log_error "Failed to build release bundle"
    exit 1
fi

echo ""
echo "🔍 Step 5: Security Verification"
echo "================================="

if strings app/release/app.aab 2>/dev/null | grep -iE "supabase_key|yoco|whatsapp|api_key" > /dev/null; then
    log_error "WARNING: Sensitive data found in APK!"
    log_warning "Check proguard-rules.pro configuration"
else
    log_success "No obvious secrets detected in APK"
fi

echo ""
echo "📋 Step 6: Generate GitHub Secrets Template"
echo "==========================================="

KEYSTORE_B64=$(base64 < release.keystore | tr -d '\n')

cat > .github/secrets-template.env << EOF
# Add these to GitHub repo Settings → Secrets → Actions

KEYSTORE_B64=$KEYSTORE_B64
KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD
KEY_PASSWORD=$KEYSTORE_PASSWORD

# From local.properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=eyJhbGci...
SUPABASE_SERVICE_ROLE_KEY=eyJhbGci...
YOCO_PUBLIC_KEY=pk_live_...
WHATSAPP_TOKEN=your_token
GEOAPIFY_API_KEY=your_key
FIREBASE_CONFIG_JSON=$(base64 < app/google-services.json | tr -d '\n')

# Google Play Console
GOOGLE_PLAY_KEY_JSON=your_service_account_json_base64_encoded
EOF

log_success "GitHub Secrets template generated: .github/secrets-template.env"
log_warning "⚠️  IMPORTANT: This contains sensitive data. Add to .gitignore immediately!"
echo "Command: echo '.github/secrets-template.env' >> .gitignore"

echo ""
echo "✅ Phase 1 Bootstrap Complete!"
echo "=============================="
echo ""
echo "📝 Next Steps:"
echo "1. Review PHASE_1_KICKOFF_BUILD_SECRETS.md"
echo "2. Add secrets to GitHub: https://github.com/YOUR_ORG/SanibonaniSave_Full/settings/secrets/actions"
echo "3. Add .github/secrets-template.env to .gitignore"
echo "4. Create .github/workflows/deploy.yml (copy from roadmap)"
echo "5. Tag a release to test: git tag v0.1.0-test && git push origin v0.1.0-test"
echo ""
echo "🎯 Phase 1 Checklist: PHASE_1_KICKOFF_BUILD_SECRETS.md"
echo ""

