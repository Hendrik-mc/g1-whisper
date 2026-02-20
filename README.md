# G1 Companion — Samsung Galaxy Z Flip 7 + Even Realities G1

A production-ready Android companion app for the **Even Realities G1 smart glasses**, built for the **Samsung Galaxy Z Flip 7**. Written entirely in Kotlin + Jetpack Compose.

---

## Features

- Dual BLE connection management (left arm + right arm, sequential send order)
- Consumer-friendly onboarding: Welcome → Permissions → Pairing → Tutorial → Dashboard
- Five AI widgets switchable from the dashboard (one active at a time)
- Text output paginated into 5 lines at 488 px width for the G1 lens
- OpenAI Realtime streaming integration
- Automatic reconnection and resync recovery flow
- Privacy-first: microphone paused by default, clear data controls
- Hidden Developer Mode for UUID discovery (long-press version label)

---

## Requirements

| Item | Version |
|------|---------|
| Android | 12+ (API 31+) |
| Target device | Samsung Galaxy Z Flip 7 |
| Kotlin | 1.9.x |
| Gradle | 8.x |
| Java | 17 |

---

## Quick Setup (Local Build)

### 1. Clone and initialise

```bash
git clone https://github.com/YOUR_USERNAME/g1-companion.git
cd g1-companion
```

### 2. Set your OpenAI key

Create `local.properties` (never committed):

```
OPENAI_API_KEY=sk-...
```

### 3. Build debug APK

```bash
./gradlew assembleDebug
```

Debug APK output:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Install on phone via USB (ADB)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Release Build (Signed APK)

### Create a keystore (one-time, local)

```bash
keytool -genkey -v \
  -keystore g1companion.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias g1companion
```

Keep `g1companion.jks` **off** Git. Store it in a password manager.

### Convert to base64 for GitHub Secrets

```bash
# macOS / Linux
base64 -i g1companion.jks | pbcopy

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("g1companion.jks")) | Set-Clipboard
```

### Build signed release APK locally

```bash
EVENAI_KEYSTORE_BASE64=$(base64 -i g1companion.jks) \
EVENAI_KEYSTORE_PASSWORD=yourpassword \
EVENAI_KEY_ALIAS=g1companion \
EVENAI_KEY_PASSWORD=yourkeypassword \
./gradlew assembleRelease
```

Signed APK output:
```
app/build/outputs/apk/release/app-release.apk
```

---

## GitHub Actions CI/CD

### Required Secrets

Go to **Settings → Secrets and variables → Actions** in your GitHub repo and add:

| Secret name | Value |
|-------------|-------|
| `EVENAI_KEYSTORE_BASE64` | Base64-encoded `.jks` file |
| `EVENAI_KEYSTORE_PASSWORD` | Keystore password |
| `EVENAI_KEY_ALIAS` | Key alias (e.g. `g1companion`) |
| `EVENAI_KEY_PASSWORD` | Key password |
| `OPENAI_API_KEY` | Your OpenAI API key |

### Workflows

| Workflow | Trigger | Output |
|----------|---------|--------|
| `ci.yml` | Push or PR to any branch | Debug APK + lint/test reports as artifacts |
| `release.yml` | Push tag `v*` (e.g. `v1.0.0`) | Signed release APK attached to GitHub Release |

### Push your first release tag

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will:
1. Build the signed APK
2. Create a GitHub Release named `v1.0.0`
3. Attach `app-release.apk` and `checksums.txt`

---

## Installing from GitHub Releases (Non-technical guide)

1. Open the repo on GitHub on your phone's browser
2. Tap **Releases** on the right sidebar
3. Tap the latest release
4. Download `app-release.apk`
5. On your Galaxy Z Flip 7: **Settings → Apps → Special app access → Install unknown apps → Browser → Allow**
6. Open the downloaded APK and tap **Install**

---

## Project Structure

```
app/src/main/kotlin/com/evenai/companion/
├── ble/                    # BLE connection manager, protocol encoder
├── data/
│   ├── model/              # Data classes
│   └── repository/         # Data sources
├── domain/
│   ├── model/              # Domain models
│   └── usecase/            # Business logic
├── openai/                 # Realtime streaming client
├── service/                # Foreground BLE service
├── ui/
│   ├── onboarding/         # Welcome, Permissions, Pairing, Tutorial
│   ├── dashboard/          # Main widget dashboard
│   ├── reconnect/          # Reconnect / resync screen
│   ├── developer/          # Hidden developer mode
│   ├── theme/              # Compose theme
│   ├── navigation/         # NavGraph
│   └── components/         # Shared composables
└── util/                   # Extensions, helpers
```

---

## Source-Grounded Decisions

See [SOURCE_DECISIONS.md](SOURCE_DECISIONS.md) for a full list of technical choices derived from the Even Realities EvenDemoApp source and Reddit field reports.

---

## Privacy

See [PRIVACY.md](PRIVACY.md).

## Security

See [SECURITY.md](SECURITY.md).
