package com.termux.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Glassmorphic renderer for AZ and icon-row drag interactions.
 */
public final class LauncherAzGestureFxView extends View {

    public enum InteractionMode {
        LETTER_TRACK,
        ICON_TRACK_LOCKED
    }

    private final Paint glassFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bridgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path liquidBridgePath = new Path();

    private final RectF tmpRect = new RectF();
    private final RectF focusDisplayRect = new RectF();
    private final RectF focusRawRect = new RectF();
    private final int[] locationOnScreen = new int[2];

    private int glassTintColor = 0xFF86A7FF;
    private int edgeTintColor = 0xFF7CE2FF;

    private boolean dragActive;
    private float targetRawX;
    private float targetRawY;
    private float displayRawX;
    private float displayRawY;

    private boolean hasAnchor;
    private float anchorRawX;
    private float anchorRawY;

    private boolean hasFocus;
    private boolean filteredOverflowActive;
    private boolean canPageLeft;
    private boolean canPageRight;
    private float edgeProximityLeft;
    private float edgeProximityRight;

    @NonNull private InteractionMode interactionMode = InteractionMode.LETTER_TRACK;

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

        glassFillPaint.setStyle(Paint.Style.FILL);
        glassInnerPaint.setStyle(Paint.Style.FILL);
        glassStrokePaint.setStyle(Paint.Style.STROKE);
        glassStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        glassStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        bridgePaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.FILL);
        edgeInnerPaint.setStyle(Paint.Style.FILL);

        applyBlurIfSupported(false);
    }

    public void setColors(int glassTintColor, int edgeTintColor) {
        this.glassTintColor = glassTintColor;
        this.edgeTintColor = edgeTintColor;
        invalidate();
    }

    public void updateDrag(
        boolean active,
        float rawX,
        float rawY,
        boolean anchorVisible,
        float anchorRawX,
        float anchorRawY,
        @Nullable RectF focusedBoundsRaw,
        @NonNull InteractionMode mode
    ) {
        dragActive = active;
        targetRawX = rawX;
        targetRawY = rawY;
        hasAnchor = anchorVisible;
        this.anchorRawX = anchorRawX;
        this.anchorRawY = anchorRawY;
        interactionMode = mode;

        if (focusedBoundsRaw != null) {
            hasFocus = true;
            focusRawRect.set(focusedBoundsRaw);
        } else {
            hasFocus = false;
        }

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        applyBlurIfSupported(active || filteredOverflowActive);
        invalidate();
    }

    public void setFilteredOverflowState(boolean active, boolean pageLeft, boolean pageRight) {
        filteredOverflowActive = active;
        canPageLeft = pageLeft;
        canPageRight = pageRight;
        applyBlurIfSupported(dragActive || active);
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
        interactionMode = InteractionMode.LETTER_TRACK;
        if (!keepOverflowAffordance) {
            filteredOverflowActive = false;
            canPageLeft = false;
            canPageRight = false;
        }
        applyBlurIfSupported(filteredOverflowActive);
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
        launchBloomAnimator.setDuration(440L);
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
            drawGlassEdgeCapsules(canvas);
        }

        if (dragActive) {
            if (displayRawX == 0f && displayRawY == 0f) {
                displayRawX = targetRawX;
                displayRawY = targetRawY;
            }
            float smoothing = interactionMode == InteractionMode.ICON_TRACK_LOCKED ? 0.30f : 0.37f;
            displayRawX = lerp(displayRawX, targetRawX, smoothing);
            displayRawY = lerp(displayRawY, targetRawY, smoothing);
            if (Math.abs(displayRawX - targetRawX) > 0.55f || Math.abs(displayRawY - targetRawY) > 0.55f) {
                needsMoreFrames = true;
            }

            if (interactionMode == InteractionMode.LETTER_TRACK) {
                drawLetterGlassDroplet(canvas);
            } else {
                drawLockedIconTrackGlass(canvas);
            }
        }

        if (launchBloomActive) {
            drawLaunchGlassBloom(canvas);
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

    private void drawLetterGlassDroplet(Canvas canvas) {
        float cx = displayRawX - locationOnScreen[0];
        float cy = displayRawY - locationOnScreen[1];

        float w = dp(42f);
        float h = dp(26f);
        float radius = dp(13f);

        tmpRect.set(cx - (w * 0.5f), cy - (h * 0.5f), cx + (w * 0.5f), cy + (h * 0.5f));
        drawGlassBody(canvas, tmpRect, radius, 0.68f, 0.36f);

        if (hasAnchor) {
            float ax = anchorRawX - locationOnScreen[0];
            float ay = anchorRawY - locationOnScreen[1];
            float anchorW = dp(18f);
            float anchorH = dp(12f);
            tmpRect.set(ax - (anchorW * 0.5f), ay - (anchorH * 0.5f), ax + (anchorW * 0.5f), ay + (anchorH * 0.5f));
            drawGlassBody(canvas, tmpRect, dp(7f), 0.52f, 0.26f);
        }
    }

    private void drawLockedIconTrackGlass(Canvas canvas) {
        if (hasFocus) {
            if (focusDisplayRect.isEmpty()) {
                focusDisplayRect.set(focusRawRect);
            } else {
                blendRect(focusDisplayRect, focusRawRect, 0.31f);
            }
        } else {
            focusDisplayRect.setEmpty();
        }

        if (hasAnchor && !focusDisplayRect.isEmpty()) {
            drawLiquidBridge(canvas, focusDisplayRect);
        }

        if (!focusDisplayRect.isEmpty()) {
            RectF local = new RectF(
                focusDisplayRect.left - locationOnScreen[0],
                focusDisplayRect.top - locationOnScreen[1],
                focusDisplayRect.right - locationOnScreen[0],
                focusDisplayRect.bottom - locationOnScreen[1]
            );
            local.inset(-dp(5f), -dp(5f));
            drawGlassBody(canvas, local, dp(14f), 0.72f, 0.42f);
        }

        float cx = displayRawX - locationOnScreen[0];
        float cy = displayRawY - locationOnScreen[1];
        tmpRect.set(cx - dp(10f), cy - dp(10f), cx + dp(10f), cy + dp(10f));
        drawGlassBody(canvas, tmpRect, dp(8f), 0.56f, 0.30f);
    }

    private void drawLiquidBridge(Canvas canvas, RectF focusRaw) {
        float startX = anchorRawX - locationOnScreen[0];
        float startY = anchorRawY - locationOnScreen[1];
        float endX = focusRaw.centerX() - locationOnScreen[0];
        float endY = Math.min(displayRawY, focusRaw.centerY()) - locationOnScreen[1];

        float neck = dp(7f);
        float shoulder = dp(12f);

        liquidBridgePath.reset();
        liquidBridgePath.moveTo(startX - neck, startY);
        liquidBridgePath.cubicTo(startX - shoulder, lerp(startY, endY, 0.42f), endX - shoulder, lerp(startY, endY, 0.58f), endX - neck, endY);
        liquidBridgePath.lineTo(endX + neck, endY);
        liquidBridgePath.cubicTo(endX + shoulder, lerp(startY, endY, 0.58f), startX + shoulder, lerp(startY, endY, 0.42f), startX + neck, startY);
        liquidBridgePath.close();

        bridgePaint.setColor(withAlpha(glassTintColor, 106));
        canvas.drawPath(liquidBridgePath, bridgePaint);
        bridgePaint.setColor(withAlpha(Color.WHITE, 52));
        canvas.drawPath(liquidBridgePath, bridgePaint);
    }

    private void drawGlassEdgeCapsules(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float capsuleW = Math.max(dp(24f), width * 0.085f);
        float capsuleH = Math.max(dp(62f), height * 0.58f);
        float cy = height * 0.5f;
        float radius = capsuleW * 0.52f;

        if (canPageLeft) {
            float intensity = 0.38f + (0.56f * edgeProximityLeft);
            float left = dp(4f);
            float top = cy - (capsuleH * 0.5f);
            float right = left + capsuleW;
            float bottom = cy + (capsuleH * 0.5f);
            drawEdgeCapsule(canvas, left, top, right, bottom, radius, intensity);
        }

        if (canPageRight) {
            float intensity = 0.38f + (0.56f * edgeProximityRight);
            float right = width - dp(4f);
            float left = right - capsuleW;
            float top = cy - (capsuleH * 0.5f);
            float bottom = cy + (capsuleH * 0.5f);
            drawEdgeCapsule(canvas, left, top, right, bottom, radius, intensity);
        }
    }

    private void drawEdgeCapsule(Canvas canvas, float left, float top, float right, float bottom, float radius, float intensity) {
        tmpRect.set(left, top, right, bottom);
        edgePaint.setColor(withAlpha(edgeTintColor, (int) (190f * intensity)));
        canvas.drawRoundRect(tmpRect, radius, radius, edgePaint);

        edgeInnerPaint.setColor(withAlpha(Color.WHITE, (int) (88f * intensity)));
        float pad = Math.max(dp(2.2f), (right - left) * 0.16f);
        RectF inner = new RectF(tmpRect);
        inner.inset(pad, pad * 1.2f);
        canvas.drawRoundRect(inner, Math.max(dp(7f), radius - pad), Math.max(dp(7f), radius - pad), edgeInnerPaint);

        glassStrokePaint.setStrokeWidth(dp(1.4f));
        glassStrokePaint.setColor(withAlpha(Color.WHITE, (int) (146f * intensity)));
        canvas.drawRoundRect(tmpRect, radius, radius, glassStrokePaint);
    }

    private void drawLaunchGlassBloom(Canvas canvas) {
        float cx = launchBloomRawX - locationOnScreen[0];
        float cy = launchBloomRawY - locationOnScreen[1];
        float maxR = (float) Math.hypot(getWidth(), getHeight());
        float r = lerp(dp(22f), maxR * 1.08f, launchBloomProgress);
        float alphaFactor = (1f - launchBloomProgress);

        bloomPaint.setColor(withAlpha(glassTintColor, (int) (198 * alphaFactor)));
        canvas.drawCircle(cx, cy, r, bloomPaint);

        bloomPaint.setColor(withAlpha(Color.WHITE, (int) (132 * alphaFactor)));
        canvas.drawCircle(cx, cy, r * 0.55f, bloomPaint);
    }

    private void drawGlassBody(Canvas canvas, RectF rect, float radius, float tintAlpha, float innerAlpha) {
        glassFillPaint.setColor(withAlpha(glassTintColor, (int) (255f * tintAlpha)));
        canvas.drawRoundRect(rect, radius, radius, glassFillPaint);

        float pad = Math.max(dp(1.8f), Math.min(rect.width(), rect.height()) * 0.12f);
        RectF inner = new RectF(rect);
        inner.inset(pad, pad * 0.9f);
        glassInnerPaint.setColor(withAlpha(Color.WHITE, (int) (255f * innerAlpha)));
        canvas.drawRoundRect(inner, Math.max(dp(4f), radius - pad), Math.max(dp(4f), radius - pad), glassInnerPaint);

        glassStrokePaint.setStrokeWidth(dp(1.6f));
        glassStrokePaint.setColor(withAlpha(Color.WHITE, 178));
        canvas.drawRoundRect(rect, radius, radius, glassStrokePaint);
    }

    private void applyBlurIfSupported(boolean enable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (enable) {
            setRenderEffect(RenderEffect.createBlurEffect(dp(7f), dp(7f), Shader.TileMode.CLAMP));
        } else {
            setRenderEffect(null);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float lerp(float start, float end, float t) {
        return start + ((end - start) * t);
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
}
