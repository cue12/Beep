package il.co.bensavit.beep.ui.anim;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A custom Drawable that renders a light-colored surface with a subtle, fine textured
 * pattern (diagonal cross-hatch weave, micro dot grid, and soft rounded border)
 * for the task view backdrop.
 */
public class LightTextureDrawable extends Drawable {

    private static final int BASE_COLOR_TOP = 0xFFFAFBFD;
    private static final int BASE_COLOR_BOTTOM = 0xFFF1F4F9;
    private static final int TEXTURE_LINE_COLOR = 0x14000000; // 8% translucent dark line
    private static final int TEXTURE_DOT_COLOR = 0x0E000000;  // 5.5% translucent dark dot
    private static final int BORDER_COLOR = 0x20000000;       // 12% border outline

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textureLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textureDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsF = new RectF();
    private final Path clipPath = new Path();
    private float cornerRadius;
    private boolean shaderDirty = true;

    public LightTextureDrawable() {
        this(24f);
    }

    public LightTextureDrawable(float cornerRadiusPx) {
        this.cornerRadius = cornerRadiusPx;

        textureLinePaint.setStyle(Paint.Style.STROKE);
        textureLinePaint.setStrokeWidth(1.2f);
        textureLinePaint.setColor(TEXTURE_LINE_COLOR);

        textureDotPaint.setStyle(Paint.Style.FILL);
        textureDotPaint.setColor(TEXTURE_DOT_COLOR);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.0f);
        borderPaint.setColor(BORDER_COLOR);
    }

    public void setCornerRadius(float cornerRadiusPx) {
        if (this.cornerRadius != cornerRadiusPx) {
            this.cornerRadius = cornerRadiusPx;
            invalidateSelf();
        }
    }

    /**
     * Attaches the light texture backdrop to the given view.
     *
     * @param view The target task card view.
     */
    public static void attachTo(@NonNull View view) {
        float density = view.getContext().getResources().getDisplayMetrics().density;
        float radius = 12f * density;
        if (view instanceof com.google.android.material.card.MaterialCardView) {
            com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) view;
            radius = card.getRadius();
            card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        LightTextureDrawable drawable = new LightTextureDrawable(radius);
        view.setBackground(drawable);
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

        boundsF.set(bounds);

        if (shaderDirty || backgroundPaint.getShader() == null) {
            LinearGradient gradient = new LinearGradient(
                    0f, 0f, 0f, height,
                    new int[]{BASE_COLOR_TOP, BASE_COLOR_BOTTOM},
                    null,
                    Shader.TileMode.CLAMP
            );
            backgroundPaint.setShader(gradient);
            shaderDirty = false;
        }

        // Clip canvas to rounded rectangle
        clipPath.reset();
        clipPath.addRoundRect(boundsF, cornerRadius, cornerRadius, Path.Direction.CW);

        int saveCount = canvas.save();
        canvas.clipPath(clipPath);

        // 1. Draw base light gradient background
        canvas.drawRect(boundsF, backgroundPaint);

        // 2. Draw subtle light texture pattern (diagonal cross-hatch lines)
        float lineSpacing = 16f; // pixel spacing between texture lines
        float maxDim = width + height;

        // Positive diagonal lines
        for (float offset = -height; offset < width; offset += lineSpacing) {
            canvas.drawLine(offset, 0, offset + height, height, textureLinePaint);
        }

        // Negative diagonal lines for cross-hatch
        for (float offset = 0; offset < maxDim; offset += lineSpacing * 1.5f) {
            canvas.drawLine(offset, 0, offset - height, height, textureLinePaint);
        }

        // 3. Draw subtle micro dot matrix texture layer for tactile depth
        float dotSpacing = 20f;
        for (float x = dotSpacing / 2f; x < width; x += dotSpacing) {
            for (float y = dotSpacing / 2f; y < height; y += dotSpacing) {
                canvas.drawCircle(x, y, 1.2f, textureDotPaint);
            }
        }

        canvas.restoreToCount(saveCount);

        // 4. Draw outer border outline
        float halfStroke = borderPaint.getStrokeWidth() / 2f;
        RectF borderRect = new RectF(
                boundsF.left + halfStroke,
                boundsF.top + halfStroke,
                boundsF.right - halfStroke,
                boundsF.bottom - halfStroke
        );
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        backgroundPaint.setAlpha(alpha);
        textureLinePaint.setAlpha((int) (alpha * 0.08f));
        textureDotPaint.setAlpha((int) (alpha * 0.055f));
        borderPaint.setAlpha(alpha);
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
