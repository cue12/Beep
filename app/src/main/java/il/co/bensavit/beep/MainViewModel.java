package il.co.bensavit.beep;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.annotation.SuppressLint;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.os.HandlerCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelStore;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import il.co.bensavit.beep.app.App;
import il.co.bensavit.beep.data.ModelRepository;
import il.co.bensavit.beep.data.SettingsManager;
import il.co.bensavit.beep.services.WhisperService;
import il.co.bensavit.beep.single.LocalWhisper;
import il.co.bensavit.beep.utils.AssetUtils;
import il.co.bensavit.beep.utils.AudioConverter;
import il.co.bensavit.beep.utils.Logger;
import il.co.bensavit.beep.utils.StorageUtils;

/**
 * ViewModel responsible for application-level settings, model repository management,
 * Gemini configuration, audio file sharing/conversion, and managing task ViewModelStores.
 */
@Keep
public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    private final SettingsManager settingsManager;
    private final ModelRepository modelRepository;
    private final WhisperService whisperService = WhisperService.INSTANCE;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final ViewModelStore taskViewModelStore = new ViewModelStore();

    private static final Set<String> SUPPORTED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".wav", ".mp3", ".aac", ".ogg", ".flac", ".m4a", ".opus", ".webm", ".mp4"
    )));

    private final MutableLiveData<Boolean> _isProgressVisible = new MutableLiveData<>(false);
    public final LiveData<Boolean> isProgressVisible = _isProgressVisible;

    private final MutableLiveData<Boolean> _isGeminiSelected = new MutableLiveData<>(false);
    public final LiveData<Boolean> isGeminiSelected = _isGeminiSelected;

    private final MutableLiveData<String> _currentModelName = new MutableLiveData<>(null);
    public final LiveData<String> currentModelName = _currentModelName;

    private final MutableLiveData<List<String>> _geminiModels = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<String>> geminiModels = _geminiModels;

    private final MutableLiveData<Boolean> _isFetchingGeminiModels = new MutableLiveData<>(false);
    public final LiveData<Boolean> isFetchingGeminiModels = _isFetchingGeminiModels;

    private final MutableLiveData<Boolean> _showLogWindow = new MutableLiveData<>(false);
    public final LiveData<Boolean> showLogWindow = _showLogWindow;

    private final MutableLiveData<Integer> _downloadProgress = new MutableLiveData<>(-1);
    public final LiveData<Integer> downloadProgress = _downloadProgress;

    private final MutableLiveData<String> _downloadingModelName = new MutableLiveData<>(null);
    public final LiveData<String> downloadingModelName = _downloadingModelName;

    public static class UserMessage {
        public final String message;
        public final String actionLabel;
        public final Runnable actionRunnable;

        public UserMessage(String message) {
            this(message, null, null);
        }

        public UserMessage(String message, String actionLabel, Runnable actionRunnable) {
            this.message = message;
            this.actionLabel = actionLabel;
            this.actionRunnable = actionRunnable;
        }
    }

    private final MutableLiveData<UserMessage> _userMessage = new MutableLiveData<>(null);
    public final LiveData<UserMessage> userMessage = _userMessage;

    public void clearUserMessage() {
        _userMessage.setValue(null);
    }

    public void showUserMessage(String message) {
        _userMessage.postValue(new UserMessage(message));
    }

    public void showUserMessage(String message, String actionLabel, Runnable action) {
        _userMessage.postValue(new UserMessage(message, actionLabel, action));
    }

    public static class DriveConfirmationRequest {
        public final String modelName;
        public final String driveUrl;

        public DriveConfirmationRequest(String modelName, String driveUrl) {
            this.modelName = modelName;
            this.driveUrl = driveUrl;
        }
    }

    private final MutableLiveData<DriveConfirmationRequest> _driveConfirmationRequest = new MutableLiveData<>(null);
    public final LiveData<DriveConfirmationRequest> driveConfirmationRequest = _driveConfirmationRequest;

    private final MutableLiveData<String> _language = new MutableLiveData<>("auto");
    public final LiveData<String> language = _language;

    private final MutableLiveData<File> _onFileReadyForTranscription = new MutableLiveData<>();
    public final LiveData<File> onFileReadyForTranscription = _onFileReadyForTranscription;

    public void clearOnFileReadyForTranscription() {
        _onFileReadyForTranscription.setValue(null);
    }

    public static class TaskEntry {
        public enum Status { RUNNING, SUCCEEDED, FAILED, CANCELLED }

        public File file;
        public final String taskKey;
        public Status status;
        public String resultText;
        public String workId;
        public long elapsedTimeMs;
        public boolean isSummaryExpanded;
        public boolean isTranscriptionExpanded;

        public TaskEntry(File file, String taskKey) {
            this(file, taskKey, Status.RUNNING, null, null, 0, true, true);
        }

        public TaskEntry(File file, String taskKey, Status status, String resultText, String workId, long elapsedTimeMs) {
            this(file, taskKey, status, resultText, workId, elapsedTimeMs, true, true);
        }

        public TaskEntry(File file, String taskKey, Status status, String resultText, String workId, long elapsedTimeMs, boolean isSummaryExpanded, boolean isTranscriptionExpanded) {
            this.file = file;
            this.taskKey = taskKey;
            this.status = status != null ? status : Status.RUNNING;
            this.resultText = resultText;
            this.workId = workId;
            this.elapsedTimeMs = elapsedTimeMs;
            this.isSummaryExpanded = isSummaryExpanded;
            this.isTranscriptionExpanded = isTranscriptionExpanded;
        }
    }

    private static final String PREFS_NAME_TASKS = "beep_tasks";
    private static final String PREFS_NAME_TASKS_SECURE = "beep_tasks_secure";
    private static final String PREF_KEY_SAVED_TASKS = "saved_transcription_tasks";

    private final List<TaskEntry> activeTaskEntries = new ArrayList<>();
    private SharedPreferences taskPrefs;

    @SuppressWarnings("deprecation")
    private synchronized SharedPreferences getTaskSharedPreferences() {
        if (taskPrefs == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(getApplication())
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                taskPrefs = EncryptedSharedPreferences.create(
                        getApplication(),
                        PREFS_NAME_TASKS_SECURE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception e) {
                Logger.e(TAG, "Failed to initialize EncryptedSharedPreferences for tasks, falling back to standard prefs", e);
                taskPrefs = getApplication().getSharedPreferences(PREFS_NAME_TASKS, Context.MODE_PRIVATE);
            }
        }
        return taskPrefs;
    }

    private void loadTaskEntriesFromDisk() {
        SharedPreferences secure = getTaskSharedPreferences();
        String jsonStr = secure.getString(PREF_KEY_SAVED_TASKS, null);

        // Migration from legacy unencrypted SharedPreferences if exists
        if (jsonStr == null || jsonStr.isEmpty()) {
            SharedPreferences legacyPrefs = getApplication().getSharedPreferences(PREFS_NAME_TASKS, Context.MODE_PRIVATE);
            if (legacyPrefs.contains(PREF_KEY_SAVED_TASKS)) {
                jsonStr = legacyPrefs.getString(PREF_KEY_SAVED_TASKS, null);
                if (jsonStr != null && !jsonStr.isEmpty()) {
                    secure.edit().putString(PREF_KEY_SAVED_TASKS, jsonStr).apply();
                }
                legacyPrefs.edit().remove(PREF_KEY_SAVED_TASKS).apply();
            }
        }

        if (jsonStr == null || jsonStr.isEmpty()) return;

        try {
            JSONArray array = new JSONArray(jsonStr);
            synchronized (this) {
                activeTaskEntries.clear();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String filePath = obj.getString("filePath");
                    String taskKey = obj.getString("taskKey");
                    TaskEntry.Status status = TaskEntry.Status.RUNNING;
                    try {
                        status = TaskEntry.Status.valueOf(obj.optString("status", "RUNNING"));
                    } catch (IllegalArgumentException e) {
                        Logger.w(TAG, "Unknown task status in JSON: " + obj.optString("status"));
                    }
                    String resultText = obj.has("resultText") && !obj.isNull("resultText") ? obj.getString("resultText") : null;
                    String workId = obj.has("workId") && !obj.isNull("workId") ? obj.getString("workId") : null;
                    long elapsedTimeMs = obj.optLong("elapsedTimeMs", 0);
                    boolean isSummaryExpanded = obj.optBoolean("isSummaryExpanded", true);
                    boolean isTranscriptionExpanded = obj.optBoolean("isTranscriptionExpanded", true);

                    File file = new File(filePath);
                    if (file.exists()) {
                        activeTaskEntries.add(new TaskEntry(file, taskKey, status, resultText, workId, elapsedTimeMs, isSummaryExpanded, isTranscriptionExpanded));
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to load task entries from disk", e);
        }
    }

    public void saveTaskEntriesToDisk() {
        executorService.execute(() -> {
            try {
                JSONArray array = new JSONArray();
                synchronized (this) {
                    for (TaskEntry entry : activeTaskEntries) {
                        JSONObject obj = new JSONObject();
                        obj.put("filePath", entry.file.getAbsolutePath());
                        obj.put("taskKey", entry.taskKey);
                        obj.put("status", entry.status.name());
                        if (entry.resultText != null) {
                            obj.put("resultText", entry.resultText);
                        }
                        if (entry.workId != null) {
                            obj.put("workId", entry.workId);
                        }
                        if (entry.elapsedTimeMs > 0) {
                            obj.put("elapsedTimeMs", entry.elapsedTimeMs);
                        }
                        obj.put("isSummaryExpanded", entry.isSummaryExpanded);
                        obj.put("isTranscriptionExpanded", entry.isTranscriptionExpanded);
                        array.put(obj);
                    }
                }
                getTaskSharedPreferences().edit().putString(PREF_KEY_SAVED_TASKS, array.toString()).apply();
                // Remove from legacy unencrypted prefs if present
                SharedPreferences legacyPrefs = getApplication().getSharedPreferences(PREFS_NAME_TASKS, Context.MODE_PRIVATE);
                if (legacyPrefs.contains(PREF_KEY_SAVED_TASKS)) {
                    legacyPrefs.edit().remove(PREF_KEY_SAVED_TASKS).apply();
                }
            } catch (Exception e) {
                Logger.e(TAG, "Failed to save task entries to disk", e);
            }
        });
    }

    public synchronized List<TaskEntry> getActiveTaskEntries() {
        return new ArrayList<>(activeTaskEntries);
    }

    public synchronized TaskEntry getTaskEntry(String taskKey) {
        for (TaskEntry entry : activeTaskEntries) {
            if (entry.taskKey.equals(taskKey)) {
                return entry;
            }
        }
        return null;
    }

    public synchronized void addTaskEntry(File file, String taskKey) {
        activeTaskEntries.add(new TaskEntry(file, taskKey));
        saveTaskEntriesToDisk();
    }

    public synchronized void removeTaskEntry(String taskKey) {
        for (int i = activeTaskEntries.size() - 1; i >= 0; i--) {
            if (activeTaskEntries.get(i).taskKey.equals(taskKey)) {
                activeTaskEntries.remove(i);
                break;
            }
        }
        saveTaskEntriesToDisk();
    }

    public synchronized void updateTaskEntryFile(String taskKey, File newFile) {
        for (int i = 0; i < activeTaskEntries.size(); i++) {
            TaskEntry old = activeTaskEntries.get(i);
            if (old.taskKey.equals(taskKey)) {
                old.file = newFile;
                break;
            }
        }
        saveTaskEntriesToDisk();
    }

    public synchronized void updateTaskWorkId(String taskKey, String workId) {
        for (int i = 0; i < activeTaskEntries.size(); i++) {
            TaskEntry entry = activeTaskEntries.get(i);
            if (entry.taskKey.equals(taskKey)) {
                entry.workId = workId;
                break;
            }
        }
        saveTaskEntriesToDisk();
    }

    public synchronized void updateTaskResult(String taskKey, TaskEntry.Status status, String resultText, long elapsedTimeMs) {
        for (int i = 0; i < activeTaskEntries.size(); i++) {
            TaskEntry entry = activeTaskEntries.get(i);
            if (entry.taskKey.equals(taskKey)) {
                entry.status = status;
                entry.resultText = resultText;
                if (elapsedTimeMs > 0) {
                    entry.elapsedTimeMs = elapsedTimeMs;
                }
                break;
            }
        }
        saveTaskEntriesToDisk();
    }

    public synchronized void updateTaskExpandState(String taskKey, boolean isSummaryExpanded, boolean isTranscriptionExpanded) {
        for (int i = 0; i < activeTaskEntries.size(); i++) {
            TaskEntry entry = activeTaskEntries.get(i);
            if (entry.taskKey.equals(taskKey)) {
                entry.isSummaryExpanded = isSummaryExpanded;
                entry.isTranscriptionExpanded = isTranscriptionExpanded;
                break;
            }
        }
        saveTaskEntriesToDisk();
    }

    public synchronized void clearTaskEntries() {
        activeTaskEntries.clear();
        saveTaskEntriesToDisk();
    }

    private final File mSharedDir;
    private static final int MAX_HISTORY_FILES = 50;

    public MainViewModel(@NonNull Application application) {
        super(application);
        App app = (App) application;
        this.settingsManager = app.getSettingsManager();
        this.modelRepository = app.getModelRepository();
        this.executorService = app.getExecutorService();
        this.mainHandler = HandlerCompat.createAsync(Looper.getMainLooper());

        mSharedDir = StorageUtils.getSharedDir(application);

        loadTaskEntriesFromDisk();

        // Copy sample file if not exists so it will be shown in history
        File sampleFile = new File(mSharedDir, "jfk.wav");
        if (!sampleFile.exists()) {
            AssetUtils.copyFileFromAssets(application, "samples/jfk.wav", sampleFile);
        }

        cleanupHistory();
        init();
    }

    private void cleanupHistory() {
        executorService.execute(() -> {
            File[] files = mSharedDir.listFiles(new AudioFileFilter());
            if (files != null && files.length > MAX_HISTORY_FILES) {
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                for (int index = MAX_HISTORY_FILES; index < files.length; index++) {
                    if (!files[index].getName().equals("jfk.wav")) { // Keep sample file
                        if (files[index].delete()) {
                            Logger.d(TAG, "Deleted old history file: " + files[index].getName());
                        }
                    }
                }
            }
        });
    }

    private void init() {
        boolean geminiSelected = settingsManager.isGeminiSelected();
        _isGeminiSelected.setValue(geminiSelected);

        _language.setValue(settingsManager.getLanguage());

        String storedModelName = settingsManager.getModelName();
        _currentModelName.setValue(storedModelName);

        String rawGeminiModel = settingsManager.getGeminiModelName();
        whisperService.setSelectedGeminiModel(rawGeminiModel);

        String apiKey = settingsManager.getGeminiApiKey();
        whisperService.setGeminiApiKey(apiKey.isEmpty() ? null : apiKey);
        if (!apiKey.isEmpty()) {
            fetchGeminiModels();
        }

        if (!geminiSelected) {
            LocalWhisper.INSTANCE.setModelPath(StorageUtils.MODELS_DIR + "/" + storedModelName);
            if (il.co.bensavit.beep.data.ModelRepository.verifyModel(getApplication(), storedModelName)) {
                try {
                    LocalWhisper.INSTANCE.init(getApplication());
                } catch (Exception e) {
                    il.co.bensavit.beep.utils.Logger.e(TAG, "Error pre-initializing model", e);
                }
            }
        }
    }

    public void setGeminiSelected(boolean selected) {
        _isGeminiSelected.setValue(selected);
        settingsManager.setGeminiSelected(selected);
    }

    public void setCurrentModelName(String modelName) {
        setCurrentModelName(modelName, true);
    }

    public void setCurrentModelName(String modelName, boolean save) {
        _currentModelName.setValue(modelName);
        if (save) {
            settingsManager.setModelName(modelName);
        }
    }

    public String getGeminiApiKey() {
        return settingsManager.getGeminiApiKey();
    }

    public void setGeminiApiKey(String key) {
        settingsManager.setGeminiApiKey(key);
        whisperService.setGeminiApiKey(key.isEmpty() ? null : key);
        if (!key.isEmpty()) {
            fetchGeminiModels();
        }
        _currentModelName.setValue(_currentModelName.getValue());
    }

    public void fetchGeminiModels() {
        String apiKey = getGeminiApiKey();
        if (apiKey.isEmpty()) return;

        _isFetchingGeminiModels.setValue(true);
        whisperService.fetchGeminiModels(apiKey, models -> {
            _geminiModels.setValue(models);
            _isFetchingGeminiModels.setValue(false);
        }, error -> {
            Logger.e(TAG, "Failed to fetch Gemini models: " + error);
            _isFetchingGeminiModels.setValue(false);
        });
    }

    public void setGeminiModel(String model) {
        whisperService.setSelectedGeminiModel(model);
        settingsManager.setGeminiModelName(model);
    }

    public void checkModelAvailability(String modelId, String apiKey, WhisperService.TranscriptionCallback callback) {
        whisperService.checkModelAvailability(modelId, apiKey, callback);
    }

    public void loadLocalModel(String modelName, Runnable onComplete) {
        if (Boolean.TRUE.equals(TranscriptionTaskViewModel.isAnyTranscriptionRunning())) {
            showUserMessage("Cannot change model while transcription is running");
            if (onComplete != null) onComplete.run();
            return;
        }
        _isProgressVisible.setValue(true);
        modelRepository.loadLocalModel(modelName, new ModelRepository.ModelLoadCallback() {
            @Override
            public void onProgress(int progress) {
                _downloadProgress.postValue(progress);
            }

            @Override
            public void onDownloadStarted(String modelName) {
                _downloadProgress.postValue(0);
                _downloadingModelName.postValue(modelName);
            }

            @Override
            public void onHtmlConfirmationRequired(String modelName, String driveUrl) {
                _downloadingModelName.postValue(null);
                _driveConfirmationRequest.postValue(new DriveConfirmationRequest(modelName, driveUrl));
            }

            @Override
            public void onDownloadFinished() {
                _downloadingModelName.postValue(null);
                _downloadProgress.postValue(-1);
            }

            @Override
            public void onError(String modelName, boolean isDownloadError, boolean alreadyExists) {
                if (alreadyExists) {
                    Logger.e(TAG, getApplication().getString(R.string.model_already_exists, modelName));
                } else if (isDownloadError) {
                    Logger.e(TAG, getApplication().getString(R.string.error_download_failed));
                } else {
                    Logger.e(TAG, getApplication().getString(R.string.error_loading_model, modelName));
                }
                showUserMessage(getApplication().getString(R.string.error_loading_model, modelName));
            }

            @Override
            public void onSuccess(String modelName) {
                _currentModelName.postValue(modelName);
                if (il.co.bensavit.beep.utils.ModelUtils.isTinyModel(modelName)) {
                    showUserMessage(getApplication().getString(R.string.warning_tiny_model_testing_only));
                } else {
                    showUserMessage(getApplication().getString(R.string.model_loaded_ready, modelName));
                }
            }

            @Override
            public void onComplete() {
                mainHandler.post(() -> {
                    _isProgressVisible.setValue(false);
                    if (onComplete != null) onComplete.run();
                });
            }
        });
    }

    public void cancelDownload() {
        _driveConfirmationRequest.postValue(null);
        modelRepository.cancelDownload();
    }

    public void onDriveUrlResolved(String modelName, String directUrl, String cookies) {
        _driveConfirmationRequest.postValue(null);
        _downloadingModelName.postValue(modelName);
        modelRepository.onDriveUrlResolved(modelName, directUrl, cookies);
    }

    public void deleteDownloadedModel(String modelName) {
        modelRepository.deleteDownloadedModel(modelName);
    }

    public void handleSharedUri(Uri uri, String originalFileName) {
        handleSharedUri(uri, originalFileName, true);
    }

    public void handleSharedUri(Uri uri, String originalFileName, boolean autoTranscribe) {
        executorService.execute(new ProcessSharedUriTask(uri, originalFileName, autoTranscribe));
    }

    private void processSharedUri(Uri uri, String originalFileName, boolean autoTranscribe) {
        File copiedFile = copyUriToSharedDir(uri, originalFileName);
        if (copiedFile == null) {
            Logger.e(TAG, "handleSharedUri: copy failed for uri=" + uri);
            showUserMessage(getApplication().getString(R.string.error_stream_read_failed),
                    getApplication().getString(R.string.action_view_logs_short),
                    () -> _showLogWindow.postValue(true));
            return;
        }

        try {
            File finalFile = prepareAudioFile(copiedFile);
            if (autoTranscribe) {
                _onFileReadyForTranscription.postValue(finalFile);
            } else {
                mainHandler.post(new ShowSavedToastTask());
            }
        } catch (Exception e) {
            Logger.e(TAG, "handleSharedUri: processing failed", e);
            String errorMsg;
            if (isMediaCodecError(e)) {
                errorMsg = getApplication().getString(R.string.error_mediacodec_decoder_failed);
            } else {
                errorMsg = getApplication().getString(R.string.error_conversion_failed);
            }
            showUserMessage(errorMsg,
                    getApplication().getString(R.string.action_view_logs_short),
                    () -> _showLogWindow.postValue(true));
            cleanupFile(copiedFile);
        }
    }

    private boolean isMediaCodecError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MediaCodec.CodecException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("MediaCodec")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private File copyUriToSharedDir(Uri uri, String originalFileName) {
        String baseName = (originalFileName != null) ? originalFileName : "shared_audio";
        String fileName = System.currentTimeMillis() + "_" + baseName;
        File rawFile = new File(getSharedFilesDir(), fileName);
        return AssetUtils.copyUriToFile(getApplication(), uri, rawFile);
    }

    private File prepareAudioFile(File copiedFile) throws IOException {
        if (isWavFile(copiedFile)) {
            return copiedFile;
        }

        return convertToWavAndCleanUp(copiedFile);
    }

    private boolean isWavFile(File file) {
        return file.getName().toLowerCase().endsWith(".wav");
    }

    private File convertToWavAndCleanUp(File sourceFile) throws IOException {
        File wavFile = createWavTargetFile(sourceFile);
        File convertedFile = AudioConverter.convertToWav(sourceFile, wavFile);
        if (!Objects.equals(convertedFile, sourceFile)) {
            cleanupFile(sourceFile);
        }
        return convertedFile;
    }

    private File createWavTargetFile(File sourceFile) {
        String baseName = sourceFile.getName();
        int dotIndex = baseName.lastIndexOf('.');
        String wavName = (dotIndex != -1 ? baseName.substring(0, dotIndex) : baseName) + ".wav";
        return new File(getSharedFilesDir(), wavName);
    }

    private void cleanupFile(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                Logger.w(TAG, "Failed to delete temporary file: " + file);
            }
        }
    }

    private class ProcessSharedUriTask implements Runnable {
        private final Uri uri;
        private final String originalFileName;
        private final boolean autoTranscribe;

        ProcessSharedUriTask(Uri uri, String originalFileName, boolean autoTranscribe) {
            this.uri = uri;
            this.originalFileName = originalFileName;
            this.autoTranscribe = autoTranscribe;
        }

        @Override
        public void run() {
            processSharedUri(uri, originalFileName, autoTranscribe);
        }
    }

    private class ShowSavedToastTask implements Runnable {
        @Override
        public void run() {
            showUserMessage(getApplication().getString(R.string.shared_file_saved_load_offline_model));
        }
    }

    public File getSharedFilesDir() {
        return mSharedDir;
    }

    public boolean isTutorialShown() {
        return settingsManager.isTutorialShown();
    }

    public void setTutorialShown(boolean shown) {
        settingsManager.setTutorialShown(shown);
    }

    public void onLogWindowShown() {
        _showLogWindow.setValue(false);
    }

    public ViewModelStore getTaskViewModelStore() {
        return taskViewModelStore;
    }

    public void clearAllResources() {
        clearAllResources(true);
    }

    public void clearAllResources(boolean unloadNativeModel) {
        if (TranscriptionTaskViewModel.isAnyTranscriptionRunning()) {
            showUserMessage(getApplication().getString(R.string.cannot_clear_running_tasks));
            return;
        }
        clearTaskEntries();
        taskViewModelStore.clear();
        whisperService.release(unloadNativeModel);
        modelRepository.cancelDownload();
    }

    public void setLanguage(String languageCode) {
        settingsManager.setLanguage(languageCode);
        _language.postValue(languageCode);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        taskViewModelStore.clear();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }

    private static class AudioFileFilter implements java.io.FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            String lower = name.toLowerCase();
            for (String extension : SUPPORTED_EXTENSIONS) {
                if (lower.endsWith(extension)) {
                    return true;
                }
            }
            return false;
        }
    }
}
