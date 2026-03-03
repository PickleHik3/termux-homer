package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

public final class AzScrubRowView extends AppCompatTextView {
    public interface ScrubCallback {
        void onScrub(char letter, int selectionIndex, boolean commit);
        void onCancel();
    }

    private static final char[] LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray();

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
        float slot = width / LETTERS.length;

        for (int i = 0; i < LETTERS.length; i++) {
            float x = (slot * i) + (slot * 0.5f);
            canvas.drawText(String.valueOf(LETTERS[i]), x, baseline, letterPaint);
        }
    }

    public void setScrubCallback(@Nullable ScrubCallback callback) {
        this.callback = callback;
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
                boolean launch = event.getY() < -dp(4);
                callback.onScrub(letter, currentSelectionIndex, launch);
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
        int index = (int) ((x / width) * LETTERS.length);
        index = Math.max(0, Math.min(LETTERS.length - 1, index));
        return LETTERS[index];
    }
}
