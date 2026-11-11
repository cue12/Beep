package il.co.bensavit.beep.app;

import android.app.Application;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.color.DynamicColors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import il.co.bensavit.beep.data.ModelRepository;
import il.co.bensavit.beep.data.SettingsManager;
import il.co.bensavit.beep.services.WhisperService;
import il.co.bensavit.beep.utils.StorageUtils;
import il.co.bensavit.beep.workers.CleanupWorker;

public class App extends Application {

  private SettingsManager settingsManager;
  private ModelRepository modelRepository;
  private ExecutorService executorService;

  @Override
  public void onCreate() {
    super.onCreate();
    DynamicColors.applyToActivitiesIfAvailable(this);
    StorageUtils.ensureDirectoriesExist(this);

    settingsManager = new SettingsManager(this);
    modelRepository = new ModelRepository(this);
    executorService = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

    scheduleCleanup();
  }

  @Override
  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (level == TRIM_MEMORY_RUNNING_CRITICAL || level == TRIM_MEMORY_COMPLETE) {
      if (il.co.bensavit.beep.TranscriptionTaskViewModel.isAnyTranscriptionRunning()) {
        il.co.bensavit.beep.utils.Logger.w("App", "Critical system memory level: " + level + " while transcription tasks are active!");
      }
    }
    // Release native resources if memory is low, we are in background, and NOT busy
    if (level >= TRIM_MEMORY_MODERATE && !WhisperService.INSTANCE.isBusy()) {
      WhisperService.INSTANCE.release();
    }
  }

  public SettingsManager getSettingsManager() {
    return settingsManager;
  }

  public ModelRepository getModelRepository() {
    return modelRepository;
  }

  public ExecutorService getExecutorService() {
    return executorService;
  }

  private void scheduleCleanup() {
    PeriodicWorkRequest cleanupRequest = new PeriodicWorkRequest.Builder(
            CleanupWorker.class, 1, TimeUnit.DAYS)
            .build();

    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CleanupOldSharedFiles",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
    );
  }
}
