package il.co.bensavit.beep;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.TooltipCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetSequence;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import il.co.bensavit.beep.databinding.ActivityMainBinding;
import il.co.bensavit.beep.ui.HistoryFilePicker;
import il.co.bensavit.beep.ui.ModelPickerAndDownloader;
import il.co.bensavit.beep.ui.TutorialManager;
import il.co.bensavit.beep.ui.adapter.TaskActionListener;
import il.co.bensavit.beep.ui.adapter.TranscriptionTaskAdapter;
import il.co.bensavit.beep.ui.anim.BackgroundAnimator;
import il.co.bensavit.beep.ui.anim.ShimmerDrawable;
import il.co.bensavit.beep.ui.dialogs.AboutDialogFragment;
import il.co.bensavit.beep.ui.dialogs.AudioRecordDialogFragment;
import il.co.bensavit.beep.ui.dialogs.ChooseGeminiModelDialogFragment;
import il.co.bensavit.beep.ui.dialogs.DownloadProgressDialogFragment;
import il.co.bensavit.beep.ui.dialogs.DriveWebDownloadDialogFragment;
import il.co.bensavit.beep.ui.dialogs.GeminiConfigDialogFragment;
import il.co.bensavit.beep.ui.dialogs.LogWindowDialogFragment;
import il.co.bensavit.beep.ui.dialogs.TranscriptionOptionsDialogFragment;
import il.co.bensavit.beep.utils.FileUtils;
import il.co.bensavit.beep.utils.InAppUpdateManager;
import il.co.bensavit.beep.utils.Logger;
import il.co.bensavit.beep.utils.ModelUtils;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private InAppUpdateManager inAppUpdateManager;
    private TranscriptionTaskAdapter taskAdapter;
    private Snackbar activeSnackbar;
    private MaterialTapTargetSequence activeTutorialSequence;

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
    private final Runnable showTutorialRunnable = this::showTutorialPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        inAppUpdateManager = new InAppUpdateManager(this, updateActivityResultLauncher);
        inAppUpdateManager.checkForUpdates(false);

        setupRecyclerView();
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
            } else if (item.getItemId() == R.id.action_about) {
                new AboutDialogFragment().show(getSupportFragmentManager(), "About");
                return true;
            } else if (item.getItemId() == R.id.action_configure_gemini) {
                new GeminiConfigDialogFragment().show(getSupportFragmentManager(), "GeminiConfig");
                return true;
            } else if (item.getItemId() == R.id.action_choose_gemini_model) {
                new ChooseGeminiModelDialogFragment().show(getSupportFragmentManager(), "ChooseGeminiModel");
                return true;
            } else if (item.getItemId() == R.id.action_transcription_options) {
                new TranscriptionOptionsDialogFragment().show(getSupportFragmentManager(), "TranscriptionOptions");
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

        binding.buttonSortTasks.setOnClickListener(v -> {
            boolean nextSortOrder = !viewModel.isSortTasksNewestFirst();
            viewModel.setSortTasksNewestFirst(nextSortOrder);
            showSnackbar(getString(nextSortOrder ? R.string.sort_newest_first : R.string.sort_oldest_first));
        });

        binding.buttonToggleAllTasks.setOnClickListener(v -> {
            boolean shouldMinimize = !areAllTasksMinimized();
            setAllTasksMinimized(shouldMinimize);
        });

        binding.buttonToggleAllTasks.setOnLongClickListener(v -> {
            showTaskCardContextMenu(v, null);
            return true;
        });

        updateToggleAllButtonState();

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

    private void setupRecyclerView() {
        binding.recyclerViewTasks.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TranscriptionTaskAdapter(this, viewModel, new TaskActionListener() {
            @Override
            public void onTaskRemoveRequested(MainViewModel.TaskEntry taskEntry, TranscriptionTaskViewModel taskViewModel) {
                taskViewModel.stopTranscription();
                viewModel.removeTaskEntry(taskEntry.taskKey);
                updateTaskList();
            }

            @Override
            public void onFileRenameRequested(MainViewModel.TaskEntry taskEntry) {
                FileUtils.showRenameDialog(MainActivity.this, taskEntry.file, newFile -> {
                    viewModel.updateTaskEntryFile(taskEntry.taskKey, newFile);
                    updateTaskList();
                });
            }

            @Override
            public void onShowSnackbar(String message, String actionText, View.OnClickListener actionListener) {
                showSnackbar(message, actionText, actionListener);
            }

            @Override
            public void onToggleAllStateChanged() {
                updateToggleAllButtonState();
            }

            @Override
            public void onMinimizeOtherTasks(String keepTaskKey) {
                minimizeOtherTasks(keepTaskKey);
            }

            @Override
            public void onSetAllTasksMinimized(boolean minimize) {
                setAllTasksMinimized(minimize);
            }
        });
        binding.recyclerViewTasks.setAdapter(taskAdapter);
        setupTaskContainerGesture();
    }

    private void updateTaskList() {
        if (viewModel == null || taskAdapter == null) return;
        List<MainViewModel.TaskEntry> entries = new ArrayList<>(viewModel.getActiveTaskEntries());
        boolean newestFirst = viewModel.isSortTasksNewestFirst();
        Collections.sort(entries, (e1, e2) -> newestFirst ? Long.compare(e2.timestamp, e1.timestamp) : Long.compare(e1.timestamp, e2.timestamp));
        taskAdapter.submitList(entries, this::updateToggleAllButtonState);
    }

    private void showSnackbar(String message) {
        showSnackbar(message, null, null);
    }

    private void showSnackbar(String message, String actionLabel, View.OnClickListener actionListener) {
        if (binding == null) return;
        dismissActiveSnackbar();
        activeSnackbar = Snackbar.make(binding.getRoot(),
                        HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY),
                        Snackbar.LENGTH_LONG);
        activeSnackbar.setTextMaxLines(5);
        if (actionLabel != null && actionListener != null) {
            activeSnackbar.setAction(actionLabel, actionListener);
        }
        activeSnackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (activeSnackbar == transientBottomBar) {
                    activeSnackbar = null;
                }
            }
        });
        activeSnackbar.show();
    }

    private void dismissActiveSnackbar() {
        if (activeSnackbar != null) {
            activeSnackbar.dismiss();
            activeSnackbar = null;
        }
    }

    private void setMode(boolean isNowGemini) {
        if (Objects.equals(viewModel.isGeminiSelected.getValue(), isNowGemini)) {
            return;
        }

        viewModel.setGeminiSelected(isNowGemini);

        if (!isNowGemini && !il.co.bensavit.beep.single.LocalWhisper.INSTANCE.isInitialized()) {
            if (viewModel.currentModelName.getValue() == null || viewModel.currentModelName.getValue().trim().isEmpty()) {
                onDownloadModelClick(null);
            }
        }

        if (isNowGemini) {
            if (viewModel.getGeminiApiKey().trim().isEmpty()) {
                showSnackbar(getString(R.string.gemini_key_required), getString(R.string.action_configure_gemini),
                        v -> new GeminiConfigDialogFragment().show(getSupportFragmentManager(), "GeminiConfig"));
                new GeminiConfigDialogFragment().show(getSupportFragmentManager(), "GeminiConfig");
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
        boolean currentIsGemini = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
        setMode(!currentIsGemini);
    }

    private void updateModeMenuTitle(boolean isGemini) {
        if (binding == null) return;
        MenuItem modeItem = binding.toolbar.getMenu().findItem(R.id.action_mode_toggle);
        if (modeItem != null) {
            modeItem.setTitle(isGemini ? R.string.mode_offline : R.string.mode_online);
        }

        MenuItem selectLangItem = binding.toolbar.getMenu().findItem(R.id.action_select_language);
        if (selectLangItem != null) {
            selectLangItem.setVisible(!isGemini);
        }

        MenuItem chooseGeminiModelItem = binding.toolbar.getMenu().findItem(R.id.action_choose_gemini_model);
        if (chooseGeminiModelItem != null) {
            chooseGeminiModelItem.setVisible(isGemini);
        }

        MenuItem geminiHelpItem = binding.toolbar.getMenu().findItem(R.id.action_gemini_help);
        if (geminiHelpItem != null) {
            geminiHelpItem.setVisible(isGemini);
        }
    }

    private void setupToolbarSwipeGestures() {
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 50;
            private static final int SWIPE_VELOCITY_THRESHOLD = 50;

            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null) return false;
                if (Boolean.TRUE.equals(TranscriptionTaskViewModel.isTranscriptionRunning.getValue())) {
                    return false;
                }
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        boolean currentIsGemini = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
                        if (diffX < 0 && !currentIsGemini) {
                            setMode(true);
                            return true;
                        } else if (diffX > 0 && currentIsGemini) {
                            setMode(false);
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        binding.toolbar.setOnTouchListener((v, event) -> {
            boolean handled = gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && !handled) {
                v.performClick();
            }
            return handled;
        });
    }

    private void setupObservers() {
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

        viewModel.isSortTasksNewestFirst.observe(this, newestFirst -> {
            if (newestFirst != null) {
                updateTaskList();
            }
        });

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

        TranscriptionTaskViewModel.activeTaskCount.observe(this, count -> updateModelSubtitle());

        viewModel.userMessage.observe(this, userMessage -> {
            if (userMessage != null && userMessage.message != null && !userMessage.message.isEmpty() && binding != null) {
                if (userMessage.actionLabel != null && userMessage.actionRunnable != null) {
                    showSnackbar(userMessage.message, userMessage.actionLabel, v -> userMessage.actionRunnable.run());
                } else {
                    showSnackbar(userMessage.message);
                }
                binding.getRoot().post(() -> viewModel.clearUserMessage());
            }
        });

        viewModel.isProgressVisible.observe(this, isVisible -> {
            if (binding != null) {
                boolean show = Boolean.TRUE.equals(isVisible);
                binding.progressBar.animate().cancel();
                if (show) {
                    binding.progressBar.setAlpha(0f);
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.progressBar.animate().alpha(1f).setDuration(150).start();
                } else {
                    binding.progressBar.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                        }
                    }).start();
                }
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
                addTranscriptionTask(new File(filePath));
            }
        });
    }

    private void restoreExistingTasks() {
        updateTaskList();
    }

    private void addTranscriptionTask(File file) {
        long timestamp = System.currentTimeMillis();
        String taskKey = file.getAbsolutePath() + "_" + timestamp;
        viewModel.addTaskEntry(file, taskKey);
        updateTaskList();

        TranscriptionTaskViewModel taskViewModel = viewModel.getOrCreateTaskViewModel(taskKey);
        taskViewModel.initTask(taskKey, viewModel);

        boolean isGemini = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
        String selectedLang = viewModel.language.getValue();
        String language = (selectedLang != null) ? selectedLang : "auto";
        taskViewModel.runTranscription(file, language, isGemini);

        boolean newestFirst = viewModel == null || viewModel.isSortTasksNewestFirst();
        if (newestFirst) {
            binding.recyclerViewTasks.post(() -> binding.recyclerViewTasks.scrollToPosition(0));
        } else {
            binding.recyclerViewTasks.post(() -> binding.recyclerViewTasks.scrollToPosition(Math.max(0, taskAdapter.getItemCount() - 1)));
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
            ArrayList<android.net.Uri> uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, android.net.Uri.class);
            Logger.d(TAG, "handleIntent: ACTION_SEND_MULTIPLE uris=" + uris);
            if (uris != null) {
                for (android.net.Uri uri : uris) {
                    handleSharedUri(uri);
                }
            }
        } else if (Intent.ACTION_MAIN.equals(action)) {
            binding.toolbar.post(showTutorialRunnable);
        }
    }

    private void showTutorialPrompt() {
        dismissActiveSnackbar();
        if (activeTutorialSequence != null) {
            activeTutorialSequence.dismiss();
            activeTutorialSequence = null;
        }
        activeTutorialSequence = TutorialManager.showTutorialPrompt(
                this,
                binding,
                viewModel,
                showTutorialRunnable,
                new TutorialManager.TutorialListener() {
                    @Override
                    public void onTutorialCompleted() {
                        activeTutorialSequence = null;
                        showSnackbar(getString(R.string.tutorial_completed));
                    }

                    @Override
                    public void onTutorialSkipped() {
                        activeTutorialSequence = null;
                        showSnackbar(getString(R.string.tutorial_skipped));
                    }
                }
        );
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
                    updateTaskList();
                    updateModelSubtitle();
                    updateToggleAllButtonState();
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

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.language_selector_title_format, currentLanguageName))
                .setSingleChoiceItems(languages, selectedIndex, (dialog, which) -> {
                    if (which >= 0 && which < languageCodes.length) {
                        String selectedCode = languageCodes[which];
                        viewModel.setLanguage(selectedCode);

                        String currentModel = viewModel.currentModelName.getValue();
                        boolean isGemini = Boolean.TRUE.equals(viewModel.isGeminiSelected.getValue());
                        if (!isGemini && ModelUtils.isLanguageConflicting(MainActivity.this, currentModel, selectedCode)) {
                            String modelDisplayName = ModelUtils.formatModelDisplayName(MainActivity.this, currentModel);
                            String errorMessage = getString(R.string.error_language_model_conflict, modelDisplayName);
                            showSnackbar(errorMessage);
                        } else {
                            String langName = languages[which];
                            showSnackbar(getString(R.string.language_selector_hint) + ": " + langName);
                        }
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private boolean hasTasksWithResult() {
        if (viewModel == null) return false;
        for (MainViewModel.TaskEntry entry : viewModel.getActiveTaskEntries()) {
            if (entry.resultText != null && !entry.resultText.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean areAllTasksMinimized() {
        if (viewModel == null) return true;
        for (MainViewModel.TaskEntry entry : viewModel.getActiveTaskEntries()) {
            if (entry.resultText != null && !entry.resultText.isEmpty()) {
                if (entry.isSummaryExpanded || entry.isTranscriptionExpanded) {
                    return false;
                }
            }
        }
        return true;
    }

    private void updateToggleAllButtonState() {
        if (binding == null) return;
        boolean hasTask = hasTasksWithResult();
        boolean allMinimized = areAllTasksMinimized();
        binding.buttonToggleAllTasks.setEnabled(hasTask);
        binding.buttonToggleAllTasks.setIconResource(allMinimized ? R.drawable.ic_unfold_more : R.drawable.ic_unfold_less);
        binding.buttonToggleAllTasks.setContentDescription(getString(allMinimized ? R.string.expand_all_tasks : R.string.minimize_all_tasks));
        TooltipCompat.setTooltipText(binding.buttonToggleAllTasks, getString(allMinimized ? R.string.expand_all_tasks : R.string.minimize_all_tasks));
    }

    private void setAllTasksMinimized(boolean minimize) {
        if (viewModel == null) return;
        for (MainViewModel.TaskEntry entry : viewModel.getActiveTaskEntries()) {
            viewModel.updateTaskExpandState(entry.taskKey, !minimize, !minimize);
        }
        updateTaskList();
    }

    private void minimizeOtherTasks(String keepTaskKey) {
        if (viewModel == null) return;
        for (MainViewModel.TaskEntry entry : viewModel.getActiveTaskEntries()) {
            if (!Objects.equals(entry.taskKey, keepTaskKey)) {
                viewModel.updateTaskExpandState(entry.taskKey, false, false);
            } else {
                viewModel.updateTaskExpandState(entry.taskKey, true, true);
            }
        }
        updateTaskList();
    }

    private void showTaskCardContextMenu(View anchorView, String currentTaskKey) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenu().add(0, 1, 0, getString(R.string.minimize_all_tasks));
        popup.getMenu().add(0, 2, 1, getString(R.string.expand_all_tasks));
        if (currentTaskKey != null) {
            popup.getMenu().add(0, 3, 2, getString(R.string.minimize_other_tasks));
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                setAllTasksMinimized(true);
                return true;
            } else if (id == 2) {
                setAllTasksMinimized(false);
                return true;
            } else if (id == 3 && currentTaskKey != null) {
                minimizeOtherTasks(currentTaskKey);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void setupTaskContainerGesture() {
        GestureDetector doubleTapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (binding == null || !hasTasksWithResult()) return false;
                boolean shouldMinimize = !areAllTasksMinimized();
                setAllTasksMinimized(shouldMinimize);
                showSnackbar(getString(shouldMinimize ? R.string.minimize_all_tasks : R.string.expand_all_tasks));
                return true;
            }
        });

        binding.recyclerViewTasks.addOnItemTouchListener(new androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull androidx.recyclerview.widget.RecyclerView rv, @NonNull MotionEvent e) {
                doubleTapDetector.onTouchEvent(e);
                return false;
            }
        });
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
        dismissActiveSnackbar();
        if (activeTutorialSequence != null) {
            activeTutorialSequence.dismiss();
            activeTutorialSequence = null;
        }
        if (binding != null) {
            binding.toolbar.removeCallbacks(showTutorialRunnable);
        }
        if (inAppUpdateManager != null) {
            inAppUpdateManager.unregister();
        }
        backgroundAnimator.stop();
        binding = null;
    }
}
