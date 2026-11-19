package il.co.bensavit.beep.ui.adapter;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.data.TaskStatus;
import il.co.bensavit.beep.TranscriptionTaskViewModel;
import il.co.bensavit.beep.ui.anim.LightTextureDrawable;
import il.co.bensavit.beep.ui.anim.ShimmerDrawable;
import il.co.bensavit.beep.utils.FileUtils;
import il.co.bensavit.beep.utils.ModelUtils;

public class TranscriptionTaskViewHolder extends RecyclerView.ViewHolder {

    private final View taskCard;
    private final TextView fileNameTv;
    private final TextView percentTv;
    private final TextView elapsedTimeTv;
    private final CircularProgressIndicator progressBar;
    private final TextView statusTv;

    private final View resultContainer;
    private final View structuredContainer;
    private final View summaryContainer;
    private final View summaryHeader;
    private final ImageView summaryChevron;
    private final TextView summaryBody;

    private final View transcriptionContainer;
    private final View transcriptionHeader;
    private final ImageView transcriptionChevron;
    private final TextView transcriptionBody;

    private final TextView resultTv;
    private final MaterialButton maximizeBtn;

    private Observer<String> textObserver;
    private Observer<Boolean> progressObserver;
    private TranscriptionTaskViewModel currentTaskViewModel;

    public TranscriptionTaskViewHolder(@NonNull View itemView) {
        super(itemView);
        taskCard = itemView.findViewById(R.id.task_card);
        LightTextureDrawable.attachTo(taskCard);
        ShimmerDrawable.attachTo(taskCard);

        fileNameTv = itemView.findViewById(R.id.text_filename);
        percentTv = itemView.findViewById(R.id.text_progress_percent);
        elapsedTimeTv = itemView.findViewById(R.id.text_elapsed_time);
        progressBar = itemView.findViewById(R.id.task_progress_bar);
        statusTv = itemView.findViewById(R.id.text_status);

        resultContainer = itemView.findViewById(R.id.container_result);
        structuredContainer = itemView.findViewById(R.id.container_result_structured);
        summaryContainer = itemView.findViewById(R.id.container_summary);
        summaryHeader = itemView.findViewById(R.id.header_summary);
        summaryChevron = itemView.findViewById(R.id.icon_summary_chevron);
        summaryBody = itemView.findViewById(R.id.text_summary_body);

        transcriptionContainer = itemView.findViewById(R.id.container_transcription);
        transcriptionHeader = itemView.findViewById(R.id.header_transcription);
        transcriptionChevron = itemView.findViewById(R.id.icon_transcription_chevron);
        transcriptionBody = itemView.findViewById(R.id.text_transcription_body);

        resultTv = itemView.findViewById(R.id.text_result);
        maximizeBtn = itemView.findViewById(R.id.button_maximize);

        summaryBody.setMovementMethod(LinkMovementMethod.getInstance());
        transcriptionBody.setMovementMethod(LinkMovementMethod.getInstance());
        resultTv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public void unbind() {
        if (currentTaskViewModel != null) {
            if (textObserver != null) {
                currentTaskViewModel.transcriptionText.removeObserver(textObserver);
                textObserver = null;
            }
            if (progressObserver != null) {
                currentTaskViewModel.isProgressVisible.removeObserver(progressObserver);
                progressObserver = null;
            }
            currentTaskViewModel = null;
        }
    }

    private void resetResultViews() {
        resultContainer.setVisibility(View.GONE);
        maximizeBtn.setVisibility(View.GONE);
        structuredContainer.setVisibility(View.GONE);
        summaryContainer.setVisibility(View.GONE);
        summaryBody.setText("");
        summaryBody.setVisibility(View.GONE);
        transcriptionContainer.setVisibility(View.GONE);
        transcriptionBody.setText("");
        transcriptionBody.setVisibility(View.GONE);
        resultTv.setText("");
        resultTv.setVisibility(View.GONE);
        elapsedTimeTv.setText("");
        elapsedTimeTv.setVisibility(View.GONE);
    }

    public void bind(@NonNull MainViewModel.TaskEntry taskEntry,
                     @NonNull LifecycleOwner lifecycleOwner,
                     @NonNull MainViewModel mainViewModel,
                     @NonNull TaskActionListener actionListener) {
        unbind();
        resetResultViews();

        Context context = itemView.getContext();
        File currentFile = taskEntry.file;
        fileNameTv.setText(currentFile.getName());

        String taskKey = taskEntry.taskKey;
        final String[] currentTaskModel = new String[]{ taskEntry.modelName };

        TranscriptionTaskViewModel taskViewModel = mainViewModel.getOrCreateTaskViewModel(taskKey);
        taskViewModel.initTask(taskKey, mainViewModel);
        currentTaskViewModel = taskViewModel;

        long startTime = taskEntry.timestamp > 0 ? taskEntry.timestamp : System.currentTimeMillis();

        if (taskEntry.status != TaskStatus.RUNNING) {
            progressBar.setVisibility(View.GONE);
            percentTv.setVisibility(View.GONE);
            if (taskEntry.elapsedTimeMs > 0) {
                long elapsedMs = taskEntry.elapsedTimeMs;
                long secs = (elapsedMs / 1000) % 60;
                long mins = (elapsedMs / (1000 * 60)) % 60;
                long tenths = (elapsedMs % 1000) / 100;
                elapsedTimeTv.setText(context.getString(R.string.elapsed_time_format, mins, secs, tenths));
                elapsedTimeTv.setVisibility(View.VISIBLE);
            } else {
                elapsedTimeTv.setVisibility(View.GONE);
            }

            if (taskEntry.status == TaskStatus.SUCCEEDED && taskEntry.resultText != null) {
                String modelName = resolveTaskModelName(mainViewModel, taskEntry, taskEntry.modelName);
                String displayName = ModelUtils.formatModelDisplayName(context, modelName);
                statusTv.setText(context.getString(R.string.transcription_complete, displayName));
                displayTaskResult(taskKey, taskEntry.resultText, taskEntry, mainViewModel, actionListener);
                taskViewModel.setRestoredResult(taskEntry.resultText, false);
            } else if (taskEntry.resultText != null) {
                statusTv.setText(taskEntry.resultText);
                taskViewModel.setRestoredResult(taskEntry.resultText, false);
            }
        } else {
            String currentText = taskViewModel.transcriptionText.getValue();
            boolean isRunning = Boolean.TRUE.equals(taskViewModel.isProgressVisible.getValue());
            if (currentText != null && !currentText.isEmpty() && !currentText.equals(context.getString(R.string.transcription_starting))) {
                applyProgressUI(context, currentText, isRunning);
            } else {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                percentTv.setVisibility(View.GONE);
                statusTv.setText(R.string.transcription_starting);
            }
            if (taskEntry.workId != null) {
                taskViewModel.observeExistingWork(taskEntry.workId);
            }
        }

        itemView.setOnLongClickListener(v -> {
            boolean isRunning = Boolean.TRUE.equals(taskViewModel.isProgressVisible.getValue());
            int titleRes = isRunning ? R.string.dialog_cancel_and_remove_task_title : R.string.dialog_remove_task_title;
            int messageRes = isRunning ? R.string.dialog_cancel_and_remove_task_message : R.string.dialog_remove_task_message;

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                    .setTitle(titleRes)
                    .setMessage(context.getString(messageRes, taskEntry.file.getName()))
                    .setPositiveButton(R.string.remove, (dialog, which) -> {
                        actionListener.onTaskRemoveRequested(taskEntry, taskViewModel);
                    });

            if (!TranscriptionTaskViewModel.isAnyTranscriptionRunning()) {
                builder.setNeutralButton(R.string.rename, (dialog, which) -> {
                    actionListener.onFileRenameRequested(taskEntry);
                });
            }

            builder.setNegativeButton(R.string.button_cancel, null)
                    .show();
            return true;
        });

        textObserver = text -> {
            if (text != null) {
                if (text.startsWith("MODEL_SELECTED:")) {
                    String modelId = text.substring("MODEL_SELECTED:".length());
                    currentTaskModel[0] = modelId;
                    mainViewModel.updateTaskModelName(taskKey, modelId);
                    String displayName = ModelUtils.formatModelDisplayName(context, modelId);
                    statusTv.setText(context.getString(R.string.model_subtitle, displayName));
                } else if (Boolean.TRUE.equals(taskViewModel.isProgressVisible.getValue())) {
                    if (text.endsWith("%")) {
                        try {
                            String numericPart = text.replace("%", "").trim();
                            Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(numericPart);
                            if (matcher.find()) {
                                String matchGroup = matcher.group(1);
                                if (matchGroup != null) {
                                    double val = Double.parseDouble(matchGroup);
                                    int percent = Math.min(100, Math.max(0, (int) Math.round(val)));
                                    percentTv.setText(percent + "%");
                                    percentTv.setVisibility(View.VISIBLE);
                                    if (progressBar.isIndeterminate()) {
                                        progressBar.setIndeterminate(false);
                                    }
                                    progressBar.setProgress(percent, true);
                                    statusTv.setText(R.string.transcribing_progress);
                                } else {
                                    statusTv.setText(text);
                                }
                            } else {
                                statusTv.setText(text);
                            }
                        } catch (Exception e) {
                            statusTv.setText(text);
                        }
                    } else {
                        statusTv.setText(text);
                        percentTv.setVisibility(View.GONE);
                        if (!progressBar.isIndeterminate()) {
                            progressBar.setIndeterminate(true);
                        }
                    }
                } else if (isErrorOrCancelledMessage(context, text)) {
                    statusTv.setText(text);
                    if (text.contains(context.getString(R.string.error_low_memory))) {
                        actionListener.onShowSnackbar(text, context.getString(R.string.mode_online), v -> {
                            mainViewModel.setGeminiSelected(true);
                        });
                    }
                } else if (!text.equals(context.getString(R.string.transcription_starting))) {
                    if (taskEntry.status != TaskStatus.RUNNING
                            && Objects.equals(text, taskEntry.resultText)) {
                        return;
                    }
                    MainViewModel.TaskEntry entry = mainViewModel.getTaskEntry(taskKey);
                    String modelName = resolveTaskModelName(mainViewModel, entry, currentTaskModel[0]);
                    String displayName = ModelUtils.formatModelDisplayName(context, modelName);
                    statusTv.setText(context.getString(R.string.transcription_complete, displayName));
                    displayTaskResult(taskKey, text, taskEntry, mainViewModel, actionListener);
                }
            }
        };

        progressObserver = visible -> {
            boolean isVisible = Boolean.TRUE.equals(visible);
            progressBar.setVisibility(isVisible ? View.VISIBLE : View.GONE);
            if (!isVisible) {
                percentTv.setVisibility(View.GONE);
                if (taskEntry.status == TaskStatus.RUNNING) {
                    long elapsedMs = System.currentTimeMillis() - startTime;
                    long secs = (elapsedMs / 1000) % 60;
                    long mins = (elapsedMs / (1000 * 60)) % 60;
                    long tenths = (elapsedMs % 1000) / 100;
                    elapsedTimeTv.setText(context.getString(R.string.elapsed_time_format, mins, secs, tenths));
                    elapsedTimeTv.setVisibility(View.VISIBLE);
                }
            }
        };

        taskViewModel.transcriptionText.observe(lifecycleOwner, textObserver);
        taskViewModel.isProgressVisible.observe(lifecycleOwner, progressObserver);
    }

    private void displayTaskResult(String taskKey,
                                  String rawResult,
                                  MainViewModel.TaskEntry restoredTaskEntry,
                                  MainViewModel mainViewModel,
                                  TaskActionListener actionListener) {
        if (rawResult == null || rawResult.isEmpty()) return;
        Context context = itemView.getContext();

        MainViewModel.TaskEntry currentTaskEntry = mainViewModel.getTaskEntry(taskKey);
        MainViewModel.TaskEntry taskEntry = currentTaskEntry != null ? currentTaskEntry : restoredTaskEntry;

        boolean initialSummaryExpanded = taskEntry == null || taskEntry.isSummaryExpanded;
        boolean initialTranscriptionExpanded = taskEntry == null || taskEntry.isTranscriptionExpanded;

        final boolean[] isSummaryExpanded = new boolean[]{initialSummaryExpanded};
        final boolean[] isTranscriptionExpanded = new boolean[]{initialTranscriptionExpanded};

        JSONObject json = null;
        try {
            json = new JSONObject(rawResult);
        } catch (JSONException ignored) {}

        boolean isCardExpanded = isSummaryExpanded[0] || isTranscriptionExpanded[0];
        resultContainer.setVisibility(isCardExpanded ? View.VISIBLE : View.GONE);
        maximizeBtn.setVisibility(View.VISIBLE);
        maximizeBtn.setText(isCardExpanded ? R.string.button_minimize_task : R.string.button_maximize_task);

        if (json != null && (json.has("Transcription") || json.has("Summary"))) {
            String transcription = json.optString("Transcription", "").trim();
            String summary = json.optString("Summary", "").trim();

            boolean hasContent = !transcription.isEmpty() || !summary.isEmpty();
            if (hasContent) {
                structuredContainer.setVisibility(View.VISIBLE);
                resultTv.setVisibility(View.GONE);

                if (!summary.isEmpty()) {
                    summaryContainer.setVisibility(View.VISIBLE);
                    summaryBody.setText(formatHtmlText(summary));
                    summaryBody.setVisibility(isSummaryExpanded[0] ? View.VISIBLE : View.GONE);
                    summaryChevron.setRotation(isSummaryExpanded[0] ? 0f : -90f);

                    summaryHeader.setOnClickListener(v -> {
                        isSummaryExpanded[0] = !isSummaryExpanded[0];
                        TransitionManager.beginDelayedTransition((ViewGroup) itemView);
                        summaryBody.setVisibility(isSummaryExpanded[0] ? View.VISIBLE : View.GONE);
                        summaryChevron.animate().rotation(isSummaryExpanded[0] ? 0f : -90f).setDuration(200).start();
                        mainViewModel.updateTaskExpandState(taskKey, isSummaryExpanded[0], isTranscriptionExpanded[0]);
                        actionListener.onToggleAllStateChanged();
                    });
                } else {
                    summaryContainer.setVisibility(View.GONE);
                }

                if (!transcription.isEmpty()) {
                    transcriptionContainer.setVisibility(View.VISIBLE);
                    transcriptionBody.setText(formatHtmlText(transcription));
                    transcriptionBody.setVisibility(isTranscriptionExpanded[0] ? View.VISIBLE : View.GONE);
                    transcriptionChevron.setRotation(isTranscriptionExpanded[0] ? 0f : -90f);

                    transcriptionHeader.setOnClickListener(v -> {
                        isTranscriptionExpanded[0] = !isTranscriptionExpanded[0];
                        TransitionManager.beginDelayedTransition((ViewGroup) itemView);
                        transcriptionBody.setVisibility(isTranscriptionExpanded[0] ? View.VISIBLE : View.GONE);
                        transcriptionChevron.animate().rotation(isTranscriptionExpanded[0] ? 0f : -90f).setDuration(200).start();
                        mainViewModel.updateTaskExpandState(taskKey, isSummaryExpanded[0], isTranscriptionExpanded[0]);
                        actionListener.onToggleAllStateChanged();
                    });
                } else {
                    transcriptionContainer.setVisibility(View.GONE);
                }
            } else {
                structuredContainer.setVisibility(View.GONE);
                resultTv.setVisibility(View.VISIBLE);
                resultTv.setText(formatHtmlText(rawResult));
            }
        } else {
            structuredContainer.setVisibility(View.GONE);
            resultTv.setVisibility(View.VISIBLE);
            resultTv.setText(formatHtmlText(rawResult));
        }

        View.OnLongClickListener copyListener = v -> {
            ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
            if (clipboard != null) {
                String copyText = rawResult;
                if (v instanceof TextView) {
                    CharSequence text = ((TextView) v).getText();
                    if (text != null && text.length() > 0) {
                        copyText = text.toString();
                    }
                }
                ClipData clip = ClipData.newPlainText("Transcription", copyText);
                clipboard.setPrimaryClip(clip);
                actionListener.onShowSnackbar(context.getString(R.string.copied_to_clipboard), null, null);
            }
            return true;
        };

        summaryBody.setOnLongClickListener(copyListener);
        transcriptionBody.setOnLongClickListener(copyListener);
        resultTv.setOnLongClickListener(copyListener);

        final JSONObject finalJson = json;
        maximizeBtn.setOnClickListener(v -> {
            boolean isCurrentlyExpanded = isSummaryExpanded[0] || isTranscriptionExpanded[0];
            boolean newExpandState = !isCurrentlyExpanded;
            isSummaryExpanded[0] = newExpandState;
            isTranscriptionExpanded[0] = newExpandState;

            TransitionManager.beginDelayedTransition((ViewGroup) itemView);
            resultContainer.setVisibility(newExpandState ? View.VISIBLE : View.GONE);
            maximizeBtn.setText(newExpandState ? R.string.button_minimize_task : R.string.button_maximize_task);

            if (finalJson != null && (finalJson.has("Transcription") || finalJson.has("Summary"))) {
                String transcription = finalJson.optString("Transcription", "").trim();
                String summary = finalJson.optString("Summary", "").trim();
                if (!summary.isEmpty()) {
                    summaryBody.setVisibility(newExpandState ? View.VISIBLE : View.GONE);
                    summaryChevron.setRotation(newExpandState ? 0f : -90f);
                }
                if (!transcription.isEmpty()) {
                    transcriptionBody.setVisibility(newExpandState ? View.VISIBLE : View.GONE);
                    transcriptionChevron.setRotation(newExpandState ? 0f : -90f);
                }
            }

            mainViewModel.updateTaskExpandState(taskKey, newExpandState, newExpandState);
            actionListener.onToggleAllStateChanged();
        });

        maximizeBtn.setOnLongClickListener(v -> {
            showTaskCardContextMenu(v, taskKey, actionListener);
            return true;
        });

        actionListener.onToggleAllStateChanged();
    }

    private void showTaskCardContextMenu(View anchorView, String taskKey, TaskActionListener actionListener) {
        Context context = itemView.getContext();
        PopupMenu popup = new PopupMenu(context, anchorView);
        popup.getMenu().add(0, 1, 0, context.getString(R.string.minimize_all_tasks));
        popup.getMenu().add(0, 2, 1, context.getString(R.string.expand_all_tasks));
        popup.getMenu().add(0, 3, 2, context.getString(R.string.minimize_other_tasks));

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                actionListener.onSetAllTasksMinimized(true);
                return true;
            } else if (id == 2) {
                actionListener.onSetAllTasksMinimized(false);
                return true;
            } else if (id == 3) {
                actionListener.onMinimizeOtherTasks(taskKey);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void applyProgressUI(Context context, String text, boolean isProgressVisible) {
        if (text == null) return;
        if (text.startsWith("MODEL_SELECTED:")) {
            String modelId = text.substring("MODEL_SELECTED:".length());
            String displayName = ModelUtils.formatModelDisplayName(context, modelId);
            statusTv.setText(context.getString(R.string.model_subtitle, displayName));
        } else if (isProgressVisible) {
            if (text.endsWith("%")) {
                try {
                    String numericPart = text.replace("%", "").trim();
                    Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(numericPart);
                    if (matcher.find()) {
                        String matchGroup = matcher.group(1);
                        if (matchGroup != null) {
                            double val = Double.parseDouble(matchGroup);
                            int percent = Math.min(100, Math.max(0, (int) Math.round(val)));
                            percentTv.setText(percent + "%");
                            percentTv.setVisibility(View.VISIBLE);
                            if (progressBar.isIndeterminate()) {
                                progressBar.setIndeterminate(false);
                            }
                            progressBar.setProgress(percent, true);
                            statusTv.setText(R.string.transcribing_progress);
                        } else {
                            statusTv.setText(text);
                        }
                    } else {
                        statusTv.setText(text);
                    }
                } catch (Exception e) {
                    statusTv.setText(text);
                }
            } else {
                statusTv.setText(text);
                percentTv.setVisibility(View.GONE);
                if (!progressBar.isIndeterminate()) {
                    progressBar.setIndeterminate(true);
                }
            }
        } else if (isErrorOrCancelledMessage(context, text)) {
            statusTv.setText(text);
        }
    }

    private boolean isErrorOrCancelledMessage(Context context, String text) {
        if (text == null) return false;
        return text.startsWith("Error")
                || text.startsWith(context.getString(R.string.error_transcription_failed))
                || text.contains(context.getString(R.string.error_low_memory))
                || text.equals(context.getString(R.string.transcription_cancelled))
                || text.startsWith("Failed to");
    }

    private CharSequence formatHtmlText(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        if (hasHtmlTags(input)) {
            return trimTrailingNewlines(HtmlCompat.fromHtml(input, HtmlCompat.FROM_HTML_MODE_LEGACY));
        }
        return input;
    }

    private boolean hasHtmlTags(String text) {
        if (text == null || !text.contains("<")) return false;
        return Pattern.compile("<[a-zA-Z/][^>]*>").matcher(text).find();
    }

    private CharSequence trimTrailingNewlines(CharSequence text) {
        if (text == null) return "";
        int len = text.length();
        while (len > 0 && Character.isWhitespace(text.charAt(len - 1))) {
            len--;
        }
        return text.subSequence(0, len);
    }

    private String resolveTaskModelName(MainViewModel mainViewModel, MainViewModel.TaskEntry entry, String taskModelFallback) {
        if (entry != null && entry.modelName != null && !entry.modelName.isEmpty() && !"auto".equals(entry.modelName)) {
            return entry.modelName;
        }
        if (taskModelFallback != null && !taskModelFallback.isEmpty() && !"auto".equals(taskModelFallback)) {
            return taskModelFallback;
        }
        return Boolean.TRUE.equals(mainViewModel.isGeminiSelected.getValue())
                ? mainViewModel.getGeminiModelName()
                : mainViewModel.currentModelName.getValue();
    }
}
