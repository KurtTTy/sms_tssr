# SMS_ISDP — TSSR Mobile Application

> **Telecom Site Survey Report (TSSR) documentation tool for field engineers**

A native Android application built with Jetpack Compose that enables telecom field engineers to capture, organize, and generate professional site survey reports with embedded photos, GPS metadata, and structured documentation.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Screenshots & Navigation Flow](#screenshots--navigation-flow)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [How It Works](#how-it-works)
- [Report Generation](#report-generation)
- [File Storage Layout](#file-storage-layout)
- [Permissions](#permissions)
- [Build & Deploy](#build--deploy)
- [Default Credentials](#default-credentials)
- [Known Limitations](#known-limitations)

---

## Overview

SMS_ISDP is a field-first Android app designed for telecom site surveyors. Engineers open the app on-site, fill in site metadata (name, GPS, address, telco), then systematically capture photos across **13 standardized TSSR sections**. Each photo is automatically stamped with site name, date/time, and GPS coordinates. When the survey is complete, the app generates a fully formatted **PDF** or **DOCX** report with all photos and metadata.

---

## Features

| Feature | Description |
|---|---|
| GPS Auto-Detection | Uses Fused Location Provider to fetch precise coordinates |
| Reverse Geocoding | Converts GPS coordinates to a human-readable address |
| Photo Capture | CameraX integration with live preview |
| Gallery Import | Pick existing photos from device storage |
| Metadata Overlay | Stamps every photo with site name, GPS, date/time, address |
| OSM Map Snapshot | Embeds an OpenStreetMap tile snapshot of the site location |
| 13 TSSR Sections | Structured photo fields covering all standard survey areas |
| PDF Report | iText7-powered PDF with embedded photos and metadata |
| DOCX Report | Apache POI-powered Word document output |
| Multi-Telco Support | Globe, Smart, Dito, WiFi |
| Folder Management | Create, rename, and reload surveys per site |
| Dark / Light Theme | Toggle via top app bar |
| Offline-First | No backend required; all data stored locally on-device |

---

## Screenshots & Navigation Flow

```
┌─────────────────┐
│   Auth Screen   │  ← Login or Sign Up
│ (adminkurt /    │
│  kurt12345)     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Main Screen   │  ← Select telco brand, view survey history
│  Globe | Smart  │
│  Dito  | WiFi   │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│                   TSSR Screen                       │
│  Site info → GPS → Photos (13 sections) → Reports  │
└─────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0.0 |
| UI Framework | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose 2.7.7 |
| Camera | CameraX 1.4.0 |
| Location | Google Play Services Location 21.3.0 |
| Maps | osmdroid (OpenStreetMap) 6.1.18 |
| Image Loading | Coil 2.6.0 |
| PDF Generation | iText7 Core 7.2.5 |
| DOCX Generation | Apache POI 5.2.5 |
| Build System | Gradle 9.2.1 with Kotlin DSL |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

---

## Prerequisites

Before building or running the project, make sure you have:

- **Android Studio** — Hedgehog (2023.1.1) or newer recommended
- **Android SDK** — API Level 26 through 35 installed
- **Java** — JDK 8 or higher
- **Google Play Services** — Available on the target device/emulator
- **Internet access** — Required for OSM tile loading and reverse geocoding

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/KurtTTy/sms_tssr.git
cd sms_tssr
```

### 2. Open in Android Studio

1. Launch Android Studio
2. Click **File → Open**
3. Navigate to the cloned `sms_tssr` folder and click **OK**
4. Wait for Gradle sync to finish (first sync downloads all dependencies)

### 3. Configure Google Maps API Key *(optional — for enhanced geocoding)*

Open `app/src/main/AndroidManifest.xml` and replace the placeholder:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_API_KEY_HERE" />
```

Replace `YOUR_API_KEY_HERE` with a key from the [Google Cloud Console](https://console.cloud.google.com/). Enable **Maps SDK for Android** and **Geocoding API** for the key.

> The app works without this key using Android's built-in `Geocoder` class, but a proper API key improves reliability.

### 4. Run the App

**Via Android Studio:**
- Connect a physical device (USB debugging enabled) or start an emulator
- Click the green **Run** button (or press `Shift + F10`)

**Via Command Line:**

```bash
# Windows
gradlew.bat installDebug

# macOS / Linux
./gradlew installDebug
```

---

## Project Structure

```
sms_isdp/
├── app/
│   └── src/main/
│       ├── java/com/example/isdp2java/
│       │   ├── MainActivity.kt          # App entry point, theme setup
│       │   ├── Navigation.kt            # Auth → Main → TSSR navigation graph
│       │   └── ui/
│       │       ├── auth/
│       │       │   └── AuthScreen.kt    # Login and Sign-Up screens
│       │       ├── main/
│       │       │   ├── MainScreen.kt    # Dashboard: brand selection + history
│       │       │   ├── TSSRScreen.kt    # Full survey form (~937 lines)
│       │       │   └── Models.kt        # Data classes (Brand, History, TSSRSectionData)
│       │       └── theme/
│       │           ├── Color.kt
│       │           ├── Theme.kt
│       │           └── Typography.kt
│       ├── res/
│       │   ├── drawable/                # Telco logos (Globe, Smart, Dito)
│       │   ├── mipmap-*/               # App launcher icons
│       │   └── xml/
│       │       └── file_paths.xml      # FileProvider path config
│       └── AndroidManifest.xml
├── gradle/
│   └── libs.versions.toml              # Centralized dependency versions
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Module settings
├── gradlew / gradlew.bat               # Gradle wrapper scripts
└── README.md
```

---

## Configuration

### Credentials

The app ships with hardcoded demo credentials:

| Field | Value |
|---|---|
| Username | `adminkurt` |
| Password | `kurt12345` |

> For a production build, replace the static check in `AuthScreen.kt` with a proper authentication backend.

### SharedPreferences Keys

| Preference File | Key | Value |
|---|---|---|
| `auth_prefs` | `username` | Saved username (if Remember Me is checked) |
| `auth_prefs` | `remember_me` | Boolean — persist login state |
| `osm_prefs` | (osmdroid internal) | OSM tile cache settings |

---

## How It Works

### 1. Authentication
The `AuthScreen` validates username and password against hardcoded values. On success it navigates to `MainScreen`. "Remember Me" persists the username in SharedPreferences.

### 2. Survey Setup (MainScreen)
The engineer selects a telco brand (Globe, Smart, Dito, or WiFi). Previously completed surveys appear in the history bottom sheet.

### 3. TSSR Data Entry (TSSRScreen)
The main survey screen contains:

- **Site Details header:** Site name, telco, GPS coordinates (auto-fetched), full address (reverse-geocoded), and vehicle access type.
- **Folder Management:** Each survey is saved in a folder named `{SiteName}_{Date}_{Time}`. Surveys can be renamed, loaded, or created fresh.

### 4. Photo Capture — 13 Sections

| Section | Photo Fields |
|---|---|
| A3.1 Site Location | Vicinity Map, Site Location Map, OSM Map |
| A3.2 Site & Tower | Overview, Tower, Cabinet, Grounding, + more |
| Panoramic Antenna | 0°, 30°, 60° … 330° (12 directions) |
| Sector Antennas | Sector 1-Alpha through Sector 3-Gamma (15 fields) |
| Microwave Photos | MW1 through MW20 |
| Outdoor Panoramic | 4 overview shots |
| Tower Top Views | 4 aerial/top-down views |
| Cabin Photos | Interior, exterior, and equipment (13 fields) |
| Basepad Photos | Foundation and basepad views (8 fields) |
| Wireless Equipment | 3 fields |
| Transport Equipment | 2 fields |
| ODF / OSP | 3 fields |
| Proposed Installation | 3 fields |

For each photo field the engineer can:
- **Camera** — capture a live photo
- **Gallery** — pick from device storage
- **Map** — capture an OSM map tile snapshot

Every captured image is automatically overlaid with:
- Site name
- Date and time
- GPS coordinates
- Full address

### 5. Metadata Persistence
After entering site info, the data is written to `metadata.properties` inside the survey folder. This allows the engineer to close the app and resume later with all fields restored.

---

## Report Generation

When the survey is complete, tap the **Generate PDF** or **Generate DOCX** button.

### PDF (iText7)
- Full A4-formatted document
- Site metadata header (name, date, telco, GPS, address)
- Each section rendered as a table with embedded JPEG thumbnails
- Saved as `{SiteName}_{Timestamp}.pdf` in the survey folder

### DOCX (Apache POI)
- Word-compatible `.docx` file
- Same structure as PDF: metadata header + section tables with images
- Saved as `{SiteName}_{Timestamp}.docx` in the survey folder

Reports can be found at:

```
Internal Storage/Android/data/com.example.isdp2java/files/SMS_ISDP/{FolderName}/
```

---

## File Storage Layout

```
[App External Files Dir]/
└── SMS_ISDP/
    └── SiteName_20260213_1515/          ← one folder per survey
        ├── metadata.properties          ← site data (name, GPS, address, etc.)
        ├── A3.1 Site Location/
        │   ├── A3.1 Site Location_Vicinity Map_20260213_151500.jpg
        │   └── A3.1 Site Location_OSM Map_20260213_151501.jpg
        ├── A3.2 Site & Tower/
        │   └── ...
        ├── Panoramic Antenna/
        │   └── ...
        ├── (... one sub-folder per section ...)
        ├── SiteName_20260213_1515.pdf
        └── SiteName_20260213_1515.docx
```

### metadata.properties format

```properties
siteName=Makati BGC Node 1
lat=14.5994
lng=120.9842
fullAddress=BGC, Taguig, Metro Manila
vehicleAccess=4 Wheeled
otherVehicleAccess=
telcoName=Globe
wifiTelcoName=
img_A3.1 Site Location_Vicinity Map=A3.1 Site Location/A3.1 Site Location_Vicinity Map_20260213_151500.jpg
img_A3.2 Site & Tower_Tower Overview=A3.2 Site & Tower/...jpg
```

---

## Permissions

The app requests the following Android permissions at runtime:

| Permission | Purpose |
|---|---|
| `CAMERA` | Capture site photos |
| `ACCESS_FINE_LOCATION` | Precise GPS coordinates |
| `ACCESS_COARSE_LOCATION` | Fallback location |
| `READ_EXTERNAL_STORAGE` | Import photos from gallery |
| `WRITE_EXTERNAL_STORAGE` | Save photos/reports (Android ≤ 9 only) |
| `INTERNET` | Load OSM map tiles and reverse geocoding |

---

## Build & Deploy

### Debug Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
```

> Sign the release APK with your keystore before distributing:
> ```bash
> jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
>   -keystore your-release-key.jks app-release-unsigned.apk alias_name
> ```

### Install Directly to Connected Device

```bash
./gradlew installDebug
```

---

## Default Credentials

> **These are for development/demo use only. Change before going to production.**

```
Username: adminkurt
Password: kurt12345
```

To change credentials, edit the login validation in `app/src/main/java/com/example/isdp2java/ui/auth/AuthScreen.kt`.

---

## Known Limitations

- **Hardcoded credentials** — authentication is local and static; no user management.
- **No cloud sync** — all data is stored on-device only.
- **Google Maps API key placeholder** — replace with a real key for production use.
- **Reverse geocoding** — relies on Android's `Geocoder` which requires network connectivity and may fail in areas with poor coverage.
- **OSM tiles** — require an internet connection; offline tile caching is not currently configured.
- **Sign-Up tab** — currently a placeholder; new account creation is not implemented.

---

## License

This project is proprietary. All rights reserved.

---

*Built for telecom field engineers by the SMS-ISDP team.*
