package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;

import java.util.Objects;

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

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        layout.setPadding(padding, padding / 2, padding, 0);

        TextView currentModelView = new TextView(requireContext());
        currentModelView.setText(getString(R.string.dialog_choose_gemini_model_current, currentModel));
        currentModelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        currentModelView.setPadding(0, 0, 0, padding / 2);
        layout.addView(currentModelView);

        ProgressBar progressBar = new ProgressBar(requireContext());
        progressBar.setVisibility(View.GONE);
        layout.addView(progressBar);

        ListView listView = new ListView(requireContext());
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_single_choice, modelList);
        listView.setAdapter(adapter);
        layout.addView(listView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // Populate model list initially
        List<String> cachedModels = viewModel.geminiModels.getValue();
        updateModelList(currentModel, cachedModels);
        adapter.notifyDataSetChanged();

        int selectedIndex = getSelectedIndex(selectedModel);
        if (selectedIndex != -1) {
            listView.setItemChecked(selectedIndex, true);
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < modelList.size()) {
                selectedModel = modelList.get(position);
                listView.setItemChecked(position, true);
            }
        });

        viewModel.isFetchingGeminiModels.observe(this, isFetching -> {
            if (isFetching != null) {
                progressBar.setVisibility(isFetching ? View.VISIBLE : View.GONE);
            }
        });

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
            TextView keyWarningView = new TextView(requireContext());
            keyWarningView.setText(R.string.gemini_key_required);
            keyWarningView.setPadding(0, 0, 0, padding / 2);
            layout.addView(keyWarningView, 1);
        } else if (cachedModels == null || cachedModels.isEmpty()) {
            viewModel.fetchGeminiModels();
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_choose_gemini_model_title)
                .setView(layout)
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

    private void updateModelList(String currentModel, List<String> fetchedModels) {
        modelList.clear();
        modelList.add("auto");
        if (fetchedModels != null) {
            for (String m : fetchedModels) {
                if (!"auto".equals(m) && !modelList.contains(m)) {
                    modelList.add(m);
                }
            }
        }
        if (currentModel != null && !currentModel.isEmpty() && !modelList.contains(currentModel)) {
            modelList.add(currentModel);
        }
    }

    private int getSelectedIndex(String modelName) {
        if (modelName == null) return -1;
        for (int index = 0; index < modelList.size(); index++) {
            if (Objects.equals(modelList.get(index), modelName)) {
                return index;
            }
        }
        return -1;
    }
}
