# Smart AI Ads Manager

A smart Android assistant that lets a user **explicitly select and connect** an installed
application, then helps them find and use the **Loading ad** control on that app's Mining
screen — while staying strictly within a safe, consent-first boundary.

The assistant **recognizes** the target screen with on-device OCR, **identifies** the exact
"Loading ad" control, **monitors** the loading state through a proper state machine, and
**records** rewards only when they are confirmed through a legitimate mechanism. It never
auto-clicks advertisements, never touches Offers / Subscribe / Wallet / Contracts / other
mining plans, and never manufactures impressions. **The user stays in control at every step.**

> ⚠️ This project is an *assistance/guidance* tool. It does not perform automated ad
> viewing. Completion is proven only by a real reward-confirmation signal — never by a timer.

---

## Features

- **App detection** — discovers installed applications via Android's package-visibility
  mechanism (`<queries>` + `QUERY_ALL_PACKAGES`), shows app name & icon, and supports
  search/filtering with individual **Connect / Disconnect** controls.
- **Explicit connection & consent** — an app is only used after the user connects it.
  The selected app is remembered in **encrypted** local storage (Android Keystore).
- **Strict target-screen restriction** — the Screen Recognition Engine recognizes the
  Mining screen and the exact **Loading ad** option. The UI only ever guides the user to
  the Loading ad control.
- **Smart ad recognition** — OCR + image-recognition matcher detects whether the Loading
  ad button is visible/available, detects loading indicators, and reports status/errors.
- **Loading state machine** — full capture + loading lifecycle:
  `IDLE → PERMISSION_REQUESTED → PERMISSION_GRANTED → CAPTURE_INITIALIZING →
  CAPTURE_ACTIVE → TARGET_APP_DETECTED → TARGET_SCREEN_DETECTED → SCREEN_DETECTED →
  TARGET_BUTTON_AVAILABLE → USER_ACTION_REQUIRED → LOADING → WAITING_FOR_VERIFICATION →
  COMPLETED`, plus `FAILED`, `TIMEOUT`, `CAPTURE_STOPPED`, `PERMISSION_REVOKED`,
  `STOPPED`. Elapsed time, timeout warnings, manual retry and stop are all supported.
  **No fixed delay is used as proof of completion.** A missing frame is never treated as
  proof that an advertisement completed.
- **Reward Manager** — verified / pending / completed / failed / unverified records with
  timestamps, app name and session history. Rewards are accepted only via an official API
  or the app's legitimate confirmation mechanism; otherwise a manual confirmation is
  recorded and clearly labeled **Unverified**.
- **Modular AI agent architecture** — AI Core, App Detection Engine, Screen Recognition
  Engine, Loading Monitor, Reward Manager, Session Manager, Permission Manager, Error
  Recovery Manager. The AI explains what is happening; it has no unrestricted control.
- **Premium dark UI** — purple-accent Material 3 dashboard with Connected Applications, AI
  Agent Status, Selected Application, Ad Assistance Status, Loading Monitor, Reward
  History and Start/Stop controls.
- **Privacy & security** — explicit consent, minimum permissions, no hidden background
  activity, no unauthorized recording, no credential collection, secure local storage,
  disconnect & delete-data options, and a clear **foreground notification** for any active
  monitoring.
- **Tests** — unit tests for app discovery, connection/disconnection, screen recognition,
  loading/timeout detection, duplicate-session prevention, reward verification, permission
  denial, and restart recovery. Screen recognition uses **mock OCR screens**, not real ads.

---

## Architecture

```
app/src/main/kotlin/com/smartai/adsmanager/
├── domain/                  # Pure Kotlin, JVM-testable core (no Android deps)
│   ├── LoadingState(.Machine)  # guarded state machine
│   ├── LoadingMonitor           # elapsed time, timeout, verification-driven completion
│   ├── MiningScreenMatcher      # OCR text → Mining screen + Loading ad detection
│   ├── VerificationDetector     # proves completion via a real signal
│   ├── RewardManager            # verified/manual reward handling
│   ├── SessionManager           # duplicate-session prevention + restart recovery
│   ├── ConnectionManager        # connect/disconnect/select
│   ├── ErrorRecoveryManager     # classify + plan recovery
│   ├── PermissionPolicy         # required permissions
│   ├── AiCore                   # explanation + guidance (Loading ad only)
│   └── RepositoryPorts          # repository interfaces
├── appdetection/            # Installed-app discovery (PackageManager + package visibility)
├── screenrecognition/       # ML Kit on-device OCR engine (pluggable interface)
├── service/                 # Screen capture architecture:
│                            #   ScreenCapturePermissionManager (MediaProjection consent),
│                            #   ScreenCaptureForegroundService (owns the capture session),
│                            #   MediaProjectionController (VirtualDisplay/ImageReader),
│                            #   ScreenFrameProcessor (background frame loop),
│                            #   AgentStateManager (state machine + status flow),
│                            #   MonitoringController (orchestrator), NotificationController,
│                            #   BootReceiver (restart recovery)
├── data/                    # Room database, DAOs, repositories, encrypted storage, DI
└── ui/                      # Compose UI: theme, dashboard, app list, assistance, rewards
```

**Data flow:** `MainActivity → ScreenCapturePermissionManager → MainViewModel →
ScreenCaptureForegroundService → MonitoringController → (MediaProjectionController +
ScreenFrameProcessor + AgentStateManager)`, with the UI observing `StateFlow`s exposed by
`MonitoringController`. The **foreground service owns the ongoing capture session**, so
monitoring continues while the user switches apps; `MainActivity` can disconnect/reconnect
without destroying the session.

**State machine** (`LoadingState`): `IDLE → PERMISSION_REQUESTED → PERMISSION_GRANTED →
CAPTURE_INITIALIZING → CAPTURE_ACTIVE → TARGET_APP_DETECTED → TARGET_SCREEN_DETECTED →
SCREEN_DETECTED → TARGET_BUTTON_AVAILABLE → USER_ACTION_REQUIRED → LOADING →
WAITING_FOR_VERIFICATION → COMPLETED`, plus `FAILED`, `TIMEOUT`, `CAPTURE_STOPPED`,
`PERMISSION_REVOKED`, `STOPPED`.

---

## Requirements

- **Android SDK** with platform `android-35` and build-tools 35.x
- **JDK 17**
- **Gradle 8.10.2** (the wrapper is included — use `./gradlew`)
- `minSdk = 24`, `targetSdk = 35`

## Build & run

From the repository root:

```bash
# Unit tests + lint + debug APK
./gradlew testDebugUnitTest lintDebug assembleDebug

# Install on a connected device/emulator
./gradlew installDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Tests

```bash
./gradlew testDebugUnitTest          # unit tests (JVM, no device needed)
./gradlew connectedAndroidTest       # instrumented Room tests (device/emulator)
```

Unit tests cover: app discovery, app connection/disconnection, screen recognition +
confidence scoring, the capture/permission state machine (`AgentStateManager`), loading
state detection, timeout handling, duplicate-session prevention, reward verification,
permission denial, and app-restart recovery.

## End-to-end test plan

The exact screen-capture scenario is verified manually on a device/emulator:

1. **Open** Smart AI Ads Manager. → The dashboard loads.
2. **Connect** the target application. → It appears under Connected Applications.
3. **Press Start Ad Assistance.** → The official Android screen-capture dialog appears.
4. **Grant** screen-capture permission, then **Start Now.** → **The app must NOT return
   to the launcher.** The foreground service starts and the dashboard shows
   **Screen Capture: ACTIVE** and **AI Agent: RUNNING**.
5. **Open the selected target app.** → The agent service keeps monitoring permitted
   screen states and reports them (the dashboard/notification reflects the state).
6. **Return to Smart AI Ads Manager.** → The previous session is still visible with the
   correct state; the session was not destroyed by backgrounding.
7. **Rotate the phone.** → The capture session resizes the existing VirtualDisplay
   (`MediaProjectionController.resize`) without creating an invalid second projection.
8. **Press Stop Assistance.** → Capture ends, all resources are released, and the
   foreground notification is removed.
9. **Deny screen-capture permission.** → The app stays stable and shows a clear
   explanation (no crash, no disappearance).

Also exercise: repeated permission grants, slow/stalled frame delivery (the "last frame"
indicator stops updating), process recreation, and low-memory conditions. A missing frame
is never treated as proof that an advertisement completed.

## Release build

1. Create a signing keystore:

   ```bash
   keytool -genkeypair -v -keystore release.keystore \
     -alias smartai -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Add a `signingConfig` for `release` in `app/build.gradle.kts` pointing at the keystore,
   or pass keystore properties. A common approach is to load credentials from
   `~/.gradle/gradle.properties` (keep these out of version control):

   ```properties
   RELEASE_STORE_FILE=/path/to/release.keystore
   RELEASE_KEY_ALIAS=smartai
   RELEASE_STORE_PASSWORD=****
   RELEASE_KEY_PASSWORD=****
   ```

3. Build the signed release APK:

   ```bash
   ./gradlew assembleRelease
   ```

   The signed APK appears at `app/build/outputs/apk/release/app-release.apk`.

> For Play Store distribution, replace the broad `QUERY_ALL_PACKAGES` permission with a
> `<queries>`-only policy and declare only the package categories you intend to support,
> as required by the Play Developer Program. See the manifest comments.

## Privacy & security notes

- MediaProjection (screen capture) requires **explicit, per-session user consent**.
- Monitoring runs only as a **foreground service** with a persistent notification.
- Connected app selections and settings are stored in **EncryptedSharedPreferences**
  (Android Keystore). Reward/session data lives in a local Room database and is excluded
  from backups.
- No credentials are collected or transmitted. The only network permission is for an
  optional official reward-confirmation API.

## GitHub Actions

`.github/workflows/android-build.yml` runs on every push/PR: it sets up JDK 17 + the
Android SDK, runs unit tests, runs lint, builds the **debug APK**, and uploads it as the
`app-debug-apk` artifact. The workflow also includes a **release** job that is disabled by
default (`if: false`); enable it and supply a signing config (see *Release build* above)
to produce a signed `app-release-apk` artifact in CI.

## Disclaimer

This tool is provided for legitimate assistance with applications the user owns and has
explicitly connected. It does not automate ad viewing, does not generate impressions, and
does not interact with advertiser content. Use responsibly and in accordance with the terms
of service of any application you connect.
