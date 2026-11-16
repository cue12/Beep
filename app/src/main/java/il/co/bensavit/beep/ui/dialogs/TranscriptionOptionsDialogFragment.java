package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    private MainViewModel viewModel;
    private SwitchMaterial switchSummary;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        layout.setPadding(padding, padding / 2, padding, 0);

        TextView sectionTitle = new TextView(requireContext());
        sectionTitle.setText(R.string.section_gemini_options);
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(0, 0, 0, padding / 4);
        layout.addView(sectionTitle);

        switchSummary = new SwitchMaterial(requireContext());
        switchSummary.setText(R.string.option_include_summary_title);
        switchSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        switchSummary.setLayoutParams(switchParams);
        boolean initialChecked = (savedInstanceState != null)
                ? savedInstanceState.getBoolean(KEY_SWITCH_STATE, viewModel.isGeminiSummaryEnabled())
                : viewModel.isGeminiSummaryEnabled();
        switchSummary.setChecked(initialChecked);
        layout.addView(switchSummary);

        TextView summaryDesc = new TextView(requireContext());
        summaryDesc.setText(R.string.option_include_summary_summary);
        summaryDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true);
        summaryDesc.setTextColor(typedValue.data);
        summaryDesc.setPadding(0, 0, 0, padding / 2);
        layout.addView(summaryDesc);

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_transcription_options_title)
                .setView(layout)
                .setPositiveButton(R.string.dialog_gemini_config_save, (dialog, which) -> 
                        viewModel.setGeminiSummaryEnabled(switchSummary.isChecked()))
                .setNegativeButton(R.string.button_cancel, null)
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (switchSummary != null) {
            outState.putBoolean(KEY_SWITCH_STATE, switchSummary.isChecked());
        }
    }
}
