package com.termux.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Renders AZ drag bloom cursor, focused-icon glow, page-edge affordances and launch bloom.
 */
public final class LauncherAzGestureFxView extends View {

    private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint anchorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint connectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path connectorPath = new Path();

    private final int[] locationOnScreen = new int[2];

    private int orbColor = 0xFF86A7FF;
    private int edgeColor = 0xFF7CE2FF;

    private boolean dragActive;
    private float targetRawX;
    private float targetRawY;
    private float displayRawX;
    private float displayRawY;

    private boolean hasAnchor;
    private float anchorRawX;
    private float anchorRawY;

    private boolean hasFocus;
    private final RectF focusRawRect = new RectF();
    private final RectF focusDisplayRect = new RectF();

    private boolean filteredOverflowActive;
    private boolean canPageLeft;
    private boolean canPageRight;
    private float edgeProximityLeft;
    private float edgeProximityRight;

    private boolean launchBloomActive;
    private float launchBloomRawX;
    private float launchBloomRawY;
    private float launchBloomProgress;
    @Nullable private ValueAnimator launchBloomAnimator;

    public LauncherAzGestureFxView(Context context) {
        super(context);
        init();
    }

    public LauncherAzGestureFxView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LauncherAzGestureFxView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);

        connectorPaint.setStyle(Paint.Style.STROKE);
        connectorPaint.setStrokeCap(Paint.Cap.ROUND);

        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeCap(Paint.Cap.ROUND);
        focusPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColors(int orbColor, int edgeColor) {
        this.orbColor = orbColor;
        this.edgeColor = edgeColor;
        invalidate();
    }

    public void updateDrag(
        boolean active,
        float rawX,
        float rawY,
        boolean anchorVisible,
        float anchorRawX,
        float anchorRawY,
        @Nullable RectF focusedBoundsRaw
    ) {
        dragActive = active;
        targetRawX = rawX;
        targetRawY = rawY;
        hasAnchor = anchorVisible;
        this.anchorRawX = anchorRawX;
        this.anchorRawY = anchorRawY;
        if (focusedBoundsRaw != null) {
            hasFocus = true;
            focusRawRect.set(focusedBoundsRaw);
        } else {
            hasFocus = false;
        }
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        invalidate();
    }

    public void setFilteredOverflowState(boolean active, boolean pageLeft, boolean pageRight) {
        filteredOverflowActive = active;
        canPageLeft = pageLeft;
        canPageRight = pageRight;
        invalidate();
    }

    public void setEdgeProximity(float left, float right) {
        edgeProximityLeft = clamp01(left);
        edgeProximityRight = clamp01(right);
        invalidate();
    }

    public void clearDrag(boolean keepOverflowAffordance) {
        dragActive = false;
        hasFocus = false;
        hasAnchor = false;
        edgeProximityLeft = 0f;
        edgeProximityRight = 0f;
        if (!keepOverflowAffordance) {
            filteredOverflowActive = false;
            canPageLeft = false;
            canPageRight = false;
        }
        invalidate();
    }

    public void playLaunchBloom(float rawX, float rawY) {
        launchBloomRawX = rawX;
        launchBloomRawY = rawY;
        launchBloomProgress = 0f;
        launchBloomActive = true;
        if (launchBloomAnimator != null) {
            launchBloomAnimator.cancel();
        }
        launchBloomAnimator = ValueAnimator.ofFloat(0f, 1f);
        launchBloomAnimator.setDuration(430L);
        launchBloomAnimator.setInterpolator(new DecelerateInterpolator());
        launchBloomAnimator.addUpdateListener(animation -> {
            launchBloomProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        launchBloomAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (launchBloomAnimator != null) {
            launchBloomAnimator.cancel();
            launchBloomAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        getLocationOnScreen(locationOnScreen);

        boolean needsMoreFrames = false;

        if (filteredOverflowActive && (canPageLeft || canPageRight)) {
            drawEdgeGlow(canvas);
        }

        if (dragActive) {
            if (displayRawX == 0f && displayRawY == 0f) {
                displayRawX = targetRawX;
                displayRawY = targetRawY;
            }
            displayRawX = lerp(displayRawX, targetRawX, 0.38f);
            displayRawY = lerp(displayRawY, targetRawY, 0.38f);
            if (Math.abs(displayRawX - targetRawX) > 0.6f || Math.abs(displayRawY - targetRawY) > 0.6f) {
                needsMoreFrames = true;
            }

            drawOrb(canvas, displayRawX, displayRawY, 1f);

            if (hasAnchor) {
                drawAnchor(canvas);
            }

            if (hasFocus) {
                if (focusDisplayRect.isEmpty()) {
                    focusDisplayRect.set(focusRawRect);
                } else {
                    blendRect(focusDisplayRect, focusRawRect, 0.34f);
                }
                if (rectDelta(focusDisplayRect, focusRawRect) > 0.9f) {
                    needsMoreFrames = true;
                }
                drawFocusGlow(canvas, focusDisplayRect);
                if (hasAnchor) {
                    drawConnector(canvas, displayRawX, displayRawY, focusDisplayRect);
                }
            } else {
                focusDisplayRect.setEmpty();
            }
        }

        if (launchBloomActive) {
            drawLaunchBloom(canvas);
            if (launchBloomProgress >= 0.999f) {
                launchBloomActive = false;
            } else {
                needsMoreFrames = true;
            }
        }

        boolean shouldStayVisible = dragActive || launchBloomActive || (filteredOverflowActive && (canPageLeft || canPageRight));
        if (!shouldStayVisible && getVisibility() != GONE) {
            setVisibility(GONE);
            return;
        }

        if (needsMoreFrames) {
            postInvalidateOnAnimation();
        }
    }

    private void drawOrb(Canvas canvas, float rawX, float rawY, float weight) {
        float x = rawX - locationOnScreen[0];
        float y = rawY - locationOnScreen[1];
        float core = dp(10f) + (dp(2f) * weight);
        float halo = dp(28f) + (dp(8f) * weight);

        haloPaint.setStyle(Paint.Style.FILL);
        haloPaint.setColor(withAlpha(orbColor, (int) (110 * weight)));
        canvas.drawCircle(x, y, halo, haloPaint);

        haloPaint.setColor(withAlpha(orbColor, (int) (54 * weight)));
        canvas.drawCircle(x, y, halo * 1.45f, haloPaint);

        orbPaint.setStyle(Paint.Style.FILL);
        orbPaint.setColor(withAlpha(Color.WHITE, 220));
        canvas.drawCircle(x, y, core * 0.42f, orbPaint);

        orbPaint.setColor(withAlpha(orbColor, 232));
        canvas.drawCircle(x, y, core, orbPaint);
    }

    private void drawAnchor(Canvas canvas) {
        float x = anchorRawX - locationOnScreen[0];
        float y = anchorRawY - locationOnScreen[1];
        float radius = dp(6f);
        anchorPaint.setStyle(Paint.Style.FILL);
        anchorPaint.setColor(withAlpha(orbColor, 182));
        canvas.drawCircle(x, y, radius * 1.7f, anchorPaint);
        anchorPaint.setColor(withAlpha(Color.WHITE, 210));
        canvas.drawCircle(x, y, radius * 0.55f, anchorPaint);
    }

    private void drawFocusGlow(Canvas canvas, RectF rawRect) {
        RectF local = new RectF(
            rawRect.left - locationOnScreen[0],
            rawRect.top - locationOnScreen[1],
            rawRect.right - locationOnScreen[0],
            rawRect.bottom - locationOnScreen[1]
        );
        float rx = dp(11f);
        float ry = dp(11f);

        haloPaint.setStyle(Paint.Style.FILL);
        haloPaint.setColor(withAlpha(orbColor, 44));
        RectF outer = new RectF(local);
        outer.inset(-dp(7f), -dp(7f));
        canvas.drawRoundRect(outer, rx + dp(6f), ry + dp(6f), haloPaint);

        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(dp(2.3f));
        focusPaint.setColor(withAlpha(orbColor, 222));
        canvas.drawRoundRect(local, rx, ry, focusPaint);
    }

    private void drawConnector(Canvas canvas, float rawX, float rawY, RectF focusRawRect) {
        float startX = anchorRawX - locationOnScreen[0];
        float startY = anchorRawY - locationOnScreen[1];
        float endX = rawX - locationOnScreen[0];
        float endY = Math.min(rawY, focusRawRect.centerY()) - locationOnScreen[1];

        connectorPath.reset();
        connectorPath.moveTo(startX, startY);
        float midY = lerp(startY, endY, 0.42f);
        connectorPath.cubicTo(startX, midY, endX, midY, endX, endY);

        connectorPaint.setStrokeWidth(dp(4.3f));
        connectorPaint.setColor(withAlpha(orbColor, 100));
        canvas.drawPath(connectorPath, connectorPaint);

        connectorPaint.setStrokeWidth(dp(2.2f));
        connectorPaint.setColor(withAlpha(Color.WHITE, 92));
        canvas.drawPath(connectorPath, connectorPaint);
    }

    private void drawEdgeGlow(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float edgeW = Math.max(dp(18f), width * 0.075f);

        if (canPageLeft) {
            float intensity = 0.32f + (0.56f * edgeProximityLeft);
            edgePaint.setStyle(Paint.Style.FILL);
            edgePaint.setColor(withAlpha(edgeColor, (int) (255 * intensity * 0.68f)));
            canvas.drawRect(0f, 0f, edgeW, height, edgePaint);
            edgePaint.setColor(withAlpha(Color.WHITE, (int) (255 * intensity * 0.24f)));
            canvas.drawRect(0f, 0f, edgeW * 0.42f, height, edgePaint);
        }

        if (canPageRight) {
            float intensity = 0.34f + (0.56f * edgeProximityRight);
            edgePaint.setStyle(Paint.Style.FILL);
            edgePaint.setColor(withAlpha(edgeColor, (int) (255 * intensity * 0.68f)));
            canvas.drawRect(width - edgeW, 0f, width, height, edgePaint);
            edgePaint.setColor(withAlpha(Color.WHITE, (int) (255 * intensity * 0.24f)));
            canvas.drawRect(width - (edgeW * 0.42f), 0f, width, height, edgePaint);
        }
    }

    private void drawLaunchBloom(Canvas canvas) {
        float cx = launchBloomRawX - locationOnScreen[0];
        float cy = launchBloomRawY - locationOnScreen[1];
        float maxR = (float) Math.hypot(getWidth(), getHeight());
        float r = lerp(dp(18f), maxR * 1.05f, launchBloomProgress);
        float alphaFactor = (1f - launchBloomProgress);

        bloomPaint.setStyle(Paint.Style.FILL);
        bloomPaint.setColor(withAlpha(orbColor, (int) (210 * alphaFactor)));
        canvas.drawCircle(cx, cy, r, bloomPaint);

        bloomPaint.setColor(withAlpha(Color.WHITE, (int) (105 * alphaFactor)));
        canvas.drawCircle(cx, cy, r * 0.62f, bloomPaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private static float lerp(float a, float b, float t) {
        return a + ((b - a) * t);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static void blendRect(RectF out, RectF target, float t) {
        out.left = lerp(out.left, target.left, t);
        out.top = lerp(out.top, target.top, t);
        out.right = lerp(out.right, target.right, t);
        out.bottom = lerp(out.bottom, target.bottom, t);
    }

    private static float rectDelta(RectF a, RectF b) {
        return Math.abs(a.left - b.left)
            + Math.abs(a.top - b.top)
            + Math.abs(a.right - b.right)
            + Math.abs(a.bottom - b.bottom);
    }
}
