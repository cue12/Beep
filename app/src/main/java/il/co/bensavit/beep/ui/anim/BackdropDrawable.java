package il.co.bensavit.beep.ui.anim;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

public class BackdropDrawable extends Drawable {

    private static final int[] DEFAULT_COLORS = new int[]{0xFF0052D4, 0xFF4364F7, 0xFF6FB1FC};

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int[] currentColors = DEFAULT_COLORS.clone();

    private LinearGradient gradientShader;
    private boolean shaderDirty = true;

    /**
     * Updates the background gradient colors.
     *
     * @param colors The new background gradient colors.
     */
    public void setColors(int[] colors) {
        if (colors != null && colors.length > 0 && !Arrays.equals(this.currentColors, colors)) {
            this.currentColors = colors.clone();
            this.shaderDirty = true;
        }
        invalidateSelf();
    }

    public int[] getCurrentColors() {
        return currentColors;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        shaderDirty = true;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        int width = bounds.width();
        int height = bounds.height();

        if (width <= 0 || height <= 0) {
            return;
        }

        drawBackground(canvas, bounds, height);
    }

    private void drawBackground(@NonNull Canvas canvas, @NonNull Rect bounds, int height) {
        if (shaderDirty || gradientShader == null) {
            gradientShader = new LinearGradient(
                    0, height, 0, 0,
                    currentColors,
                    null,
                    Shader.TileMode.CLAMP
            );
            backgroundPaint.setShader(gradientShader);
            shaderDirty = false;
        }
        canvas.drawRect(bounds, backgroundPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        backgroundPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        backgroundPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
