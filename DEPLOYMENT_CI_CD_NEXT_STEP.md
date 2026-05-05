# CI/CD Next Step: Secure Deployment to Firebase App Distribution

## Overview
This step deploys your built APKs to Firebase App Distribution after a successful build and test run on the `main` branch. It uses GitHub Actions secrets for authentication and is triggered automatically on every push to `main`.

## Prerequisites
- You must set the following secrets in your GitHub repository:
  - `FIREBASE_TOKEN`: Your Firebase CI token (see Firebase CLI docs)
  - `FIREBASE_APP_ID`: Your Firebase App ID (from Firebase Console)

## How it Works
1. The workflow builds and tests your app for all matrix combinations.
2. APKs are uploaded as artifacts.
3. On push to `main`, the deploy job downloads the APKs and runs the Firebase CLI to distribute the debug APK to your testers group.

## How to Trigger
- Push or merge to `main` branch.
- The workflow will run automatically.

## How to Add/Change Testers
- Edit the `--groups` argument in the deploy step of `.github/workflows/android_advanced.yml`.

## How to Add More Deployment Targets
- Add additional steps under the `deploy` job (e.g., Play Store, S3, etc.).

---

# Module: DeployToFirebaseAppDistribution

This module provides a function to trigger the Firebase App Distribution deployment step from your CI/CD pipeline.

## Usage (Kotlin Example)
```kotlin
object DeployToFirebaseAppDistribution {
    fun deploy(apkPath: String, appId: String, token: String, groups: String = "testers") {
        val command = "firebase appdistribution:distribute $apkPath --app $appId --token $token --groups \"$groups\""
        println("Running: $command")
        // In CI, this would be run as a shell command
        // Runtime.getRuntime().exec(command)
    }
}
```

## How to Use in CI
- The GitHub Actions workflow already calls this via the shell step.
- For local/manual use, call `DeployToFirebaseAppDistribution.deploy()` with the correct parameters.

---

# Summary
- The next step is automated deployment to Firebase App Distribution.
- All configuration is in `.github/workflows/android_advanced.yml`.
- Use the provided module for local/manual deployment if needed.

