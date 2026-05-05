#!/bin/bash
# Quick Verification Script - SanibonaniSave Improvements
# Tests: Keyboard Scrolling, Session Timeout (3min), Platform Admin Login

echo "=========================================="
echo "SanibonaniSave - Quick Verification"
echo "Date: $(date)"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}VERIFICATION CHECKLIST${NC}"
echo ""

# Check 1: KeyboardAwareScrollColumn Utility
echo -n "1. Checking KeyboardAwareScrollColumn utility... "
if grep -q "KeyboardAwareScrollColumn" app/src/main/java/com/sanibonani/save/ui/utils/KeyboardAwareScroll.kt 2>/dev/null; then
    echo -e "${GREEN}✓ FOUND${NC}"
else
    echo -e "${RED}✗ NOT FOUND${NC}"
fi

# Check 2: SessionConfig with 3-minute timeout
echo -n "2. Checking SessionConfig (180 seconds timeout)... "
if grep -q "PASSWORD_RESET_SESSION_TIMEOUT_SECONDS = 180" app/src/main/java/com/sanibonani/save/domain/utils/SessionConfig.kt 2>/dev/null; then
    echo -e "${GREEN}✓ FOUND${NC}"
else
    echo -e "${RED}✗ NOT FOUND${NC}"
fi

# Check 3: Platform Admin Credentials Config
echo -n "3. Checking PlatformAdminAuthPolicy config... "
if grep -q "torryymsimango@gmail.com" app/src/main/java/com/sanibonani/save/domain/utils/SessionConfig.kt 2>/dev/null; then
    echo -e "${GREEN}✓ FOUND${NC}"
else
    echo -e "${RED}✗ NOT FOUND${NC}"
fi

# Check 4: PasswordRecoveryScreen Updated
echo -n "4. Checking PasswordRecoveryScreen updated... "
if grep -q "KeyboardAwareScrollColumn" app/src/main/java/com/sanibonani/save/ui/screens/auth/PasswordRecoveryScreen.kt 2>/dev/null; then
    echo -e "${GREEN}✓ UPDATED${NC}"
else
    echo -e "${RED}✗ NOT UPDATED${NC}"
fi

# Check 5: RegisterScreen Updated
echo -n "5. Checking RegisterScreen keyboard handling... "
if grep -q "KeyboardAwareScrollColumn" app/src/main/java/com/sanibonani/save/ui/screens/auth/AuthScreens.kt 2>/dev/null; then
    echo -e "${GREEN}✓ UPDATED${NC}"
else
    echo -e "${RED}✗ NOT UPDATED${NC}"
fi

echo ""
echo -e "${YELLOW}MANUAL TESTING NEEDED${NC}"
echo "=========================================="
echo ""
echo "Test These Scenarios:"
echo "1. Open app and test keyboard scrolling:"
echo "   - Password Recovery: Check form scrolls when keyboard appears"
echo "   - Registration: Check all fields visible while typing"
echo "   - Password Reset: Check new password field visible"
echo ""
echo "2. Test 3-minute session timeout:"
echo "   - Request password reset email"
echo "   - Open reset link"
echo "   - Wait 3+ minutes WITHOUT updating password"
echo "   - Verify session expired error OR redirect to login"
echo ""
echo "3. Test Platform Admin Login:"
echo "   - Login with:"
echo "     Email:    torryymsimango@gmail.com"
echo "     Password: torry123M"
echo "   - Verify admin portal loads successfully"
echo ""
echo "=========================================="
echo ""

# Test Commands Reference
echo -e "${YELLOW}BUILD COMMANDS${NC}"
echo "=========================================="
echo "Debug Build:"
echo "  ./gradlew clean build -x lintVitalRelease"
echo ""
echo "Run on Emulator:"
echo "  ./gradlew installDebug"
echo ""
echo "=========================================="

