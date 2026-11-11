package il.co.bensavit.beep.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FilenameFilter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.media.MediaMetadataRetriever;
import android.util.Log;


import il.co.bensavit.beep.R;
import il.co.bensavit.beep.TranscriptionTaskViewModel;
import il.co.bensavit.beep.data.HistoryItem;
import il.co.bensavit.beep.utils.FileUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryFilePicker extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "HistoryFilePicker";
    public static final String BUNDLE_KEY_FILE_PATH = "file_path";

    private File mDirectory;
    private FileAdapter mAdapter;
    private boolean mSortByName = false;
    private TextView mTitleView;
    private RecyclerView mRecyclerView;
    private View mEmptyText;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public static HistoryFilePicker newInstance(File directory) {
        HistoryFilePicker fragment = new HistoryFilePicker();
        Bundle args = new Bundle();
        args.putString("directory_path", directory.getAbsolutePath());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_file_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            String path = getArguments().getString("directory_path");
            if (path != null) {
                mDirectory = new File(path);
            }
        }

        mRecyclerView = view.findViewById(R.id.recycler_files);
        mEmptyText = view.findViewById(R.id.text_empty);
        View sortButton = view.findViewById(R.id.button_sort);
        mTitleView = view.findViewById(R.id.title_history);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new FileAdapter();
        mRecyclerView.setAdapter(mAdapter);

        TranscriptionTaskViewModel.isTranscriptionRunning.observe(getViewLifecycleOwner(), running -> {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        });

        loadFiles();

        sortButton.setOnClickListener(new SortButtonClickListener());
    }

    private void loadFiles() {
        if (mDirectory == null) return;
        
        mExecutor.execute(() -> {
            File[] files = mDirectory.listFiles(new AudioFileFilter());
            List<File> fileList = new ArrayList<>();
            if (files != null) {
                fileList.addAll(Arrays.asList(files));
            }

            if (mSortByName) {
                Collections.sort(fileList, new FileNameComparator());
            } else {
                Collections.sort(fileList, new FileDateComparator());
            }

            List<HistoryItem> historyItems = new ArrayList<>();
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            for (File file : fileList) {
                String duration = "Unknown";
                try {
                    retriever.setDataSource(file.getAbsolutePath());
                    String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (time != null) {
                        long timeInMillis = Long.parseLong(time);
                        long timeInSeconds = timeInMillis / 1000;
                        duration = timeInSeconds + " sec";
                    }
                } catch (Exception e) {
                    Log.e("MaterialFilePicker", "Failed to get duration: " + file.getName());
                }
                historyItems.add(new HistoryItem(file, duration, file.lastModified()));
            }
            try {
                retriever.release();
            } catch (Exception ignored) {}

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> updateUI(historyItems));
            }
        });
    }

    private void updateUI(List<HistoryItem> historyItems) {
        mAdapter.setFiles(historyItems);

        if (mTitleView != null) {
            mTitleView.setText(getString(R.string.history_title, historyItems.size()));
        }

        if (mRecyclerView != null && mEmptyText != null) {
            boolean isEmpty = historyItems.isEmpty();
            mRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void showRenameDialog(File file) {
        if (TranscriptionTaskViewModel.isAnyTranscriptionRunning()) {
            return;
        }
        FileUtils.showRenameDialog(requireContext(), file, newFile -> loadFiles());
    }

    private class FileAdapter extends RecyclerView.Adapter<FileViewHolder> {
        private final List<HistoryItem> items = new ArrayList<>();

        public void setFiles(List<HistoryItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new FileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            HistoryItem item = items.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private class FileViewHolder extends RecyclerView.ViewHolder {
        private final TextView fileName;
        private final TextView fileInfo;
        private final View renameButton;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.text_filename);
            fileInfo = itemView.findViewById(R.id.text_file_info);
            renameButton = itemView.findViewById(R.id.button_rename);
        }

        public void bind(HistoryItem item) {
            File file = item.getFile();
            fileName.setText(file.getName());
            String date = DateFormat.getDateTimeInstance().format(item.getDate());
            String info = getString(R.string.file_info_format, date, item.getDuration());
            fileInfo.setText(info);

            boolean isTaskRunning = TranscriptionTaskViewModel.isAnyTranscriptionRunning();
            renameButton.setEnabled(!isTaskRunning);
            renameButton.setAlpha(isTaskRunning ? 0.3f : 1.0f);

            itemView.setOnClickListener(new FileItemClickListener(file));
            renameButton.setOnClickListener(new RenameButtonClickListener(file));
            itemView.setOnLongClickListener(new FileLongClickListener(file));
        }
    }

    private class SortButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mSortByName = !mSortByName;
            loadFiles();
            if (getView() != null) {
                com.google.android.material.snackbar.Snackbar.make(
                        getView(),
                        mSortByName ? R.string.sort_by_name : R.string.sort_by_date,
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show();
            }
        }
    }

    private static class AudioFileFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            String lower = name.toLowerCase();
            return lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".aac")
                    || lower.endsWith(".ogg") || lower.endsWith(".flac") || lower.endsWith(".m4a")
                    || lower.endsWith(".opus") || lower.endsWith(".webm") || lower.endsWith(".mp4");
        }
    }

    private static class FileNameComparator implements Comparator<File> {
        @Override
        public int compare(File f1, File f2) {
            return f1.getName().compareToIgnoreCase(f2.getName());
        }
    }

    private static class FileDateComparator implements Comparator<File> {
        @Override
        public int compare(File f1, File f2) {
            return Long.compare(f2.lastModified(), f1.lastModified());
        }
    }

    private class FileItemClickListener implements View.OnClickListener {
        private final File file;

        FileItemClickListener(File file) {
            this.file = file;
        }

        @Override
        public void onClick(View v) {
            Bundle result = new Bundle();
            result.putString(BUNDLE_KEY_FILE_PATH, file.getAbsolutePath());
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismiss();
        }
    }

    private class RenameButtonClickListener implements View.OnClickListener {
        private final File file;

        RenameButtonClickListener(File file) {
            this.file = file;
        }

        @Override
        public void onClick(View v) {
            if (TranscriptionTaskViewModel.isAnyTranscriptionRunning()) {
                return;
            }
            showRenameDialog(file);
        }
    }

    private class FileLongClickListener implements View.OnLongClickListener {
        private final File file;

        FileLongClickListener(File file) {
            this.file = file;
        }

        @Override
        public boolean onLongClick(View v) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_file_title)
                    .setMessage(getString(R.string.delete_file_message, file.getName()))
                    .setPositiveButton(R.string.delete, new DeleteDialogPositiveClickListener(file))
                    .setNegativeButton(R.string.button_cancel, null)
                    .show();
            return true;
        }
    }

    private class DeleteDialogPositiveClickListener implements DialogInterface.OnClickListener {
        private final File file;

        DeleteDialogPositiveClickListener(File file) {
            this.file = file;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (file.delete()) {
                loadFiles();
                if (getView() != null) {
                    com.google.android.material.snackbar.Snackbar.make(
                            getView(),
                            R.string.file_deleted,
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show();
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }
}
