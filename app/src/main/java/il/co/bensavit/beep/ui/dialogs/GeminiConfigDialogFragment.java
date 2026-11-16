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
