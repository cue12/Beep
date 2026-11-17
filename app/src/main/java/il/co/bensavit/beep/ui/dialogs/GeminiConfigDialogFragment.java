package il.co.bensavit.beep.ui.dialogs;

import android.content.ActivityNotFoundException;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.utils.Logger;

public class GeminiConfigDialogFragment extends DialogFragment {

    private MainViewModel viewModel;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        String currentKey = viewModel.getGeminiApiKey();

        View view = getLayoutInflater().inflate(R.layout.dialog_gemini_config, null);
        EditText input = view.findViewById(R.id.edit_text_api_key);
        TextView helpLink = view.findViewById(R.id.text_help_link);

        if (input != null) {
            input.setText(currentKey);
        }

        if (helpLink != null) {
            helpLink.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/6BRyynZkvf0?si=IWlTsfdl0J0GzbKP"));
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Logger.e("GeminiConfigDialog", "Unable to open help URL", e);
                }
            });
        }

        View testBtn = view.findViewById(R.id.button_test_key);
        View progressBar = view.findViewById(R.id.progress_test_key);
        TextView resultText = view.findViewById(R.id.text_test_result);

        if (testBtn != null) {
            testBtn.setOnClickListener(v -> {
                if (input == null) return;
                String keyToTest = input.getText().toString().trim();
                if (keyToTest.isEmpty()) {
                    if (resultText != null && isAdded()) {
                        resultText.setVisibility(View.VISIBLE);
                        resultText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark));
                        resultText.setText(getString(R.string.key_invalid, getString(R.string.error_key_empty)));
                    }
                    return;
                }

                testBtn.setEnabled(false);
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                if (resultText != null) resultText.setVisibility(View.GONE);

                viewModel.testGeminiApiKey(keyToTest,
                    successMsg -> {
                        if (!isAdded() || getContext() == null) return;
                        testBtn.setEnabled(true);
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (resultText != null) {
                            resultText.setVisibility(View.VISIBLE);
                            resultText.setTextColor(requireContext().getColor(android.R.color.holo_green_dark));
                            resultText.setText(getString(R.string.key_valid));
                        }
                    },
                    errorMsg -> {
                        if (!isAdded() || getContext() == null) return;
                        testBtn.setEnabled(true);
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (resultText != null) {
                            resultText.setVisibility(View.VISIBLE);
                            resultText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark));
                            resultText.setText(getString(R.string.key_invalid, errorMsg));
                        }
                    }
                );
            });
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_gemini_config_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_gemini_config_save, (dialog, which) -> {
                    if (input != null) {
                        String newKey = input.getText().toString().trim();
                        viewModel.setGeminiApiKey(newKey);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .create();
    }
}
