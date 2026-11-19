package il.co.bensavit.beep.ui.adapter;

import android.view.View;
import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.TranscriptionTaskViewModel;

public interface TaskActionListener {
    void onTaskRemoveRequested(MainViewModel.TaskEntry taskEntry, TranscriptionTaskViewModel taskViewModel);
    void onFileRenameRequested(MainViewModel.TaskEntry taskEntry);
    void onShowSnackbar(String message, String actionText, View.OnClickListener actionListener);
    void onToggleAllStateChanged();
    void onMinimizeOtherTasks(String keepTaskKey);
    void onSetAllTasksMinimized(boolean minimize);
}
