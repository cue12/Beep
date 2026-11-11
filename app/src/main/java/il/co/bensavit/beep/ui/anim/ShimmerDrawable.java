package il.co.bensavit.beep.ui.anim;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A custom Drawable that draws a sweeping light "shimmer reflection" beam along the EDGES / BORDER
 * of a View. It clips cleanly to the target view's border outline without filling or affecting
 * the interior surface of the button.
 */
public class ShimmerDrawable extends Drawable {

    private static final int DEFAULT_SHIMMER_COLOR = 0x80FFFFFF; // 50% translucent white shine
    private static final float DEFAULT_STROKE_WIDTH_DP = 3.5f; // Edge border thickness in dp

    private static final Set<ShimmerDrawable> activeShimmers = ConcurrentHashMap.newKeySet();
    private static float globalProgress = 0f;

    private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final RectF borderBounds = new RectF();

    private ValueAnimator animator;
    private float translationFactor = -1.5f;

    private int highlightColor;
    private float strokeWidth = 8f; // pixels
    private float cornerRadius = 24f; // pixels
    private boolean shaderDirty = true;

    public ShimmerDrawable() {
        this(DEFAULT_SHIMMER_COLOR);
    }

    public ShimmerDrawable(int highlightColor) {
        this.highlightColor = highlightColor;
        shimmerPaint.setStyle(Paint.Style.STROKE);
        shimmerPaint.setStrokeWidth(strokeWidth);
    }

    /**
     * Sets the global shimmer progress (0.0 to 1.0), synchronizing all active shimmer overlays.
     *
     * @param fraction Global progress fraction from the synchronized backdrop animator.
     */
    public static void setGlobalProgress(float fraction) {
        globalProgress = fraction;
        for (ShimmerDrawable shimmer : activeShimmers) {
            if (shimmer != null) {
                shimmer.setProgress(fraction);
            }
        }
    }

    public static float getGlobalProgress() {
        return globalProgress;
    }

    /**
     * Attaches this shimmer overlay to the given view and starts animating automatically.
     *
     * @param view The target View (e.g. Button) to attach the edge shimmer overlay to.
     * @return The created and started ShimmerDrawable instance.
     */
    public static ShimmerDrawable attachTo(@NonNull View view) {
        return attachTo(view, DEFAULT_SHIMMER_COLOR);
    }

    /**
     * Attaches an edge shimmer overlay with custom highlight color to the given view and starts animating.
     *
     * @param view           The target View to shimmer.
     * @param highlightColor The ARGB color of the shine beam.
     * @return The created and started ShimmerDrawable instance.
     */
    public static ShimmerDrawable attachTo(@NonNull View view, int highlightColor) {
        ShimmerDrawable shimmer = new ShimmerDrawable(highlightColor);

        float density = view.getContext().getResources().getDisplayMetrics().density;
        shimmer.setStrokeWidth(DEFAULT_STROKE_WIDTH_DP * density);

        // Auto-detect corner radius if MaterialButton, MaterialCardView, or TextInputLayout
        if (view instanceof com.google.android.material.button.MaterialButton) {
            shimmer.setCornerRadius(((com.google.android.material.button.MaterialButton) view).getCornerRadius());
        } else if (view instanceof com.google.android.material.card.MaterialCardView) {
            shimmer.setCornerRadius(((com.google.android.material.card.MaterialCardView) view).getRadius());
        } else if (view instanceof com.google.android.material.textfield.TextInputLayout) {
            shimmer.setCornerRadius(((com.google.android.material.textfield.TextInputLayout) view).getBoxCornerRadiusTopStart());
        } else {
            shimmer.setCornerRadius(12f * density);
        }

        view.getOverlay().add(shimmer);

        // Keep overlay bounds in sync with view dimensions
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                shimmer.setBounds(0, 0, right - left, bottom - top)
        );
        shimmer.setBounds(0, 0, view.getWidth(), view.getHeight());

        // Automatically unregister and clean up when view is detached
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                shimmer.start();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                shimmer.stop();
            }
        });

        shimmer.start();
        return shimmer;
    }

    /**
     * Sets the stroke width (thickness) of the edge shimmer in pixels.
     *
     * @param strokeWidth The stroke width in pixels.
     */
    public void setStrokeWidth(float strokeWidth) {
        if (this.strokeWidth != strokeWidth) {
            this.strokeWidth = strokeWidth;
            shimmerPaint.setStrokeWidth(strokeWidth);
            invalidateSelf();
        }
    }

    /**
     * Sets the corner radius of the view border in pixels.
     *
     * @param cornerRadius The corner radius in pixels.
     */
    public void setCornerRadius(float cornerRadius) {
        if (this.cornerRadius != cornerRadius) {
            this.cornerRadius = cornerRadius;
            invalidateSelf();
        }
    }

    /**
     * Configures animation parameters.
     * Note: Shimmer timing is synchronized with the global backdrop animation cycle.
     *
     * @param durationMs     Duration in milliseconds.
     * @param repeatDelayMs Delay in milliseconds.
     */
    public void setAnimationConfig(long durationMs, long repeatDelayMs) {
        // Shimmer sweep timing is synchronized with BackgroundAnimator
    }

    /**
     * Sets the shine highlight color.
     *
     * @param color The ARGB color.
     */
    public void setHighlightColor(int color) {
        if (this.highlightColor != color) {
            this.highlightColor = color;
            this.shaderDirty = true;
            invalidateSelf();
        }
    }

    /**
     * Sets the animation progress fraction [0.0, 1.0] for this shimmer drawable.
     *
     * @param fraction Progress fraction, mapped to translation factor across view bounds.
     */
    public void setProgress(float fraction) {
        this.translationFactor = -1.5f + fraction * 4.0f;
        invalidateSelf();
    }

    /**
     * Starts the shimmer reflection sweep animation loop, registering with global sync.
     */
    public void start() {
        activeShimmers.add(this);
        setProgress(globalProgress);
    }

    /**
     * Stops the shimmer animation and unregisters from global sync.
     */
    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        activeShimmers.remove(this);
        translationFactor = -1.5f;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        shaderDirty = true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        int width = bounds.width();
        int height = bounds.height();

        if (width <= 0 || height <= 0) {
            return;
        }

        if (shaderDirty || shimmerPaint.getShader() == null) {
            // An angled beam of light: transparent -> bright center -> transparent
            float beamWidth = Math.max(width, height) * 0.45f;
            LinearGradient gradient = new LinearGradient(
                    0f, 0f, beamWidth, beamWidth,
                    new int[]{0x00FFFFFF, highlightColor, 0x00FFFFFF},
                    new float[]{0.15f, 0.50f, 0.85f},
                    Shader.TileMode.CLAMP
            );
            shimmerPaint.setShader(gradient);
            shaderDirty = false;
        }

        // Translate the diagonal light beam across the view bounds
        float dx = translationFactor * width;
        float dy = translationFactor * height * 0.5f;
        shaderMatrix.setTranslate(dx, dy);
        if (shimmerPaint.getShader() != null) {
            shimmerPaint.getShader().setLocalMatrix(shaderMatrix);
        }

        // Inset by half the stroke width so the border stroke stays inside view bounds
        float halfStroke = strokeWidth / 2f;
        borderBounds.set(
                bounds.left + halfStroke,
                bounds.top + halfStroke,
                bounds.right - halfStroke,
                bounds.bottom - halfStroke
        );

        canvas.drawRoundRect(borderBounds, cornerRadius, cornerRadius, shimmerPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        shimmerPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        shimmerPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
