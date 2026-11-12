# wait for Beep! (Beep)

[![CodeQL Security Analysis](https://github.com/cue12/Beep/actions/workflows/codeql.yml/badge.svg)](https://github.com/cue12/Beep/actions/workflows/codeql.yml)
![Android SDK](https://img.shields.io/badge/Android-SDK%2026%2B-brightgreen)
![Target SDK](https://img.shields.io/badge/Target%20SDK-36-blue)
![License](https://img.shields.io/badge/License-Fair_Source_0.9-blue)

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

## Reproducible Builds & Verification

**wait for Beep!** is configured for reproducible builds and cryptographic supply-chain verification. Anyone can audit and verify that the distributed binaries (App Bundle / APK) match the open-source GitHub code bit-for-bit.

### 1. Cryptographic Build Provenance (GitHub Attestations)
Official releases published via GitHub Actions include [GitHub Artifact Attestations](https://docs.github.com/en/actions/security-guides/using-artifact-attestations-to-establish-provenance-for-builds) backed by Sigstore transparency logs.

To verify a downloaded `.aab` or `.apk` against this repository:
```bash
gh attestation verify app-release.aab --repo cue12/Beep
```

### 2. Verifying Local Build vs. Published Release
To verify that the code in a release tag produces the exact same DEX bytecode and native C++ binaries (`libwhisper.so`):

1. **Checkout the release Git tag:**
   ```bash
   git checkout v1.3.0
   ```
2. **Build the release bundle locally:**
   ```bash
   ./gradlew bundleRelease
   ```
3. **Compare DEX & Native Binary Hashes:**
   Unzip both the published release bundle and your locally generated bundle:
   ```bash
   unzip app-release.aab -d release_contents
   unzip app/build/outputs/bundle/release/app-release.aab -d local_contents

   # Compare Java/Kotlin DEX bytecode SHA-256 hashes
   sha256sum release_contents/base/dex/*
   sha256sum local_contents/base/dex/*

   # Compare native C++ (whisper.cpp) library hashes
   sha256sum release_contents/base/lib/arm64-v8a/*
   sha256sum local_contents/base/lib/arm64-v8a/*
   ```

*Note: The Google Play Store re-signs distributed APKs with Google Play App Signing. While Play Store APK signatures will differ from your developer key, the compiled DEX bytecode and C++ shared objects inside the APK remain identical.*

---

## License

This project is licensed under the terms described in the [LICENSE](LICENSE) file.

