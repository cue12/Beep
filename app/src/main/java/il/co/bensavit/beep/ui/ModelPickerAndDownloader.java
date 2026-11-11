package il.co.bensavit.beep.ui;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.data.ModelRepository;
import il.co.bensavit.beep.ui.dialogs.GeminiConfigDialogFragment;
import il.co.bensavit.beep.utils.ModelUtils;
import il.co.bensavit.beep.utils.StorageUtils;

/**
 * Utility class for selecting and downloading models.
 */
public final class ModelPickerAndDownloader {

    // Remote models that can be downloaded/selected
    private static final List<String> REMOTE_MODELS = ModelRepository.getRemoteModelNames();

    private ModelPickerAndDownloader() {
        // Utility class
    }

    public interface OnModelLoadingListener {
        void onLoadingStarted();

        void onLoadingFinished();

        void onError(String message);
    }

    public static void show(AppCompatActivity activity, MainViewModel viewModel, View anchorView, OnModelLoadingListener listener) {
        List<String> downloadedModels = getDownloadedModels(activity);

        ModelDownloadListAdapter adapter = new ModelDownloadListAdapter(
                activity, viewModel, REMOTE_MODELS, downloadedModels);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dialog_download_model_title)
                .setAdapter(adapter, (dialog, which) -> {
                    String selection = adapter.getItem(which);
                    handleModelSelection(activity, viewModel, selection, listener);
                })
                .setNeutralButton(R.string.action_configure_gemini, (dialog, which) -> showGeminiConfig(activity))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private static List<String> getDownloadedModels(AppCompatActivity activity) {
        File modelsDir = StorageUtils.getModelsDir(activity);
        if (!modelsDir.exists()) {
            return new ArrayList<>();
        }
        String[] localFiles = modelsDir.list();
        return localFiles != null ? new ArrayList<>(Arrays.asList(localFiles)) : new ArrayList<>();
    }

    private static void handleModelSelection(AppCompatActivity activity, MainViewModel viewModel,
                                             String selection, OnModelLoadingListener listener) {
        String currentLang = viewModel.language.getValue();
        if (ModelUtils.isLanguageConflicting(activity, selection, currentLang)) {
            String modelDisplayName = ModelUtils.formatModelDisplayName(activity, selection);
            String errorMessage = activity.getString(R.string.error_language_model_conflict, modelDisplayName);
            viewModel.showUserMessage(errorMessage);
        }

        viewModel.setCurrentModelName(selection);
        viewModel.setGeminiSelected(false);

        boolean isDownloaded = isModelDownloaded(activity, selection);

        if (!isDownloaded && listener != null) {
            listener.onLoadingStarted();
        }

        viewModel.loadLocalModel(selection, () -> {
            if (listener != null) {
                listener.onLoadingFinished();
            }
        });
    }

    private static boolean isModelDownloaded(AppCompatActivity activity, String modelName) {
        return ModelRepository.verifyModel(activity, modelName);
    }

    private static void showGeminiConfig(AppCompatActivity activity) {
        new GeminiConfigDialogFragment().show(activity.getSupportFragmentManager(), "GeminiConfig");
    }
}
