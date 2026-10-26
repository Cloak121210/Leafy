# Leafy 🌿 — Eco-Conscious Offline Local AI Workspace for Android

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![Offline](https://img.shields.io/badge/Privacy-100%25%20Offline-emerald.svg)](#privacy--data-sovereignty)
[![Runtimes](https://img.shields.io/badge/Runtimes-llama.cpp%20|%20Bitnet.cpp%20|%20T--MAC%20|%20ExecuTorch-orange.svg)](#multi-runtime-edge-architecture)

> **Leafy** is a PocketPal-inspired, 100% offline Android application for private, energy-efficient on-device AI chat and model management. It unites four specialized edge inference runtimes (**llama.cpp**, **Bitnet.cpp**, **T-MAC**, and **ExecuTorch**) behind an intuitive, bright botanical interface with real-time battery telemetry, eco-power profiles, conversation branching, and local document RAG.

---

## 🌟 Key Features

### ⚡ Multi-Runtime Edge Architecture
- **Bitnet.cpp (`1.58-bit i2_s`)**: Uses ternary weights `{-1, 0, +1}` that turn floating-point multiplications into lightweight integer additions, slashing CPU energy draw by up to **71%**.
- **T-MAC (`INT2/INT3/INT4-LUT`)**: Employs activation Lookup Tables (LUTs) in mobile CPU cache to bypass runtime dequantization overhead.
- **llama.cpp (`GGUF • Q4_K_M / Q8_0`)**: Industry-standard GGUF engine with ARM NEON SIMD intrinsics and quantized KV-cache for deep context windows.
- **ExecuTorch (`.pte • XNNPACK`)**: PyTorch edge runtime executing compiled graphs with near-zero overhead and mobile NPU/XNNPACK acceleration.
- **Local Model Weight Import**: Pick and mount custom `.gguf`, `.bin`, `.pte`, and `.tmac` weights directly from device storage.

### 🌿 Full Suite of Local AI Workflows
- **Conversation Branching & Message Editing**: Edit any previous prompt or regenerate an assistant reply to fork a non-destructive sibling branch. Navigate between conversation timelines effortlessly with `< 1/2 >`.
- **Local Document RAG & Context Pinning**: Add or import local Markdown and text notes. Passages are indexed and retrieved 100% on-device via BM25 chunk scoring and cited under assistant responses.
- **Offline Voice Input & Native TTS**: Dictate queries using Android's offline speech recognition and listen to responses read aloud with the built-in offline `TextToSpeech` engine.
- **Real-Time Token Speed & Battery Telemetry**: Live HUD showing Tokens/sec (`t/s`), Time-to-First-Token (`TTFT`), RAM consumption, and live hardware battery drain/temperature from Android's `BatteryManager`.
- **System Prompt Templates & Persona Presets**: Select from curated personas (*Eco Guide*, *Edge Systems Architect*, *Socratic Study Partner*, *Botanical Storyteller*) or create custom personas with temperature and top-p controls.

### 🔋 Preset Power & Thermal Profiles
- **Eco Leaf**: 2 efficiency threads, INT4 KV-cache compression, thermal ceiling kept under 34°C (-62% power draw).
- **Balanced Canopy**: 4 adaptive threads with Q8_0 KV-cache for cool, responsive daily conversations.
- **Sunburst Turbo**: 8 threads, full context window, and maximum token throughput for heavy coding and technical RAG.

### 🎨 Clean Front-and-Center UI & Botanical Aesthetic
- **Minimalist Front Canvas**: Full-screen chat with a floating model capsule and capsule composer. Advanced tools and configurations are organized into a **Special Bento Command Menu** and the **Leafy Canopy Drawer**.
- **Dual Typography Modes**: High-legibility **Rounded Clean Sans-Serif** (`Quicksand` display + `Nunito` body) with an instant one-tap toggle for **Playful Comic Sans Mode** (`Comic Neue`).
- **Native Markdown Parser**: Properly renders headers (`#`), fenced code blocks with language tags and copy button, blockquotes (`>`), bullet lists, and inline formatting without exposing raw markdown marks.
- **Dark Moss Canopy Mode**: Seamless toggle between bright sunlit botanical mist and deep OLED moss dark theme.

---

## 🔒 Privacy & Data Sovereignty

- **Zero Cloud Calls**: Leafy requires zero cloud accounts, zero logins, and zero remote API endpoints.
- **Zero Telemetry & Zero Trackers**: All conversations, branches, notes, and embeddings remain strictly within your device's local Room SQLite database (`leafy_offline_ai.db`).
- **One-Tap Wipe**: Easily clear local chat history or export data anytime from the Eco & Settings screen.

---

## 🏗️ Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3 (M3)
- **Language**: Kotlin 2.2.10
- **Database**: [Room](https://developer.android.com/training/data-storage/room) with KSP (Kotlin Symbol Processing)
- **Architecture**: MVVM with Kotlin Coroutines & `StateFlow`
- **Typography**: Locally bundled Google Fonts (`Quicksand`, `Nunito`, `Comic Neue`, `JetBrains Mono`)
- **System APIs**: Android `BatteryManager`, `ActivityManager`, `TextToSpeech`, and Storage Access Framework (`OpenDocument`)

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 36 (Min SDK 24)
- JDK 11 or higher

### Build Debug APK
```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/Leafy.git
cd Leafy

# Build debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is open-source under the [Apache License 2.0](LICENSE).
