# Beep Application — Manual QA Verification Checklist

This document contains the standard end-to-end manual QA test scenarios for verifying the **Beep** Android application. Execute these test cases prior to major release builds or significant architectural changes.

---

## Quick Navigation

- [1. Onboarding & Interactive Tutorial](#1-onboarding--interactive-tutorial)
- [2. External Audio Sharing & Intent Integration (WhatsApp)](#2-external-audio-sharing--intent-integration-whatsapp)
- [3. Dual Engine & Mode Switching (Offline vs Online)](#3-dual-engine--mode-switching-offline-vs-online)
- [4. Offline Engine (Whisper C++ / GGML Models)](#4-offline-engine-whisper-c--ggml-models)
- [5. Online Engine (Gemini Cloud API)](#5-online-engine-gemini-cloud-api)
- [6. Audio Language Configuration](#6-audio-language-configuration)
- [7. History & Storage File Selection](#7-history--storage-file-selection)
- [8. Transcription Task Cards & Gestures](#8-transcription-task-cards--gestures)
- [9. In-App Audio Recording](#9-in-app-audio-recording)
- [10. Background Processing & WorkManager](#10-background-processing--workmanager)
- [11. System Integration, Edge Cases & UI/UX Polish](#11-system-integration-edge-cases--uiux-polish)

---

## 1. Onboarding & Interactive Tutorial

### 1.1 First Launch Auto-Trigger
- [ ] **TC-TUT-01: First-Launch Sequence**
  - **Precondition:** Clear app data or perform a fresh installation.
  - **Steps:** Launch Beep for the first time.
  - **Expected Result:** The 9-step interactive tutorial (`MaterialTapTargetSequence`) begins automatically:
    1. Output Card (Welcome & WhatsApp instructions)
    2. Engine Mode Toggle (Offline/Online swipe/menu instructions)
    3. Model Selection (Offline model warning & selection)
    4. Audio Language Selection
    5. History Button
    6. Transcription Task Container (Card long-press gestures)
    7. Sort Tasks Button
    8. Toolbar Options Menu
    9. Clear Button
  - **Verification:** State `tutorial_shown` persists as `true` upon completion, and a "Tutorial completed!" Snackbar appears.

- [ ] **TC-TUT-02: Tutorial Skip Gesture**
  - **Precondition:** Reset tutorial state.
  - **Steps:** Launch app or restart tutorial, then tap anywhere outside the focal prompt target or press the hardware Back button.
  - **Expected Result:** The tutorial dismisses immediately. A "Tutorial skipped. Reopen anytime from the menu ('Start Tutorial')." Snackbar is displayed.

### 1.2 Re-Triggering Tutorial
- [ ] **TC-TUT-03: Manual Re-Trigger via Menu**
  - **Precondition:** Tutorial has already been completed or skipped.
  - **Steps:** Tap the overflow menu (3 dots) in the top-right toolbar -> Select **Start Tutorial**.
  - **Expected Result:** Tutorial state resets and the 9-step sequence restarts from Step 1.

- [ ] **TC-TUT-04: Manual Re-Trigger via Toolbar Long-Press**
  - **Precondition:** App opened on main screen.
  - **Steps:** Perform a long-press on the top toolbar area.
  - **Expected Result:** A Snackbar "Tutorial state reset!" appears, and the tutorial sequence starts from Step 1.

---

## 2. External Audio Sharing & Intent Integration (WhatsApp)

### 2.1 WhatsApp Audio Sharing
- [ ] **TC-SHR-01: Single Voice Note Share from WhatsApp**
  - **Steps:** Open WhatsApp -> Long-press a voice message -> Tap **Share** -> Select **Beep**.
  - **Expected Result:** Beep launches directly into the main screen, converts the audio (e.g., `.opus`) to 16kHz WAV format, inflates a new transcription task card, and begins transcribing automatically.

- [ ] **TC-SHR-02: Multiple Audio Files Share**
  - **Steps:** Select multiple audio files in a File Manager or Messaging app -> Tap **Share** -> Select **Beep**.
  - **Expected Result:** Beep handles `ACTION_SEND_MULTIPLE`, inflates task cards for each file, and executes concurrent transcriptions.

### 2.2 Audio Formats & Conversions
- [ ] **TC-SHR-03: Diverse Audio Extensions**
  - **Steps:** Share `.m4a`, `.ogg`, `.mp3`, `.aac`, `.flac`, and `.wav` audio files to Beep.
  - **Expected Result:** `AudioConverter` & `WaveEncoder` successfully convert all non-16kHz WAV files to 16kHz mono WAV without error before feeding into the engine.

---

## 3. Dual Engine & Mode Switching (Offline vs Online)

### 3.1 Mode Switching Gestures & Menu
- [ ] **TC-MOD-01: Toolbar Horizontal Swipe Gesture**
  - **Steps:** Swipe horizontally across the top toolbar to toggle between Online (Gemini) and Offline (Whisper) modes.
  - **Expected Result:** 
    - Selected mode updates smoothly.
    - Background canvas animator smoothly transitions between Offline accent color and Online accent color.
    - Card shimmer animation updates to match the active mode color.

- [ ] **TC-MOD-02: Overflow Menu Toggle**
  - **Steps:** Tap overflow menu -> Select **Mode: Offline (Whisper)** / **Mode: Online (Gemini)**.
  - **Expected Result:** Engine updates accordingly and UI reflects active mode status.

---

## 4. Offline Engine (Whisper C++ / GGML Models)

### 4.1 Model Downloading & Selection
- [ ] **TC-OFF-01: Model Download Dialog**
  - **Steps:** Switch to Offline mode -> Open menu -> Tap **Select / Download Model**.
  - **Expected Result:** Dialog lists available GGML models (Tiny, Base, Small, Medium). Downloading displays real-time progress percentage.

- [ ] **TC-OFF-02: Tiny Model Quality Warning**
  - **Steps:** Select the **Tiny** model.
  - **Expected Result:** A warning dialog/notice appears informing the user that Tiny model quality is low and intended for quick testing only, recommending larger models for actual conversions.

### 4.2 Local Transcription & Memory Management
- [ ] **TC-OFF-03: Offline Transcription Accuracy & Speed**
  - **Steps:** Share a 15-second clear voice recording in Offline mode using Base or Small model.
  - **Expected Result:** Transcription finishes cleanly with accurate text. Elapsed time counter updates accurately.

- [ ] **TC-OFF-04: Model Unloading via Clear Button**
  - **Steps:** Complete an offline transcription -> Long-press the **Clear** button -> Confirm the "Clear All & Unload Model" dialog.
  - **Expected Result:** Confirmation dialog appears; upon confirmation, task cards are removed from screen AND native Whisper model memory is safely unloaded from RAM.

---

## 5. Online Engine (Gemini Cloud API)

### 5.1 API Key Setup & Validation
- [ ] **TC-ONL-01: Gemini API Key Configuration**
  - **Steps:** Switch to Online mode without an API key -> Tap toolbar or menu **Configure Gemini**.
  - **Expected Result:** `GeminiConfigDialogFragment` opens. Saving a valid API key persists it to `SettingsManager`. Tapping "Get Gemini API Key (Tutorial)" opens the external video guide URL.

### 5.2 Cloud Transcription & Error Handling
- [ ] **TC-ONL-02: Successful Online Transcription**
  - **Steps:** With a valid Gemini API key, share an audio file in Online mode.
  - **Expected Result:** Fast transcription response with high accuracy and low latency.

- [ ] **TC-ONL-03: Network Disconnection / Invalid Key**
  - **Steps:** 
    1. Turn off Wi-Fi & Mobile Data, then initiate Online transcription.
    2. Enter an invalid API key, then initiate Online transcription.
  - **Expected Result:** App shows explicit error message (e.g. Network offline or Invalid API key / 401 Unauthorized) without crashing.

---

## 6. Audio Language Configuration

- [ ] **TC-LANG-01: Auto-Detect Language**
  - **Steps:** Set language to **Auto-Detect** -> Transcribe audio in different languages (e.g., English, Hebrew, Spanish).
  - **Expected Result:** Whisper/Gemini accurately auto-detects and transcribes in the source spoken language.

- [ ] **TC-LANG-02: Explicit Language Override**
  - **Steps:** Open menu -> Tap **Select Audio Language** -> Choose a specific language (e.g. Hebrew or Spanish).
  - **Expected Result:** Transcriptions strictly enforce the selected language setting.

---

## 7. History & Storage File Selection

- [ ] **TC-HIST-01: Viewing History & Sample Message**
  - **Steps:** Tap the **History** button.
  - **Expected Result:** History dialog displays past transcription records and provides a sample audio message option for instant testing.

- [ ] **TC-HIST-02: Direct Local File Picker**
  - **Steps:** Tap History dialog -> Select local file picker -> Choose an audio file from internal storage.
  - **Expected Result:** Selected file is loaded, converted, and transcribed correctly.

---

## 8. Transcription Task Cards & Gestures

- [ ] **TC-CRD-01: Copy Text Gesture**
  - **Steps:** Long-press directly on the **transcription text** within a task card.
  - **Expected Result:** Text is copied to device clipboard. A "Copied to clipboard" Snackbar appears.

- [ ] **TC-CRD-02: Delete Individual Card Gesture**
  - **Steps:** Long-press on the **card background** (outside the transcription text).
  - **Expected Result:** The individual task card is removed from the screen with a smooth transition.

- [ ] **TC-CRD-03: Task Sorting (Newest vs Oldest)**
  - **Steps:** Add multiple transcription cards -> Tap **Sort Tasks** button.
  - **Expected Result:** Task list toggles between Newest First and Oldest First ordering, with confirmation Snackbar ("Newest first" / "Oldest first").

- [ ] **TC-CRD-04: Clear All Tasks**
  - **Steps:** Tap the **Clear** button -> Confirm the "Clear Tasks" dialog.
  - **Expected Result:** Confirmation dialog appears; upon confirmation, all task cards are removed from the screen container.

---

## 9. In-App Audio Recording

- [ ] **TC-REC-01: Microphone Permission Handling**
  - **Steps:** Launch app fresh -> Tap menu -> **Record Audio**.
  - **Expected Result:** Prompt for `RECORD_AUDIO` runtime permission. Denying shows an appropriate permission explanation dialog.

- [ ] **TC-REC-02: Record & Transcribe Flow**
  - **Steps:** Grant permission -> Record 5 seconds of audio -> Tap Stop/Done.
  - **Expected Result:** Recorded audio file is saved, converted, and immediately transcribed as a new task card.

---

## 10. Background Processing & WorkManager

- [ ] **TC-BG-01: App Backgrounding During Active Transcription**
  - **Steps:** Share a long audio file (e.g., 2 minutes) -> Immediately press Home button to background Beep.
  - **Expected Result:** Foreground notification ("Background tasks running") appears. Transcription completes successfully in background. Re-opening Beep displays the completed transcription card.

---

## 11. System Integration, Edge Cases & UI/UX Polish

- [ ] **TC-SYS-01: Process Death & Rotation**
  - **Steps:** Rotate device between Portrait and Landscape during active transcription and idle state.
  - **Expected Result:** UI state, active tasks, selected mode, and ongoing progress survive configuration changes cleanly without UI overlap or app crashes.

- [ ] **TC-SYS-02: Dark & Light Theme Support**
  - **Steps:** Toggle system Dark Theme on/off.
  - **Expected Result:** Colors, card backgrounds, shimmer animations, and text readability adjust gracefully in both themes.

- [ ] **TC-SYS-03: RTL Layout Support (Hebrew)**
  - **Steps:** Switch system language or app locale to Hebrew (`he`).
  - **Expected Result:** Entire UI layout mirrors correctly for Right-to-Left alignment (buttons, toolbar, task card text).
