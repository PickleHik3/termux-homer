package com.termux.app.terminal;

import android.view.KeyEvent;

import com.termux.app.SuggestionBarCallback;

public final class SuggestionBarInputHook {

    private SuggestionBarInputHook() {
    }

    public static void onKeyDown(SuggestionBarCallback callback, int keyCode) {
        if (callback == null) {
            return;
        }
        boolean delete = keyCode == KeyEvent.KEYCODE_DEL;
        boolean enter = keyCode == KeyEvent.KEYCODE_ENTER;
        if (delete || enter) {
            callback.reloadSuggestionBar(delete, enter);
        }
    }

    public static void onCodePoint(SuggestionBarCallback callback, int codePoint, boolean ctrlDown) {
        if (callback == null || ctrlDown) {
            return;
        }
        callback.reloadSuggestionBar((char) codePoint);
    }

    public static void onTerminalCleared(SuggestionBarCallback callback) {
        if (callback == null) {
            return;
        }
        callback.reloadSuggestionBar(false, true);
    }
}
