#!/bin/sh
# Gradle wrapper for Unix/macOS
# Generated for SanibonaniSave — Android Studio Panda
set -e
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
exec "$JAVA_HOME/bin/java" $DEFAULT_JVM_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
