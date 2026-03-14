package com.termux.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AzScrubRowView extends AppCompatTextView {
    public enum InteractionMode {
        WAVE_TRACK,
        INLINE_EMPHASIS_TRACK
    }

    public enum GesturePhase {
        DOWN,
        MOVE,
        UP
    }

    public interface ScrubCallback {
        void onScrub(char letter, int selectionIndex, float touchX, float touchY, float rawX, float rawY, @NonNull GesturePhase phase);
        void onCancel();
        default void onDoubleTap() {}
    }

    public static final char PINNED_APPS_SYMBOL = '\u2605';
    private static final char[] ALPHABET_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray();
    private static final char[] LETTERS = (PINNED_APPS_SYMBOL + "ABCDEFGHIJKLMNOPQRSTUVWXYZ#").toCharArray();
    private char[] visibleLetters = LETTERS;

    @Nullable private ScrubCallback callback;
    private int currentSelectionIndex = 0;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float activeTouchX = -1f;
    private float waveStrength = 0f;
    private int accentColor = Color.WHITE;
    @Nullable private ValueAnimator settleAnimator;
    private long lastTapUpTimeMs;
    private float lastTapUpX = Float.NaN;
    private int doubleTapTimeoutMs;
    private int doubleTapSlopPx;
    private boolean suppressUpScrub;
    @NonNull private InteractionMode interactionMode = InteractionMode.WAVE_TRACK;
    @Nullable private Character lockedInlineLetter;

    public AzScrubRowView(Context context) {
        super(context);
        init();
    }

    public AzScrubRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AzScrubRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setText("");
        setSingleLine(true);
        setTextSize(11f);
        setPadding(0, dp(2), 0, dp(5));
        setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            setElevation(dp(20));
            setTranslationZ(dp(20));
        }
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTextSize(getTextSize());
        letterPaint.setColor(getCurrentTextColor());
        ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
        doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout();
        doubleTapSlopPx = viewConfiguration.getScaledDoubleTapSlop();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        int baseColor = getCurrentTextColor();
        letterPaint.setColor(baseColor);
        float baseTextSize = getTextSize();
        letterPaint.setTextSize(baseTextSize);
        float contentBottom = height - getPaddingBottom();
        float slot = width / Math.max(1, visibleLetters.length);
        float anchorX = activeTouchX < 0f ? (width * 0.5f) : activeTouchX;
        float waveAmplitude = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 0f : (dp(15) * waveStrength);
        int activeIndex = (int) (anchorX / Math.max(1f, slot));
        activeIndex = Math.max(0, Math.min(visibleLetters.length - 1, activeIndex));
        if (interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK && lockedInlineLetter != null) {
            char target = Character.toUpperCase(lockedInlineLetter);
            for (int i = 0; i < visibleLetters.length; i++) {
                if (visibleLetters[i] == target) {
                    activeIndex = i;
                    break;
                }
            }
        }

        for (int i = 0; i < visibleLetters.length; i++) {
            float x = (slot * i) + (slot * 0.5f);
            float distance = Math.abs(x - anchorX) / Math.max(1f, slot);
            float envelope = (float) Math.exp(-(distance * distance) * 0.85f);
            float waveLift = (float) Math.sin(Math.min(1f, envelope) * (Math.PI * 0.5f)) * waveAmplitude;
            boolean activeFocus = waveStrength > 0.01f && i == activeIndex;
            if (interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK && i == activeIndex) {
                activeFocus = true;
            }
            if (activeFocus) {
                waveLift *= 1.2f;
            }
            float scale;
            if (interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK) {
                float inlineInfluence = i == activeIndex ? 1f : Math.max(0f, 0.65f - (distance * 0.28f));
                scale = 1f + (0.24f * inlineInfluence);
            } else {
                scale = 1f + (0.34f * envelope * waveStrength);
            }
            letterPaint.setTextSize(baseTextSize * scale);
            applyLetterWeight(envelope, activeFocus);
            Paint.FontMetrics letterMetrics = letterPaint.getFontMetrics();
            // Keep baseline closer to bottom so raised crest has enough headroom and avoids clipping.
            float baseline = (contentBottom - dp(2) - letterMetrics.descent) - waveLift;
            if (activeFocus) {
                float glowRatio = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 0.88f : 0.68f;
                int bright = blendColors(baseColor, accentColor, glowRatio);
                letterPaint.setColor(bright);
            } else {
                letterPaint.setColor(baseColor);
            }
            canvas.drawText(String.valueOf(visibleLetters[i]), x, baseline, letterPaint);
        }
    }

    public void setScrubCallback(@Nullable ScrubCallback callback) {
        this.callback = callback;
    }

    public void setVisibleLetters(@NonNull Set<Character> letters) {
        if (letters.isEmpty()) {
            visibleLetters = LETTERS;
            invalidate();
            return;
        }
        LinkedHashSet<Character> normalized = new LinkedHashSet<>();
        for (Character c : letters) {
            if (c == null) continue;
            char upper = Character.toUpperCase(c);
            if ((upper >= 'A' && upper <= 'Z') || upper == '#') {
                normalized.add(upper);
            }
        }
        if (normalized.isEmpty()) {
            visibleLetters = LETTERS;
            invalidate();
            return;
        }
        char[] out = new char[normalized.size() + 1];
        int i = 0;
        out[i++] = PINNED_APPS_SYMBOL;
        for (char base : ALPHABET_LETTERS) {
            if (normalized.contains(base)) {
                out[i++] = base;
            }
        }
        if (i <= 1) {
            visibleLetters = LETTERS;
        } else if (i == out.length) {
            visibleLetters = out;
        } else {
            char[] trimmed = new char[i];
            System.arraycopy(out, 0, trimmed, 0, i);
            visibleLetters = trimmed;
        }
        invalidate();
    }

    public void setInteractionAccentColor(int color) {
        accentColor = color;
        invalidate();
    }

    public void setInteractionMode(@NonNull InteractionMode mode) {
        interactionMode = mode;
        if (mode == InteractionMode.WAVE_TRACK) {
            lockedInlineLetter = null;
        }
        invalidate();
    }

    public void setLockedInlineLetter(@Nullable Character letter) {
        lockedInlineLetter = letter;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (callback == null) return super.onTouchEvent(event);
        float x = Math.max(0f, Math.min(getWidth(), event.getX()));
        char letter = pickLetter(event.getX());
        int selectionIndex = Math.max(0, (int) ((-event.getY()) / Math.max(12f, getHeight() / 2f)));
        currentSelectionIndex = selectionIndex;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopSettleAnimation();
                activeTouchX = x;
                waveStrength = 1f;
                if (interactionMode == InteractionMode.WAVE_TRACK) {
                    lockedInlineLetter = null;
                }
                bringToFront();
                updateInteractionLayerOffset();
                invalidate();
                long now = event.getEventTime();
                boolean isDoubleTap = (now - lastTapUpTimeMs) <= doubleTapTimeoutMs
                    && !Float.isNaN(lastTapUpX)
                    && Math.abs(x - lastTapUpX) <= doubleTapSlopPx;
                if (isDoubleTap) {
                    suppressUpScrub = true;
                    callback.onDoubleTap();
                    return true;
                }
                suppressUpScrub = false;
                callback.onScrub(letter, currentSelectionIndex, event.getX(), event.getY(), event.getRawX(), event.getRawY(), GesturePhase.DOWN);
                return true;
            case MotionEvent.ACTION_MOVE:
                activeTouchX = x;
                waveStrength = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 0.92f : 1f;
                updateInteractionLayerOffset();
                invalidate();
                callback.onScrub(letter, currentSelectionIndex, event.getX(), event.getY(), event.getRawX(), event.getRawY(), GesturePhase.MOVE);
                return true;
            case MotionEvent.ACTION_UP:
                lastTapUpTimeMs = event.getEventTime();
                lastTapUpX = x;
                if (!suppressUpScrub) {
                    callback.onScrub(letter, currentSelectionIndex, event.getX(), event.getY(), event.getRawX(), event.getRawY(), GesturePhase.UP);
                }
                suppressUpScrub = false;
                if (interactionMode == InteractionMode.WAVE_TRACK) {
                    animateWaveRelease();
                } else {
                    waveStrength = 0f;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                suppressUpScrub = false;
                callback.onCancel();
                if (interactionMode == InteractionMode.WAVE_TRACK) {
                    animateWaveRelease();
                } else {
                    waveStrength = 0f;
                    invalidate();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void animateWaveRelease() {
        stopSettleAnimation();
        settleAnimator = ValueAnimator.ofFloat(waveStrength, 0f);
        settleAnimator.setDuration(165L);
        settleAnimator.addUpdateListener(animation -> {
            waveStrength = (float) animation.getAnimatedValue();
            updateInteractionLayerOffset();
            invalidate();
        });
        settleAnimator.start();
    }

    private void stopSettleAnimation() {
        if (settleAnimator != null) {
            settleAnimator.cancel();
            settleAnimator = null;
        }
    }

    private char pickLetter(float x) {
        float width = Math.max(1f, getWidth());
        int len = Math.max(1, visibleLetters.length);
        int index = (int) ((x / width) * len);
        index = Math.max(0, Math.min(len - 1, index));
        return visibleLetters[index];
    }

    private void updateInteractionLayerOffset() {
        setTranslationY(0f);
    }

    private void applyLetterWeight(float envelope, boolean active) {
        float influence = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
            ? (active ? 1f : Math.max(0f, Math.min(1f, envelope * 0.65f)))
            : Math.max(0f, Math.min(1f, envelope * waveStrength));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int weight = (int) (420 + (influence * 380));
            if (active) weight = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 920 : 900;
            weight = Math.max(200, Math.min(900, weight));
            letterPaint.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        } else {
            if (active) {
                letterPaint.setTypeface(Typeface.DEFAULT_BOLD);
                letterPaint.setFakeBoldText(true);
            } else if (influence > 0.55f) {
                letterPaint.setTypeface(Typeface.DEFAULT_BOLD);
                letterPaint.setFakeBoldText(false);
            } else {
                letterPaint.setTypeface(Typeface.DEFAULT);
                letterPaint.setFakeBoldText(false);
            }
        }
    }

    private static int blendColors(int from, int to, float ratio) {
        float t = Math.max(0f, Math.min(1f, ratio));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }
}
