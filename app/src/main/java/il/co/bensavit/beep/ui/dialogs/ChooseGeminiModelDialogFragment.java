package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;

public class ChooseGeminiModelDialogFragment extends DialogFragment {

    private static final String KEY_SELECTED_MODEL = "selected_model";

    private MainViewModel viewModel;
    private ArrayAdapter<String> adapter;
    private final List<String> modelList = new ArrayList<>();
    private String selectedModel;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        String currentModel = viewModel.getGeminiModelName();
        selectedModel = (savedInstanceState != null) 
                ? savedInstanceState.getString(KEY_SELECTED_MODEL, currentModel) 
                : currentModel;

        View view = getLayoutInflater().inflate(R.layout.dialog_choose_gemini_model, null);
        TextView currentModelView = view.findViewById(R.id.text_current_model);
        TextView keyWarningView = view.findViewById(R.id.text_key_warning);
        View progressBar = view.findViewById(R.id.progress_fetching);
        ListView listView = view.findViewById(R.id.list_gemini_models);

        currentModelView.setText(getString(R.string.dialog_choose_gemini_model_current, currentModel));

        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_single_choice, modelList);
        listView.setAdapter(adapter);

        // Populate model list initially
        List<String> cachedModels = viewModel.geminiModels.getValue();
        updateModelList(currentModel, cachedModels);
        adapter.notifyDataSetChanged();

        int selectedIndex = getSelectedIndex(selectedModel);
        if (selectedIndex != -1) {
            listView.setItemChecked(selectedIndex, true);
        }

        listView.setOnItemClickListener((parent, v, position, id) -> {
            if (position >= 0 && position < modelList.size()) {
                selectedModel = modelList.get(position);
                listView.setItemChecked(position, true);
            }
        });

        viewModel.isFetchingGeminiModels.removeObservers(this);
        viewModel.isFetchingGeminiModels.observe(this, isFetching -> {
            if (isFetching != null && progressBar != null) {
                progressBar.setVisibility(isFetching ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.geminiModels.removeObservers(this);
        viewModel.geminiModels.observe(this, models -> {
            String updatedCurrentModel = viewModel.getGeminiModelName();
            currentModelView.setText(getString(R.string.dialog_choose_gemini_model_current, updatedCurrentModel));
            updateModelList(updatedCurrentModel, models);
            adapter.notifyDataSetChanged();

            int index = getSelectedIndex(selectedModel);
            if (index != -1) {
                listView.setItemChecked(index, true);
            }
        });

        if (viewModel.getGeminiApiKey().isEmpty()) {
            if (keyWarningView != null) {
                keyWarningView.setVisibility(View.VISIBLE);
            }
        } else if (cachedModels == null || cachedModels.isEmpty()) {
            viewModel.fetchGeminiModels();
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_choose_gemini_model_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_gemini_config_save, (dialog, which) -> {
                    if (selectedModel != null && !selectedModel.isEmpty()) {
                        viewModel.setGeminiModel(selectedModel);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SELECTED_MODEL, selectedModel);
    }

    static final Pattern FLASH_MODEL_PATTERN = Pattern.compile("gemini-([0-9]|f).*(latest|flash|lite)", Pattern.CASE_INSENSITIVE);

    private void updateModelList(String currentModel, List<String> fetchedModels) {
        modelList.clear();
        modelList.add("auto");
        String normalizedCurrent = (currentModel != null && currentModel.startsWith("models/"))
                ? currentModel.substring("models/".length()) : currentModel;
        if (fetchedModels != null) {
            for (String m : fetchedModels) {
                if (m == null || "auto".equalsIgnoreCase(m)) continue;
                String cleanName = m.startsWith("models/") ? m.substring("models/".length()) : m;
                if (FLASH_MODEL_PATTERN.matcher(cleanName).matches() && !modelList.contains(cleanName)) {
                    modelList.add(cleanName);
                }
            }
        }
        if (normalizedCurrent != null && !normalizedCurrent.isEmpty() && !modelList.contains(normalizedCurrent)) {
            modelList.add(normalizedCurrent);
        }
    }

    private int getSelectedIndex(String modelName) {
        if (modelName == null) return -1;
        String normalized = modelName.startsWith("models/") ? modelName.substring("models/".length()) : modelName;
        for (int index = 0; index < modelList.size(); index++) {
            if (Objects.equals(modelList.get(index), normalized) || Objects.equals(modelList.get(index), modelName)) {
                return index;
            }
        }
        return -1;
    }
}
