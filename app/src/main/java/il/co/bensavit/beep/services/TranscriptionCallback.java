package il.co.bensavit.beep.services;

/**
 * Concrete callback class for receiving transcription status, progress, success, and error events.
 */
public interface TranscriptionCallback {
  default void onProgress(String message) {}
  default void onSuccess(String result) {}
  default void onError(String error) {}
}
