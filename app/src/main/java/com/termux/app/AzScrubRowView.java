package com.termux.app;

import android.content.Context;
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
        setText("A B C D E F G H I J K L M N O P Q R S T U V W X Y Z #");
        setSingleLine(true);
        setTextSize(11f);
        setPadding(16, 8, 16, 8);
        setClickable(true);
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
                callback.onScrub(letter, currentSelectionIndex, true);
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

