package il.co.bensavit.beep.ui.dialogs;

import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ScrollView;
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
        TextView textView = new TextView(requireContext());
        textView.setText(Logger.getLogs());
        textView.setPadding(48, 48, 48, 48);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextSize(12);

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(textView);

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_logs_title)
                .setView(scrollView)
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
