package il.co.bensavit.beep;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.os.HandlerCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.File;
import java.util.UUID;

import il.co.bensavit.beep.services.TranscriptionWorker;
import il.co.bensavit.beep.services.WhisperService;
import il.co.bensavit.beep.utils.Logger;

/**
 * ViewModel dedicated to managing the lifecycle, progress, and execution of a single
 * audio transcription task using WorkManager.
 */
@Keep
public class TranscriptionTaskViewModel extends AndroidViewModel {

    private static final String TAG = "TranscriptionTaskVM";

    private final WhisperService whisperService = WhisperService.INSTANCE;
    private final Handler mainHandler;

    private static final MutableLiveData<Boolean> _isTranscriptionRunning = new MutableLiveData<>(false);
    public static final LiveData<Boolean> isTranscriptionRunning = _isTranscriptionRunning;

    private static final MutableLiveData<Integer> _activeTaskCount = new MutableLiveData<>(0);
    public static final LiveData<Integer> activeTaskCount = _activeTaskCount;

    private static int sActiveTranscriptionCount = 0;

    private static synchronized void updateTranscriptionRunning(boolean increment) {
        sActiveTranscriptionCount = Math.max(0, sActiveTranscriptionCount + (increment ? 1 : -1));
        _isTranscriptionRunning.postValue(sActiveTranscriptionCount > 0);
        _activeTaskCount.postValue(sActiveTranscriptionCount);
    }

    public static boolean isAnyTranscriptionRunning() {
        return sActiveTranscriptionCount > 0;
    }

    public static int getActiveTaskCount() {
        return sActiveTranscriptionCount;
    }

    private final MutableLiveData<String> _transcriptionText = new MutableLiveData<>();
    public final LiveData<String> transcriptionText = _transcriptionText;

    private final MutableLiveData<Boolean> _isProgressVisible = new MutableLiveData<>(false);
    public final LiveData<Boolean> isProgressVisible = _isProgressVisible;

    private UUID mCurrentWorkId;
    private Observer<WorkInfo> mWorkInfoObserver;
    private LiveData<WorkInfo> mWorkInfoLiveData;
    private boolean mIsTaskRunning = false;

    private String mTaskKey;
    private MainViewModel mMainViewModel;
    private long mStartTime = 0;

    public TranscriptionTaskViewModel(@NonNull Application application) {
        super(application);
        this.mainHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public void initTask(String taskKey, MainViewModel mainViewModel) {
        this.mTaskKey = taskKey;
        this.mMainViewModel = mainViewModel;
    }

    public void setRestoredResult(String text, boolean isProgressVisible) {
        _transcriptionText.setValue(text);
        _isProgressVisible.setValue(isProgressVisible);
    }

    public void runTranscription(File file, String language, boolean useGemini) {
        stopTranscription(); // Cancel any existing transcription before starting a new one

        mStartTime = System.currentTimeMillis();

        Data inputData = new Data.Builder()
                .putString(TranscriptionWorker.KEY_FILE_PATH, file.getAbsolutePath())
                .putString(TranscriptionWorker.KEY_LANGUAGE, language != null ? language : "auto")
                .putBoolean(TranscriptionWorker.KEY_USE_GEMINI, useGemini)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(TranscriptionWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager workManager = WorkManager.getInstance(getApplication());
        workManager.enqueue(workRequest);
        mCurrentWorkId = workRequest.getId();

        if (mMainViewModel != null && mTaskKey != null) {
            mMainViewModel.updateTaskWorkId(mTaskKey, mCurrentWorkId.toString());
        }

        mIsTaskRunning = true;
        updateTranscriptionRunning(true);

        _transcriptionText.postValue(getApplication().getString(R.string.transcription_starting));
        _isProgressVisible.postValue(true);

        ensureWorkInfoObserver();

        mWorkInfoLiveData = workManager.getWorkInfoByIdLiveData(mCurrentWorkId);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            mWorkInfoLiveData.observeForever(mWorkInfoObserver);
        } else {
            final UUID workId = mCurrentWorkId;
            mainHandler.post(() -> {
                if (mCurrentWorkId != null && mCurrentWorkId.equals(workId)) {
                    mWorkInfoLiveData = workManager.getWorkInfoByIdLiveData(workId);
                    mWorkInfoLiveData.observeForever(mWorkInfoObserver);
                }
            });
        }
    }

    public void observeExistingWork(String workIdStr) {
        if (workIdStr == null || workIdStr.isEmpty() || mCurrentWorkId != null) return;
        try {
            final UUID workId = UUID.fromString(workIdStr);
            mCurrentWorkId = workId;
            if (mStartTime == 0) {
                mStartTime = System.currentTimeMillis();
            }
            WorkManager workManager = WorkManager.getInstance(getApplication());

            if (!mIsTaskRunning) {
                mIsTaskRunning = true;
                updateTranscriptionRunning(true);
            }
            _isProgressVisible.postValue(true);

            ensureWorkInfoObserver();

            mWorkInfoLiveData = workManager.getWorkInfoByIdLiveData(workId);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                mWorkInfoLiveData.observeForever(mWorkInfoObserver);
            } else {
                mainHandler.post(() -> {
                    if (mCurrentWorkId != null && mCurrentWorkId.equals(workId)) {
                        mWorkInfoLiveData = workManager.getWorkInfoByIdLiveData(workId);
                        mWorkInfoLiveData.observeForever(mWorkInfoObserver);
                    }
                });
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to parse work ID: " + workIdStr, e);
        }
    }

    private void ensureWorkInfoObserver() {
        if (mWorkInfoObserver != null) return;
        mWorkInfoObserver = new Observer<WorkInfo>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                if (workInfo != null) {
                    Data progress = workInfo.getProgress();
                    String progressMsg = progress.getString(TranscriptionWorker.KEY_PROGRESS);
                    if (progressMsg != null) {
                        _transcriptionText.postValue(progressMsg);
                    }

                    if (workInfo.getState().isFinished()) {
                        _isProgressVisible.postValue(false);
                        if (mIsTaskRunning) {
                            mIsTaskRunning = false;
                            updateTranscriptionRunning(false);
                        }
                        long elapsedMs = mStartTime > 0 ? System.currentTimeMillis() - mStartTime : 0;
                        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            String result = workInfo.getOutputData().getString(TranscriptionWorker.KEY_RESULT);
                            if (result != null) {
                                _transcriptionText.postValue(result);
                                if (mMainViewModel != null && mTaskKey != null) {
                                    mMainViewModel.updateTaskResult(mTaskKey, MainViewModel.TaskEntry.Status.SUCCEEDED, result, elapsedMs);
                                }
                            }
                        } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                            String error = workInfo.getOutputData().getString(TranscriptionWorker.KEY_ERROR);
                            if (error == null) error = "Unknown error";
                            String errorText;
                            if ("OUT_OF_MEMORY".equals(error)) {
                                errorText = getApplication().getString(R.string.error_low_memory);
                            } else {
                                errorText = getApplication().getString(R.string.error_transcription_failed) + ": " + error;
                            }
                            _transcriptionText.postValue(errorText);
                            if (mMainViewModel != null && mTaskKey != null) {
                                mMainViewModel.updateTaskResult(mTaskKey, MainViewModel.TaskEntry.Status.FAILED, errorText, elapsedMs);
                            }
                        } else if (workInfo.getState() == WorkInfo.State.CANCELLED) {
                            String cancelText = getApplication().getString(R.string.transcription_cancelled);
                            _transcriptionText.postValue(cancelText);
                            if (mMainViewModel != null && mTaskKey != null) {
                                mMainViewModel.updateTaskResult(mTaskKey, MainViewModel.TaskEntry.Status.CANCELLED, cancelText, elapsedMs);
                            }
                        }

                        // Clean up observer on the stored LiveData reference
                        mainHandler.post(() -> cleanupObserver());
                    }
                }
            }
        };
    }

    private void cleanupObserver() {
        if (mWorkInfoLiveData != null && mWorkInfoObserver != null) {
            final LiveData<WorkInfo> liveData = mWorkInfoLiveData;
            final Observer<WorkInfo> observer = mWorkInfoObserver;
            if (Looper.myLooper() == Looper.getMainLooper()) {
                liveData.removeObserver(observer);
            } else {
                mainHandler.post(() -> liveData.removeObserver(observer));
            }
            mWorkInfoObserver = null;
            mWorkInfoLiveData = null;
            mCurrentWorkId = null;
        }
    }

    public void stopTranscription() {
        if (mCurrentWorkId != null) {
            Logger.i(TAG, "Stopping transcription task: " + mCurrentWorkId);
            WorkManager workManager = WorkManager.getInstance(getApplication());
            workManager.cancelWorkById(mCurrentWorkId);

            whisperService.stopCurrentTranscription();

            cleanupObserver();

            if (mIsTaskRunning) {
                mIsTaskRunning = false;
                updateTranscriptionRunning(false);
            }
            _isProgressVisible.postValue(false);
        }
    }

    public void clearTranscription() {
        stopTranscription();
        _transcriptionText.setValue("");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopTranscription();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}
