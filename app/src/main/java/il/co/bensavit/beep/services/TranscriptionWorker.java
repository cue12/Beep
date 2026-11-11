package il.co.bensavit.beep.services;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import il.co.bensavit.beep.data.SettingsManager;
import il.co.bensavit.beep.utils.Logger;
import il.co.bensavit.beep.utils.StorageUtils;

public class TranscriptionWorker extends Worker {
    private static final String TAG = "TranscriptionWorker";

    public static final String KEY_FILE_PATH = "file_path";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_USE_GEMINI = "use_gemini";
    public static final String KEY_PROGRESS = "progress_msg";
    public static final String KEY_RESULT = "transcription_result";
    public static final String KEY_ERROR = "error_msg";

    public TranscriptionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        final String filePath = getInputData().getString(KEY_FILE_PATH);
        final String language = getInputData().getString(KEY_LANGUAGE);
        final boolean useGemini = getInputData().getBoolean(KEY_USE_GEMINI, false);

        if (filePath == null) {
            return Result.failure(new Data.Builder().putString(KEY_ERROR, "File path is null").build());
        }

        File file = new File(filePath);
        final CompletableFuture<String> future = new CompletableFuture<>();

        WhisperService.TranscriptionCallback callback = new WhisperService.TranscriptionCallback() {
            @Override
            public void onProgress(String message) {
                setProgressAsync(new Data.Builder().putString(KEY_PROGRESS, message).build());
            }

            @Override
            public void onSuccess(String result) {
                future.complete(result);
            }

            @Override
            public void onError(String error) {
                future.completeExceptionally(new Exception(error));
            }
        };

        if (useGemini) {
            WhisperService.INSTANCE.transcribeWithGemini(file, callback, getApplicationContext());
        } else {
            // Ensure Local Whisper is initialized if we are doing offline transcription
            if (!il.co.bensavit.beep.single.LocalWhisper.INSTANCE.isInitialized()) {
                try {
                    callback.onProgress(getApplicationContext().getString(il.co.bensavit.beep.R.string.please_wait_model_loading));
                    il.co.bensavit.beep.app.App app = (il.co.bensavit.beep.app.App) getApplicationContext();
                    SettingsManager settingsManager = app.getSettingsManager();
                    String modelName = settingsManager.getModelName();
                    il.co.bensavit.beep.single.LocalWhisper.INSTANCE.setModelPath(StorageUtils.MODELS_DIR + "/" + modelName);
                    il.co.bensavit.beep.single.LocalWhisper.INSTANCE.init(getApplicationContext());
                } catch (Exception e) {
                    Logger.e(TAG, "Initialization failed", e);
                    return Result.failure(new Data.Builder().putString(KEY_ERROR, "Initialization failed: " + e.getMessage()).build());
                }
            }
            WhisperService.INSTANCE.transcribeSample(file, language, callback, getApplicationContext());
        }

        try {
            String transcription = future.get(10, TimeUnit.MINUTES); // Timeout after 10 minutes to prevent hanging
            return Result.success(new Data.Builder().putString(KEY_RESULT, transcription).build());
        } catch (InterruptedException e) {
            Logger.i(TAG, "Worker interrupted, stopping transcription");
            WhisperService.INSTANCE.stopCurrentTranscription();
            return Result.retry();
        } catch (TimeoutException e) {
            Logger.e(TAG, "Worker timed out after 10 minutes");
            WhisperService.INSTANCE.stopCurrentTranscription();
            return Result.failure(new Data.Builder().putString(KEY_ERROR, "Transcription timed out").build());
        } catch (OutOfMemoryError e) {
            Logger.e(TAG, "Worker ran out of memory: " + e.getMessage(), e);
            WhisperService.INSTANCE.stopCurrentTranscription();
            return Result.failure(new Data.Builder().putString(KEY_ERROR, "OUT_OF_MEMORY").build());
        } catch (Throwable t) {
            Logger.e(TAG, "Worker failed: " + t.getMessage(), t);
            WhisperService.INSTANCE.stopCurrentTranscription();
            String errorMsg = t.getMessage();
            if (t.getCause() instanceof OutOfMemoryError || (errorMsg != null && (errorMsg.contains("OutOfMemory") || errorMsg.contains("alloc")))) {
                errorMsg = "OUT_OF_MEMORY";
            }
            return Result.failure(new Data.Builder().putString(KEY_ERROR, errorMsg != null ? errorMsg : "Unknown error").build());
        }
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Logger.i(TAG, "onStopped called, cancelling transcription");
        WhisperService.INSTANCE.stopCurrentTranscription();
    }
}
