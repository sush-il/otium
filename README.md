# Otium

An offline Android app and website blocker. Pick the apps and sites that eat your time, set the rules, and Otium closes them when your time is up. Otium is Latin for peaceful free time, the time you get back. Nothing leaves your phone: no account, no internet permission, no ads, no tracking.

## What it does

- **Profiles** – groups of apps and sites with their own rules. A rule can be: always blocked, blocked on a weekly schedule, a daily or hourly time limit, a limit on how many times you open the app, or blocked until a date and time.
- **Per-app and per-site limits** – quick daily limits for a single app or website, stacked on top of your profiles. Browsers are read for the address, so a site can be limited even though the browser itself is allowed.
- **Keywords** – block pages whose address contains words you choose.
- **Shorts / Reels / adult content** – close YouTube Shorts, Instagram Reels and Facebook Reels without blocking the whole app, and block a bundled list of adult sites.
- **Time-left timer** – a small floating tab shows how much time an app has left, plus a brief flash at 10, 5 and 1 minutes.
- **Block screen** – when a rule says no, a calm full-screen page says why and sends you home.
- **Modes** – how hard it is to loosen your own rules:
  - **Normal** – change anything any time.
  - **Lock** – you must type a long random number before you can edit profiles (and optionally open the app or Settings).
  - **Strict** – set a time or schedule during which rules, Settings and uninstalling are locked. An emergency unlock exists but is slow (a long wait that doubles each time).
- **Backup, home-screen widget, dark and light themes** (pastel Catppuccin look).

## Install

1. Download `Otium-<version>.apk` from the [Releases](../../releases) page and open it. Android will ask to allow installs from your browser or file manager.
2. Open Otium and grant the permissions below. It walks you through each one.

Needs Android 8.0 (API 26) or newer.

### Permissions, and why

| Permission | Why |
| --- | --- |
| Accessibility service | Knows which app is in front, and reads the browser address bar. It never reads other screen content and sends nothing anywhere. |
| Usage access | Counts daily time spent per app for limits. |
| Display over other apps | Shows the floating timer and lets the block screen appear on top. |
| Notifications | Low-time alerts if overlays are off. |
| Device admin (optional) | Only for Strict mode, to stop the app being uninstalled. |
| See all installed apps | So the app picker lists everything, not just apps with a launcher icon. |

On some phones, sideloaded apps can't use accessibility until you allow it: *App info → ⋮ → Allow restricted settings*.

There is no permanent notification and no foreground service. The accessibility service is the only thing running, and it only wakes on window changes.

## How it works

The accessibility service reports window changes to a small engine. The engine checks the app in front against your rules (a pure Kotlin evaluator, unit tested) and schedules the next check for exactly when something could change, instead of polling. When a rule blocks, it starts the block screen.

Stack: Kotlin, Jetpack Compose (single activity), Room, DataStore, coroutines, manual dependency injection.

```
src/main/java/com/focusblock/
  domain/    rule evaluation, modes, emergency unlock (pure Kotlin, unit tested)
  data/      Room database, settings, usage stats, backup
  service/   accessibility service, block engine, timer overlay
  ui/        Compose screens
```

## Build

```
./gradlew assembleDebug        # debug APK -> build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # signed release APK (see below)
```

Needs JDK 21 and the Android SDK. For a signed release, add these to `~/.gradle/gradle.properties` (never commit them): `FOCUSBLOCK_STORE_FILE`, `FOCUSBLOCK_STORE_PASSWORD`, `FOCUSBLOCK_KEY_ALIAS`, `FOCUSBLOCK_KEY_PASSWORD`. Without them the release APK is unsigned and won't install. Keep the keystore safe: updates must be signed with the same key.
