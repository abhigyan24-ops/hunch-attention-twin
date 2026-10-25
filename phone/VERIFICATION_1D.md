# Stage 1D verification — 2026-10-25

Detected SDK android-37.0 and Build Tools 36.0.0; configured compile SDK 37.0 and target SDK 37, min SDK 26. JDK 21.0.11; Gradle 9.4.1. Wrapper generated using installed Gradle:

```text
> Task :wrapper

BUILD SUCCESSFUL in 37s
1 actionable task: 1 executed
```

Root commands, both exit 0:

```text
schema-sync: PASS (1.0.0; 1 Kotlin version file(s))
twin-purity: PASS (5 twin file(s); absent modules are not target verification)
```

Build command: `gradlew.bat --gradle-user-home .gradle --no-daemon '-Dorg.gradle.internal.http.connectionTimeout=15000' '-Dorg.gradle.internal.http.socketTimeout=15000' assembleDebug testDebugUnitTest`, with JAVA_HOME set to the installed JDK, ANDROID_HOME set to the detected SDK and JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true. Exit 0; exact terminal summary:

```text
BUILD SUCCESSFUL in 5m 3s
43 actionable tasks: 43 executed
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.4.1/userguide/configuration_cache_enabling.html
```

The JUnit XML confirms SchemaVersionTest: tests=1, failures=0, errors=0, skipped=0. APK: app/build/outputs/apk/debug/app-debug.apk (12748826 bytes).

Nonfatal warning:

```text
Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so. Run with --info option to learn more.
```

Setup failures and recovery are recorded in ../DECISIONS_PENDING.md (consolidated Stage 1D notes). No app install, device/emulator launch or notification/hardware check was performed: not verified on target.
