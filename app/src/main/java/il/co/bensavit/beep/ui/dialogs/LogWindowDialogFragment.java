package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import il.co.bensavit.beep.R;
import il.co.bensavit.beep.utils.Logger;

public class LogWindowDialogFragment extends DialogFragment {

    public interface OnLogClearedListener {
        void onLogCleared();
    }

    private OnLogClearedListener listener;

    public void setOnLogClearedListener(OnLogClearedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_log_window, null);
        TextView textView = view.findViewById(R.id.text_logs);

        if (textView != null) {
            textView.setText(Logger.getLogs());
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_logs_title)
                .setView(view)
                .setPositiveButton(R.string.button_close, null)
                .setNeutralButton(R.string.button_clear, (dialog, which) -> {
                    Logger.clear();
                    if (listener != null) {
                        listener.onLogCleared();
                    }
                })
                .create();
    }
}
