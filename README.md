# wait for Beep! (Beep)

[![CodeQL Security Analysis](https://github.com/cue12/Beep/actions/workflows/codeql.yml/badge.svg)](https://github.com/cue12/Beep/actions/workflows/codeql.yml)
![Android SDK](https://img.shields.io/badge/Android-SDK%2026%2B-brightgreen)
![Target SDK](https://img.shields.io/badge/Target%20SDK-36-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-orange)

**wait for Beep!** is a high-performance, privacy-focused Android application designed for speech-to-text audio transcription and voice processing.

---

## Key Features

- 🔒 **Offline & Privacy-First:** Local speech recognition powered by native C++ [`whisper.cpp`](https://github.com/ggerganov/whisper.cpp) (GGML models) runs completely on-device with zero data transmission.
- ☁️ **Cloud AI Integration:** Seamlessly toggle between local Whisper models and Google Gemini AI for high-accuracy cloud transcription.
- ⚡ **Hardware Accelerated:** Native C++ JNI bridge dynamically selects SIMD-optimized binaries (`ARM64 FP16`, `ARMv7 VFPv4/NEON`, `x86_64`) based on runtime CPU feature detection.
- 🎙️ **Audio Recording & Conversion:** Integrated voice recorder and `MediaCodec` audio pipeline convert any input audio format into standardized 16kHz mono WAV for processing.
- 📲 **Seamless Share Integration:** Accepts shared audio files directly from external apps via system share intents (`android.intent.action.SEND`).
- ⚙️ **Background Resilience:** Reliable background transcription processing using Android `WorkManager`.

---

## Architecture Overview

The app follows a **Model-View-ViewModel (MVVM)** pattern combined with a **Service/Repository** architecture:

- **Presentation Layer (`il.co.bensavit.beep.ui`):** ViewBinding-based activities, custom dialogs, and ViewModels (`MainViewModel`, `TranscriptionTaskViewModel`).
- **Execution & Service Layer:** `WhisperService`, `LocalWhisper`, and `TranscriptionWorker` background jobs.
- **Native JNI Engine:** C++ JNI wrapper communicating with the `whisper.cpp` GGML core.
- **Data & Utility Layer:** `ModelRepository` (Hugging Face / Direct downloads), `SettingsManager`, `AudioConverter`, and `WaveEncoder`.

---

## Prerequisites & Building

### Requirements
* **Android Studio:** Ladybug (2024.2.1) or newer
* **JDK:** Java 17
* **Android SDK:** API Level 36 (`compileSdkVersion 36`)
* **Android NDK:** Version `27.0.12077973`
* **CMake:** Pre-installed via Android SDK Manager

### Build Commands

Clone the repository and build the debug APK using the Gradle wrapper:

```bash
# Make gradlew executable (Linux/macOS)
chmod +x gradlew

# Build Debug APK
./gradlew assembleDebug
```

The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## License

This project is licensed under the terms described in the [LICENSE](LICENSE) file.
