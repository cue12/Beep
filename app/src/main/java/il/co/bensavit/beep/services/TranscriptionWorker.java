package il.co.bensavit.beep.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import il.co.bensavit.beep.R;
import il.co.bensavit.beep.app.App;
import il.co.bensavit.beep.data.SettingsManager;
import il.co.bensavit.beep.single.LocalWhisper;
import il.co.bensavit.beep.utils.Logger;
import il.co.bensavit.beep.utils.ModelUtils;
import il.co.bensavit.beep.utils.StorageUtils;
import il.co.bensavit.beep.utils.VibrationUtils;

public class TranscriptionWorker extends ListenableWorker {
    private static final String TAG = "TranscriptionWorker";
    private final int notificationId = getId().hashCode();
    private static final String CHANNEL_ID = "transcription_channel";

    public static final String KEY_FILE_PATH = "file_path";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_USE_GEMINI = "use_gemini";
    public static final String KEY_PROGRESS = "progress_msg";
    public static final String KEY_RESULT = "transcription_result";
    public static final String KEY_ERROR = "error_msg";
    public static final String KEY_MODEL_NAME = "model_name";

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> timeoutTask;
    private SettableFuture<Result> future;

    public TranscriptionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
        SettableFuture<ForegroundInfo> foregroundFuture = SettableFuture.create();
        foregroundFuture.set(createForegroundInfo(getApplicationContext().getString(R.string.transcription_starting)));
        return foregroundFuture;
    }

    private void ensureNotificationChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null && notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.app_name) + " Transcription",
                        NotificationManager.IMPORTANCE_LOW
                );
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private ForegroundInfo createForegroundInfo(@NonNull String progressMsg) {
        Context context = getApplicationContext();
        ensureNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.app_name) + " Transcription")
                .setContentText(progressMsg)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        }
        return new ForegroundInfo(notificationId, notification);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        future = SettableFuture.create();

        ((App) getApplicationContext()).getExecutorService().execute(() -> {
            try {
                try {
                    ListenableFuture<Void> foregroundFuture = setForegroundAsync(createForegroundInfo(getApplicationContext().getString(R.string.transcription_starting)));
                    foregroundFuture.addListener(() -> {
                        try {
                            foregroundFuture.get();
                        } catch (Exception e) {
                            Logger.w(TAG, "Failed to set foreground status: " + e.getMessage());
                        }
                    }, Runnable::run);
                } catch (Exception e) {
                    Logger.w(TAG, "Failed to set foreground status: " + e.getMessage());
                }

                final String filePath = getInputData().getString(KEY_FILE_PATH);
                final String language = getInputData().getString(KEY_LANGUAGE);
                final boolean useGemini = getInputData().getBoolean(KEY_USE_GEMINI, false);

                if (filePath == null) {
                    future.set(Result.failure(new Data.Builder().putString(KEY_ERROR, "File path is null").build()));
                    return;
                }

                File file = new File(filePath);
                final AtomicReference<String> actualModelSelected = new AtomicReference<>();

                synchronized (this) {
                    scheduler = Executors.newSingleThreadScheduledExecutor();
                    timeoutTask = scheduler.schedule(() -> {
                        Logger.e(TAG, "Worker timed out after 10 minutes");
                        WhisperService.INSTANCE.stopCurrentTranscription();
                        future.set(Result.failure(new Data.Builder().putString(KEY_ERROR, "Transcription timed out").build()));
                        shutdownScheduler();
                    }, 10, TimeUnit.MINUTES);
                }

                future.addListener(() -> {
                    if (future.isCancelled()) {
                        Logger.i(TAG, "Worker future cancelled, stopping transcription");
                        stopSchedulerAndTranscription();
                    }
                }, Runnable::run);

                TranscriptionCallback callback = new TranscriptionCallback() {
                    @Override
                    public void onProgress(String message) {
                        if (message != null && message.startsWith("MODEL_SELECTED:")) {
                            actualModelSelected.set(message.substring("MODEL_SELECTED:".length()));
                        }
                        Data.Builder progressBuilder = new Data.Builder().putString(KEY_PROGRESS, message);
                        String selectedModel = actualModelSelected.get();
                        if (selectedModel != null && !selectedModel.isEmpty()) {
                            progressBuilder.putString(KEY_MODEL_NAME, selectedModel);
                        }
                        try {
                            setProgressAsync(progressBuilder.build());
                            Context context = getApplicationContext();
                            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                            if (notificationManager != null) {
                                String notificationText = message;
                                if (message != null && message.startsWith("MODEL_SELECTED:")) {
                                    String modelId = message.substring("MODEL_SELECTED:".length());
                                    notificationText = context.getString(R.string.model_subtitle, ModelUtils.formatModelDisplayName(context, modelId));
                                }
                                Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                                        .setContentTitle(context.getString(R.string.app_name) + " Transcription")
                                        .setContentText(notificationText)
                                        .setSmallIcon(R.mipmap.ic_launcher)
                                        .setOngoing(true)
                                        .setPriority(NotificationCompat.PRIORITY_LOW)
                                        .build();
                                notificationManager.notify(notificationId, notification);
                            }
                        } catch (Exception e) {
                            // Ignore notification update error if worker is finishing or stopped
                        }
                    }

                    @Override
                    public void onSuccess(String result) {
                        stopScheduler();
                        Data.Builder outputBuilder = new Data.Builder().putString(KEY_RESULT, result);
                        String selectedModel = actualModelSelected.get();
                        if (selectedModel != null && !selectedModel.isEmpty()) {
                            outputBuilder.putString(KEY_MODEL_NAME, selectedModel);
                        }
                        VibrationUtils.vibrateCompletion(getApplicationContext());
                        future.set(Result.success(outputBuilder.build()));
                    }

                    @Override
                    public void onError(String error) {
                        stopSchedulerAndTranscription();
                        String errorMsg = error;
                        if (errorMsg != null && (errorMsg.contains("OutOfMemory") || errorMsg.contains("alloc"))) {
                            errorMsg = "OUT_OF_MEMORY";
                        }
                        future.set(Result.failure(new Data.Builder().putString(KEY_ERROR, errorMsg != null ? errorMsg : "Unknown error").build()));
                    }
                };

                if (useGemini) {
                    App app = (App) getApplicationContext();
                    SettingsManager settingsManager = app.getSettingsManager();
                    String apiKey = settingsManager.getGeminiApiKey();
                    String modelName = settingsManager.getGeminiModelName();
                    WhisperService.INSTANCE.transcribeWithGemini(file, apiKey, modelName, callback, getApplicationContext());
                } else {
                    if (!LocalWhisper.INSTANCE.isInitialized()) {
                        try {
                            callback.onProgress(getApplicationContext().getString(R.string.please_wait_model_loading));
                            App app = (App) getApplicationContext();
                            SettingsManager settingsManager = app.getSettingsManager();
                            String modelName = settingsManager.getModelName();
                            LocalWhisper.INSTANCE.setModelPath(StorageUtils.MODELS_DIR + "/" + modelName);
                            LocalWhisper.INSTANCE.init(getApplicationContext());
                        } catch (Throwable e) {
                            Logger.e(TAG, "Initialization failed", e);
                            stopSchedulerAndTranscription();
                            future.set(Result.failure(new Data.Builder().putString(KEY_ERROR, "Initialization failed: " + e.getMessage()).build()));
                            return;
                        }
                    }
                    WhisperService.INSTANCE.transcribeSample(file, language, callback, getApplicationContext());
                }
            } catch (Throwable t) {
                Logger.e(TAG, "Unexpected error in startWork background thread", t);
                stopSchedulerAndTranscription();
                future.set(Result.failure(new Data.Builder().putString(KEY_ERROR, "Error: " + t.getMessage()).build()));
            }
        });

        return future;
    }

    private synchronized void stopScheduler() {
        if (timeoutTask != null) {
            timeoutTask.cancel(true);
            timeoutTask = null;
        }
        shutdownScheduler();
    }

    private synchronized void stopSchedulerAndTranscription() {
        stopScheduler();
        WhisperService.INSTANCE.stopCurrentTranscription();
    }

    private synchronized void shutdownScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Logger.i(TAG, "onStopped called, cancelling transcription");
        stopSchedulerAndTranscription();
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
}
