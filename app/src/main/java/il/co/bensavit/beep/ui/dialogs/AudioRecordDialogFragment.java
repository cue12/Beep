package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.utils.Logger;

public class AudioRecordDialogFragment extends DialogFragment {

    private static final String TAG = "AudioRecordDialog";

    private MediaRecorder recorder;
    private File outputFile;
    private TextView timerTextView;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTimeMs;
    private boolean isRecording = false;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                long elapsedMs = System.currentTimeMillis() - startTimeMs;
                long seconds = (elapsedMs / 1000) % 60;
                long minutes = (elapsedMs / (1000 * 60)) % 60;
                if (timerTextView != null) {
                    timerTextView.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds));
                }
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        View view = getLayoutInflater().inflate(R.layout.dialog_audio_record, null);
        timerTextView = view.findViewById(R.id.text_timer);

        boolean started = startRecording();
        if (!started) {
            viewModel.showUserMessage(getString(R.string.error_recording_failed));
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_record_audio)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.button_stop_and_transcribe, (dialog, which) -> {
                    stopRecording(true);
                    if (outputFile != null && outputFile.exists() && outputFile.length() > 0) {
                        viewModel.handleSharedUri(Uri.fromFile(outputFile), outputFile.getName());
                    }
                })
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> stopRecording(false))
                .create();
    }

    private boolean startRecording() {
        try {
            File cacheDir = requireContext().getCacheDir();
            outputFile = new File(cacheDir, "record_" + System.currentTimeMillis() + ".m4a");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                recorder = new MediaRecorder(requireContext());
            } else {
                recorder = new MediaRecorder();
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioChannels(1);
            recorder.setOutputFile(outputFile.getAbsolutePath());

            recorder.prepare();
            recorder.start();

            isRecording = true;
            startTimeMs = System.currentTimeMillis();
            timerHandler.post(timerRunnable);
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start MediaRecorder", e);
            stopRecording(false);
            return false;
        }
    }

    private void stopRecording(boolean keepFile) {
        if (isRecording) {
            isRecording = false;
            timerHandler.removeCallbacks(timerRunnable);
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to stop MediaRecorder", e);
                } finally {
                    recorder.release();
                    recorder = null;
                }
            }
        }
        if (!keepFile && outputFile != null && outputFile.exists()) {
            if (!outputFile.delete()) {
                Logger.w(TAG, "Failed to delete temp recording file");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording(false);
    }
}
