# RoadWatch FL — Multi-Sensor Road Telemetry & Dataset Recorder

<p align="center">
  <img src="SensorApp/assets/icon.png" alt="RoadWatch FL Icon" width="120" />
</p>

<p align="center">
  A high-frequency Android sensor recording and road-condition telemetry suite designed for vehicular pavement monitoring, roughness index profiling (IRI), multi-device fleet synchronization, and federated/centralized machine learning dataset collection.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3DDC84?style=flat-square&logo=android" />
  <img alt="Kotlin" src="https://img.shields.io/badge/NativeApp-Kotlin%201.9-7F52FF?style=flat-square&logo=kotlin" />
  <img alt="React Native" src="https://img.shields.io/badge/SensorApp-React%20Native%200.81-61DAFB?style=flat-square&logo=react" />
  <img alt="Expo" src="https://img.shields.io/badge/Expo-SDK%20~54-000020?style=flat-square&logo=expo" />
  <img alt="Bluetooth Sync" src="https://img.shields.io/badge/Sync-Bluetooth%20Mesh%20SPP-blue?style=flat-square&logo=bluetooth" />
  <img alt="Material 3" src="https://img.shields.io/badge/UI-Material%20Design%203-blue?style=flat-square" />
  <img alt="TypeScript" src="https://img.shields.io/badge/TypeScript-5.9-3178C6?style=flat-square&logo=typescript" />
  <img alt="License" src="https://img.shields.io/badge/License-Proprietary%20%2F%20Research-red?style=flat-square" />
</p>

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Comparison Matrix: NativeApp vs SensorApp](#-comparison-matrix-nativeapp-vs-sensorapp)
- [Why NativeApp Outperforms SensorApp](#-why-nativeapp-outperforms-sensorapp)
- [Key Features](#-key-features)
- [Bluetooth Multi-Device Synchronization (NativeApp)](#-bluetooth-multi-device-synchronization-nativeapp)
- [Sensors & Telemetry Specifications](#-sensors--telemetry-specifications)
  - [Hardware & Virtual Sensor Channels](#hardware--virtual-sensor-channels)
  - [Geospatial & GPS Precision](#geospatial--gps-precision)
- [Vehicle Motion State & Anomaly Annotation](#-vehicle-motion-state--anomaly-annotation)
  - [Vehicle Motion State Tracking](#vehicle-motion-state-tracking-nativeapp)
  - [Built-in Anomaly Classes](#built-in-anomaly-classes)
  - [Custom Dynamic Labels](#custom-dynamic-labels)
- [CSV Output Format & Schemas](#-csv-output-format--schemas)
  - [NativeApp Schema (33 Columns)](#nativeapp-schema-33-columns)
  - [SensorApp Schema (21 Columns)](#sensorapp-schema-21-columns)
  - [Data Recording Rules & Device Prefixing](#data-recording-rules--device-prefixing)
- [Project Structure](#-project-structure)
- [Getting Started — NativeApp (Kotlin)](#-getting-started--nativeapp-kotlin)
  - [Prerequisites](#prerequisites-nativeapp)
  - [Configuration](#configuration-nativeapp)
  - [Building APKs (Debug & Release)](#building-apks-nativeapp)
  - [Installation](#installation-nativeapp)
- [Getting Started — SensorApp (React Native)](#-getting-started--sensorapp-react-native)
  - [Prerequisites](#prerequisites-sensorapp)
  - [Installation & Running](#installation--running-sensorapp)
  - [Building Standalone APKs](#building-standalone-apks-sensorapp)
- [System Architecture & Pipelines](#-system-architecture--pipelines)
  - [NativeApp Direct Hardware Pipeline](#nativeapp-direct-hardware-pipeline)
  - [SensorApp Bridge Pipeline](#sensorapp-bridge-pipeline)
  - [Zero-Data-Loss Pause & Resume](#zero-data-loss-pause--resume)
  - [Foreground Service & WakeLock Execution](#foreground-service--wakelock-execution)
- [Android Permissions](#-android-permissions)
- [Design Aesthetics & Theme](#-design-aesthetics--theme)

---

## 📋 Overview

**RoadWatch FL** provides two distinct mobile client architectures designed for road-surface telemetry, pavement quality auditing, and vehicular dynamic modeling:

1. **`NativeApp` (Kotlin / Android Jetpack / Material 3)**:
   A high-performance native Android application engineered for scientific and research-grade data acquisition. It connects directly to the underlying Linux/Android Hardware Abstraction Layer (`SensorManager`), supports ultra-high sampling rates (**100 Hz to 400+ Hz**), records **11 sensor streams into 33-column CSV files**, maintains uninterrupted logging via an Android `ForegroundService` with `WakeLock`, synchronizes multiple recording devices wirelessly over Bluetooth, provides on-device interactive tabular inspection, and isolates vehicular motion states (`moving` vs `stopped`).

2. **`SensorApp` (React Native / Expo SDK 54 / TypeScript)**:
   A rapid-prototyping cross-platform implementation using `expo-sensors` and `React Native Paper`. It records **8 core sensor channels** at up to **50 Hz** into a standard **21-column CSV** dataset.

---

## ⚖️ Comparison Matrix: NativeApp vs SensorApp

| Capability / Feature | `NativeApp` (Native Android) | `SensorApp` (React Native) |
|---|---|---|
| **Primary Language** | Kotlin 1.9 (Java 17 bytecode) | TypeScript 5.9 (JavaScript / React 19) |
| **Framework & UI** | Native Android SDK (API 26–34), Material Design 3, Jetpack Navigation | React Native 0.81, Expo SDK ~54, React Native Paper (MD3) |
| **Sensor Architecture** | Direct Linux HAL / `SensorManager` event listeners | `expo-sensors` bridging over JS event loop |
| **Active Sensor Modules** | **11 Modules** (Incl. Orientation, Gravity, 4D Rotation Vector) | **8 Modules** (Core inertial, environmental, location) |
| **Max Sampling Rate** | **100 Hz – 400+ Hz** (Hardware & sensor chip bound) | **~50 Hz** (JavaScript single-thread & bridge bound) |
| **Background Execution** | **Yes** — Dedicated `ForegroundService` (`location` + `connectedDevice`) with `WakeLock` | **No** — Pauses shortly after screen turns off or app is minimized |
| **Multi-Device Sync** | **Yes** — Real-time Bluetooth RFCOMM mesh sync with clock offset calibration | No (Standalone single-device recording only) |
| **Vehicle Motion State** | **Yes** — Dedicated `motion_state` column (`moving` / `stopped`) | No (Implicit from GPS speed / inertial values) |
| **Session Elapsed Time** | **Yes** — Dedicated `elapsed_time_ms` column (0 to end) | No (Requires subtracting first epoch timestamp) |
| **Anomaly Labeling** | Built-in Urban/Rural + **Persistent Custom Labels** (`SharedPreferences`) | Built-in Urban/Rural + Session-only custom labels |
| **Live Telemetry Frequency** | **Yes** — Real-time computed sampling Hz + sample counter card | Static interval selection presets |
| **CSV Dataset Columns** | **33 Columns** (Spatial attitude, gravity, quaternion, motion state, elapsed time) | **21 Columns** (Core inertial, environmental, GPS, label) |
| **In-App Data Preview** | **Interactive Table Viewer Dialog** (Scrollable matrix with headers) | Raw text preview snippet (First 10 lines) |
| **File Management** | **In-App File Renaming** + Device Prefixing (`<device>_recording_...`) | Fixed timestamp naming only |
| **Secure File Sharing** | Android `FileProvider` (`ACTION_SEND` chooser) | `expo-sharing` (native file sharing sheet) |
| **Build System** | Pure Gradle Wrapper (`./gradlew assembleDebug` / `assembleRelease`) | Expo CLI + local Gradle bundling (`npm run build:*`) |

---

## 🏆 Why NativeApp Outperforms SensorApp

While `SensorApp` is ideal for rapid prototyping and quick testing on physical phones via Expo Go, **`NativeApp` is fundamentally superior for real-world vehicular telemetry, pavement engineering, and machine learning research**. Here is why:

### 1. True High-Frequency Hardware Sampling (100–400+ Hz vs ~50 Hz)
- **The React Native Bridge Bottleneck**: In `SensorApp`, sensor updates generated in native C++ drivers must be serialized into JSON, dispatched across the React Native bridge/JSI, and handled on JavaScript's single thread. Pushing beyond ~50 Hz causes event backlog, severe garbage collection pauses, and non-deterministic timestamp jitter.
- **Direct HAL Interrupts in NativeApp**: `NativeApp` hooks directly into the Android Linux kernel HAL via `SensorEventListener`. An independent worker thread scheduled by `ScheduledExecutorService` snapshots all 11 sensor registers simultaneously with sub-millisecond precision. For International Roughness Index (IRI) calculation and pothole edge shockwave detection, high frequency is mandatory.

### 2. Bulletproof Background Recording (`ForegroundService` + `WakeLock`)
- **The Screen-Off Problem**: During road survey drives lasting 30–120 minutes, surveyors cannot keep phone screens permanently active at full brightness (causes extreme battery drain and thermal throttling under direct sunlight). In `SensorApp`, as soon as the screen sleeps or navigation apps take the foreground, Android suspends the JS runtime, **silently terminating data collection**.
- **Uninterrupted Service in NativeApp**: `NativeApp` runs as an official Android `ForegroundService` with `location` and `connectedDevice` capabilities, accompanied by a low-priority persistent notification and a CPU `WakeLock`. It records continuously regardless of screen locks, incoming phone calls, or app switching.

### 3. Comprehensive 11-Channel Telemetry (33 Columns vs 21 Columns)
`NativeApp` captures 3 crucial vehicular dynamics channels that `SensorApp` completely lacks:
1. **True 3D Orientation (Azimuth, Pitch, Roll in degrees)**: Derived directly from the device rotation matrix. Detects road gradient/incline (pitch) and road superelevation / banking angle (roll).
2. **Isolated Gravity Vector (`grav_x`, `grav_y`, `grav_z`)**: Separates static gravitational acceleration from dynamic road vibration shocks, essential for coordinate normalization.
3. **4D Rotation Vector Quaternion (`rot_x`, `rot_y`, `rot_z`, `rot_scalar`)**: Drift-free attitude quaternion ($x, y, z, w$) for spatial machine learning models.
4. **Session Elapsed Time (`elapsed_time_ms`)**: Direct relative session duration in milliseconds from $t=0$, eliminating timestamp math during model training.

### 4. Wireless Multi-Device Bluetooth Synchronization
Modern road research often requires placing multiple phones in a single vehicle (e.g., dashboard, floorboard, roof mount, rear axle) or across a convoy of survey vehicles.
- `SensorApp` has no inter-device communication.
- `NativeApp` integrates a full **Bluetooth Sync Mesh**: one phone acts as Master/Leader, synchronizing Start, Pause, Resume, Stop, Anomaly Labels, and Motion States across all connected phones with microsecond clock offset calibration and latency compensation.

### 5. Separation of Vehicle Motion States (`moving` vs `stopped`)
Vibration logs recorded while idling at traffic lights, stop signs, or railroad crossings corrupt roughness profiling algorithms.
- In `SensorApp`, surveyors must manually post-process GPS speed to filter stopped states.
- `NativeApp` features a docked, single-tap `MOVING` / `STOPPED` toggle, explicitly stamped into the `motion_state` column.

### 6. Persistent Annotation & On-Device Validation
- In `NativeApp`, custom labels created via `+ ADD LABEL` are permanently saved to `SharedPreferences` across app restarts.
- Researchers can immediately inspect captured CSV data inside the vehicle using the built-in `TableViewerDialog` (scrollable table matrix with styled headers) rather than waiting to transfer files to a computer.

---

## ✨ Key Features

- **High-Frequency Multi-Sensor Recording**: Synchronously samples hardware IMUs, spatial orientation, gravity, rotation quaternions, barometric pressure, environmental lux, pedometer steps, and multi-GNSS location coordinates.
- **Bluetooth Multi-Device Fleet Sync (`NativeApp`)**: Wirelessly connect multiple smartphones over Bluetooth SPP. Coordinate synchronized recording triggers, clock offset calibration, and shared annotation tagging across all devices.
- **Dedicated Vehicle Motion State Tagging (`NativeApp`)**: Seamlessly toggle between `MOVING` (default) and `STOPPED` states via a pinned bottom bar. Stamped per sample into `motion_state`.
- **Dynamic Anomaly Ground-Truth Annotation**:
  - Pre-configured categories for **Urban** and **Rural** road distress types.
  - Interactive **`+ ADD LABEL`** dialog allowing surveyors to create custom anomaly tags on the fly.
  - Custom labels persist across app sessions in `NativeApp` via `SharedPreferences`.
  - Tapping an active label toggles it back to `normal`.
- **Zero-Loss Pause & Resume**: Pause mid-session when encountering traffic lights or obstacles without dropping captured buffer records; resume to continuously append to the active recording session.
- **Device Identifier Prefixing (`NativeApp`)**: Configure custom device labels (e.g. `RoofMount`, `Dashboard`, `Axle1`) in Settings; CSV files are automatically prefixed (`<device>_recording_...`) for instant multi-phone survey organization.
- **In-App File Renaming & Table Viewer (`NativeApp`)**: Rename recording files on-device and inspect captured CSV records in an interactive, scrollable tabular grid.
- **Secure File Sharing via FileProvider (`NativeApp`)**: Share CSV files via Google Drive, Gmail, Slack, Bluetooth, or USB using standard Android Content URIs.
- **Live Empirical Frequency Monitor (`NativeApp`)**: Real-time display showing actual hardware acquisition rate (Hz), target software sampling frequency (Hz), total sample count, and active label.

---

## 📡 Bluetooth Multi-Device Synchronization (NativeApp)

`NativeApp` includes a full-featured, zero-cloud peer-to-peer synchronization engine based on Bluetooth Classic RFCOMM (`BluetoothSyncManager`):

```
       ┌────────────────────────┐
       │   Master Smartphone    │
       │ (Dashboard Controller) │
       └───────────┬────────────┘
                   │ Bluetooth RFCOMM (SPP)
         ┌─────────┴─────────┐
         ▼                   ▼
┌──────────────────┐ ┌──────────────────┐
│  Peer Phone #1   │ │  Peer Phone #2   │
│  (Floor / Axle)  │ │  (Roof Mount)    │
└──────────────────┘ └──────────────────┘
```

### Synchronization Capabilities:
1. **Clock Calibration Protocol**: Implements an NTP-style ping-pong exchange measuring Round-Trip Time (RTT) and calculating precise clock offsets between devices down to sub-millisecond precision.
2. **Scheduled Start**: When recording starts, the master calculates an epoch start time ($T_{\text{target}} = \text{now} + \Delta$) ensuring all phones begin sampling at the **exact same millisecond**.
3. **Synchronized Controls**: Pausing, resuming, or stopping on any device triggers an instant coordinated action across all connected peers.
4. **Synchronized Anomaly Tagging**: Tagging a pothole on the master phone immediately broadcasts the label to all phones; transit delays are compensated by **retroactively updating** buffer samples captured during radio transmission.
5. **Multi-Peer Mesh / Star Topology**: Supports connecting multiple phones simultaneously without requiring cellular data or Wi-Fi routers.

---

## 🔬 Sensors & Telemetry Specifications

### Hardware & Virtual Sensor Channels

| Sensor Name | Platform Availability | Android Type / API Source | CSV Column(s) | Units & Dimensions | Purpose & Road Distress Role |
|---|:---:|---|---|---|---|
| **Accelerometer** | Both | `Sensor.TYPE_ACCELEROMETER` / `Accelerometer` | `acc_x`, `acc_y`, `acc_z` | $\text{m/s}^2$ (or $g$) | Measures total acceleration including gravity. Primary channel for vertical road shocks (potholes, bumps) and lateral sway. |
| **Gyroscope** | Both | `Sensor.TYPE_GYROSCOPE` / `Gyroscope` | `gyro_x`, `gyro_y`, `gyro_z` | $\text{rad/s}$ | Angular velocity along pitch, roll, and yaw axes. Detects cornering, sudden swerving, and uneven surface banking. |
| **Magnetometer** | Both | `Sensor.TYPE_MAGNETIC_FIELD` / `Magnetometer` | `mag_x`, `mag_y`, `mag_z` | $\mu\text{T}$ (microteslas) | Ambient geomagnetic field. Provides compass bearing and detects proximity to metallic infrastructure (bridges, rails, utility trenches). |
| **Orientation** | `NativeApp` | `TYPE_ORIENTATION` + Computed Rotation Matrix | `orient_azimuth`, `orient_pitch`, `orient_roll` | Degrees ($^\circ$) | 3D spatial attitude of vehicle. Directly captures road grade/slope (pitch) and road superelevation/crossfall (roll). |
| **Gravity** | `NativeApp` | `Sensor.TYPE_GRAVITY` (Hardware / LPF Fallback) | `grav_x`, `grav_y`, `grav_z` | $\text{m/s}^2$ | Isolates the directional gravity vector. Enables dynamic vehicle body leveling and dynamic shock isolation. |
| **Rotation Vector** | `NativeApp` | `Sensor.TYPE_ROTATION_VECTOR` | `rot_x`, `rot_y`, `rot_z`, `rot_scalar` | Unitless Direction Cosines + Quaternion ($w$) | Sensor-fused, drift-free orientation quaternion ($x, y, z, w$) for spatial machine learning models and coordinate transformations. |
| **Barometer** | Both | `Sensor.TYPE_PRESSURE` / `Barometer` | `pressure` | $\text{hPa}$ (hectopascals) | Atmospheric pressure. Sensitive to micro-altitude variations (overpasses, highway flyovers, bridge dips). |
| **Light Sensor** | Both | `Sensor.TYPE_LIGHT` / `LightSensor` | `illuminance` | $\text{lx}$ (lux) | Ambient photometric illuminance. Flags environmental contexts (day/night, tunnels, covered underpasses). |
| **Device Motion** | Both | Attitude Euler Angles / `DeviceMotion` | `dm_alpha`, `dm_beta`, `dm_gamma` | Radians | Cross-platform relative orientation angles for mobile rotation tracking. |
| **Pedometer** | Both | `Sensor.TYPE_STEP_COUNTER` / `Pedometer` | `step_count` | Cumulative integer | Tracks step count. Used to filter out foot-survey data from vehicular transit recordings. |
| **Location (GNSS)** | Both | `LocationManager` (`GPS`, `NETWORK`, `FUSED`) | `latitude`, `longitude`, `speed`, `altitude` | Deg ($^\circ$), $\text{m/s}$, $\text{m}$ | Full-precision global positioning coordinates, ground speed, and altitude for mapping pavement distress. |

### Geospatial & GPS Precision

In both applications, location coordinates are captured at **full 64-bit IEEE 754 double precision**:
- **Latitude & Longitude**: Stored without artificial rounding or truncation (e.g. `23.774581298410214`), providing a mathematical resolution under 1 millimeter. Actual field accuracy corresponds to the device's multi-GNSS receiver capabilities (typically 1.5–4.0 meters under clear sky).
- **Ground Speed**: Recorded in meters per second ($\text{m/s}$) directly from GPS Doppler measurement (`Location.getSpeed()`).
- **Altitude**: Altitude above WGS 84 reference ellipsoid in meters.
- **Synchronization**: Location fixes are continuously polled and latched to the synchronous IMU sampling buffer at the selected sampling interval.

---

## 🏷 Vehicle Motion State & Anomaly Annotation

### Vehicle Motion State Tracking (`NativeApp`)

Survey logs frequently suffer from noise generated when vehicles are idling at red lights or stuck in traffic jams. `NativeApp` features a persistent motion state selector:

- **States**: `moving` (default) and `stopped`.
- **UI Control**: A pinned `MaterialButtonToggleGroup` docked above the bottom navigation bar for single-tap switching during surveys.
- **CSV Logging**: Logged in every record row under the `motion_state` column.

### Built-in Anomaly Classes

When an anomaly is encountered, tapping its chip stamps the subsequent rows with that label until toggled off (reverting to `normal`).

| Environment | Built-in Anomaly Classes |
|:---:|---|
| **Urban** | `Pothole`, `Speed Breaker`, `Uneven Manhole`, `Utility Cut`, `Alligator Cracking`, `Raveling` |
| **Rural** | `Edge Drop-off`, `Washout`, `Rutting`, `Improvised Bump`, `HBB Displacement`, `Obstacle` |

### Custom Dynamic Labels

Need to survey specific features (e.g. *Rumble Strip*, *Expansion Joint*, *Rail Crossing*)?
- **Adding Labels**: Tap **`+ ADD LABEL`** on the home screen, enter the label name, and press **Add**.
- **Instant Activation**: The custom chip is immediately created and selected as the active recording tag.
- **Persistence (`NativeApp`)**: Custom labels are saved to `SharedPreferences` (`"custom_anomaly_labels"`) and automatically reloaded whenever the app opens.
- **Deletion**: Custom chips feature an inline close icon (`×`) to remove labels that are no longer needed.

---

## 📄 CSV Output Format & Schemas

### NativeApp Schema (33 Columns)

```csv
timestamp,elapsed_time_ms,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,pressure,illuminance,dm_alpha,dm_beta,dm_gamma,step_count,latitude,longitude,speed,altitude,orient_azimuth,orient_pitch,orient_roll,grav_x,grav_y,grav_z,rot_x,rot_y,rot_z,rot_scalar,label,motion_state
```

#### Field Specifications:

| # | Column Name | Type | Unit | Description |
|:---:|---|:---:|:---:|---|
| 1 | `timestamp` | Long | ms | Unix epoch timestamp in milliseconds |
| 2 | `elapsed_time_ms` | Long | ms | Relative elapsed time since recording session started |
| 3–5 | `acc_x`, `acc_y`, `acc_z` | Float | $\text{m/s}^2$ | 3-axis accelerometer acceleration |
| 6–8 | `gyro_x`, `gyro_y`, `gyro_z` | Float | $\text{rad/s}$ | 3-axis gyroscope angular velocity |
| 9–11 | `mag_x`, `mag_y`, `mag_z` | Float | $\mu\text{T}$ | 3-axis geomagnetic field strength |
| 12 | `pressure` | Float | $\text{hPa}$ | Atmospheric air pressure |
| 13 | `illuminance` | Float | $\text{lx}$ | Ambient photometric illuminance |
| 14–16 | `dm_alpha`, `dm_beta`, `dm_gamma` | Float | rad | Device attitude rotation angles |
| 17 | `step_count` | Int | count | Cumulative pedometer steps since boot |
| 18–19 | `latitude`, `longitude` | Double | deg | GNSS coordinates (64-bit precision) |
| 20 | `speed` | Float | $\text{m/s}$ | Vehicle ground speed |
| 21 | `altitude` | Double | m | Altitude above reference ellipsoid |
| 22–24 | `orient_azimuth`, `orient_pitch`, `orient_roll` | Float | deg | Compass heading ($0\text{--}360^\circ$), Pitch ($-180\text{--}180^\circ$), Roll ($-90\text{--}90^\circ$) |
| 25–27 | `grav_x`, `grav_y`, `grav_z` | Float | $\text{m/s}^2$ | Directional gravity vector components |
| 28–31 | `rot_x`, `rot_y`, `rot_z`, `rot_scalar` | Float | — | Fused unit quaternion ($x, y, z, w$) |
| 32 | `label` | String | — | Active road condition tag (`normal`, `Pothole`, etc.) |
| 33 | `motion_state` | String | — | Active vehicle state (`moving` or `stopped`) |

---

### SensorApp Schema (21 Columns)

```csv
timestamp,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,pressure,illuminance,dm_alpha,dm_beta,dm_gamma,step_count,latitude,longitude,speed,altitude,label
```

#### Field Specifications:

| # | Column Name | Type | Unit | Description |
|:---:|---|:---:|:---:|---|
| 1 | `timestamp` | Long | ms | Unix epoch timestamp in milliseconds |
| 2–4 | `acc_x`, `acc_y`, `acc_z` | Float | $g$ | 3-axis acceleration |
| 5–7 | `gyro_x`, `gyro_y`, `gyro_z` | Float | $\text{rad/s}$ | 3-axis angular velocity |
| 8–10 | `mag_x`, `mag_y`, `mag_z` | Float | $\mu\text{T}$ | 3-axis ambient magnetic flux |
| 11 | `pressure` | Float | $\text{hPa}$ | Barometric air pressure |
| 12 | `illuminance` | Float | $\text{lx}$ | Environmental light level |
| 13–15 | `dm_alpha`, `dm_beta`, `dm_gamma` | Float | rad | Device orientation radians |
| 16 | `step_count` | Int | count | Cumulative pedometer count |
| 17–20 | `latitude`, `longitude`, `speed`, `altitude` | Double / Float | deg, $\text{m/s}$, m | GPS coordinates and motion dynamics |
| 21 | `label` | String | — | Active road condition tag |

---

### Data Recording Rules & Device Prefixing

1. **Disabled Sensors**: If a sensor is toggled off in the UI, its numerical fields are logged as `0`.
2. **Missing Location**: If Location is disabled or satellites are acquiring, coordinates and altitude output empty values (`""` / `null`).
3. **File Naming & Device Prefixing (`NativeApp`)**:
   - Configurable in Settings -> **Device Name** (e.g. `RoofPhone`).
   - Generates filename: `[DeviceName_]recording_all_sensors_<epoch_timestamp>.csv`.
   - Files can be renamed anytime inside the Recordings tab.
4. **Storage Directories**:
   - `NativeApp`: `context.getExternalFilesDir(null)/recordings/` (Public app sandbox, directly accessible over USB).
   - `SensorApp`: `FileSystem.documentDirectory + "recordings/"`.

---

## 📁 Project Structure

```
RoadWatch-FL/
├── README.md                           # Master documentation
│
├── NativeApp/                          # Production Native Kotlin Android App
│   ├── build.gradle.kts                # Root project build script
│   ├── settings.gradle.kts             # Gradle settings & module definitions
│   ├── gradle.properties               # Memory & daemon settings
│   ├── gradlew / gradlew.bat           # Gradle wrapper execution scripts
│   ├── local.properties                # SDK path configuration (sdk.dir)
│   └── app/
│       ├── build.gradle.kts            # App module build script (API 34, Java 17)
│       ├── proguard-rules.pro          # Release optimization rules
│       └── src/main/
│           ├── AndroidManifest.xml     # Permissions, Bluetooth, Service, FileProvider
│           ├── java/com/roadwatch/fl/
│           │   ├── MainActivity.kt     # App host, bottom nav binding, service connection
│           │   ├── model/
│           │   │   └── SensorData.kt   # Immutable data class with 33 telemetry fields
│           │   ├── service/
│           │   │   └── SensorService.kt # Foreground service, sensor loops, WakeLock, GPS
│           │   ├── bluetooth/
│           │   │   └── BluetoothSyncManager.kt # Multi-device mesh sync & clock calibration
│           │   ├── fragment/
│           │   │   ├── HomeFragment.kt        # Telemetry monitor, controls, motion state
│           │   │   ├── RecordingsFragment.kt  # Recordings list, rename, share, table trigger
│           │   │   ├── SettingsFragment.kt    # Sampling sliders, device name, BT sync setup
│           │   │   ├── SensorAdapter.kt       # RecyclerView adapter for 11 sensor cards
│           │   │   └── RecordingAdapter.kt    # RecyclerView adapter for CSV records
│           │   ├── dialog/
│           │   │   └── TableViewerDialog.kt   # Scrollable matrix viewer for CSV inspection
│           │   └── util/
│           │       ├── StorageManager.kt      # CSV writer, renaming, device prefixing
│           │       └── FileShareUtils.kt      # Secure Android FileProvider sharing
│           └── res/
│               ├── drawable/           # 11 sensor vector icons, backgrounds, badges
│               ├── font/               # JetBrains Mono (telemetry) & Inter typography
│               ├── layout/             # Modern MD3 XML layouts & dialogs
│               ├── menu/               # Bottom navigation item definitions
│               ├── navigation/         # Jetpack Navigation Graph
│               ├── xml/file_paths.xml  # FileProvider path definitions
│               └── values/             # Colors (Cyber-Slate palette), strings, styles
│
└── SensorApp/                          # Cross-Platform React Native (Expo) App
    ├── App.tsx                         # Root component, MD3 dark theme, bottom navigation
    ├── app.json                        # Expo app manifest & permissions
    ├── index.ts                        # Application entry point
    ├── package.json                    # Dependencies & build scripts
    ├── tsconfig.json                   # TypeScript configuration
    ├── assets/                         # App icon, splash screen, favicon
    └── src/
        ├── context/
        │   └── SensorContext.tsx       # Global React state, sensor listeners, buffer logic
        ├── screens/
        │   ├── HomeScreen.tsx          # Real-time sensor preview & recording controls
        │   ├── RecordingsScreen.tsx    # File list, preview snippet, expo-sharing
        │   └── SettingsScreen.tsx      # Sampling intervals & max frequency benchmark
        └── services/
            └── StorageService.ts       # CSV file I/O via expo-file-system
```

---

## 🚀 Getting Started — NativeApp (Kotlin)

<a id="prerequisites-nativeapp"></a>
### Prerequisites
- **Android Studio** (Hedgehog 2023.1.1+ recommended) or **Android SDK Command-Line Tools**.
- **JDK 17** (Eclipse Adoptium Temurin 17 or Android Studio bundled JDK).
- Android device running **Android 8.0 (API 26) or higher** with USB/Wireless Debugging enabled.

<a id="configuration-nativeapp"></a>
### Configuration
Create a `local.properties` file inside `NativeApp/` pointing to your Android SDK directory:

```properties
sdk.dir=/home/<your-username>/Android/Sdk
# On macOS: sdk.dir=/Users/<your-username>/Library/Android/sdk
# On Windows: sdk.dir=C:\\Users\\<your-username>\\AppData\\Local\\Android\\Sdk
```

<a id="building-apks-nativeapp"></a>
### Building APKs (Debug & Release)

Open a terminal in the `NativeApp` directory:

```bash
cd NativeApp

# 1. Build Debug APK
./gradlew assembleDebug

# 2. Build Release APK (Pre-configured with debug signature for direct distribution)
./gradlew assembleRelease
```

**Generated APK Locations:**

| Target | File Path |
|---|---|
| **Debug APK** | `NativeApp/app/build/outputs/apk/debug/app-debug.apk` |
| **Release APK** | `NativeApp/app/build/outputs/apk/release/app-release.apk` |

Verify APK signature with `apksigner`:
```bash
apksigner verify -v app/build/outputs/apk/release/app-release.apk
```

<a id="installation-nativeapp"></a>
### Installation via ADB

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 🚀 Getting Started — SensorApp (React Native)

<a id="prerequisites-sensorapp"></a>
### Prerequisites
- **Node.js** ≥ 18.x and **npm** ≥ 9.x
- **Expo Go** app on your Android device (for live development) OR an Android build environment.

<a id="installation--running-sensorapp"></a>
### Installation & Running

```bash
cd SensorApp

# 1. Install dependencies
npm install

# 2. Start the Metro bundler
npm start

# Or start with a remote tunnel (recommended if device is on another Wi-Fi network)
npm run start:tunnel
```

- Open **Expo Go** on your Android phone and scan the QR code displayed in the terminal.
- Alternatively, press `a` in the terminal to launch on a connected USB device or emulator.

<a id="building-standalone-apks-sensorapp"></a>
### Building Standalone APKs

`SensorApp` supports local standalone APK generation without an Expo cloud account:

```bash
cd SensorApp

# 1. Generate the native android directory (if not already created)
npm run prebuild

# 2. Build Debug APK
npm run build:debug

# 3. Build Release APK
npm run build:release
```

**Generated APK Locations:**

| Target | File Path |
|---|---|
| **Debug APK** | `SensorApp/android/app/build/outputs/apk/debug/app-debug.apk` |
| **Release APK** | `SensorApp/android/app/build/outputs/apk/release/app-release.apk` |

---

## ⚙️ System Architecture & Pipelines

### NativeApp Direct Hardware Pipeline

```
[Hardware Sensors] (Acc, Gyro, Mag, Baro, Light, RotVec, Gravity, Orient, Step)
       │
       ▼ (Direct hardware interrupts via SensorEventListener)
[SensorService] (Android Foreground Service with WakeLock)
       ├── onSensorChanged(event) -> Updates latest atomic cache arrays
       ├── onLocationChanged(loc) -> Latches high-precision GPS fixes
       ├── BluetoothSyncManager   -> Transmits/receives sync triggers & clock pings
       │
       ▼ (ScheduledExecutorService executing every `samplingInterval` ms)
[Synchronous Data Snapshot]
       │
       ├── Stamped with: timestamp + elapsedMs + 11 sensor channels + label + motion_state
       ▼
[Thread-Safe In-Memory Buffer] (CopyOnWriteArrayList<SensorData>)
       │
       ▼ (Triggered on STOP Recording)
[StorageManager.saveSession()]
       │
       ▼ (Direct Buffered File Stream with Device Prefix)
[CSV File Export] -> <ExternalFilesDir>/recordings/[<Device>_]recording_all_sensors_<ts>.csv
```

### SensorApp Bridge Pipeline

```
[expo-sensors & expo-location listeners] (Async JavaScript callbacks)
       │
       ▼
[latestReadings Ref] (Mutable in-memory state in SensorContext)
       │
       ▼ (setInterval loop active during recording)
[Snapshot Frame]
       │
       ▼
[bufferRef (Array)]
       │
       ▼ (Triggered on STOP Recording)
[StorageService.saveSession()]
       │
       ▼ (expo-file-system async write)
[CSV File Export] -> <DocumentDirectory>/recordings/recording_all_sensors_<timestamp>.csv
```

### Zero-Data-Loss Pause & Resume

- **On PAUSE**: The sampling loop/timer is paused and sensor state flags are updated. The in-memory buffer is **retained intact**.
- **On RESUME**: The sampling loop restarts immediately. Newly sampled frames are appended directly to the end of the existing buffer.
- **On STOP**: The full accumulated dataset is serialized and flushed to disk in a single I/O operation.

### Foreground Service & WakeLock Execution (`NativeApp`)

To guarantee un-throttled data collection during long survey drives:
1. `SensorService` is declared with `foregroundServiceType="location|connectedDevice"` to comply with Android 14+ background execution policies.
2. Acquires a `PowerManager.PARTIAL_WAKE_LOCK`, ensuring the CPU stays awake and sensor clocks continue running smoothly even when the screen is turned off.
3. Holds internal execution priority, preventing OEM memory cleaners or battery optimizers from killing active sessions.

---

## 🔒 Android Permissions

| Permission | Declared In | Purpose |
|---|:---:|---|
| `ACCESS_FINE_LOCATION` | Both | High-accuracy GPS positioning |
| `ACCESS_COARSE_LOCATION` | Both | Network-assisted positioning fallback |
| `ACTIVITY_RECOGNITION` | Both | Hardware pedometer / step counter access |
| `FOREGROUND_SERVICE` | `NativeApp` | Allows sensor recording in the background |
| `FOREGROUND_SERVICE_LOCATION` | `NativeApp` | Required by Android 14+ for background GPS recording |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | `NativeApp` | Required by Android 14+ for background Bluetooth synchronization |
| `WAKE_LOCK` | `NativeApp` | Prevents CPU sleep when screen is off during survey sessions |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | `NativeApp` | Legacy Bluetooth connection (Android ≤ 11) |
| `BLUETOOTH_CONNECT` | `NativeApp` | Bluetooth SPP socket connection (Android 12+) |
| `BLUETOOTH_SCAN` | `NativeApp` | Peer discovery without location flags (Android 12+) |
| `BLUETOOTH_ADVERTISE` | `NativeApp` | Allows device to be discovered as a sync peer (Android 12+) |
| `HIGH_SAMPLING_RATE_SENSORS` | `NativeApp` | Unlocks sensor sampling rates above 200 Hz (Android 12+) |
| `POST_NOTIFICATIONS` | `NativeApp` | Displays the persistent foreground service notification (Android 13+) |
| `READ_EXTERNAL_STORAGE` | `SensorApp` | Legacy file system compatibility (Android ≤ 12) |
| `WRITE_EXTERNAL_STORAGE` | `SensorApp` | Legacy file system compatibility (Android ≤ 12) |

---

## 🎨 Design Aesthetics & Theme

Both applications feature a cohesive **Cyber-Slate Dark Theme**:
- **Background**: `#0B0F17` (Deep Obsidian / Black)
- **Surfaces & Cards**: `#131B26` / `#1C2636` with `#222F42` structural borders
- **Primary Accent**: `#00D8F6` (Electric Cyan)
- **Secondary Accent**: `#818CF8` (Soft Violet)
- **Telemetry Amber**: `#F59E0B` (Live frequency & sampling indicator)
- **Settings Card Focus**: High-contrast white border (`#FFFFFF`, `1.5dp`) around sampling interval adjustment cards for immediate visual hierarchy.
- **Typography**: Monospaced numerical readouts via **JetBrains Mono** for jitter-free telemetry inspection paired with clean **Inter** interface typography.

---

<p align="center">
  Crafted for research and precision data collection by <strong>Swapnil</strong>.
</p>
