package il.co.bensavit.beep.ui.anim;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

public class BackdropDrawable extends Drawable {

    private static final int[] DEFAULT_COLORS = new int[]{0xFF0052D4, 0xFF4364F7, 0xFF6FB1FC};

    private static final int COLOR_LEFT_PADDLE = Color.parseColor("#E53935");
    private static final int COLOR_RIGHT_PADDLE = Color.parseColor("#1E88E5");
    private static final int COLOR_BALL = Color.DKGRAY;
    private static final int COLOR_NET = Color.LTGRAY;

    private static final float BOUNCE_COUNT = 7f;

    private final Paint pingPongPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float animationProgress = 0f;
    private int[] currentColors = DEFAULT_COLORS.clone();

    private LinearGradient gradientShader;
    private boolean shaderDirty = true;

    /**
     * Updates the animation progress and background colors.
     *
     * @param progress The animation progress, expected to be in the range [0.0, 1.0].
     * @param colors   The new background gradient colors.
     */
    public void setProgress(@FloatRange(from = 0.0, to = 1.0) float progress, int[] colors) {
        this.animationProgress = progress;
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
        //drawCustomAnimation(canvas, animationProgress, width, height);
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

    protected void drawCustomAnimation(@NonNull Canvas canvas, @FloatRange(from = 0.0, to = 1.0) float progress, int width, int height) {
        if (width == 0 || height == 0) return;

        float cx = width / 2f;
        float cy = height / 2f;
        float radius = Math.min(width, height) / 2f;

        float ballRadius = radius * 0.06f;
        float paddleWidth = radius * 0.04f;
        float margin = radius * 0.1f;

        float ballX = calculateBallX(progress, cx, radius, margin, paddleWidth, ballRadius);
        float ballY = calculateBallY(progress, ballX, cx, cy, radius, ballRadius);

        // Calculate angular position for the paddles instead of vertical Y position
        float leftAngle = calculatePaddleAngle(progress, ballY, cy, radius, true);
        float rightAngle = calculatePaddleAngle(progress, ballY, cy, radius, false);

        drawPaddles(canvas, cx, cy, radius, margin, paddleWidth, leftAngle, rightAngle);
        drawBall(canvas, ballX, ballY, ballRadius);
    }

    private float calculatePaddleAngle(float progress, float ballY, float cy, float radius, boolean isLeft) {
        float xFraction = (progress <= 0.5f) ? (progress * 2f) : ((1f - progress) * 2f);
        float weight = isLeft ? (1f - xFraction) : xFraction;

        // Project the ball's Y position to the outer edge to find the target angle
        float dx = isLeft ? -radius : radius;
        float dy = ballY - cy;
        float targetAngle = (float) Math.toDegrees(Math.atan2(dy, dx));

        // Normalize negative angles on the left side (atan2 jumps from 180 to -179)
        if (isLeft && targetAngle < 0) {
            targetAngle += 360f;
        }

        float restAngle = isLeft ? 180f : 0f;

        // Interpolate between the resting position (middle) and tracking the ball
        return (targetAngle * weight) + (restAngle * (1f - weight));
    }

    private void drawPaddles(@NonNull Canvas canvas, float cx, float cy, float radius, float margin, float paddleWidth, float leftAngle, float rightAngle) {
        pingPongPaint.setStyle(Paint.Style.STROKE);
        pingPongPaint.setStrokeWidth(paddleWidth);
        pingPongPaint.setStrokeCap(Paint.Cap.ROUND);

        float arcRadius = radius - margin - paddleWidth / 2f;
        float leftEdge = cx - arcRadius;
        float topEdge = cy - arcRadius;
        float rightEdge = cx + arcRadius;
        float bottomEdge = cy + arcRadius;

        float sweepAngle = 30f; // Angular length of the paddles

        // Left Paddle
        pingPongPaint.setColor(COLOR_LEFT_PADDLE);
        canvas.drawArc(leftEdge, topEdge, rightEdge, bottomEdge, leftAngle - sweepAngle / 2f, sweepAngle, false, pingPongPaint);

        // Right Paddle
        pingPongPaint.setColor(COLOR_RIGHT_PADDLE);
        canvas.drawArc(leftEdge, topEdge, rightEdge, bottomEdge, rightAngle - sweepAngle / 2f, sweepAngle, false, pingPongPaint);

        // Reset paint back to fill for the ball drawing
        pingPongPaint.setStyle(Paint.Style.FILL);
    }

    private float calculateBallX(float progress, float cx, float radius, float margin, float paddleWidth, float ballRadius) {
        float xFraction = (progress <= 0.5f) ? (progress * 2f) : ((1f - progress) * 2f);
        float minX = cx - radius + margin + paddleWidth + ballRadius;
        float maxX = cx + radius - margin - paddleWidth - ballRadius;
        return minX + (maxX - minX) * xFraction;
    }

    private float calculateBallY(float progress, float ballX, float cx, float cy, float radius, float ballRadius) {
        // Distance from center X
        float dx = Math.abs(ballX - cx);
        // Calculate the height of the circle at this specific X position
        float circleHeight = (float) Math.sqrt(Math.max(0, radius * radius - dx * dx));
        float maxAvailableYOffset = Math.max(0f, circleHeight - ballRadius);

        float yCycles = progress * BOUNCE_COUNT;
        float yFraction = Math.abs((yCycles % 2f) - 1f);

        return cy - maxAvailableYOffset + (2 * maxAvailableYOffset) * yFraction;
    }

    private void drawBall(@NonNull Canvas canvas, float x, float y, float radius) {
        pingPongPaint.setStyle(Paint.Style.FILL);
        pingPongPaint.setColor(COLOR_BALL);
        canvas.drawCircle(x, y, radius, pingPongPaint);
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