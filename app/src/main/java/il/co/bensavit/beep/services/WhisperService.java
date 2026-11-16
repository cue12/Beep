package il.co.bensavit.beep.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.os.HandlerCompat;

import il.co.bensavit.beep.R;
import il.co.bensavit.beep.app.App;
import il.co.bensavit.beep.bean.WhisperSegment;
import il.co.bensavit.beep.data.SettingsManager;
import il.co.bensavit.beep.single.LocalWhisper;
import il.co.bensavit.beep.utils.Logger;
import il.co.bensavit.beep.utils.WaveEncoder;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.reactivestreams.Subscription;

public class WhisperService {
  public interface TranscriptionCallback {
    void onProgress(String message);
    void onSuccess(String result);
    void onError(String error);
  }

  public static final WhisperService INSTANCE = new WhisperService();
  private static final String TAG = "WhisperService";
  private static final String GEMINI_MODEL_PREFIX = "models/";
  private Handler mainHandler;
  private Subscription currentSubscription;
  private final Set<ListenableFuture<GenerateContentResponse>> activeGeminiFutures = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private int activeTranscriptions = 0;

  private Handler getMainHandler() {
    if (mainHandler == null) {
      mainHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    }
    return mainHandler;
  }

  // ARCHITECTURE NOTE: STATELESS EXECUTION
  // WhisperService is stateless with respect to configuration (API key, model selections).
  // Do NOT store mutable configuration fields here. All configuration must be passed directly from
  // SettingsManager (Single Source of Truth) via method parameters.
  private volatile List<String> cachedGeminiModels = Collections.emptyList();

  public synchronized boolean isBusy() {
    return activeTranscriptions > 0;
  }

  private synchronized void incrementActive() {
    activeTranscriptions++;
  }

  private synchronized void decrementActive() {
    activeTranscriptions = Math.max(0, activeTranscriptions - 1);
  }

  public void transcribeSample(File sampleFile, String language, TranscriptionCallback callback, Context context) {
    incrementActive();
    TranscriptionCallback wrappedCallback = new TranscriptionCallback() {
      private boolean completed = false;

      @Override public void onProgress(String message) { callback.onProgress(message); }

      @Override public synchronized void onSuccess(String result) {
        if (!completed) {
          completed = true;
          decrementActive();
          callback.onSuccess(result);
        }
      }

      @Override public synchronized void onError(String error) {
        if (!completed) {
          completed = true;
          decrementActive();
          callback.onError(error);
        }
      }
    };

    if (!sampleFile.exists()) {
      Logger.e(TAG, "transcribeSample: Audio file does not exist: " + sampleFile.getAbsolutePath());
      wrappedCallback.onError(context.getString(R.string.error_failed_to_decode));
      return;
    }

    String modelName = LocalWhisper.INSTANCE.getCurrentModelName();
    if (modelName == null || modelName.isEmpty()) {
      SettingsManager settingsManager = new SettingsManager(context);
      modelName = settingsManager.getModelName();
    }
    wrappedCallback.onProgress("MODEL_SELECTED:" + modelName);

    long start = System.currentTimeMillis();
    float[] audioData;
    try {
      audioData = WaveEncoder.decodeWaveFile(sampleFile);
    } catch (IOException e) {
      Logger.e(TAG, "transcribeSample: Error decoding wave file", e);
      wrappedCallback.onError(context.getString(R.string.error_failed_to_decode));
      return;
    } catch (OutOfMemoryError e) {
      Logger.e(TAG, "transcribeSample: Out of memory decoding wave file", e);
      wrappedCallback.onError("OUT_OF_MEMORY");
      return;
    } catch (Throwable t) {
      Logger.e(TAG, "transcribeSample: Unexpected error during decoding", t);
      wrappedCallback.onError(context.getString(R.string.error_unexpected_decoding));
      return;
    }
    long end = System.currentTimeMillis();
    logOnly("decode wave file:" + (end - start) + "ms");

    long transcriptionStart = System.currentTimeMillis();
    List<WhisperSegment> transcription;
    try {
      transcription = LocalWhisper.INSTANCE.transcribeDataWithTime(context, audioData, language, wrappedCallback::onProgress);
    } catch (Throwable t) {
      Logger.e(TAG, "transcription error", t);
      String errorMsg = t.getMessage();
      if (t instanceof OutOfMemoryError || (errorMsg != null && errorMsg.contains("OutOfMemory"))) {
        wrappedCallback.onError("OUT_OF_MEMORY");
      } else {
        wrappedCallback.onError(context.getString(R.string.error_transcription_failed));
      }
      return;
    }
    long transcriptionEnd = System.currentTimeMillis();
    if (transcription != null) {
      StringBuilder sb = new StringBuilder();
      for (WhisperSegment segment : transcription) {
        sb.append(segment.getSentence());
      }
      String finalMsg1 = sb.toString();
      logOnly("Transcript successful:" + (transcriptionEnd - transcriptionStart) + "ms");
      wrappedCallback.onSuccess(finalMsg1);
    } else {
      logOnly("Transcript failed:" + (transcriptionEnd - transcriptionStart) + "ms");
      wrappedCallback.onError(context.getString(R.string.error_transcription_failed));
    }
  }

  public void checkModelAvailability(String modelId, String apiKey, TranscriptionCallback callback) {
    if (apiKey == null || apiKey.isEmpty()) {
      callback.onError(modelId);
      return;
    }

    if ("auto".equals(modelId)) {
      callback.onSuccess(modelId);
      return;
    }

    try {
      // Ensure modelId has the models/ prefix which is often required for definitive validation
      String modelPath = modelId.contains("/") ? modelId : GEMINI_MODEL_PREFIX + modelId;
      GenerativeModel gm = new GenerativeModel(modelPath, apiKey);
      GenerativeModelFutures model = GenerativeModelFutures.from(gm);

      // Using a minimal generateContent call is the most reliable way to validate a key
      Content content = new Content.Builder().addText(".").build();
      ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
      activeGeminiFutures.add(response);

      Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
        @Override
        public void onSuccess(GenerateContentResponse result) {
          activeGeminiFutures.remove(response);
          // If we got any response, the key and model are valid
          callback.onSuccess(modelId);
        }

        @Override
        public void onFailure(Throwable t) {
          activeGeminiFutures.remove(response);
          Logger.w(TAG, "Model " + modelId + " availability check failed: " + t.getMessage());
          callback.onError(modelId);
        }
      }, r -> getMainHandler().post(r));
    } catch (Exception e) {
      Logger.e(TAG, "Error checking model availability for " + modelId, e);
      callback.onError(modelId);
    }
  }

  /**
   * Transcribes an audio file using the Gemini API.
   *
   * <p><strong>ARCHITECTURE REQUIREMENT - STATELESS EXECUTION:</strong>
   * This method is explicitly stateless with respect to configuration. It does NOT store or rely on
   * API keys or selected models as mutable instance variables in {@link WhisperService}. The caller
   * (e.g. {@code TranscriptionWorker}) must provide {@code apiKey} and {@code selectedModel} directly
   * from {@code SettingsManager} (the Single Source of Truth). Do NOT re-introduce mutable configuration
   * state fields into this service class.
   */
  public void transcribeWithGemini(File sampleFile, String apiKey, String selectedModel, TranscriptionCallback callback, Context context) {
    incrementActive();
    TranscriptionCallback wrappedCallback = new TranscriptionCallback() {
      private boolean completed = false;

      @Override public void onProgress(String message) { callback.onProgress(message); }

      @Override public synchronized void onSuccess(String result) {
        if (!completed) {
          completed = true;
          decrementActive();
          callback.onSuccess(result);
        }
      }

      @Override public synchronized void onError(String error) {
        if (!completed) {
          completed = true;
          decrementActive();
          callback.onError(error);
        }
      }
    };

    wrappedCallback.onProgress(context.getString(R.string.transcribing_with_gemini, sampleFile.getName()));

    String cleanKey = apiKey != null ? apiKey.trim() : "";
    if (cleanKey.isEmpty()) {
      wrappedCallback.onError(context.getString(R.string.error_gemini_key_not_found));
      return;
    }

    String modelToUse = (selectedModel == null || selectedModel.trim().isEmpty()) ? "auto" : selectedModel.trim();

    if ("auto".equals(modelToUse)) {
      if (cachedGeminiModels.isEmpty()) {
        fetchGeminiModels(cleanKey, models -> {
          if (models.isEmpty()) {
            getMainHandler().post(() -> wrappedCallback.onError(context.getString(R.string.error_fetching_models, "No models found")));
          } else {
            resolveAutoAndTranscribe(sampleFile, models, cleanKey, wrappedCallback, context);
          }
        }, error -> getMainHandler().post(() -> wrappedCallback.onError(context.getString(R.string.error_fetching_models, error))));
        return;
      } else {
        resolveAutoAndTranscribe(sampleFile, cachedGeminiModels, cleanKey, wrappedCallback, context);
        return;
      }
    }

    wrappedCallback.onProgress("MODEL_SELECTED:" + modelToUse);
    transcribeWithModel(sampleFile, modelToUse, cleanKey, wrappedCallback, context);
  }

  public List<String> getPrioritizedModels(List<String> models) {
    List<String> priorityList = new ArrayList<>();
    String[] topPriorities = {"gemini-3.5-flash", "gemini-3.5-flash-lite"};
    for (String p : topPriorities) {
      for (String m : models) {
        if (m.equals(p)) {
          priorityList.add(m);
          break;
        }
      }
    }

    for (String m : models) {
      if (m.contains("flash") && !m.contains("8b") && !priorityList.contains(m)) priorityList.add(m);
    }
    for (String m : models) {
      if (m.contains("flash") && !priorityList.contains(m)) priorityList.add(m);
    }
    for (String m : models) {
      if (!priorityList.contains(m)) priorityList.add(m);
    }
    return priorityList;
  }

  private void resolveAutoAndTranscribe(File sampleFile, List<String> models, String apiKey, TranscriptionCallback callback, Context context) {
    List<String> priorityList = getPrioritizedModels(models);
    checkNextAndTranscribe(sampleFile, priorityList, 0, apiKey, callback, context);
  }

  private void checkNextAndTranscribe(File sampleFile, List<String> priorityList, int index, String apiKey, TranscriptionCallback callback, Context context) {
    if (index >= priorityList.size()) {
      getMainHandler().post(() -> callback.onError(context.getString(R.string.error_no_suitable_model)));
      return;
    }

    String modelId = priorityList.get(index);
    checkModelAvailability(modelId, apiKey, new TranscriptionCallback() {
      @Override public void onProgress(String message) {}
      @Override public void onSuccess(String result) {
        if (!isBusy()) {
          Logger.i(TAG, "Transcription was cancelled during model resolution");
          return;
        }
        getMainHandler().post(() -> callback.onProgress("MODEL_SELECTED:" + modelId));
        new Thread(() -> transcribeWithModel(sampleFile, modelId, apiKey, callback, context)).start();
      }
      @Override public void onError(String error) {
        if (isBusy()) {
          checkNextAndTranscribe(sampleFile, priorityList, index + 1, apiKey, callback, context);
        }
      }
    });
  }

  private String getMimeType(File file) {
    String name = file.getName().toLowerCase();
    if (name.endsWith(".wav")) return "audio/wav";
    if (name.endsWith(".mp3")) return "audio/mpeg";
    if (name.endsWith(".aac")) return "audio/aac";
    if (name.endsWith(".ogg")) return "audio/ogg";
    if (name.endsWith(".flac")) return "audio/flac";
    if (name.endsWith(".m4a")) return "audio/m4a";
    if (name.endsWith(".mp4")) return "audio/mp4";
    if (name.endsWith(".opus")) return "audio/opus";
    if (name.endsWith(".webm")) return "audio/webm";
    return "audio/wav"; // Default fallback
  }

  private void transcribeWithModel(File sampleFile, String modelId, String apiKey, TranscriptionCallback callback, Context context) {
    if (!isBusy()) {
      Logger.i(TAG, "transcribeWithModel cancelled before start");
      callback.onError(context.getString(R.string.transcription_cancelled));
      return;
    }

    try {
      byte[] fileBytes = Files.readAllBytes(sampleFile.toPath());
      String modelPath = modelId.contains("/") ? modelId : GEMINI_MODEL_PREFIX + modelId;
      GenerativeModel gm = new GenerativeModel(modelPath, apiKey);
      GenerativeModelFutures model = GenerativeModelFutures.from(gm);
      ChatFutures chat = model.startChat();

      getMainHandler().post(() -> callback.onProgress(
          context.getString(R.string.transcribing_with_gemini, sampleFile.getName())));

      executeTranscriptionPhase(chat, sampleFile, fileBytes, modelId, callback, context);
    } catch (OutOfMemoryError e) {
      Logger.e(TAG, "Out of memory reading audio file for Gemini", e);
      callback.onError("OUT_OF_MEMORY");
    } catch (Throwable t) {
      Logger.e(TAG, "Failed to read audio file or initialize Gemini stream", t);
      callback.onError(context.getString(R.string.error_failed_to_read_audio));
    }
  }

  private void executeTranscriptionPhase(ChatFutures chat, File sampleFile, byte[] fileBytes, String modelId, TranscriptionCallback callback, Context context) {
    Content content1 = new Content.Builder()
        .addText(readAssetTextFile(context, "gemini_prompt.txt"))
        .addBlob(getMimeType(sampleFile), fileBytes)
        .build();

    ListenableFuture<GenerateContentResponse> future1 = chat.sendMessage(content1);
    activeGeminiFutures.add(future1);

    Futures.addCallback(future1, new FutureCallback<GenerateContentResponse>() {
      @Override
      public void onSuccess(GenerateContentResponse response1) {
        activeGeminiFutures.remove(future1);
        String transcriptionText;
        try {
          transcriptionText = response1.getText() != null ? response1.getText() : "";
        } catch (Throwable t) {
          handleGeminiFailure(future1, modelId, t, "transcription", callback, context);
          return;
        }

        App app = (App) context.getApplicationContext();
        if (!app.getSettingsManager().isGeminiSummaryEnabled()) {
          deliverResult(transcriptionText, null, callback);
          return;
        }

        executeSummaryPhase(chat, transcriptionText, modelId, callback, context);
      }

      @Override
      public void onFailure(Throwable t) {
        handleGeminiFailure(future1, modelId, t, "transcription", callback, context);
      }
    }, r -> getMainHandler().post(r));
  }

  private void executeSummaryPhase(ChatFutures chat, String transcriptionText, String modelId, TranscriptionCallback callback, Context context) {
    getMainHandler().post(() -> callback.onProgress("Generating summary..."));

    String summaryPrompt = readAssetTextFile(context, "gemini_summary_prompt.txt");
    if (summaryPrompt.isEmpty()) {
      summaryPrompt = "Create a concise summary of the aforementioned transcription in the language of the transcription.";
    }

    Content content2 = new Content.Builder()
        .addText(summaryPrompt)
        .build();

    ListenableFuture<GenerateContentResponse> future2 = chat.sendMessage(content2);
    activeGeminiFutures.add(future2);

    Futures.addCallback(future2, new FutureCallback<GenerateContentResponse>() {
      @Override
      public void onSuccess(GenerateContentResponse response2) {
        activeGeminiFutures.remove(future2);
        String summaryText = null;
        try {
          summaryText = response2.getText() != null ? response2.getText() : "";
        } catch (Throwable t) {
          Logger.w(TAG, "Failed to extract summary text from response for model " + modelId + ": " + t.getMessage());
        }
        deliverResult(transcriptionText, summaryText, callback);
      }

      @Override
      public void onFailure(Throwable t) {
        activeGeminiFutures.remove(future2);
        if (t instanceof CancellationException) {
          Logger.i(TAG, "Gemini summary generation was cancelled");
          return;
        }
        Logger.w(TAG, "Gemini summary generation failed for model " + modelId + ", falling back to transcription only: " + t.getMessage());
        deliverResult(transcriptionText, null, callback);
      }
    }, r -> getMainHandler().post(r));
  }

  private void deliverResult(String transcription, String summary, TranscriptionCallback callback) {
    try {
      JSONObject jsonOutput = new JSONObject();
      jsonOutput.put("Transcription", transcription);
      if (summary != null && !summary.isEmpty()) {
        jsonOutput.put("Summary", summary);
      }

      String resultString = jsonOutput.toString(2);
      getMainHandler().post(() -> callback.onSuccess(resultString));
    } catch (Exception e) {
      Logger.e(TAG, "Error building JSON output for Gemini transcription", e);
      getMainHandler().post(() -> callback.onError("Error creating JSON result: " + e.getMessage()));
    }
  }

  private void handleGeminiFailure(ListenableFuture<?> future, String modelId, Throwable t, String phase, TranscriptionCallback callback, Context context) {
    activeGeminiFutures.remove(future);
    Logger.e(TAG, "Gemini " + phase + " failed for model " + modelId, t);
    getMainHandler().post(() -> {
      String message = t.getMessage();
      String handledError = getGeminiErrorMessage(message, modelId, context);
      if (handledError != null) {
        callback.onError(handledError);
      } else {
        callback.onError(context.getString(R.string.error_gemini_general, message));
      }
    });
  }

  public void fetchGeminiModels(String apiKey, Consumer<List<String>> onSuccess, Consumer<String> onError) {
    if (apiKey == null || apiKey.trim().isEmpty()) {
      onError.accept("API Key is missing");
      return;
    }

    final String cleanKey = apiKey.trim();

    new Thread(() -> {
      HttpURLConnection conn = null;
      try {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models");
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("x-goog-api-key", cleanKey);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.connect();

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
          String result;
          try (InputStream in = conn.getInputStream();
               Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            result = s.hasNext() ? s.next() : "";
          }
          
          JSONObject json = new JSONObject(result);
          JSONArray modelsArray = json.getJSONArray("models");
          List<String> modelIds = new ArrayList<>();
          for (int i = 0; i < modelsArray.length(); i++) {
            JSONObject m = modelsArray.getJSONObject(i);
            String name = m.getString("name"); // e.g. "models/gemini-1.5-flash"
            String displayName = m.optString("displayName", "");
            
            // Filter for models that support audio (generateContent and multimodal)
            JSONArray methods = m.optJSONArray("supportedGenerationMethods");
            boolean supportsContent = false;
            if (methods != null) {
              for (int j = 0; j < methods.length(); j++) {
                if ("generateContent".equals(methods.getString(j))) {
                  supportsContent = true;
                  break;
                }
              }
            }
            
            if (supportsContent) {
              // We keep the models/ prefix for consistency with the SDK if needed, 
              // but current logic often strips it. Let's keep the raw ID.
              modelIds.add(name.replace(GEMINI_MODEL_PREFIX, ""));
            }
          }
          
          cachedGeminiModels = Collections.unmodifiableList(new ArrayList<>(modelIds));
          getMainHandler().post(() -> onSuccess.accept(modelIds));
        } else {
          String errorMessage = "Server responded with " + responseCode;
          try (InputStream err = conn.getErrorStream()) {
            if (err != null) {
              try (Scanner s = new Scanner(err, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                String errBody = s.hasNext() ? s.next() : "";
                try {
                  JSONObject errJson = new JSONObject(errBody);
                  if (errJson.has("error")) {
                    JSONObject errObj = errJson.getJSONObject("error");
                    String apiMsg = errObj.optString("message", "");
                    if (!apiMsg.isEmpty()) {
                      errorMessage = apiMsg;
                    }
                  }
                } catch (Exception ignored) {
                  if (!errBody.trim().isEmpty()) {
                    errorMessage = errBody.trim();
                  }
                }
              }
            }
          }
          final String finalErr = errorMessage;
          getMainHandler().post(() -> onError.accept(finalErr));
        }
      } catch (Throwable t) {
        Logger.e(TAG, "Error fetching Gemini models", t);
        String errorMsg = (t.getMessage() != null && !t.getMessage().trim().isEmpty()) ? t.getMessage() : "Unknown error occurred";
        getMainHandler().post(() -> onError.accept(errorMsg));
      } finally {
        if (conn != null) {
          conn.disconnect();
        }
      }
    }).start();
  }

  private String getGeminiErrorMessage(String rawMessage, String modelName, Context context) {
    if (rawMessage == null) return null;
    try {
      // Check if the message contains a JSON error object
      int jsonStart = rawMessage.indexOf("{");
      if (jsonStart != -1) {
        String jsonStr = rawMessage.substring(jsonStart);
        // Basic check for the format provided by user: { "error" { "code" : 404 } }
        // Note: standard JSON is {"error": {"code": 404}}
        JSONObject json = new JSONObject(jsonStr);
        if (json.has("error")) {
          JSONObject errorObj = json.getJSONObject("error");
          int code = errorObj.optInt("code");
          if (code == 404) {
            return context.getString(R.string.error_gemini_model_not_found, modelName);
          }
          String message = errorObj.optString("message", "");
          if (!message.isEmpty()) {
            return context.getString(R.string.error_gemini_response_code, code, message);
          }
        }
      }
    } catch (Exception ignored) {
      // Not valid JSON or different format, fallback to simple string check
    }

    if (rawMessage.contains("404")) {
      return context.getString(R.string.error_gemini_model_retired, modelName);
    }
    return null;
  }

  private String readAssetTextFile(Context context, String fileName) {
    try (InputStream is = context.getAssets().open(fileName);
         Scanner s = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
      return s.hasNext() ? s.next() : "";
    } catch (IOException e) {
      Logger.e(TAG, "Error reading asset file: " + fileName, e);
      return "";
    }
  }

  private void logOnly(String msg) {
    Logger.i(TAG, msg);
  }

  public synchronized void release() {
    release(true);
  }

  public synchronized void release(boolean unloadNativeModel) {
    stopCurrentTranscription();
    if (unloadNativeModel && mainHandler != null) {
      mainHandler.removeCallbacksAndMessages(null);
    }
    activeTranscriptions = 0;
    cachedGeminiModels = Collections.emptyList();
    if (unloadNativeModel) {
      LocalWhisper.INSTANCE.release();
    }
  }

  public synchronized void stopCurrentTranscription() {
    if (currentSubscription != null) {
      Logger.i(TAG, "Stopping active Gemini transcription subscription");
      currentSubscription.cancel();
      currentSubscription = null;
      decrementActive();
    }
    if (!activeGeminiFutures.isEmpty()) {
      Logger.i(TAG, "Cancelling active Gemini futures (" + activeGeminiFutures.size() + ")");
      for (ListenableFuture<GenerateContentResponse> future : activeGeminiFutures) {
        if (future != null) {
          future.cancel(true);
        }
      }
      activeGeminiFutures.clear();
      decrementActive();
    }
  }
}
