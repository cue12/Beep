package il.co.bensavit.beep.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import java.util.Objects;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;

public class TranscriptionTaskAdapter extends ListAdapter<MainViewModel.TaskEntry, TranscriptionTaskViewHolder> {

    private final LifecycleOwner lifecycleOwner;
    private final MainViewModel mainViewModel;
    private final TaskActionListener actionListener;

    private static final DiffUtil.ItemCallback<MainViewModel.TaskEntry> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MainViewModel.TaskEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull MainViewModel.TaskEntry oldItem, @NonNull MainViewModel.TaskEntry newItem) {
                    return Objects.equals(oldItem.taskKey, newItem.taskKey);
                }

                @Override
                public boolean areContentsTheSame(@NonNull MainViewModel.TaskEntry oldItem, @NonNull MainViewModel.TaskEntry newItem) {
                    return Objects.equals(oldItem.status, newItem.status)
                            && Objects.equals(oldItem.resultText, newItem.resultText)
                            && Objects.equals(oldItem.file, newItem.file)
                            && Objects.equals(oldItem.modelName, newItem.modelName)
                            && oldItem.elapsedTimeMs == newItem.elapsedTimeMs
                            && Objects.equals(oldItem.workId, newItem.workId)
                            && oldItem.isSummaryExpanded == newItem.isSummaryExpanded
                            && oldItem.isTranscriptionExpanded == newItem.isTranscriptionExpanded
                            && oldItem.timestamp == newItem.timestamp;
                }
            };

    public TranscriptionTaskAdapter(@NonNull LifecycleOwner lifecycleOwner,
                                    @NonNull MainViewModel mainViewModel,
                                    @NonNull TaskActionListener actionListener) {
        super(DIFF_CALLBACK);
        this.lifecycleOwner = lifecycleOwner;
        this.mainViewModel = mainViewModel;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public TranscriptionTaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transcription_task, parent, false);
        return new TranscriptionTaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TranscriptionTaskViewHolder holder, int position) {
        holder.bind(getItem(position), lifecycleOwner, mainViewModel, actionListener);
    }

    @Override
    public void onViewRecycled(@NonNull TranscriptionTaskViewHolder holder) {
        super.onViewRecycled(holder);
        holder.unbind();
    }

    public boolean hasTasksWithResult() {
        for (int i = 0; i < getItemCount(); i++) {
            MainViewModel.TaskEntry entry = getItem(i);
            if (entry != null && entry.resultText != null && !entry.resultText.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
