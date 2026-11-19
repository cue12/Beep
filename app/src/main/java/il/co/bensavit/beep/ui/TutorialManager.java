package il.co.bensavit.beep.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import java.util.List;

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

    public static MaterialTapTargetSequence showTutorialPrompt(
            Activity activity,
            ActivityMainBinding binding,
            MainViewModel viewModel,
            Runnable showTutorialRunnable,
            TutorialListener listener
    ) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || binding == null) {
            return null;
        }

        if (viewModel.isTutorialShown()) {
            return null;
        }

        if (binding.outputCard.getWidth() <= 0) {
            binding.toolbar.removeCallbacks(showTutorialRunnable);
            binding.toolbar.postDelayed(showTutorialRunnable, 200);
            return null;
        }

        if (isAnyDialogShowing(activity)) {
            registerDialogDismissCallback(activity, binding, showTutorialRunnable);
            return null;
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
        View firstChild = binding.recyclerViewTasks.getChildCount() > 0 ? binding.recyclerViewTasks.getChildAt(0) : null;
        View taskTarget = (firstChild != null && firstChild.getWidth() > 0 && firstChild.getHeight() > 0 && firstChild.isAttachedToWindow())
                ? firstChild
                : binding.outputCard;
        sequence.addPrompt(createTutorialPrompt(activity, taskTarget,
                activity.getString(R.string.tutorial_tasks_title), activity.getString(R.string.tutorial_tasks_desc), onSkipped));

        // 7. Minimize / Expand Tasks
        sequence.addPrompt(createTutorialPrompt(activity, binding.buttonToggleAllTasks,
                activity.getString(R.string.tutorial_toggle_tasks_title), activity.getString(R.string.tutorial_toggle_tasks_desc), onSkipped));

        // 8. Sort Tasks
        sequence.addPrompt(createTutorialPrompt(activity, binding.buttonSortTasks,
                activity.getString(R.string.tutorial_sort_title), activity.getString(R.string.tutorial_sort_desc), onSkipped));

        // 9. Menu Options
        sequence.addPrompt(createTutorialPrompt(activity, binding.toolbar,
                activity.getString(R.string.tutorial_options_title), activity.getString(R.string.tutorial_options_desc), onSkipped));

        // 10. Clear
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
        return sequence;
    }

    public static boolean isAnyDialogShowing(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }

        if (activity instanceof FragmentActivity) {
            FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
            if (isAnyDialogShowingInManager(fm)) {
                return true;
            }
        }

        return !activity.hasWindowFocus();
    }

    private static boolean isAnyDialogShowingInManager(FragmentManager fm) {
        if (fm == null) return false;
        List<Fragment> fragments = fm.getFragments();
        for (Fragment fragment : fragments) {
            if (fragment == null) {
                continue;
            }
            if (fragment instanceof DialogFragment && fragment.isAdded()) {
                DialogFragment df = (DialogFragment) fragment;
                if (df.getDialog() != null && df.getDialog().isShowing()) {
                    return true;
                }
            }
            if (fragment.isAdded() && isAnyDialogShowingInManager(fragment.getChildFragmentManager())) {
                return true;
            }
        }
        return false;
    }

    private static void registerDialogDismissCallback(
            Activity activity,
            ActivityMainBinding binding,
            Runnable showTutorialRunnable
    ) {
        if (activity == null || binding == null || showTutorialRunnable == null) {
            return;
        }

        binding.toolbar.removeCallbacks(showTutorialRunnable);

        if (activity instanceof FragmentActivity) {
            FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
            fm.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    if (f instanceof DialogFragment) {
                        fm.unregisterFragmentLifecycleCallbacks(this);
                        binding.toolbar.removeCallbacks(showTutorialRunnable);
                        binding.toolbar.post(showTutorialRunnable);
                    }
                }
            }, true);
        }

        ViewTreeObserver vto = binding.toolbar.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener() {
                @Override
                public void onWindowFocusChanged(boolean hasFocus) {
                    if (hasFocus) {
                        if (binding.toolbar.getViewTreeObserver().isAlive()) {
                            binding.toolbar.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                        }
                        binding.toolbar.removeCallbacks(showTutorialRunnable);
                        binding.toolbar.post(showTutorialRunnable);
                    }
                }
            });
        }
    }

    private static View findActivePromptView(Activity activity) {
        if (activity == null) return null;
        View decorView = activity.getWindow().getDecorView();
        if (decorView instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) decorView;
            for (int i = root.getChildCount() - 1; i >= 0; i--) {
                View child = root.getChildAt(i);
                if (child.getId() == uk.co.samuelwall.materialtaptargetprompt.R.id.material_target_prompt_view) {
                    return child;
                }
            }
        }
        return activity.findViewById(uk.co.samuelwall.materialtaptargetprompt.R.id.material_target_prompt_view);
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
                    if (state == MaterialTapTargetPrompt.STATE_REVEALING || state == MaterialTapTargetPrompt.STATE_REVEALED) {
                        View promptView = findActivePromptView(activity);
                        if (promptView != null) {
                            promptView.setElevation(9999f);
                            promptView.bringToFront();
                        }
                    }
                    if (state == MaterialTapTargetPrompt.STATE_NON_FOCAL_PRESSED || state == MaterialTapTargetPrompt.STATE_BACK_BUTTON_PRESSED) {
                        if (onSkipped != null) {
                            onSkipped.run();
                        }
                    }
                });
    }
}
