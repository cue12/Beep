package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;

public class DownloadProgressDialogFragment extends DialogFragment {

    private static final String ARG_MODEL_NAME = "model_name";
    private LinearProgressIndicator progressIndicator;

    public static DownloadProgressDialogFragment newInstance(String modelName) {
        DownloadProgressDialogFragment fragment = new DownloadProgressDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODEL_NAME, modelName);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String modelName = getArguments() != null ? getArguments().getString(ARG_MODEL_NAME) : "";
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        layout.setPadding(padding, padding, padding, padding);

        progressIndicator = new LinearProgressIndicator(requireContext());
        progressIndicator.setIndeterminate(false);
        progressIndicator.setMax(100);
        layout.addView(progressIndicator);

        viewModel.downloadProgress.observe(this, progress -> {
            if (progressIndicator != null && progress >= 0) {
                progressIndicator.setProgress(progress);
            }
        });

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_downloading_model_title))
                .setMessage(getString(R.string.downloading_model, modelName))
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> {
                    viewModel.cancelDownload();
                })
                .create();
    }
}
