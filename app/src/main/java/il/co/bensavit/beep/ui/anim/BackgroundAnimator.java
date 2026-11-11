package il.co.bensavit.beep.ui.anim;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.view.View;

public class BackgroundAnimator {
    private static final long ANIMATION_DURATION_MS = 2000L; // Faster cycle for "flicker"
    private static final ArgbEvaluator ARGB_EVALUATOR = new ArgbEvaluator();

    // Neutral, eye-friendly dark canvas palette for full-screen backdrop
    private static final int[] NEUTRAL_BASE_COLORS = {0xFF15141C, 0xFF1B1A24, 0xFF22202E};
    private static final int[] NEUTRAL_FLICK_COLORS = {0xFF181720, 0xFF201E2B, 0xFF282536};

    // Mode-specific accent colors for focused UI element highlights & shimmer
    public static final int COLOR_ACCENT_GEMINI = 0xCC4364F7;  // Gemini Blue Accent
    public static final int COLOR_ACCENT_WHISPER = 0xCC9C27B0; // Whisper Purple Accent

    private ValueAnimator animator;
    private BackdropDrawable backdropDrawable;
    private final int[] animatedColors = new int[3];

    /**
     * Gets the mode accent highlight color based on active mode selection.
     */
    public static int getModeAccentColor(Boolean isGeminiSelected) {
        return Boolean.TRUE.equals(isGeminiSelected) ? COLOR_ACCENT_GEMINI : COLOR_ACCENT_WHISPER;
    }

    /**
     * Starts the background flicker animation.
     *
     * @param root               The root view whose background will be animated.
     * @param isGeminiSelected   True if Online (Gemini) mode is active, false for Local.
     */
    public void start(View root, Boolean isGeminiSelected) {
        if (root == null) return;
        ensureBackdropDrawable(root);

        if (animator != null) {
            animator.cancel();
        }

        final int[] baseColors = getBaseColors(false);
        final int[] flickColors = getFlickColors(false);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);

        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();

            // Animate colors between base and flick states to simulate flickering
            for (int i = 0; i < animatedColors.length; i++) {
                animatedColors[i] = (int) ARGB_EVALUATOR.evaluate(fraction, baseColors[i], flickColors[i]);
            }

            if (backdropDrawable != null) {
                backdropDrawable.setProgress(fraction, animatedColors);
            }
            ShimmerDrawable.setGlobalProgress(fraction);
        });
        animator.start();
    }

    /**
     * Pauses the animation and sets the backdrop to the stable base colors.
     */
    public void pause(View root, Boolean isGeminiSelected) {
        cancelAnimator();
        if (root == null) return;
        ensureBackdropDrawable(root);

        final int[] baseColors = getBaseColors(false);
        if (backdropDrawable != null) {
            backdropDrawable.setProgress(0f, baseColors);
        }
        ShimmerDrawable.setGlobalProgress(0f);
    }

    /**
     * Stops the animation completely.
     */
    public void stop() {
        cancelAnimator();
        if (backdropDrawable != null) {
            backdropDrawable.setProgress(0f, backdropDrawable.getCurrentColors());
        }
        ShimmerDrawable.setGlobalProgress(0f);
    }

    private void cancelAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void ensureBackdropDrawable(View root) {
        if (backdropDrawable == null || root.getBackground() != backdropDrawable) {
            backdropDrawable = new BackdropDrawable();
            root.setBackground(backdropDrawable);
        }
    }

    private int[] getBaseColors(boolean isGeminiSelected) {
        return NEUTRAL_BASE_COLORS;
    }

    private int[] getFlickColors(boolean isGeminiSelected) {
        return NEUTRAL_FLICK_COLORS;
    }
}