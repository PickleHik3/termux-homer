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
import android.os.SystemClock;
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
    private final RectF azRowRawBounds = new RectF();
    private final RectF appsRowRawBounds = new RectF();
    private final RectF extraKeysRawBounds = new RectF();
    private final int[] locationOnScreen = new int[2];

    private int glassTintColor = 0xFF86A7FF;
    private int vividLetterTintColor = 0xFF9AB8FF;
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
    private long lastFocusUpdateUptimeMs = 0L;
    private static final long FOCUS_HOLD_MS = 140L;

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
        this.glassTintColor = enforceGlassVisibility(glassTintColor, 0.78f);
        this.vividLetterTintColor = boostColor(this.glassTintColor, 1.20f, 1.08f);
        this.edgeTintColor = enforceGlassVisibility(edgeTintColor, 0.84f);
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
        boolean wasActive = dragActive;
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

        if (active && !wasActive) {
            if (mode == InteractionMode.LETTER_TRACK && focusedBoundsRaw != null && !focusedBoundsRaw.isEmpty()) {
                displayRawX = focusedBoundsRaw.centerX();
                displayRawY = focusedBoundsRaw.centerY();
            } else {
                displayRawX = rawX;
                displayRawY = rawY;
            }
            focusDisplayRect.setEmpty();
        }

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        applyBlurIfSupported((active && mode == InteractionMode.ICON_TRACK_LOCKED) || filteredOverflowActive);
        invalidate();
    }

    public void setRowBounds(@Nullable RectF azRowRaw, @Nullable RectF appsRowRaw, @Nullable RectF extraKeysRowRaw) {
        if (azRowRaw != null) {
            azRowRawBounds.set(azRowRaw);
        } else {
            azRowRawBounds.setEmpty();
        }
        if (appsRowRaw != null) {
            appsRowRawBounds.set(appsRowRaw);
        } else {
            appsRowRawBounds.setEmpty();
        }
        if (extraKeysRowRaw != null) {
            extraKeysRawBounds.set(extraKeysRowRaw);
        } else {
            extraKeysRawBounds.setEmpty();
        }
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
        displayRawX = 0f;
        displayRawY = 0f;
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
            float desiredRawX = targetRawX;
            float desiredRawY = targetRawY;
            if (interactionMode == InteractionMode.LETTER_TRACK && hasFocus && !focusRawRect.isEmpty()) {
                desiredRawX = focusRawRect.centerX();
                desiredRawY = focusRawRect.centerY();
            }
            if (displayRawX == 0f && displayRawY == 0f) {
                displayRawX = desiredRawX;
                displayRawY = desiredRawY;
            }
            float smoothing = interactionMode == InteractionMode.ICON_TRACK_LOCKED ? 0.30f : 0.34f;
            displayRawX = lerp(displayRawX, desiredRawX, smoothing);
            displayRawY = lerp(displayRawY, desiredRawY, smoothing);
            if (Math.abs(displayRawX - desiredRawX) > 0.55f || Math.abs(displayRawY - desiredRawY) > 0.55f) {
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
        if (!azRowRawBounds.isEmpty()) {
            float top = azRowRawBounds.top - locationOnScreen[1];
            float bottom = azRowRawBounds.bottom - locationOnScreen[1];
            cy = clamp(cy, top + dp(6f), bottom - dp(6f));
        }

        float w = dp(30f);
        float h = dp(21f);
        float radius = dp(11f);
        if (hasFocus && !focusRawRect.isEmpty()) {
            float slotW = focusRawRect.width();
            float slotH = focusRawRect.height();
            w = Math.max(dp(20f), Math.min(dp(34f), slotW * 0.90f));
            h = Math.max(dp(16f), Math.min(dp(24f), slotH * 0.92f));
            radius = Math.max(dp(8f), Math.min(dp(14f), h * 0.48f));
            cx = focusRawRect.centerX() - locationOnScreen[0];
            cy = focusRawRect.centerY() - locationOnScreen[1];
        }

        tmpRect.set(cx - (w * 0.5f), cy - (h * 0.5f), cx + (w * 0.5f), cy + (h * 0.5f));
        drawLetterGlassPopupEvolution(canvas, tmpRect, radius);
    }

    private void drawLetterGlassPopupEvolution(Canvas canvas, RectF pill, float radius) {
        // Evolved from folder-popup style: richer tint body, restrained inner veil, crisp dual rim.
        glassFillPaint.setColor(withAlpha(vividLetterTintColor, 170));
        canvas.drawRoundRect(pill, radius, radius, glassFillPaint);

        RectF innerVeil = new RectF(pill);
        float veilPad = Math.max(dp(1.4f), Math.min(pill.width(), pill.height()) * 0.10f);
        innerVeil.inset(veilPad, veilPad * 0.95f);
        glassInnerPaint.setColor(withAlpha(Color.WHITE, 56));
        canvas.drawRoundRect(innerVeil, Math.max(dp(4f), radius - veilPad), Math.max(dp(4f), radius - veilPad), glassInnerPaint);

        RectF sheen = new RectF(innerVeil);
        sheen.bottom = sheen.top + Math.max(dp(4.6f), innerVeil.height() * 0.42f);
        glassInnerPaint.setColor(withAlpha(Color.WHITE, 86));
        canvas.drawRoundRect(sheen, Math.max(dp(3f), radius - dp(5f)), Math.max(dp(3f), radius - dp(5f)), glassInnerPaint);

        glassStrokePaint.setStrokeWidth(dp(1.25f));
        glassStrokePaint.setColor(withAlpha(Color.WHITE, 170));
        canvas.drawRoundRect(pill, radius, radius, glassStrokePaint);

        RectF innerRim = new RectF(pill);
        innerRim.inset(dp(0.9f), dp(0.9f));
        glassStrokePaint.setStrokeWidth(dp(0.9f));
        glassStrokePaint.setColor(withAlpha(vividLetterTintColor, 130));
        canvas.drawRoundRect(innerRim, Math.max(dp(4f), radius - dp(1.2f)), Math.max(dp(4f), radius - dp(1.2f)), glassStrokePaint);
    }

    private void drawLockedIconTrackGlass(Canvas canvas) {
        if (hasFocus) {
            lastFocusUpdateUptimeMs = SystemClock.uptimeMillis();
            if (focusDisplayRect.isEmpty()) {
                focusDisplayRect.set(focusRawRect);
            } else {
                blendRect(focusDisplayRect, focusRawRect, 0.31f);
            }
        } else {
            if ((SystemClock.uptimeMillis() - lastFocusUpdateUptimeMs) > FOCUS_HOLD_MS) {
                focusDisplayRect.setEmpty();
            }
        }

        int save = -1;
        if (!appsRowRawBounds.isEmpty()) {
            float left = appsRowRawBounds.left - locationOnScreen[0];
            float top = appsRowRawBounds.top - locationOnScreen[1];
            float right = appsRowRawBounds.right - locationOnScreen[0];
            float bottom = appsRowRawBounds.bottom - locationOnScreen[1];
            save = canvas.save();
            canvas.clipRect(left, top, right, bottom);
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
            float shellPad = Math.max(dp(6f), Math.min(local.width(), local.height()) * 0.17f);
            local.inset(-shellPad, -shellPad);
            drawGlassBody(canvas, local, dp(14f), 0.72f, 0.42f);
        } else {
            float cx = displayRawX - locationOnScreen[0];
            float cy = displayRawY - locationOnScreen[1];
            tmpRect.set(cx - dp(7f), cy - dp(7f), cx + dp(7f), cy + dp(7f));
            drawGlassBody(canvas, tmpRect, dp(7f), 0.34f, 0.18f);
        }
        if (save >= 0) {
            canvas.restoreToCount(save);
        }
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
        float top = 0f;
        float bottom = getHeight();
        if (!appsRowRawBounds.isEmpty()) {
            top = appsRowRawBounds.top - locationOnScreen[1];
            bottom = appsRowRawBounds.bottom - locationOnScreen[1];
        }
        float height = Math.max(dp(18f), bottom - top);
        float capsuleW = Math.max(dp(24f), width * 0.085f);
        float capsuleH = Math.max(dp(28f), height * 0.86f);
        float cy = top + (height * 0.5f);
        float radius = capsuleW * 0.52f;

        if (canPageLeft) {
            float intensity = 0.38f + (0.56f * edgeProximityLeft);
            float left = dp(4f);
            float capsuleTop = cy - (capsuleH * 0.5f);
            float right = left + capsuleW;
            float capsuleBottom = cy + (capsuleH * 0.5f);
            drawEdgeCapsule(canvas, left, capsuleTop, right, capsuleBottom, radius, intensity);
        }

        if (canPageRight) {
            float intensity = 0.38f + (0.56f * edgeProximityRight);
            float right = width - dp(4f);
            float left = right - capsuleW;
            float capsuleTop = cy - (capsuleH * 0.5f);
            float capsuleBottom = cy + (capsuleH * 0.5f);
            drawEdgeCapsule(canvas, left, capsuleTop, right, capsuleBottom, radius, intensity);
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

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int enforceGlassVisibility(int color, float minValue) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0.42f, hsv[1]);
        hsv[2] = Math.max(minValue, hsv[2]);
        return Color.HSVToColor((color >>> 24) == 0 ? 0xE8 : (color >>> 24), hsv);
    }

    private static int boostColor(int color, float satMul, float valMul) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = clamp(hsv[1] * satMul, 0f, 1f);
        hsv[2] = clamp(hsv[2] * valMul, 0f, 1f);
        return Color.HSVToColor((color >>> 24) == 0 ? 0xFF : (color >>> 24), hsv);
    }

    private static void blendRect(RectF out, RectF target, float t) {
        out.left = lerp(out.left, target.left, t);
        out.top = lerp(out.top, target.top, t);
        out.right = lerp(out.right, target.right, t);
        out.bottom = lerp(out.bottom, target.bottom, t);
    }
}
