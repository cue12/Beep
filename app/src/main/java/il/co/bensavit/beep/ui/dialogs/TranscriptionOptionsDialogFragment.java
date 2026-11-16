package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;

public class TranscriptionOptionsDialogFragment extends DialogFragment {

    private static final String KEY_SWITCH_STATE = "switch_summary_state";
    private static final String KEY_SWITCH_VIBRATION_STATE = "switch_vibration_state";

    private MainViewModel viewModel;
    private SwitchMaterial switchSummary;
    private SwitchMaterial switchVibration;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        View view = getLayoutInflater().inflate(R.layout.dialog_transcription_options, null);
        switchVibration = view.findViewById(R.id.switch_vibration);
        switchSummary = view.findViewById(R.id.switch_summary);

        view.findViewById(R.id.text_vibration_summary).setOnClickListener(v -> switchVibration.toggle());
        view.findViewById(R.id.text_summary_summary).setOnClickListener(v -> switchSummary.toggle());

        boolean initialVibrationChecked = (savedInstanceState != null)
                ? savedInstanceState.getBoolean(KEY_SWITCH_VIBRATION_STATE, viewModel.isVibrationEnabled())
                : viewModel.isVibrationEnabled();
        switchVibration.setChecked(initialVibrationChecked);

        boolean initialChecked = (savedInstanceState != null)
                ? savedInstanceState.getBoolean(KEY_SWITCH_STATE, viewModel.isGeminiSummaryEnabled())
                : viewModel.isGeminiSummaryEnabled();
        switchSummary.setChecked(initialChecked);

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_transcription_options_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_gemini_config_save, (dialog, which) -> {
                    viewModel.setGeminiSummaryEnabled(switchSummary.isChecked());
                    viewModel.setVibrationEnabled(switchVibration.isChecked());
                })
                .setNegativeButton(R.string.button_cancel, null)
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (switchSummary != null) {
            outState.putBoolean(KEY_SWITCH_STATE, switchSummary.isChecked());
        }
        if (switchVibration != null) {
            outState.putBoolean(KEY_SWITCH_VIBRATION_STATE, switchVibration.isChecked());
        }
    }
}

