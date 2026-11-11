package il.co.bensavit.beep.single;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.os.HandlerCompat;

import il.co.bensavit.beep.bean.WhisperSegment;
import il.co.bensavit.beep.utils.AssetUtils;
import il.co.bensavit.beep.utils.Logger;
import com.whispercpp.java.whisper.WhisperContext;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class LocalWhisper {
  public static final LocalWhisper INSTANCE = new LocalWhisper();

  private volatile String currentModelPath = "models/ggml-base.bin";
  private volatile WhisperContext whisperContext;
  private Handler mainHandler;

  private Handler getMainHandler() {
    if (mainHandler == null) {
      mainHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    }
    return mainHandler;
  }

  private LocalWhisper() {
  }

  public synchronized void setModelPath(String modelPath) {
    if (!modelPath.equals(currentModelPath)) {
      Logger.i("LocalWhisper", "Switching model from " + currentModelPath + " to " + modelPath);
      currentModelPath = modelPath;
      if (whisperContext != null) {
        try {
          Logger.i("LocalWhisper", "Releasing previous native resources...");
          whisperContext.release();
          Logger.i("LocalWhisper", "Previous resources released.");
        } catch (Exception e) {
          Logger.e("LocalWhisper", "Error releasing model", e);
        }
        whisperContext = null;
      }
    }
  }

  public String getCurrentModelPath() {
    return currentModelPath;
  }

  public String getCurrentModelName() {
    if (currentModelPath == null || currentModelPath.isEmpty()) {
      return "";
    }
    return new File(currentModelPath).getName();
  }

  public boolean isInitialized() {
    return whisperContext != null;
  }

  public synchronized void init(Context context) {
    if (whisperContext == null) {
      Logger.i("LocalWhisper", "Initializing native Whisper context for: " + currentModelPath);
      Context appCtx = context.getApplicationContext();
      File filesDir = appCtx.getFilesDir();
      File modelFile = AssetUtils.copyFileIfNotExists(appCtx, filesDir, currentModelPath);
      
      if (!modelFile.exists()) {
        Logger.e("LocalWhisper", "Model file does not exist: " + modelFile.getAbsolutePath());
        throw new IllegalStateException("Model file does not exist: " + modelFile.getName());
      }
      
      if (modelFile.length() == 0) {
        Logger.e("LocalWhisper", "Model file is empty: " + modelFile.getAbsolutePath());
        // Clean up empty file so it can be re-downloaded/copied
        modelFile.delete();
        throw new IllegalStateException("Model file is empty: " + modelFile.getName());
      }

      String realModelFilePath = modelFile.getAbsolutePath();
      Logger.i("LocalWhisper", "Loading model from: " + realModelFilePath + " (size: " + modelFile.length() + " bytes)");
      
      try {
        // Initialize WhisperContext which triggers JNI model loading
        whisperContext = WhisperContext.createContextFromFile(realModelFilePath);
        Logger.i("LocalWhisper", "Native Whisper context successfully initialized.");
      } catch (Exception e) {
        Logger.e("LocalWhisper", "Failed to create Whisper context from file: " + realModelFilePath, e);
        // If it failed to load, the file might be corrupted (e.g. HTML instead of binary)
        // We might want to delete it, but let's be cautious for now.
        throw e;
      }
    } else {
      Logger.i("LocalWhisper", "Model already initialized, skipping.");
    }
  }

  public synchronized void release() {
    if (mainHandler != null) {
      mainHandler.removeCallbacksAndMessages(null);
    }
    if (whisperContext != null) {
      try {
        whisperContext.release();
      } catch (Exception e) {
        e.printStackTrace();
      }
      whisperContext = null;
    }
  }

  private void toastModelLoading(Context context) {
    getMainHandler().post(() -> {
      if (context != null) {
        Toast.makeText(context.getApplicationContext(), il.co.bensavit.beep.R.string.please_wait_model_loading, Toast.LENGTH_SHORT).show();
      }
    });
  }

  public synchronized List<WhisperSegment> transcribeDataWithTime(Context context, float[] audioData, String language, WhisperContext.TranscriptionProgressListener listener) throws ExecutionException, InterruptedException {
    if (whisperContext == null) {
      toastModelLoading(context);
      return null;
    } else {
      // Calls WhisperContext which uses JNI for transcription with timestamps
      return whisperContext.transcribeDataWithTime(audioData, language, listener);
    }
  }
}
