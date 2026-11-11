package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;

public class GeminiConfigDialogFragment extends DialogFragment {

    private MainViewModel viewModel;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        String currentKey = viewModel.getGeminiApiKey();

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        layout.setPadding(padding, padding / 2, padding, 0);

        final EditText input = new EditText(requireContext());
        input.setHint(R.string.dialog_gemini_config_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(currentKey);
        layout.addView(input);

        TextView helpLink = new TextView(requireContext());
        helpLink.setText(R.string.action_gemini_help);
        helpLink.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, requireContext().getTheme()));
        helpLink.setPadding(0, padding / 2, 0, 0);
        helpLink.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/6BRyynZkvf0?si=IWlTsfdl0J0GzbKP"));
            startActivity(intent);
        });
        layout.addView(helpLink);

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_gemini_config_title)
                .setView(layout)
                .setPositiveButton(R.string.dialog_gemini_config_save, (dialog, which) -> {
                    String newKey = input.getText().toString().trim();
                    viewModel.setGeminiApiKey(newKey);
                    // Note: We might want to trigger a message in the Activity, 
                    // but since outputMsg is currently used for many things, 
                    // maybe the ViewModel can expose a "message" LiveData.
                })
                .setNegativeButton(R.string.button_cancel, null)
                .create();
    }
}
