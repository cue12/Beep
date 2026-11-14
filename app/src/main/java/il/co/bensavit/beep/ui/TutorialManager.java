package il.co.bensavit.beep.ui;

import android.app.Activity;
import android.view.View;

import il.co.bensavit.beep.MainViewModel;
import il.co.bensavit.beep.R;
import il.co.bensavit.beep.databinding.ActivityMainBinding;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetSequence;

public class TutorialManager {

    public interface TutorialListener {
        void onTutorialCompleted();
        void onTutorialSkipped();
    }

    public static void showTutorialPrompt(
            Activity activity,
            ActivityMainBinding binding,
            MainViewModel viewModel,
            Runnable showTutorialRunnable,
            TutorialListener listener
    ) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || binding == null) {
            return;
        }

        if (viewModel.isTutorialShown()) {
            return;
        }

        if (binding.outputCard.getWidth() <= 0) {
            binding.toolbar.removeCallbacks(showTutorialRunnable);
            binding.toolbar.postDelayed(showTutorialRunnable, 200);
            return;
        }

        MaterialTapTargetSequence sequence = new MaterialTapTargetSequence();
        final boolean[] tutorialSkipped = new boolean[]{false};

        Runnable onSkipped = () -> {
            if (!tutorialSkipped[0]) {
                tutorialSkipped[0] = true;
                viewModel.setTutorialShown(true);
                sequence.dismiss();
                if (listener != null) {
                    listener.onTutorialSkipped();
                }
            }
        };

        // 1. Welcome & How to use (WhatsApp focus)
        sequence.addPrompt(createTutorialPrompt(activity, binding.outputCard,
                activity.getString(R.string.tutorial_welcome_title), activity.getString(R.string.tutorial_welcome_desc), onSkipped));

        // 2. Mode Toggle
        sequence.addPrompt(createTutorialPrompt(activity, binding.toolbar,
                activity.getString(R.string.tutorial_mode_title), activity.getString(R.string.tutorial_mode_desc), onSkipped));

        // 3. Model Selection
        sequence.addPrompt(createTutorialPrompt(activity, binding.toolbar,
                activity.getString(R.string.tutorial_model_title), activity.getString(R.string.tutorial_model_desc), onSkipped));

        // 4. Audio Language Selection
        sequence.addPrompt(createTutorialPrompt(activity, binding.toolbar,
                activity.getString(R.string.tutorial_language_title), activity.getString(R.string.tutorial_language_desc), onSkipped));

        // 5. History
        sequence.addPrompt(createTutorialPrompt(activity, binding.buttonHistory,
                activity.getString(R.string.tutorial_history_title), activity.getString(R.string.tutorial_history_desc), onSkipped));

        // 6. Copying & Managing Tasks
        View firstChild = binding.transcriptionContainer.getChildCount() > 0 ? binding.transcriptionContainer.getChildAt(0) : null;
        View taskTarget = (firstChild != null && firstChild.getWidth() > 0 && firstChild.getHeight() > 0 && firstChild.isAttachedToWindow())
                ? firstChild
                : binding.outputCard;
        sequence.addPrompt(createTutorialPrompt(activity, taskTarget,
                activity.getString(R.string.tutorial_tasks_title), activity.getString(R.string.tutorial_tasks_desc), onSkipped));

        // 7. Sort Tasks
        sequence.addPrompt(createTutorialPrompt(activity, binding.buttonSortTasks,
                activity.getString(R.string.tutorial_sort_title), activity.getString(R.string.tutorial_sort_desc), onSkipped));

        // 8. Menu Options
        sequence.addPrompt(createTutorialPrompt(activity, binding.toolbar,
                activity.getString(R.string.tutorial_options_title), activity.getString(R.string.tutorial_options_desc), onSkipped));

        // 9. Clear
        sequence.addPrompt(createTutorialPrompt(activity, binding.buttonClear,
                activity.getString(R.string.tutorial_clear_title), activity.getString(R.string.tutorial_clear_desc), onSkipped));

        sequence.setSequenceCompleteListener(() -> {
            if (!tutorialSkipped[0]) {
                viewModel.setTutorialShown(true);
                if (listener != null) {
                    listener.onTutorialCompleted();
                }
            }
        });

        sequence.show();
    }

    private static MaterialTapTargetPrompt.Builder createTutorialPrompt(
            Activity activity,
            View target,
            String title,
            String desc,
            Runnable onSkipped
    ) {
        return new MaterialTapTargetPrompt.Builder(activity)
                .setTarget(target)
                .setPrimaryText(title)
                .setSecondaryText(desc)
                .setPromptStateChangeListener((prompt, state) -> {
                    if (state == MaterialTapTargetPrompt.STATE_NON_FOCAL_PRESSED || state == MaterialTapTargetPrompt.STATE_BACK_BUTTON_PRESSED) {
                        if (onSkipped != null) {
                            onSkipped.run();
                        }
                    }
                });
    }
}
