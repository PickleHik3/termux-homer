package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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
    private final Rect textBounds = new Rect();

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
        setPadding(0, dp(6), 0, dp(6));
        setClickable(true);
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

        letterPaint.setColor(getCurrentTextColor());
        letterPaint.setTextSize(getTextSize());
        letterPaint.getTextBounds("A", 0, 1, textBounds);
        float baseline = (height * 0.5f) + (textBounds.height() * 0.35f);
        float slot = width / Math.max(1, visibleLetters.length);

        for (int i = 0; i < visibleLetters.length; i++) {
            float x = (slot * i) + (slot * 0.5f);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (callback == null) return super.onTouchEvent(event);
        char letter = pickLetter(event.getX());
        int selectionIndex = Math.max(0, (int) ((-event.getY()) / Math.max(12f, getHeight() / 2f)));
        currentSelectionIndex = selectionIndex;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                callback.onScrub(letter, currentSelectionIndex, false);
                return true;
            case MotionEvent.ACTION_UP:
                callback.onScrub(letter, currentSelectionIndex, false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                callback.onCancel();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private char pickLetter(float x) {
        float width = Math.max(1f, getWidth());
        int len = Math.max(1, visibleLetters.length);
        int index = (int) ((x / width) * len);
        index = Math.max(0, Math.min(len - 1, index));
        return visibleLetters[index];
    }
}
