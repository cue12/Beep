package com.whispercpp.java.whisper;

import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.InputStream;

@RequiresApi(api = Build.VERSION_CODES.O)
public class WhisperLib {
  private static final String LOG_TAG = "LibWhisper";

  static {

    Log.d(LOG_TAG, "Primary ABI: " + Build.SUPPORTED_ABIS[0]);
    boolean loadVfpv4 = false;
    boolean loadV8fp16 = false;
    if (WhisperUtils.isArmEabiV7a()) {
      String cpuInfo = WhisperUtils.cpuInfo();
      if (cpuInfo != null) {
        Log.d(LOG_TAG, "CPU info: " + cpuInfo);
        if (cpuInfo.contains("vfpv4")) {
          Log.d(LOG_TAG, "CPU supports vfpv4");
          loadVfpv4 = true;
        }
      }
    } else if (WhisperUtils.isArmEabiV8a()) {
      String cpuInfo = WhisperUtils.cpuInfo();
      if (cpuInfo != null) {
        Log.d(LOG_TAG, "CPU info: " + cpuInfo);
        if (cpuInfo.contains("fphp")) {
          Log.d(LOG_TAG, "CPU supports fp16 arithmetic");
          loadV8fp16 = true;
        }
      }
    }

    if (loadVfpv4) {
      Log.d(LOG_TAG, "Loading libwhisper_vfpv4.so");
      System.loadLibrary("whisper_vfpv4");
    } else if (loadV8fp16) {
      Log.d(LOG_TAG, "Loading libwhisper_v8fp16_va.so");
      System.loadLibrary("whisper_v8fp16_va");
    } else {
      Log.d(LOG_TAG, "Loading libwhisper.so");
      System.loadLibrary("whisper");
    }
  }

  /**
   * JNI call to initialize the Whisper context from an InputStream.
   * @param inputStream The stream containing the model data.
   * @return A pointer (long) to the native whisper_context.
   */
  public static native long initContextFromInputStream(InputStream inputStream);

  /**
   * JNI call to initialize the Whisper context from an Android Asset.
   * @param assetManager The AssetManager to use.
   * @param assetPath The path to the model file within the assets.
   * @return A pointer (long) to the native whisper_context.
   */
  public static native long initContextFromAsset(AssetManager assetManager, String assetPath);

  /**
   * JNI call to initialize the Whisper context from a file path.
   * @param modelPath The absolute path to the model file on disk.
   * @return A pointer (long) to the native whisper_context.
   */
  public static native long initContext(String modelPath);

  /**
   * JNI call to free the native Whisper context.
   * @param contextPtr The pointer to the native whisper_context to free.
   */
  public static native void freeContext(long contextPtr);

  /**
   * Callback interface for JNI to report new transcription segments.
   */
  public interface TranscriptionCallback {
    void onNewSegment(String text);
    void onProgress(int progress);
  }

  /**
   * JNI call to perform a full transcription of the provided audio data.
   * @param contextPtr The pointer to the native whisper_context.
   * @param numThreads The number of threads to use for processing.
   * @param language The language code for transcription (e.g., "en", "auto").
   * @param audioData The PCM f32 audio data (16kHz).
   * @param callback The callback to receive partial results.
   */
  public static native void fullTranscribe(long contextPtr, int numThreads, String language, float[] audioData, TranscriptionCallback callback);

  /**
   * JNI call to get the number of text segments from the last transcription.
   * @param contextPtr The pointer to the native whisper_context.
   * @return The number of segments.
   */
  public static native int getTextSegmentCount(long contextPtr);

  /**
   * JNI call to get the text of a specific segment.
   * @param contextPtr The pointer to the native whisper_context.
   * @param index The index of the segment.
   * @return The text of the segment.
   */
  public static native String getTextSegment(long contextPtr, int index);

  /**
   * JNI call to get the start time of a specific segment.
   * @param contextPtr The pointer to the native whisper_context.
   * @param index The index of the segment.
   * @return The start time in centiseconds.
   */
  public static native long getTextSegmentT0(long contextPtr, int index);

  /**
   * JNI call to get the end time of a specific segment.
   * @param contextPtr The pointer to the native whisper_context.
   * @param index The index of the segment.
   * @return The end time in centiseconds.
   */
  public static native long getTextSegmentT1(long contextPtr, int index);

  /**
   * JNI call to retrieve system information from the native library (e.g., SIMD support).
   * @return A string containing system info.
   */
  public static native String getSystemInfo();

  /**
   * JNI call to perform a memcpy benchmark.
   * @param nthread Number of threads.
   * @return Benchmark results as a string.
   */
  public static native String benchMemcpy(int nthread);

  /**
   * JNI call to perform a GGML matrix multiplication benchmark.
   * @param nthread Number of threads.
   * @return Benchmark results as a string.
   */
  public static native String benchGgmlMulMat(int nthread);
}
