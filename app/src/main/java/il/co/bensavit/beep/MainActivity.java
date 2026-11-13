package il.co.bensavit.beep;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.TransitionManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import il.co.bensavit.beep.databinding.ActivityMainBinding;
import il.co.bensavit.beep.ui.HistoryFilePicker;
import il.co.bensavit.beep.ui.ModelPickerAndDownloader;
import il.co.bensavit.beep.ui.anim.BackgroundAnimator;
import il.co.bensavit.beep.ui.anim.LightTextureDrawable;
import il.co.bensavit.beep.ui.anim.ShimmerDrawable;
import il.co.bensavit.beep.ui.dialogs.AboutDialogFragment;
import il.co.bensavit.beep.ui.dialogs.AudioRecordDialogFragment;
import il.co.bensavit.beep.ui.dialogs.ChooseGeminiModelDialogFragment;
import il.co.bensavit.beep.ui.dialogs.DownloadProgressDialogFragment;
import il.co.bensavit.beep.ui.dialogs.DriveWebDownloadDialogFragment;
import il.co.bensavit.beep.ui.dialogs.GeminiConfigDialogFragment;
import il.co.bensavit.beep.ui.dialogs.LogWindowDialogFragment;
import il.co.bensavit.beep.utils.FileUtils;
import il.co.bensavit.beep.utils.InAppUpdateManager;
import il.co.bensavit.beep.utils.Logger;
import il.co.bensavit.beep.utils.ModelUtils;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetSequence;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private InAppUpdateManager inAppUpdateManager;

    private static final String TAG = "MainActivity";

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showAudioRecordDialog();
                } else {
                    outputMsg(getString(R.string.permission_record_audio_required));
                }
            });

    private final ActivityResultLauncher<IntentSenderRequest> updateActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() != RESULT_OK) {
                    Logger.e(TAG, "In-app update flow failed or cancelled. Result code: " + result.getResultCode());
                }
            });

    private final BackgroundAnimator backgroundAnimator = new BackgroundAnimator();
    private ShimmerDrawable cardShimmer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        inAppUpdateManager = new InAppUpdateManager(this, updateActivityResultLauncher);
        inAppUpdateManager.checkForUpdates(false);

        setupObservers();
        setupFragmentResultListeners();
        restoreExistingTasks();

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_mode_toggle) {
                toggleMode();
                return true;
            } else if (item.getItemId() == R.id.action_select_language) {
                showLanguagePickerDialog();
                return true;
            } else if (item.getItemId() == R.id.action_download_model) {
                onDownloadModelClick(null);
                return true;
            } else if (item.getItemId() == R.id.action_view_logs) {
                showLogWindowDialog();
                return true;
            } else if (item.getItemId() == R.id.action_about) {
                new AboutDialogFragment().show(getSupportFragmentManager(), "About");
                return true;
            } else if (item.getItemId() == R.id.action_configure_gemini) {
                new GeminiConfigDialogFragment().show(getSupportFragmentManager(), "GeminiConfig");
                return true;
            } else if (item.getItemId() == R.id.action_choose_gemini_model) {
                new ChooseGeminiModelDialogFragment().show(getSupportFragmentManager(), "ChooseGeminiModel");
                return true;
            } else if (item.getItemId() == R.id.action_record_audio) {
                onRecordAudioClick();
                return true;
            } else if (item.getItemId() == R.id.action_gemini_help) {
                Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtu.be/6BRyynZkvf0?si=IWlTsfdl0J0GzbKP"));
                startActivity(intent);
                return true;
            } else if (item.getItemId() == R.id.action_start_tutorial) {
                viewModel.setTutorialShown(false);
                showTutorialPrompt();
                return true;
            }
            return false;
        });

        binding.toolbar.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue())) {
                if (viewModel.getGeminiApiKey().trim().isEmpty()) {
                    new GeminiConfigDialogFragment().show(getSupportFragmentManager(), "GeminiConfig");
                }
            } else {
                if (!Boolean.TRUE.equals(TranscriptionTaskViewModel.isTranscriptionRunning.getValue())) {
                    onDownloadModelClick(null);
                }
            }
        });

        binding.buttonHistory.setOnClickListener(this::onHistoryClick);
        binding.buttonClear.setOnClickListener(this::onClearClick);
        binding.buttonClear.setOnLongClickListener(this::onClearLongClick);

        // Attach Shimmer Reflection light sweep animation to UI elements
        //ShimmerDrawable.attachTo(binding.buttonHistory);
        //ShimmerDrawable.attachTo(binding.buttonClear);
        cardShimmer = ShimmerDrawable.attachTo(binding.outputCard, BackgroundAnimator.getModeAccentColor(viewModel.isGeminiSelected.getValue()));
        binding.toolbar.setOnLongClickListener(v -> {
            viewModel.setTutorialShown(false);
            showSnackbar("Tutorial state reset!");
            showTutorialPrompt();
            return true;
        });
        updateModeMenuTitle(viewModel.isGeminiSelected.getValue());

        setupToolbarSwipeGestures();

        backgroundAnimator.start(binding.getRoot(), viewModel.isGeminiSelected.getValue());

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }


    }

    private void showSnackbar(String message) {
        showSnackbar(message, null, null);
    }

    private void showSnackbar(String message, String actionLabel, View.OnClickListener actionListener) {
        if (binding == null) return;
        com.google.android.material.snackbar.Snackbar snackbar =
                com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
                        HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
        snackbar.setTextMaxLines(5);
        if (actionLabel != null && actionListener != null) {
            snackbar.setAction(actionLabel, actionListener);
        }
        snackbar.show();
    }

    private void setMode(boolean isNowGemini) {
        boolean currentlyGemini = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
        if (currentlyGemini == isNowGemini) {
            return;
        }
        viewModel.setGeminiSelected(isNowGemini);
        if (isNowGemini) {
            if (viewModel.getGeminiApiKey().trim().isEmpty()) {
                showSnackbar(getString(R.string.gemini_key_required), getString(R.string.action_configure_gemini),
                        v -> new GeminiConfigDialogFragment().show(getSupportFragmentManager(), "GeminiConfig"));
            } else {
                showSnackbar(getString(R.string.switched_to_online_mode));
            }
        } else {
            if (!il.co.bensavit.beep.single.LocalWhisper.INSTANCE.isInitialized()) {
                showSnackbar(getString(R.string.model_not_loaded_message), getString(R.string.button_load_model),
                        v -> onDownloadModelClick(null));
            } else {
                String currentModel = viewModel.currentModelName.getValue();
                if (ModelUtils.isTinyModel(currentModel)) {
                    showSnackbar(getString(R.string.warning_tiny_model_testing_only));
                } else {
                    showSnackbar(getString(R.string.switched_to_offline_mode));
                }
            }
        }
    }

    private void toggleMode() {
        boolean currentlyGemini = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
        setMode(!currentlyGemini);
    }

    private void setupToolbarSwipeGestures() {
        android.view.GestureDetector gestureDetector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    private static final int SWIPE_THRESHOLD = 52;
                    private static final int SWIPE_VELOCITY_THRESHOLD = 52;

                    @Override
                    public boolean onDown(@androidx.annotation.NonNull android.view.MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(@androidx.annotation.NonNull android.view.MotionEvent e) {
                        binding.toolbar.performClick();
                        return true;
                    }

                    @Override
                    public void onLongPress(@androidx.annotation.NonNull android.view.MotionEvent e) {
                        binding.toolbar.performLongClick();
                    }

                    @Override
                    public boolean onFling(android.view.MotionEvent e1, @androidx.annotation.NonNull android.view.MotionEvent e2, float velocityX, float velocityY) {
                        if (e1 == null) return false;
                        float diffX = e2.getX() - e1.getX();
                        float diffY = e2.getY() - e1.getY();
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD ||
                                Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            toggleMode();
                            return true;
                        }
                        return false;
                    }
                });

        binding.toolbar.setOnTouchListener((v, event) -> {
            boolean handled = gestureDetector.onTouchEvent(event);
            if (event.getAction() == android.view.MotionEvent.ACTION_UP && !handled) {
                v.performClick();
            }
            return handled;
        });
    }

    private void updateModeMenuTitle(Boolean isGemini) {
        if (isGemini == null) return;
        MenuItem modeItem = binding.toolbar.getMenu().findItem(R.id.action_mode_toggle);
        if (modeItem != null) {
            String modeName = getString(isGemini ? R.string.mode_online : R.string.mode_offline);
            modeItem.setTitle(getString(R.string.mode_toggle_format, modeName));
        }
    }

    private void setupObservers() {
        viewModel.userMessage.observe(this, userMsg -> {
            if (userMsg != null) {
                showSnackbar(userMsg.message, userMsg.actionLabel,
                        userMsg.actionRunnable != null ? v -> userMsg.actionRunnable.run() : null);
                viewModel.clearUserMessage();
            }
        });

        viewModel.isProgressVisible.observe(this, visible ->
                binding.progressBar.setVisibility(visible ? View.VISIBLE : View.GONE));

        TranscriptionTaskViewModel.isTranscriptionRunning.observe(this, running -> {
            if (Boolean.TRUE.equals(running)) {
                backgroundAnimator.pause(binding.getRoot(), viewModel.isGeminiSelected.getValue());
            } else {
                backgroundAnimator.start(binding.getRoot(), viewModel.isGeminiSelected.getValue());
            }
        });

        TranscriptionTaskViewModel.activeTaskCount.observe(this, count -> updateModelSubtitle());

        viewModel.isGeminiSelected.observe(this, selected -> {
            if (Boolean.TRUE.equals(TranscriptionTaskViewModel.isAnyTranscriptionRunning())) {
                backgroundAnimator.pause(binding.getRoot(), selected);
            } else {
                backgroundAnimator.start(binding.getRoot(), selected);
            }
            if (cardShimmer != null) {
                cardShimmer.setHighlightColor(BackgroundAnimator.getModeAccentColor(selected));
            }
            updateModeMenuTitle(selected);
            updateModelSubtitle();
        });

        viewModel.currentModelName.observe(this, this::updateModelSubtitle);

        viewModel.onFileReadyForTranscription.observe(this, file -> {
            if (file != null) {
                addTranscriptionTask(file);
                viewModel.clearOnFileReadyForTranscription();
            }
        });

        viewModel.downloadingModelName.observe(this, modelName -> {
            if (modelName != null) {
                if (getSupportFragmentManager().findFragmentByTag("DriveWebDownload") != null) {
                    return;
                }
                if (getSupportFragmentManager().findFragmentByTag("DownloadProgress") == null) {
                    DownloadProgressDialogFragment.newInstance(modelName).show(getSupportFragmentManager(), "DownloadProgress");
                }
            } else {
                androidx.fragment.app.DialogFragment fragment = (androidx.fragment.app.DialogFragment) getSupportFragmentManager().findFragmentByTag("DownloadProgress");
                if (fragment != null) fragment.dismissAllowingStateLoss();
            }
        });

        viewModel.driveConfirmationRequest.observe(this, request -> {
            if (request != null) {
                androidx.fragment.app.DialogFragment progressFrag = (androidx.fragment.app.DialogFragment) getSupportFragmentManager().findFragmentByTag("DownloadProgress");
                if (progressFrag != null) {
                    progressFrag.dismissAllowingStateLoss();
                }
                if (getSupportFragmentManager().findFragmentByTag("DriveWebDownload") == null) {
                    DriveWebDownloadDialogFragment.newInstance(request.modelName, request.driveUrl)
                            .show(getSupportFragmentManager(), "DriveWebDownload");
                }
            } else {
                androidx.fragment.app.DialogFragment fragment = (androidx.fragment.app.DialogFragment) getSupportFragmentManager().findFragmentByTag("DriveWebDownload");
                if (fragment != null) fragment.dismissAllowingStateLoss();
            }
        });

        viewModel.showLogWindow.observe(this, show -> {
            if (Objects.equals(show, true)) {
                showLogWindowDialog();
                viewModel.onLogWindowShown();
            }
        });

        TranscriptionTaskViewModel.isTranscriptionRunning.observe(this, isRunning -> {
            boolean running = Boolean.TRUE.equals(isRunning);
            MenuItem modelItem = binding.toolbar.getMenu().findItem(R.id.action_download_model);
            if (modelItem != null) {
                modelItem.setEnabled(!running);
                if (modelItem.getIcon() != null) {
                    modelItem.getIcon().setAlpha(running ? 128 : 255);
                }
            }
            MenuItem modeItem = binding.toolbar.getMenu().findItem(R.id.action_mode_toggle);
            if (modeItem != null) {
                modeItem.setEnabled(!running);
            }
        });
    }

    private void setupFragmentResultListeners() {
        getSupportFragmentManager().setFragmentResultListener(HistoryFilePicker.REQUEST_KEY, this, (requestKey, bundle) -> {
            String filePath = bundle.getString(HistoryFilePicker.BUNDLE_KEY_FILE_PATH);
            if (filePath != null) {
                if (isModelMissing()) {
                    return;
                }
                addTranscriptionTask(new java.io.File(filePath));
            }
        });
    }

    private void restoreExistingTasks() {
        for (MainViewModel.TaskEntry taskEntry : viewModel.getActiveTaskEntries()) {
            addTranscriptionTask(taskEntry.file, taskEntry);
        }
    }

    private void addTranscriptionTask(java.io.File file) {
        addTranscriptionTask(file, null);
    }

    private void addTranscriptionTask(java.io.File file, MainViewModel.TaskEntry restoredTaskEntry) {
        final java.io.File[] currentFile = new java.io.File[]{file};
        final String[] currentTaskModel = new String[]{ restoredTaskEntry != null ? restoredTaskEntry.modelName : null };
        View taskView = getLayoutInflater().inflate(R.layout.item_transcription_task, binding.transcriptionContainer, false);
        binding.transcriptionContainer.addView(taskView);
        View taskCard = taskView.findViewById(R.id.task_card);
        LightTextureDrawable.attachTo(taskCard);
        ShimmerDrawable.attachTo(taskCard);
        binding.scrollView.post(() -> binding.scrollView.fullScroll(View.FOCUS_DOWN));

        android.widget.TextView fileNameTv = taskView.findViewById(R.id.text_filename);
        android.widget.TextView elapsedTimeTv = taskView.findViewById(R.id.text_elapsed_time);
        android.widget.ProgressBar progressBar = taskView.findViewById(R.id.task_progress_bar);
        android.widget.TextView statusTv = taskView.findViewById(R.id.text_status);

        fileNameTv.setText(currentFile[0].getName());

        long startTime = System.currentTimeMillis();
        boolean isNewTask = (restoredTaskEntry == null);

        // Create or retrieve a dedicated ViewModel for this task
        final String taskKey = isNewTask ? currentFile[0].getAbsolutePath() + "_" + System.currentTimeMillis() : restoredTaskEntry.taskKey;
        if (isNewTask) {
            viewModel.addTaskEntry(currentFile[0], taskKey);
        }

        TranscriptionTaskViewModel taskViewModel = new ViewModelProvider(viewModel.getTaskViewModelStore(),
                new ViewModelProvider.AndroidViewModelFactory(getApplication()))
                .get(taskKey, TranscriptionTaskViewModel.class);
        taskViewModel.initTask(taskKey, viewModel);

        if (restoredTaskEntry != null && restoredTaskEntry.status != MainViewModel.TaskEntry.Status.RUNNING) {
            // Task completed or stopped previously
            progressBar.setVisibility(View.GONE);
            if (restoredTaskEntry.elapsedTimeMs > 0) {
                long elapsedMs = restoredTaskEntry.elapsedTimeMs;
                long secs = (elapsedMs / 1000) % 60;
                long mins = (elapsedMs / (1000 * 60)) % 60;
                long tenths = (elapsedMs % 1000) / 100;
                elapsedTimeTv.setText(getString(R.string.elapsed_time_format, mins, secs, tenths));
                elapsedTimeTv.setVisibility(View.VISIBLE);
            }

            if (restoredTaskEntry.status == MainViewModel.TaskEntry.Status.SUCCEEDED && restoredTaskEntry.resultText != null) {
                String modelName = restoredTaskEntry.modelName;
                if (modelName == null || modelName.isEmpty()) {
                    modelName = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue())
                            ? viewModel.getGeminiModelName()
                            : viewModel.currentModelName.getValue();
                }
                String displayName = ModelUtils.formatModelDisplayName(this, modelName);
                statusTv.setText(getString(R.string.transcription_complete, displayName));
                displayTaskResult(taskView, taskKey, restoredTaskEntry.resultText, restoredTaskEntry);
                taskViewModel.setRestoredResult(restoredTaskEntry.resultText, false);
            } else if (restoredTaskEntry.resultText != null) {
                statusTv.setText(restoredTaskEntry.resultText);
                taskViewModel.setRestoredResult(restoredTaskEntry.resultText, false);
            }
        } else {
            progressBar.setVisibility(View.VISIBLE);
            statusTv.setText(R.string.transcription_starting);
            if (restoredTaskEntry != null && restoredTaskEntry.workId != null) {
                taskViewModel.observeExistingWork(restoredTaskEntry.workId);
            } else if (restoredTaskEntry != null) {
                boolean isGemini = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
                String selectedLang = viewModel.language.getValue();
                String language = (selectedLang != null) ? selectedLang : "auto";
                taskViewModel.runTranscription(currentFile[0], language, isGemini);
            }
        }

        taskView.setOnLongClickListener(v -> {
            boolean isRunning = Boolean.TRUE.equals(taskViewModel.isProgressVisible.getValue());
            int titleRes = isRunning ? R.string.dialog_cancel_and_remove_task_title : R.string.dialog_remove_task_title;
            int messageRes = isRunning ? R.string.dialog_cancel_and_remove_task_message : R.string.dialog_remove_task_message;

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle(titleRes)
                    .setMessage(getString(messageRes, currentFile[0].getName()))
                    .setPositiveButton(R.string.remove, (dialog, which) -> {
                        taskViewModel.stopTranscription();
                        viewModel.removeTaskEntry(taskKey);
                        binding.transcriptionContainer.removeView(taskView);
                    });

            if (!TranscriptionTaskViewModel.isAnyTranscriptionRunning()) {
                builder.setNeutralButton(R.string.rename, (dialog, which) -> {
                    FileUtils.showRenameDialog(this, currentFile[0], newFile -> {
                        currentFile[0] = newFile;
                        fileNameTv.setText(newFile.getName());
                        viewModel.updateTaskEntryFile(taskKey, newFile);
                    });
                });
            }

            builder.setNegativeButton(R.string.button_cancel, null)
                    .show();
            return true;
        });

        taskViewModel.transcriptionText.observe(this, text -> {
            if (text != null) {
                if (text.startsWith("MODEL_SELECTED:")) {
                    String modelId = text.substring("MODEL_SELECTED:".length());
                    currentTaskModel[0] = modelId;
                    viewModel.updateTaskModelName(taskKey, modelId);
                    String displayName = ModelUtils.formatModelDisplayName(this, modelId);
                    statusTv.setText(getString(R.string.model_subtitle, displayName));
                } else if (Boolean.TRUE.equals(taskViewModel.isProgressVisible.getValue())) {
                    statusTv.setText(text);
                } else if (isErrorOrCancelledMessage(text)) {
                    statusTv.setText(text);
                    if (text.contains(getString(R.string.error_low_memory))) {
                        showSnackbar(text, getString(R.string.mode_online), v -> {
                            viewModel.setGeminiSelected(true);
                        });
                    }
                } else if (!text.equals(getString(R.string.transcription_starting))) {
                    if (restoredTaskEntry != null && restoredTaskEntry.status != MainViewModel.TaskEntry.Status.RUNNING
                            && Objects.equals(text, restoredTaskEntry.resultText)) {
                        return;
                    }
                    MainViewModel.TaskEntry entry = viewModel.getTaskEntry(taskKey);
                    String modelName = (entry != null && entry.modelName != null && !entry.modelName.isEmpty())
                            ? entry.modelName
                            : currentTaskModel[0];
                    if (modelName == null || modelName.isEmpty()) {
                        modelName = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue())
                                ? viewModel.getGeminiModelName()
                                : viewModel.currentModelName.getValue();
                    }
                    String displayName = ModelUtils.formatModelDisplayName(this, modelName);
                    statusTv.setText(getString(R.string.transcription_complete, displayName));
                    displayTaskResult(taskView, taskKey, text, restoredTaskEntry);
                }
            }
        });

        taskViewModel.isProgressVisible.observe(this, visible -> {
            progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (!visible) {
                if (restoredTaskEntry == null || restoredTaskEntry.status == MainViewModel.TaskEntry.Status.RUNNING) {
                    long elapsedMs = System.currentTimeMillis() - startTime;
                    long secs = (elapsedMs / 1000) % 60;
                    long mins = (elapsedMs / (1000 * 60)) % 60;
                    long tenths = (elapsedMs % 1000) / 100;
                    elapsedTimeTv.setText(getString(R.string.elapsed_time_format, mins, secs, tenths));
                    elapsedTimeTv.setVisibility(View.VISIBLE);
                }
            }
        });

        if (isNewTask) {
            boolean isGemini = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
            String selectedLang = viewModel.language.getValue();
            String language = (selectedLang != null) ? selectedLang : "auto";
            taskViewModel.runTranscription(currentFile[0], language, isGemini);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            Logger.d(TAG, "handleIntent: intent is null");
            return;
        }
        String action = intent.getAction();
        String type = intent.getType();
        Logger.d(TAG, "handleIntent: action=" + action + ", type=" + type);

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            android.net.Uri uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri.class);
            Logger.d(TAG, "handleIntent: ACTION_SEND uri=" + uri);
            if (uri != null) {
                handleSharedUri(uri);
            } else {
                Logger.w(TAG, "handleIntent: EXTRA_STREAM is null");
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            java.util.ArrayList<android.net.Uri> uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, android.net.Uri.class);
            Logger.d(TAG, "handleIntent: ACTION_SEND_MULTIPLE uris=" + uris);
            if (uris != null) {
                for (android.net.Uri uri : uris) {
                    handleSharedUri(uri);
                }
            }
        } else if (Intent.ACTION_MAIN.equals(action)) {
            binding.toolbar.post(this::showTutorialPrompt); //showTutorialPrompt();
        }
    }

    private void showTutorialPrompt() {
        if (isFinishing() || isDestroyed()) return;

        if (viewModel.isTutorialShown()) {
            return;
        }

        if (binding.outputCard.getWidth() <= 0) {
            binding.toolbar.postDelayed(this::showTutorialPrompt, 200);
            return;
        }

        MaterialTapTargetSequence sequence = new MaterialTapTargetSequence();

        // 1. Welcome & How to use (WhatsApp focus)
        sequence.addPrompt(new MaterialTapTargetPrompt.Builder(MainActivity.this)
                .setTarget(binding.outputCard)
                .setPrimaryText("Welcome to Beep!")
                .setSecondaryText("To transcribe:\n1. Long-press a WhatsApp audio message.\n2. Tap 'Share' and select 'Beep'.")
                .create());

        // 2. Mode Toggle
        sequence.addPrompt(new MaterialTapTargetPrompt.Builder(MainActivity.this)
                .setTarget(binding.toolbar)
                .setPrimaryText("Offline / Online Mode")
                .setSecondaryText("Swipe right for Online (Gemini) or left for Offline (Whisper) on the menu bar, or switch in the menu.")
                .create());

        // 3. Model Selection
        sequence.addPrompt(new MaterialTapTargetPrompt.Builder(MainActivity.this)
                .setTarget(binding.toolbar)
                .setPrimaryText(getString(R.string.tutorial_model_title))
                .setSecondaryText(getString(R.string.tutorial_model_desc))
                .create());

        // 4. Audio Language Selection
        sequence.addPrompt(new MaterialTapTargetPrompt.Builder(MainActivity.this)
                .setTarget(binding.toolbar)
                .setPrimaryText(getString(R.string.tutorial_language_title))
                .setSecondaryText(getString(R.string.tutorial_language_desc))
                .create());

        // 5. History
        sequence.addPrompt(new MaterialTapTargetPrompt.Builder(MainActivity.this)
                .setTarget(binding.buttonHistory)
                .setPrimaryText("History")
                .setSecondaryText("View your past transcriptions or a sample message.")
                .create());

        // 6. Copying & Managing Tasks
        View taskTarget = binding.transcriptionContainer.getChildCount() > 0 ? binding.transcriptionContainer.getChildAt(0) : binding.transcriptionContainer;
        sequence.addPrompt(new MaterialTapTargetPrompt.Builder(MainActivity.this)
                .setTarget(taskTarget)
                .setPrimaryText(getString(R.string.tutorial_tasks_title))
                .setSecondaryText(getString(R.string.tutorial_tasks_desc))
                .create());

        // 7. Menu Options
        sequence.addPrompt(new MaterialTapTargetPrompt.Builder(MainActivity.this)
                .setTarget(binding.toolbar)
                .setPrimaryText("More Options")
                .setSecondaryText("Access 'Select Audio Language', 'Record Audio', 'Select / Download Model', 'Configure Gemini', 'View Logs', and 'About' from the menu.\n\nLong-press the menu bar anytime to bring back this tutorial.")
                .create());

        // 8. Clear
        sequence.addPrompt(new MaterialTapTargetPrompt.Builder(MainActivity.this)
                .setTarget(binding.buttonClear)
                .setPrimaryText("Clear")
                .setSecondaryText("Tap to clear tasks from screen. Long-press to also unload the model from memory.")
                .create());

        sequence.setSequenceCompleteListener(() -> {
            viewModel.setTutorialShown(true);
            showSnackbar("Tutorial completed!");
        });

        sequence.show();
    }

    private void handleSharedUri(android.net.Uri uri) {
        boolean isGemini = Objects.equals(viewModel.isGeminiSelected.getValue(), true);
        if (!isGemini && !il.co.bensavit.beep.single.LocalWhisper.INSTANCE.isInitialized()) {
            viewModel.handleSharedUri(uri, FileUtils.getFileName(this, uri), false);
            return;
        }
        if (isModelMissing()) {
            return;
        }
        viewModel.handleSharedUri(uri, FileUtils.getFileName(this, uri));
    }

    private void onRecordAudioClick() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showAudioRecordDialog();
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO);
        }
    }

    private void showAudioRecordDialog() {
        if (isModelMissing()) {
            return;
        }
        new AudioRecordDialogFragment().show(getSupportFragmentManager(), "AudioRecord");
    }

    private void showLogWindowDialog() {
        LogWindowDialogFragment dialog = new LogWindowDialogFragment();
        dialog.setOnLogClearedListener(() -> {
            if (binding != null) {
                showSnackbar(getString(R.string.logs_cleared));
            }
        });
        dialog.show(getSupportFragmentManager(), "Logs");
    }

    private void onDownloadModelClick(View view) {
        ModelPickerAndDownloader.show(this, viewModel, view, new ModelPickerAndDownloader.OnModelLoadingListener() {
            @Override
            public void onLoadingStarted() {
                if (view != null) view.setEnabled(false);
            }

            @Override
            public void onLoadingFinished() {
                if (view != null) view.setEnabled(true);
                updateModelSubtitle();
            }

            @Override
            public void onError(String message) {
                outputMsg(message);
            }
        });
    }

    private void updateModelSubtitle() {
        updateModelSubtitle(viewModel.currentModelName.getValue());
    }

    private void updateModelSubtitle(String rawModelName) {
        if (binding == null) return;

        boolean isOnline = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
        boolean isReady;
        String displayName;

        if (isOnline) {
            displayName = getString(R.string.mode_online);
            isReady = !viewModel.getGeminiApiKey().trim().isEmpty();
        } else {
            displayName = ModelUtils.formatModelDisplayName(this, rawModelName);
            isReady = il.co.bensavit.beep.single.LocalWhisper.INSTANCE.isInitialized();
        }

        String subtitleText = ModelUtils.formatModelSubtitle(this, displayName, isOnline, isReady);

        int activeTasks = TranscriptionTaskViewModel.getActiveTaskCount();
        if (activeTasks > 0) {
            binding.toolbar.setSubtitle(subtitleText + " • " + getString(R.string.background_tasks_running, activeTasks));
        } else {
            binding.toolbar.setSubtitle(subtitleText);
        }
    }

    private void outputMsg(String msg) {
        showSnackbar(msg);
    }

    private void onHistoryClick(View v) {
        showHistory();
    }

    private void showHistory() {
        HistoryFilePicker.newInstance(viewModel.getSharedFilesDir())
                .show(getSupportFragmentManager(), "MaterialFilePicker");
    }

    private boolean onClearLongClick(View v) {
        performClear(true);
        return true;
    }

    private void onClearClick(View v) {
        performClear(false);
    }

    private void performClear(boolean unloadNativeModel) {
        if (TranscriptionTaskViewModel.isAnyTranscriptionRunning()) {
            showSnackbar(getString(R.string.cannot_clear_running_tasks));
            return;
        }

        int titleRes = unloadNativeModel ? R.string.dialog_clear_all_title : R.string.dialog_clear_tasks_title;
        int messageRes = unloadNativeModel ? R.string.dialog_clear_all_message : R.string.dialog_clear_tasks_message;

        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(R.string.button_clear, (dialog, which) -> {
                    viewModel.clearAllResources(unloadNativeModel);
                    binding.transcriptionContainer.removeAllViews();
                    updateModelSubtitle();
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private boolean isModelMissing() {
        if (Objects.equals(viewModel.isGeminiSelected.getValue(), true)) {
            String apiKey = viewModel.getGeminiApiKey();
            if (apiKey.trim().isEmpty()) {
                showSnackbar(getString(R.string.gemini_key_required), getString(R.string.action_configure_gemini),
                        v -> new GeminiConfigDialogFragment().show(getSupportFragmentManager(), "GeminiConfig"));
                new GeminiConfigDialogFragment().show(getSupportFragmentManager(), "GeminiConfig");
                return true;
            }
        } else {
            if (!il.co.bensavit.beep.single.LocalWhisper.INSTANCE.isInitialized()) {
                showSnackbar(getString(R.string.model_not_loaded_message), getString(R.string.button_load_model),
                        v -> onDownloadModelClick(null));
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.model_not_loaded_title)
                        .setMessage(R.string.model_not_loaded_message)
                        .setPositiveButton(R.string.button_load_model, (dialog, which) -> onDownloadModelClick(null))
                        .setNegativeButton(R.string.button_cancel, null)
                        .show();
                return true;
            }
        }
        return false;
    }

    public void checkForUpdatesManually() {
        if (inAppUpdateManager != null) {
            inAppUpdateManager.checkForUpdates(true);
        }
    }

    private void showLanguagePickerDialog() {
        String[] languages = getResources().getStringArray(R.array.languages);
        if (languages.length > 0) {
            languages[0] = getString(R.string.default_language);
        }
        String[] languageCodes = getResources().getStringArray(R.array.language_codes);
        String currentCode = viewModel.language.getValue();
        if (currentCode == null || currentCode.trim().isEmpty()) {
            currentCode = "auto";
        }

        int selectedIndex = 0;
        for (int i = 0; i < languageCodes.length; i++) {
            if (languageCodes[i].equalsIgnoreCase(currentCode)) {
                selectedIndex = i;
                break;
            }
        }

        String currentLanguageName = (selectedIndex == 0 || selectedIndex >= languages.length) 
                ? getString(R.string.default_language) 
                : languages[selectedIndex];
        String dialogTitle = getString(R.string.language_selector_title_format, currentLanguageName);

        new MaterialAlertDialogBuilder(this)
                .setTitle(dialogTitle)
                .setSingleChoiceItems(languages, selectedIndex, (dialog, which) -> {
                    if (which >= 0 && which < languageCodes.length && which < languages.length) {
                        String selectedCode = languageCodes[which];

                        viewModel.setLanguage(selectedCode);

                        String currentModel = viewModel.currentModelName.getValue();
                        boolean isGemini = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
                        if (!isGemini && ModelUtils.isLanguageConflicting(MainActivity.this, currentModel, selectedCode)) {
                            String modelDisplayName = ModelUtils.formatModelDisplayName(MainActivity.this, currentModel);
                            String errorMessage = getString(R.string.error_language_model_conflict, modelDisplayName);
                            showSnackbar(errorMessage);
                        } else {
                            showSnackbar(getString(R.string.language_selector_hint) + ": " + languages[which]);
                        }
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private boolean isErrorOrCancelledMessage(String text) {
        if (text == null) return false;
        return text.startsWith("Error")
                || text.startsWith(getString(R.string.error_transcription_failed))
                || text.contains(getString(R.string.error_low_memory))
                || text.equals(getString(R.string.transcription_cancelled))
                || text.startsWith("Failed to");
    }

    private void displayTaskResult(View taskView, String taskKey, String rawResult, MainViewModel.TaskEntry restoredTaskEntry) {
        if (rawResult == null || rawResult.isEmpty()) return;

        View resultContainer = taskView.findViewById(R.id.container_result);
        View structuredContainer = taskView.findViewById(R.id.container_result_structured);
        View summaryContainer = taskView.findViewById(R.id.container_summary);
        View summaryHeader = taskView.findViewById(R.id.header_summary);
        ImageView summaryChevron = taskView.findViewById(R.id.icon_summary_chevron);
        TextView summaryBody = taskView.findViewById(R.id.text_summary_body);

        View transcriptionContainer = taskView.findViewById(R.id.container_transcription);
        View transcriptionHeader = taskView.findViewById(R.id.header_transcription);
        ImageView transcriptionChevron = taskView.findViewById(R.id.icon_transcription_chevron);
        TextView transcriptionBody = taskView.findViewById(R.id.text_transcription_body);

        TextView resultTv = taskView.findViewById(R.id.text_result);
        MaterialButton maximizeBtn = taskView.findViewById(R.id.button_maximize);

        summaryBody.setMovementMethod(LinkMovementMethod.getInstance());
        transcriptionBody.setMovementMethod(LinkMovementMethod.getInstance());
        resultTv.setMovementMethod(LinkMovementMethod.getInstance());

        MainViewModel.TaskEntry currentTaskEntry = viewModel.getTaskEntry(taskKey);
        MainViewModel.TaskEntry taskEntry = currentTaskEntry != null ? currentTaskEntry : restoredTaskEntry;

        boolean initialSummaryExpanded = taskEntry == null || taskEntry.isSummaryExpanded;
        boolean initialTranscriptionExpanded = taskEntry == null || taskEntry.isTranscriptionExpanded;

        final boolean[] isSummaryExpanded = new boolean[]{initialSummaryExpanded};
        final boolean[] isTranscriptionExpanded = new boolean[]{initialTranscriptionExpanded};

        JSONObject json = null;
        try {
            json = new JSONObject(rawResult);
        } catch (JSONException ignored) {}

        boolean isCollapsed = maximizeBtn.getVisibility() == View.VISIBLE && resultContainer.getVisibility() == View.GONE;
        resultContainer.setVisibility(isCollapsed ? View.GONE : View.VISIBLE);
        maximizeBtn.setVisibility(View.VISIBLE);
        maximizeBtn.setText(isCollapsed ? R.string.button_maximize_task : R.string.button_minimize_task);

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
                        TransitionManager.beginDelayedTransition((ViewGroup) taskView);
                        summaryBody.setVisibility(isSummaryExpanded[0] ? View.VISIBLE : View.GONE);
                        summaryChevron.animate().rotation(isSummaryExpanded[0] ? 0f : -90f).setDuration(200).start();
                        viewModel.updateTaskExpandState(taskKey, isSummaryExpanded[0], isTranscriptionExpanded[0]);
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
                        TransitionManager.beginDelayedTransition((ViewGroup) taskView);
                        transcriptionBody.setVisibility(isTranscriptionExpanded[0] ? View.VISIBLE : View.GONE);
                        transcriptionChevron.animate().rotation(isTranscriptionExpanded[0] ? 0f : -90f).setDuration(200).start();
                        viewModel.updateTaskExpandState(taskKey, isSummaryExpanded[0], isTranscriptionExpanded[0]);
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
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
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
                showSnackbar(getString(R.string.copied_to_clipboard));
            }
            return true;
        };

        summaryBody.setOnLongClickListener(copyListener);
        transcriptionBody.setOnLongClickListener(copyListener);
        resultTv.setOnLongClickListener(copyListener);

        maximizeBtn.setOnClickListener(v -> {
            boolean isVisible = resultContainer.getVisibility() == View.VISIBLE;
            TransitionManager.beginDelayedTransition((ViewGroup) taskView);
            resultContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            maximizeBtn.setText(isVisible ? R.string.button_maximize_task : R.string.button_minimize_task);
        });
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
        return java.util.regex.Pattern.compile("<[a-zA-Z/][^>]*>").matcher(text).find();
    }

    private CharSequence trimTrailingNewlines(CharSequence text) {
        if (text == null) return "";
        int len = text.length();
        while (len > 0 && Character.isWhitespace(text.charAt(len - 1))) {
            len--;
        }
        return text.subSequence(0, len);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inAppUpdateManager != null) {
            inAppUpdateManager.checkOnResume();
        }
        updateModelSubtitle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inAppUpdateManager != null) {
            inAppUpdateManager.unregister();
        }
        backgroundAnimator.stop();
        binding = null;
    }
}
