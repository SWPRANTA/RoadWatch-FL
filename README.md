# RoadWatch FL — Sensor Data Recorder

<p align="center">
  <img src="SensorApp/assets/icon.png" alt="RoadWatch FL Icon" width="120" />
</p>

<p align="center">
  A futuristic, dark-themed Android application built with <strong>React Native (Expo)</strong> for real-time multi-sensor data monitoring and high-frequency CSV recording — designed for road-condition data collection and field research.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-green?style=flat-square&logo=android" />
  <img alt="Expo" src="https://img.shields.io/badge/Expo-54-blue?style=flat-square&logo=expo" />
  <img alt="React Native" src="https://img.shields.io/badge/React%20Native-0.81-61DAFB?style=flat-square&logo=react" />
  <img alt="TypeScript" src="https://img.shields.io/badge/TypeScript-5.9-blue?style=flat-square&logo=typescript" />
  <img alt="License" src="https://img.shields.io/badge/license-Private-red?style=flat-square" />
</p>

---

## 📖 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Sensors & Data Columns](#-sensors--data-columns)
- [Anomaly Labels](#-anomaly-labels)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Running the App](#running-the-app)
- [Building a Distributable APK](#-building-a-distributable-apk)
- [How It Works](#-how-it-works)
- [CSV Output Format](#-csv-output-format)
- [Permissions](#-permissions)

---

## ✨ Features

- **Real-Time Sensor Monitoring** — Live telemetry display for all 8 sensor modules simultaneously.
- **High-Frequency Recording** — Samples all sensors at a **20 ms interval** (50 Hz) and writes data to a CSV file.
- **Pause & Resume** — Pause data collection mid-session without losing already-captured data; resume to continue appending to the same file buffer.
- **Selective Sensor Capture** — Toggle individual sensors on/off; disabled sensors log `0` in the CSV.
- **GPS & Location Tracking** — Records latitude, longitude, speed, and altitude alongside inertial data.
- **Anomaly Labelling** — Tag every row in the CSV with a road-condition label (Urban / Rural categories) or create your own **custom labels** on the fly.
- **Recordings Library** — Dedicated tab to browse, preview (first 10 rows), share, and delete past recording files.
- **CSV Export & Sharing** — Instantly share any recording via any installed app (Google Drive, email, etc.).
- **Futuristic Dark UI** — Neon-cyan/purple MD3 dark theme with a polished, premium feel.
- **Portrait-Locked** — Optimized for handheld use during field data collection.

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| Framework | [React Native](https://reactnative.dev/) 0.81 via [Expo](https://expo.dev/) ~54 |
| Language | TypeScript 5.9 |
| UI Components | [React Native Paper](https://callstack.github.io/react-native-paper/) (MD3) |
| Navigation | [@react-navigation/bottom-tabs](https://reactnavigation.org/) |
| Sensors | [expo-sensors](https://docs.expo.dev/versions/latest/sdk/sensors/) |
| Location | [expo-location](https://docs.expo.dev/versions/latest/sdk/location/) |
| File System | [expo-file-system](https://docs.expo.dev/versions/latest/sdk/filesystem/) |
| Sharing | [expo-sharing](https://docs.expo.dev/versions/latest/sdk/sharing/) |
| Icons | [@expo/vector-icons](https://icons.expo.fyi/) (MaterialCommunityIcons) |

---

## 📁 Project Structure

```
RoadWatch-FL/
├── README.md
└── SensorApp/
    ├── App.tsx                        # Root component — navigation & theme setup
    ├── app.json                       # Expo configuration (permissions, package name)
    ├── index.ts                       # Entry point
    ├── package.json
    ├── tsconfig.json
    ├── assets/                        # App icons & splash screen
    └── src/
        ├── context/
        │   └── SensorContext.tsx      # Global state — sensor subscriptions, recording,
        │                              #   pause/resume, labels, custom labels
        ├── screens/
        │   ├── HomeScreen.tsx         # Monitor tab — live data, recording controls,
        │   │                          #   pause button, anomaly label picker
        │   └── RecordingsScreen.tsx   # Recordings tab — file browser & management
        └── services/
            └── StorageService.ts      # CSV generation, file listing, delete & preview
```

---

## 🔬 Sensors & Data Columns

The app supports **8 sensor modules**. Each is independently toggleable before or during a session.

| Icon | Sensor | Recorded Fields | Unit |
|:----:|--------|-----------------|------|
| 📐 | **Accelerometer** | `acc_x`, `acc_y`, `acc_z` | g |
| 🔄 | **Gyroscope** | `gyro_x`, `gyro_y`, `gyro_z` | rad/s |
| 🧲 | **Magnetometer** | `mag_x`, `mag_y`, `mag_z` | μT |
| 🌡️ | **Barometer** | `pressure` | hPa |
| 💡 | **Light Sensor** | `illuminance` | lx |
| 🌀 | **Device Motion** | `dm_alpha`, `dm_beta`, `dm_gamma` | rad |
| 👣 | **Pedometer** | `step_count` | steps |
| 📍 | **Location (GPS)** | `latitude`, `longitude`, `speed`, `altitude` | deg / m/s / m |

---

## 🏷 Anomaly Labels

Every recorded row is tagged with the **active label** at the time of sampling (defaults to `normal`).

### Built-in labels

| Category | Labels |
|----------|--------|
| **Urban** | Pothole, Speed Breaker, Uneven Manhole, Utility Cut, Alligator Cracking, Raveling |
| **Rural** | Edge Drop-off, Washout, Rutting, Improvised Bump, HBB Displacement, Obstacle |

### Custom labels

Type any label name in the text field at the bottom of the label panel and press **+** (or the keyboard's **Done** key) to add it instantly. Custom labels appear in a **CUSTOM** category and work identically to built-in ones — tap to select, tap again to deselect (reverts to `normal`).

---

## 🚀 Getting Started

### Prerequisites

- **Node.js** ≥ 18
- **npm** ≥ 9
- **Expo CLI** (installed automatically via `npx`)
- **Android device** or emulator with USB/wireless debugging enabled
- **Expo Go** app (for quick development) or a local development build

### Installation

```bash
# 1. Clone the repository
git clone <your-repo-url> RoadWatch-FL
cd RoadWatch-FL/SensorApp

# 2. Install dependencies
npm install
```

### Running the App

```bash
# Start the Expo development server
npm start

# Start with tunnel (useful for networks with firewall restrictions)
npm run start:tunnel
```

> **Tip:** Scan the QR code with **Expo Go** to load the app instantly on your device, or press `a` in the terminal to launch on a connected Android device/emulator.

---

## 📦 Building a Distributable APK

The project uses a local Gradle build — no Expo cloud account required.

```bash
cd SensorApp

# Debug APK (install anywhere, no signing required)
npm run build:debug

# Release APK (requires a configured keystore)
npm run build:release
```

What each command does internally:

1. **`build:bundle`** — Runs Metro to create the JS bundle and copies assets into `android/app/src/main/`.
2. **`assembleDebug` / `assembleRelease`** — Gradle compiles the native project and outputs the APK.

**Output paths:**

| Build | APK location |
|-------|-------------|
| Debug | `android/app/build/outputs/apk/debug/app-debug.apk` |
| Release | `android/app/build/outputs/apk/release/app-release.apk` |

> **First run:** Generate the native `android/` folder once with `npm run prebuild` if it doesn't already exist.

---

## ⚙️ How It Works

### Recording Pipeline

```
Sensor Hardware
      │
      ▼
expo-sensors / expo-location  (listeners always active, ~20 ms cadence)
      │
      ▼
latestReadings ref  ◄─────────────────────────────────────────────────┐
      │                                                                │
      ▼                                                                │
setInterval(20 ms)  ── active when isRecording=true & isPaused=false ─►│
      │                                                                │
      ▼                                                                │
SensorData snapshot (timestamp + all enabled fields + active label)   │
      │                                                                │
      ├── pushed to bufferRef (in-memory array) ◄──────────────────────┘
      │
      ▼  (on STOP)
StorageService.saveSession()
      │
      ▼
CSV file in:  <documentDirectory>/recordings/recording_all_sensors_<ts>.csv
```

### Pause / Resume

Pressing **PAUSE** clears the `setInterval` but keeps `bufferRef` intact. Pressing **RESUME** restarts the interval — new samples are appended to the same buffer. The final CSV is only written when **STOP** is pressed.

### State Management

All sensor state lives in `SensorContext` — a React context wrapping the entire app.

| State | Description |
|---|---|
| `isRecording` | Whether a capture session is active |
| `isPaused` | Whether sampling is temporarily halted |
| `currentData` | The latest sampled `SensorData` frame (used for live display) |
| `enabledSensors` | Per-sensor boolean toggles |
| `currentLabel` | The anomaly label stamped on each row |
| `customLabels` | User-created label names persisted for the session |
| `lastSavedFile` | URI of the most recently saved CSV |
| `selectedSensor` | Which sensor module is visualized in the Monitor card |

---

## 📄 CSV Output Format

Each recording is saved as a UTF-8 CSV file with **21 columns**:

```
timestamp,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,
pressure,illuminance,dm_alpha,dm_beta,dm_gamma,step_count,
latitude,longitude,speed,altitude,label
```

| Column | Type | Notes |
|--------|------|-------|
| `timestamp` | integer | Unix epoch in milliseconds |
| `acc_*`, `gyro_*`, `mag_*` | float | 0 if sensor disabled |
| `pressure`, `illuminance` | float | 0 if sensor disabled |
| `dm_alpha/beta/gamma` | float | Device rotation in radians |
| `step_count` | integer | Cumulative steps since app launch |
| `latitude`, `longitude`, `speed`, `altitude` | float / null | null if Location disabled |
| `label` | string | `normal` or the selected anomaly label |

Files are named `recording_all_sensors_<epoch_ms>.csv` and sorted newest-first in the Recordings tab.

---

## 🔒 Permissions

The following Android permissions are declared in `app.json` and requested at runtime:

| Permission | Purpose |
|---|---|
| `ACTIVITY_RECOGNITION` | Step counter / pedometer |
| `ACCESS_FINE_LOCATION` | High-accuracy GPS coordinates |
| `ACCESS_COARSE_LOCATION` | Fallback location (network-based) |
| `READ_EXTERNAL_STORAGE` | Legacy file access (Android ≤ 12) |
| `WRITE_EXTERNAL_STORAGE` | Legacy file write (Android ≤ 12) |

---

<p align="center">
  Made with ❤️ by <strong>Swapnil</strong>
</p>
