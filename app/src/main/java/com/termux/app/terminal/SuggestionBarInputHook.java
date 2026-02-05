package com.termux.app.terminal;

import android.view.KeyEvent;

import com.termux.app.SuggestionBarCallback;

public final class SuggestionBarInputHook {

    private static final StringBuilder INPUT_BUFFER = new StringBuilder();

    private SuggestionBarInputHook() {
    }

    public static void onKeyDown(SuggestionBarCallback callback, int keyCode) {
        if (callback == null) {
            return;
        }
        boolean delete = keyCode == KeyEvent.KEYCODE_DEL;
        boolean enter = keyCode == KeyEvent.KEYCODE_ENTER;
        if (delete && INPUT_BUFFER.length() > 0) {
            INPUT_BUFFER.deleteCharAt(INPUT_BUFFER.length() - 1);
        } else if (enter) {
            INPUT_BUFFER.setLength(0);
        }
        if (delete || enter) {
            callback.reloadSuggestionBar(INPUT_BUFFER.toString());
        }
    }

    public static void onCodePoint(SuggestionBarCallback callback, int codePoint, boolean ctrlDown) {
        if (callback == null || ctrlDown) {
            return;
        }
        INPUT_BUFFER.append((char) codePoint);
        callback.reloadSuggestionBar(INPUT_BUFFER.toString());
    }

    public static void onTerminalCleared(SuggestionBarCallback callback) {
        if (callback == null) {
            return;
        }
        INPUT_BUFFER.setLength(0);
        callback.reloadSuggestionBar("");
    }
}
