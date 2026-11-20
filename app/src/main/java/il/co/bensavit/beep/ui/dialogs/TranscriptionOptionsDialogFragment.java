package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.data.SettingsManager;

public class TranscriptionOptionsDialogFragment extends DialogFragment {

    private static final String KEY_SWITCH_STATE = "switch_summary_state";
    private static final String KEY_SWITCH_VIBRATION_STATE = "switch_vibration_state";
    private static final String KEY_SELECTED_TIMEOUT = "selected_timeout";
    private static final String KEY_SHOW_TIMEOUT_PICKER = "show_timeout_picker";

    private static final int[] TIMEOUT_OPTIONS = new int[]{5, 10, 15, 30, 60, 0};

    private MainViewModel viewModel;
    private SwitchMaterial switchSummary;
    private SwitchMaterial switchVibration;
    private TextView textTimeoutSummary;
    private int selectedTimeoutMinutes;
    private Dialog activeTimeoutPickerDialog;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        View view = getLayoutInflater().inflate(R.layout.dialog_transcription_options, null);
        switchVibration = view.findViewById(R.id.switch_vibration);
        switchSummary = view.findViewById(R.id.switch_summary);
        textTimeoutSummary = view.findViewById(R.id.text_timeout_summary);
        View layoutTimeoutOption = view.findViewById(R.id.layout_timeout_option);

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

        selectedTimeoutMinutes = (savedInstanceState != null)
                ? savedInstanceState.getInt(KEY_SELECTED_TIMEOUT, viewModel.getTranscriptionTimeoutMinutes())
                : viewModel.getTranscriptionTimeoutMinutes();

        updateTimeoutSummaryText();

        if (layoutTimeoutOption != null) {
            layoutTimeoutOption.setOnClickListener(v -> showTimeoutPickerDialog());
        }

        if (savedInstanceState != null && savedInstanceState.getBoolean(KEY_SHOW_TIMEOUT_PICKER, false)) {
            showTimeoutPickerDialog();
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_transcription_options_title)
                .setView(view)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    viewModel.setGeminiSummaryEnabled(switchSummary.isChecked());
                    viewModel.setVibrationEnabled(switchVibration.isChecked());
                    viewModel.setTranscriptionTimeoutMinutes(selectedTimeoutMinutes);
                })
                .setNegativeButton(R.string.button_cancel, null)
                .create();
    }

    private void updateTimeoutSummaryText() {
        if (textTimeoutSummary == null) return;
        String formattedDuration = formatTimeoutDuration(selectedTimeoutMinutes);
        textTimeoutSummary.setText(getString(R.string.option_timeout_summary, formattedDuration));
    }

    private String formatTimeoutDuration(int minutes) {
        if (minutes <= 0) {
            return getString(R.string.timeout_unlimited);
        } else if (minutes == SettingsManager.DEFAULT_TIMEOUT_MINUTES) {
            return getString(R.string.timeout_minutes_default, SettingsManager.DEFAULT_TIMEOUT_MINUTES);
        } else {
            return getString(R.string.timeout_minutes_format, minutes);
        }
    }

    private void showTimeoutPickerDialog() {
        if (!isAdded() || getContext() == null) return;

        CharSequence[] items = new CharSequence[TIMEOUT_OPTIONS.length];
        int selectedIndex = -1;

        for (int i = 0; i < TIMEOUT_OPTIONS.length; i++) {
            int val = TIMEOUT_OPTIONS[i];
            items[i] = formatTimeoutDuration(val);
            if (val == selectedTimeoutMinutes) {
                selectedIndex = i;
            }
        }

        activeTimeoutPickerDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_select_timeout_title)
                .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                    if (which >= 0 && which < TIMEOUT_OPTIONS.length) {
                        selectedTimeoutMinutes = TIMEOUT_OPTIONS[which];
                        updateTimeoutSummaryText();
                    }
                    activeTimeoutPickerDialog = null;
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> activeTimeoutPickerDialog = null)
                .setOnDismissListener(dialog -> activeTimeoutPickerDialog = null)
                .show();
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
        outState.putInt(KEY_SELECTED_TIMEOUT, selectedTimeoutMinutes);
        outState.putBoolean(KEY_SHOW_TIMEOUT_PICKER, activeTimeoutPickerDialog != null && activeTimeoutPickerDialog.isShowing());
    }

    @Override
    public void onDestroyView() {
        if (activeTimeoutPickerDialog != null && activeTimeoutPickerDialog.isShowing()) {
            activeTimeoutPickerDialog.dismiss();
            activeTimeoutPickerDialog = null;
        }
        super.onDestroyView();
    }
}
