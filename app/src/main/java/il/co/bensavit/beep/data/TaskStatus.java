package il.co.bensavit.beep.data;

import androidx.annotation.Keep;

/**
 * Enumeration representing the execution status of an audio transcription task.
 */
@Keep
public enum TaskStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
