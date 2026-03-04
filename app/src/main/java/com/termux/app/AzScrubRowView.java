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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AzScrubRowView extends AppCompatTextView {
    public interface ScrubCallback {
        void onScrub(char letter, int selectionIndex, boolean commit);
        void onCancel();
    }

    private static final char[] LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray();
    private char[] visibleLetters = LETTERS;

    @Nullable private ScrubCallback callback;
    private int currentSelectionIndex = 0;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float activeTouchX = -1f;
    private float waveStrength = 0f;
    private int accentColor = Color.WHITE;
    @Nullable private ValueAnimator settleAnimator;

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
        float contentTop = getPaddingTop();
        float contentBottom = height - getPaddingBottom();
        float slot = width / Math.max(1, visibleLetters.length);
        float anchorX = activeTouchX < 0f ? (width * 0.5f) : activeTouchX;
        float waveAmplitude = dp(24) * waveStrength;
        int activeIndex = (int) (anchorX / Math.max(1f, slot));
        activeIndex = Math.max(0, Math.min(visibleLetters.length - 1, activeIndex));

        for (int i = 0; i < visibleLetters.length; i++) {
            float x = (slot * i) + (slot * 0.5f);
            float distance = Math.abs(x - anchorX) / Math.max(1f, slot);
            float envelope = (float) Math.exp(-(distance * distance) * 0.85f);
            float waveLift = (float) Math.sin(Math.min(1f, envelope) * (Math.PI * 0.5f)) * waveAmplitude;
            if (i == activeIndex) {
                waveLift *= 1.35f;
            }
            float scale = 1f + (0.34f * envelope * waveStrength);
            letterPaint.setTextSize(baseTextSize * scale);
            applyLetterWeight(envelope, i == activeIndex);
            Paint.FontMetrics letterMetrics = letterPaint.getFontMetrics();
            // Keep baseline closer to bottom so raised crest has enough headroom and avoids clipping.
            float baseline = (contentBottom - dp(2) - letterMetrics.descent) - waveLift;
            if (i == activeIndex && waveStrength > 0.01f) {
                int bright = blendColors(baseColor, accentColor, 0.68f);
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
        char[] out = new char[normalized.size()];
        int i = 0;
        for (char base : LETTERS) {
            if (normalized.contains(base)) {
                out[i++] = base;
            }
        }
        if (i == 0) {
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
                bringToFront();
                updateInteractionLayerOffset();
                invalidate();
                callback.onScrub(letter, currentSelectionIndex, false);
                return true;
            case MotionEvent.ACTION_MOVE:
                activeTouchX = x;
                waveStrength = 1f;
                updateInteractionLayerOffset();
                invalidate();
                callback.onScrub(letter, currentSelectionIndex, false);
                return true;
            case MotionEvent.ACTION_UP:
                callback.onScrub(letter, currentSelectionIndex, false);
                animateWaveRelease();
                return true;
            case MotionEvent.ACTION_CANCEL:
                callback.onCancel();
                animateWaveRelease();
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
        float lift = -dp(10) * waveStrength;
        setTranslationY(lift);
    }

    private void applyLetterWeight(float envelope, boolean active) {
        float influence = Math.max(0f, Math.min(1f, envelope * waveStrength));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int weight = (int) (420 + (influence * 380));
            if (active) weight = 900;
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
