# IDLIX Android (Mobile & Android TV)

A native Android application built with **Kotlin**, **Jetpack Compose**, **Media3 ExoPlayer**, and a **Client-side WebView Scraper (Option B - Standalone)**.

Designed for **Android Mobile Devices** and **Android TV** (with D-Pad remote control focus support).

---

## 🚀 Features

- **No External Backend Server Required (Option B)**: Uses background Android WebView to seamlessly bypass Cloudflare JS challenges and execute the 6-step IDLIX token redemption chain.
- **Android TV Ready**: D-Pad remote control navigation, TV Focus highlights, and Leanback launcher support.
- **HLS Video Streaming**: Native Media3 ExoPlayer with `.m3u8` video streaming support & `.vtt` multi-language subtitle tracks.
- **Rich Catalog & Search**: Browse Homepage sections (Trending, Movies, TV Series, Genres) and search content directly.
- **Ultra Lightweight**: Small APK footprint (< 20 MB) and low memory/CPU overhead during video playback.

---

## 📁 Project Structure

```
idlix-android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/idlix/android/
│           ├── MainActivity.kt               # Navigation Entry Point
│           ├── data/
│           │   ├── api/IdlixApiClient.kt    # OkHttp Catalog Fetcher
│           │   ├── model/Models.kt          # Data models
│           │   └── scraper/IdlixStreamScraper.kt # Client-side WebView Stream Scraper
│           └── ui/
│               ├── components/Components.kt # MovieCard with D-Pad Focus, HeroBanner, Rows
│               ├── player/PlayerActivity.kt # Media3 ExoPlayer Fullscreen Activity
│               ├── screens/
│               │   ├── DetailScreen.kt     # Movie/Series overview & Episode selector
│               │   ├── HomeScreen.kt       # Catalog browsing screen
│               │   └── SearchScreen.kt     # Movie search screen
│               └── theme/Theme.kt          # IDLIX Dark Mode UI Theme
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
└── settings.gradle.kts
```

---

## 🛠️ How to Build and Run

### Option 1: Android Studio (Recommended)

1. Open **Android Studio**.
2. Select **Open an existing project** and navigate to `D:\Projects\Self\IDLIX\idlix-android`.
3. Wait for Gradle Sync to complete.
4. Select your target device (Android Emulator, Physical Android Phone, or Android TV Emulator/Device).
5. Click **Run (`Shift + F10`)**.

### Option 2: Command Line / Antigravity

**Quick Build & Run Shortcuts (Windows):**
- **Build APK:**
  ```cmd
  .\build.bat
  ```
- **Build + Auto Install + Launch on Device:**
  ```cmd
  .\run.bat
  ```

**Standard Gradle Commands:**
- Generate Debug APK:
  ```cmd
  .\gradlew assembleDebug
  ```
  The generated APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`

- Install via ADB:
  ```cmd
  adb install app/build/outputs/apk/debug/app-debug.apk
  ```

