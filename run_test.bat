@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio1\jbr"
set "USERPROFILE=C:\Users\CRISS"
set "HOME=C:\Users\CRISS"
call gradlew.bat :domain:testDebugUnitTest --tests com.sanibonani.save.LiveSimulationJVMRunner.runDeepHistoricalActivitySimulation -c settings.jvm.gradle.kts -I init_polyfill.gradle --no-daemon
