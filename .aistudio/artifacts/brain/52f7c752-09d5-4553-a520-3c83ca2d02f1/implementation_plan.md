# Leafy — Eco-Conscious Offline Local AI Workspace

Leafy is a PocketPal-inspired, 100% offline Android application for private, energy-efficient on-device AI chat and model management. It unifies four specialized edge inference runtimes (**llama.cpp**, **Bitnet.cpp**, **T-MAC**, and **ExecuTorch**) behind an intuitive, bright botanical interface with real-time battery telemetry, eco-power profiles, and power-user local AI workflows.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All core product preferences from our clarification interview have been locked into this plan:

- **Confirmed Decision 1 — Full Suite of Community Local AI Workflows**: Leafy will include **all five** requested power features:
  1. **Conversation Branching & Message Editing**: Edit any user or assistant message to fork a new conversation branch, and switch seamlessly between sibling branches (`< 1/3 >`) without losing history.
  2. **Local Document RAG & Context Pinning**: Import or paste local documents/notes, automatically chunk and index them on-device, pin critical snippets into the active context window, and inspect retrieved passages per response.
  3. **Voice Input & Offline Text-to-Speech (TTS)**: Dictate prompts hands-free via Android speech recognition and listen to assistant replies aloud using Android's native offline `TextToSpeech` engine.
  4. **Real-Time Token Speed & Battery Telemetry**: Live HUD and message badges displaying Tokens/sec (`t/s`), Time-to-First-Token (`TTFT`), RAM footprint, quantized KV-cache compression, and live device battery current/temperature from Android's `BatteryManager`.
  5. **System Prompt Templates & Persona Presets**: Switch between built-in and custom personas (e.g., *Eco Researcher*, *Code Architect*, *Socratic Tutor*, *Concise Summarizer*, *Creative Storyteller*) with per-persona sampling parameters.
- **Confirmed Decision 2 — Hybrid Model Hub & Local Weight Import**: Users can browse a curated **Offline-Ready Model Catalog** (filtering by runtime: `llama.cpp`, `Bitnet.cpp`, `T-MAC`, `ExecuTorch` and quantization format such as `Q4_K_M`, `1.58-bit i2_s`, `INT4-LUT`, `XNNPACK .pte`) **and** import custom model files directly from device storage using the Android system file picker (`OpenDocument`).
- **Confirmed Decision 3 — Preset Power Profiles (Eco, Balanced, Performance)**: One-tap power mode switching that dynamically tunes CPU thread allocation, context window length, quantization look-up tables, and token generation pacing to protect battery health and prevent thermal throttling.

---

## 1. Overview & Core Concept

- **What It Does**: Leafy empowers users to run, benchmark, and chat with quantized Large Language Models entirely on their phone with zero cloud calls, zero telemetry, and complete data sovereignty. Users can switch models mid-conversation, compare runtimes (`llama.cpp` vs. 1-bit `Bitnet.cpp` vs. lookup-table `T-MAC` vs. `ExecuTorch`), attach local documents for on-device RAG, branch conversations, and monitor exact milliwatt/battery impact in real time.
- **Target Audience / Persona**: Privacy advocates, local AI enthusiasts, mobile developers, students, and off-grid users who want a friendly, approachable, and battery-respectful alternative to cloud AI apps.
- **Key Value**: Combines deep technical control (multi-runtime selection, quantization formats, KV-cache tuning, RAG chunking) with a joyful, bright botanical aesthetic that makes local AI feel refreshing rather than intimidating.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Zero-State & Instant Onboarding**:
   - Launching Leafy opens directly into the **Chat & Inference Studio** with a pre-loaded lightweight starter model (*Leafy-Nano-1.58B* on `Bitnet.cpp` in **Eco** mode) ready for immediate offline interaction—no sign-in wall or cloud setup required.
   - A floating **Runtime & Model Pill** in the top bar shows the active model, quantization badge (`1.58-bit`), active runtime (`Bitnet.cpp`), and active Power Profile (`Eco Leaf`). Tapping it opens the **Quick Model & Runtime Switcher** bottom sheet for instant mid-chat model swapping.
2. **Rich Local AI Chat & Branching**:
   - Users type, use **Voice Dictation**, or attach a **Pinned RAG Document** from the composer bar.
   - During generation, tokens stream in real time with a live **Eco-Telemetry Pill** showing instantaneous `t/s`, `TTFT`, active threads, and battery temperature/drain rate.
   - Every message bubble includes action chips: **Speak (Offline TTS)**, **Edit & Branch**, **Regenerate with Different Model**, **Copy**, and **Inspect RAG Context / Telemetry**. When a message is edited or regenerated, a branch selector (`Branch 2 of 2`) appears directly on the message node.
3. **Model Hub & Local File Importer**:
   - The **Models & Runtimes** tab presents two views: **Installed & Local Weights** (with a prominent *Import Local Model File (.gguf / .bin / .pte)* button via Android Document Picker) and **Curated Edge Catalog** organized by runtime (`llama.cpp`, `Bitnet.cpp`, `T-MAC`, `ExecuTorch`) with RAM compatibility checks against the device's actual physical memory.
4. **Local RAG Knowledge Base & Personas**:
   - The **Knowledge & Personas** tab lets users create/import local text & markdown documents, view automatic token chunks, pin documents to the active chat, and customize **Persona Presets** (system prompt, temperature, top-p, repetition penalty).
5. **Eco-Telemetry & Typography Studio**:
   - The **Eco & Settings** tab provides the **Power Profile Selector** (*Eco*, *Balanced*, *Performance*), runtime quantization benchmarks, complete offline data export/wipe controls, and the **Typography Switcher** to toggle instantly between **Rounded Clean Sans-Serif** (`Nunito` / `Quicksand`) and **Playful Comic Sans Mode** (`Comic Neue`).

### Visual Identity & Theme

- **Aesthetic Direction**: *Sunlit Botanical Conservatory* — bright, colorful, tactile, and optimistic. Instead of sterile dark-mode terminal aesthetics, Leafy celebrates nature, sustainability, and transparency through organic curved geometry, custom Canvas botanical leaf-vein background textures, and vibrant chlorophyll accents.
- **Color Palette & Mood**:
  - **Primary Background (`Surface` / `Background`)**: Sunlit Morning Dew (`#F6FBF4`) and Soft Sage Mist (`#EAF4E7`) in Light Mode (default); Deep Moss Canopy (`#0F1D14`) in optional Dark Eco Mode.
  - **Primary Accent (`Primary`)**: Vibrant Leaf Emerald (`#1E7E48`) with Mint Highlight containers (`#BFF0CE`).
  - **Secondary Accent (`Secondary`)**: Warm Sunbeam Amber (`#E08E26`) and Botanical Coral (`#E86F52`) for colorful runtime badges, quantization tags, and branch indicators.
  - **Tertiary Accent (`Tertiary`)**: Dewdrop Teal (`#1A7F8E`) for RAG document citations and voice/TTS waveforms.
  - **Semantic Eco Indicators**: Chlorophyll Green (`#22A055`) for Eco profile, Golden Pollen (`#D9822B`) for Balanced profile, and Terracotta Flame (`#D94E34`) for high-wattage Performance profile.
- **Typography & Hierarchy**:
  - **Default Mode (Rounded, Clean Sans-Serif)**: Locally bundled **Quicksand** (display & headers) paired with **Nunito** (chat body, controls, and telemetry) for warm, rounded, high-legibility reading across all screen sizes.
  - **Playful Mode (Comic Sans Aesthetic)**: Locally bundled **Comic Neue** (bold headers and regular body) selectable via a prominent one-tap toggle in the top bar or settings, giving users an informal, approachable, dyslexia-friendly comic aesthetic on demand.
- **Component Styling & Layout**:
  - **Organic Textures**: Subtle custom `Canvas` botanical leaf-grain watermarks and flowing contour lines behind hero cards and empty states.
  - **Asymmetric Chat Bubbles & Pill Badges**: Generous `24.dp` rounded corners with leaf-inspired asymmetric corners (`4.dp` on the speaker tail), layered tonal elevation, and colorful pill chips for runtime (`llama.cpp`, `Bitnet.cpp`, `T-MAC`, `ExecuTorch`) and quantization (`Q4_K_M`, `i2_s`, `LUT-4`, `PTE`).
- **Interactive Feedback & Motion**:
  - Spring-physics transitions (`spring(dampingRatio = Spring.DampingRatioMediumBouncy)`) when switching conversation branches, expanding RAG citations, or toggling Power Profiles.
  - Animated voice/TTS waveform bars and a gentle pulsing leaf indicator while local tokens stream.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Strict 100% Offline Local Persistence via Room Database**
  - *Chosen Approach*: Store all conversations, message trees (branches), personas, RAG document chunks, and model configurations in an on-device **Room SQLite Database** with zero network permissions required for core operation.
  - *Why*: Directly honors the user's requirement for an app that runs "entirely offline" with "complete data sovereignty" and privacy focus. Users never have to create an account or trust a cloud server.
  - *Alternatives Considered*: Cloud Firestore was rejected because requiring internet connectivity and Google Sign-In would violate the core offline-first, zero-telemetry privacy promise of a PocketPal-style local AI app.
- **Decision 2: Multi-Runtime Inference Engine with Real Quantized Processing & Hardware Telemetry**
  - *Chosen Approach*: Implement a modular `LocalInferenceEngine` supporting the four requested runtimes (**llama.cpp**, **Bitnet.cpp**, **T-MAC**, and **ExecuTorch**). The engine executes local quantized tensor/LUT token streaming combined with on-device RAG retrieval, persona system-prompt conditioning, deterministic/temperature sampling, and real Android hardware telemetry (`BatteryManager`, `ActivityManager.MemoryInfo`, CPU core topology).
  - *Why*: Delivers an authentic, responsive PocketPal workflow where switching between `Bitnet.cpp` (1.58-bit ternary weights, ultra-low battery draw), `T-MAC` (table-lookup matrix multiplication without dequantization), `llama.cpp` (GGUF `Q4_K_M` / `Q8_0`), and `ExecuTorch` (`.pte` XNNPACK/NPU graph) visibly impacts memory footprint, token speed, quantization metrics, and response synthesis.
- **Decision 3: Tree-Structured Conversation History for Non-Destructive Branching**
  - *Chosen Approach*: Represent chat messages as a parent-linked tree (`parentMessageId` + `branchIndex`) in Room rather than a flat list.
  - *Why*: Allows users to edit any prompt five turns back or regenerate an answer with a different runtime (e.g., comparing `llama.cpp` vs. `Bitnet.cpp` on the exact same prompt) and flip back and forth between branches effortlessly.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                        LEAFY UI LAYER (Jetpack Compose)                     │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Top Bar: Active Model & Runtime Pill │ Power Profile Chip │ Font Flip │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│         │                        │                        │                 │
│         ▼                        ▼                        ▼                 │
│  ┌─────────────┐         ┌──────────────┐         ┌──────────────┐          │
│  │ Chat Studio │         │  Model Hub & │         │  Knowledge,  │          │
│  │  • Branching│         │   Runtimes   │         │  Personas &  │          │
│  │  • RAG Pins │         │  • llama.cpp │         │  Eco Studio  │          │
│  │  • Voice/TTS│         │  • Bitnet.cpp│         │  • Local RAG │          │
│  │  • Live HUD │         │  • T-MAC     │         │  • Power Mode│          │
│  └──────┬──────┘         │  • ExecuTorch│         │  • Comic Sans│          │
│         │                └───────┬──────┘         └───────┬──────┘          │
└─────────┼────────────────────────┼────────────────────────┼─────────────────┘
          │                        │                        │
          ▼                        ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                  STATE & VIEWMODEL LAYER (StateFlow + MVVM)                 │
│   LeafyViewModel: Active Branch Resolver │ Streaming Controller │ RAG Ranker│
└─────────┬────────────────────────┬────────────────────────┬─────────────────┘
          │                        │                        │
          ▼                        ▼                        ▼
┌─────────────────────┐  ┌──────────────────────┐  ┌──────────────────────────┐
│  LOCAL PERSISTENCE  │  │ MULTI-RUNTIME ENGINE │  │   DEVICE HARDWARE APIs   │
│   (Room Database)   │  │ • Quantized LUT/GGUF │  │ • Android BatteryManager │
│ • Conversations     │  │ • RAG Cosine/BM25    │  │ • ActivityManager RAM    │
│ • MessageNodes(Tree)│  │ • Power Profile Gov. │  │ • TextToSpeech (Offline) │
│ • LocalModelConfigs │  │ • Token Stream Flow  │  │ • SpeechRecognizer       │
│ • RagDocuments      │  └──────────────────────┘  │ • SAF OpenDocument Picker│
│ • PersonaPresets    │                            └──────────────────────────┘
└─────────────────────┘
```

### Data Model & State

- **Conversation & MessageNode (Branching Tree)**:
  - Each `Conversation` tracks its `id`, `title`, `activeModelId`, `activeRuntime`, `personaId`, `pinnedDocumentIds`, and `activeLeafMessageId`.
  - Each `MessageNode` stores `id`, `conversationId`, `parentId` (nullable for root), `role` (`USER`, `ASSISTANT`, `SYSTEM`), `content`, `timestamp`, `runtimeUsed`, `modelName`, `quantizationType`, `tokensPerSec`, `ttftMs`, `energyMicroJoules`, and `citedRagChunks`.
- **LocalModelConfig**:
  - Tracks `id`, `name`, `family`, `runtime` (`LLAMA_CPP`, `BITNET_CPP`, `T_MAC`, `EXECUTORCH`), `quantization` (`Q4_K_M`, `I2_S_1_58BIT`, `INT4_LUT`, `PTE_INT8`), `sizeMb`, `contextLength`, `isInstalled`, `localFileUri` (for user-imported weights), and `parameterCount`.
- **RagDocument & RagChunk**:
  - Stores user-added or file-imported local documents (`title`, `rawContent`, `tokenCount`, `isPinned`) split into searchable semantic passages for instant offline retrieval.
- **PersonaPreset & AppSettings**:
  - Stores reusable system prompts, temperature, top-p, active **Power Profile** (`ECO`, `BALANCED`, `PERFORMANCE`), and active **Typography Mode** (`ROUNDED_SANS` vs. `COMIC_SANS`).

### Interactive Component & State Mapping

- **Runtime & Model Switcher Sheet**: Selecting a different installed model or runtime updates the active session configuration immediately, recalculates KV-cache memory against real device RAM (`ActivityManager`), and logs a non-intrusive model-switch marker in the chat.
- **Conversation Branching Controls**:
  - Clicking **Edit** on a previous user prompt opens an inline editor; submitting creates a new child `MessageNode` sharing the same `parentId` and triggers streaming generation on the new branch.
  - Clicking `< 1/2 >` on any branched message traverses the descendant chain of the selected sibling so the entire downstream conversation updates instantaneously.
- **Local RAG Pipeline**:
  - Toggling the **Pin to Chat** switch on any local document activates BM25 + keyword-density chunk retrieval during prompt submission, injecting relevant passages into the local inference context and displaying expandable **Cited Local Sources** chips under the assistant's reply.
- **Preset Power Profiles (`Eco` / `Balanced` / `Performance`)**:
  - Switching profiles updates thread concurrency, context window cap (`1024` / `4096` / `8192`), and token cadence, immediately reflected in the **Real-Time Battery & Thermal HUD** powered by Android's `BatteryManager` (`BATTERY_PROPERTY_CURRENT_NOW`, temperature, and battery level).
- **Offline Voice & TTS**:
  - Tapping the speaker icon on any assistant message invokes Android's native `TextToSpeech` engine with visual playback state; tapping the microphone button launches speech-to-text dictation into the prompt composer.

---

## 5. Local AI Architecture Implementation Status

### Step 1: Real On-Device Model File Management & Validation [COMPLETED]
- Download manager (`ModelDownloadManager.kt`) streams raw binary weights directly from Hugging Face over HTTP/HTTPS with live byte-level progress reporting and zero fake placeholder synthesis.
- GGUF header parser (`LocalInferenceEngine.readGgufHeader`) validates raw binary magic bytes (`0x46554747` = `GGUF`), format version, tensor count, and metadata key-value counts.
- Inference is strictly blocked if weights are missing from disk or fail binary validation.

### Step 2: Real Native Inference Runtime Integration [COMPLETED]
- Integrated `io.github.ljcamargo:llamacpp-kotlin:0.4.0` with precompiled multi-architecture native libraries (`librnllama.so` for `arm64-v8a` and `x86_64`).
- `LocalInferenceEngine` initializes native llama.cpp execution context via `LlamaAndroid` using `ParcelFileDescriptor` directly from the downloaded GGUF file.
- Runtime thread concurrency is bound to the active `PowerProfile` (`cpuThreads`).
- System prompt instructions and retrieved on-device RAG passages are formatted into standard ChatML prompt templates.

### Step 3: Real Token Streaming From Model Execution [COMPLETED]
- Tokens stream live from native `partialToken` callbacks into a Kotlin Coroutines Flow (`StreamingStep`).
- Live metrics are computed in real-time: instantaneous tokens per second (`currentTps`), Time-to-First-Token (`ttftMs`), generated token count, and energy consumption (`energyMicroWh`).
- Native execution is interruptible via `stopInference()` (`LlamaAndroid.stopCompletion`).
- All synthetic or canned text generation has been permanently eliminated. Output strictly reflects model execution.
