package il.co.bensavit.beep.ui;

import android.app.Activity;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.utils.ModelUtils;
import il.co.bensavit.beep.utils.StorageUtils;

public class ModelDownloadListAdapter extends ArrayAdapter<String> {
    private final Activity activity;
    private final MainViewModel viewModel;
    private final List<String> localModels;
    private final List<String> downloadedModels;
    private final List<String> displayItems = new ArrayList<>();

    public ModelDownloadListAdapter(Activity activity, MainViewModel viewModel, List<String> localModels, List<String> downloadedModels) {
        super(activity, R.layout.item_model);
        this.activity = activity;
        this.viewModel = viewModel;
        this.localModels = localModels;
        this.downloadedModels = downloadedModels;
        updateDisplayItems();
    }

    private void updateDisplayItems() {
        displayItems.clear();
        displayItems.addAll(localModels);
    }

    @Override
    public int getCount() {
        return displayItems.size();
    }

    @Override
    public String getItem(int position) {
        return displayItems.get(position);
    }

    @Override
    public boolean isEnabled(int position) {
        String model = getItem(position);
        // Disable models that don't have a download ID yet
        return il.co.bensavit.beep.data.ModelRepository.isModelAvailable(model);
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        if (view == null || !(view instanceof LinearLayout) || view.findViewById(R.id.text_model_name) == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_model, parent, false);
        }

        String item = getItem(position);
        if (item == null) return view;

        TextView nameTv = view.findViewById(R.id.text_model_name);
        TextView statusTv = view.findViewById(R.id.text_model_status);
        ImageView statusIv = view.findViewById(R.id.image_model_status);
        View deleteBtn = view.findViewById(R.id.button_delete_model);

        nameTv.setText(ModelUtils.formatModelDisplayName(getContext(), item));
        nameTv.setPaintFlags(nameTv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        nameTv.setAlpha(1.0f);
        statusIv.setVisibility(View.VISIBLE);
        statusIv.setImageResource(0);
        deleteBtn.setVisibility(View.GONE);

        boolean isDownloaded = downloadedModels.contains(item);
        boolean isCorrupted = il.co.bensavit.beep.data.ModelRepository.isCorrupted(getContext(), item);

        if (isDownloaded || isCorrupted) {
            File modelFile = new File(StorageUtils.getModelsDir(getContext()), isCorrupted ? item + il.co.bensavit.beep.data.ModelRepository.SUFFIX_CORRUPTED : item);

            if (isCorrupted) {
                statusTv.setText(R.string.model_status_corrupted);
                statusIv.setImageResource(android.R.drawable.stat_notify_error);
                statusIv.setColorFilter(getContext().getColor(android.R.color.holo_red_dark));
            } else {
                String sizeStr = android.text.format.Formatter.formatFileSize(getContext(), modelFile.length());
                statusTv.setText(getContext().getString(R.string.model_status_with_size, getContext().getString(R.string.model_status_ready), sizeStr));
                statusIv.setImageResource(android.R.drawable.checkbox_on_background);
                statusIv.clearColorFilter();
            }
            deleteBtn.setVisibility(View.VISIBLE);
        } else if (il.co.bensavit.beep.data.ModelRepository.isModelAvailable(item)) {
            statusTv.setText(R.string.model_status_download);
            statusIv.setImageResource(android.R.drawable.stat_sys_download);
            nameTv.setAlpha(0.9f);
        } else {
            statusTv.setText(R.string.model_status_coming_soon);
            statusIv.setImageResource(android.R.drawable.ic_menu_info_details);
            nameTv.setPaintFlags(nameTv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            nameTv.setAlpha(0.5f);
            statusIv.setAlpha(0.3f);
            statusTv.setAlpha(0.5f);
        }


        if (deleteBtn.getVisibility() == View.VISIBLE) {
            deleteBtn.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.delete_file_title)
                        .setMessage(getContext().getString(R.string.delete_file_message, item))
                        .setPositiveButton(R.string.button_ok, (d, w) -> {
                            viewModel.deleteDownloadedModel(item);
                            downloadedModels.remove(item);
                            downloadedModels.remove(item + il.co.bensavit.beep.data.ModelRepository.SUFFIX_CORRUPTED);
                            updateDisplayItems();
                            notifyDataSetChanged();
                        })
                        .setNegativeButton(R.string.button_cancel, null)
                        .show();
            });
        }

        return view;
    }
}
