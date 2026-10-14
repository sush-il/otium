# Focus Block

An offline Android app blocker (Kotlin, Jetpack Compose). Single-module Gradle project:
the repo root **is** the app.

## Layout
- `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradlew` — the Gradle build
- `src/main/` — app code (`java/com/focusblock/…`), `res/`, `assets/`, `AndroidManifest.xml`
- `src/test/` — unit tests
- `docs/` — `PLAN.md` (build plan + feature catalogue) and `NOTES.md` (reverse-engineering notes)
- `misc/` — reference material not needed to build (git-ignored)

## Build
```
./gradlew assembleDebug        # debug APK -> build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # release APK (needs a signing keystore to install)
```
