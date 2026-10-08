# 2AM Cam (Android)

> Disposable flash-film camera experience for Android. Built with native CameraX, Camera2 Interop, Jetpack Compose, and pure offline local storage.

## 🌟 Core Features

- **Direct Flash Engine:** Hardware flash synchronization (`FLASH_MODE_ON`) with maximum quality capture mode (`CAPTURE_MODE_MAXIMIZE_QUALITY`) for authentic disposable night-flash look.
- **Zone Focus Presets:**
  - **Portrait Mode:** Fixed focal distance at ~1.0m (diopter 1.0) with locked depth-of-field.
  - **Group Mode:** Fixed focal distance at ~2.5m (diopter 0.4) for crisp group photos.
- **Film Image Processing Pipeline:**
  - Contrast boost & crushed blacks for film aesthetic.
  - Warm & green tint calibration.
  - Optical vignette to draw focus to the center.
  - Procedural film grain noise overlay.
  - RAM-safe processing with adaptive downsampling to prevent OOM on budget devices.
- **Daily Rolls (Local Storage):**
  - Pure offline file system storage organized into `DailyRolls/yyyy-MM-dd/`.
  - Zero database, zero cloud, zero telemetry.
  - Atomic file writes (`.tmp` -> rename) to prevent corruption.
- **Minimalist Dark UI:**
  - Fullscreen viewfinder with Jetpack Compose.
  - Shutter button with haptic feedback & white-flash animation.
  - Integrated Daily Rolls film viewer with full-screen photo display.
  - One-tap export to system gallery (`Pictures/2AM Cam`).

## 🛠 Tech Stack

- **Language:** Kotlin 1.9.23
- **UI Toolkit:** Jetpack Compose (Material3)
- **Camera:** CameraX 1.3.1 + Camera2 Interop
- **Concurrency:** Kotlin Coroutines & Flow
- **Architecture:** MVVM + Clean Architecture principles
- **Minimum SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)

## 🚀 Getting Started

### Prerequisites

- Android Studio Iguana / Jellyfish or newer
- JDK 17
- Android SDK with Platform 34

### Build & Run

```bash
# Clone the repository
git clone https://github.com/minhlai1123/2amcam-androin-.git
cd 2amcam-androin-

# Run unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Install to connected device via ADB
./gradlew installDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

## 📄 License

MIT License.
