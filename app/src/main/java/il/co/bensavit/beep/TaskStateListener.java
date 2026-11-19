package il.co.bensavit.beep;

import androidx.annotation.Keep;

import il.co.bensavit.beep.data.TaskStatus;

/**
 * Listener interface for decoupling task state updates in {@link TranscriptionTaskViewModel}
 * from concrete ViewModel implementations.
 */
@Keep
public interface TaskStateListener {
    void updateTaskWorkId(String taskKey, String workId);
    void updateTaskModelName(String taskKey, String modelName);
    void updateTaskResult(String taskKey, TaskStatus status, String resultText, long elapsedTimeMs);
}

