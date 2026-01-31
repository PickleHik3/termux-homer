package com.termux.app.terminal;

import android.view.KeyEvent;

import com.termux.app.SuggestionBarCallback;

import org.junit.Assert;
import org.junit.Test;

public class SuggestionBarInputHookTest {

    private static final class RecordingCallback implements SuggestionBarCallback {
        private int charCalls;
        private int keyCalls;
        private Character lastChar;
        private Boolean lastDelete;
        private Boolean lastEnter;

        @Override
        public void reloadSuggestionBar(char inputChar) {
            charCalls++;
            lastChar = inputChar;
        }

        @Override
        public void reloadSuggestionBar(boolean delete, boolean enter) {
            keyCalls++;
            lastDelete = delete;
            lastEnter = enter;
        }
    }

    @Test
    public void codePointTriggersCallbackWhenCtrlNotDown() {
        RecordingCallback callback = new RecordingCallback();
        SuggestionBarInputHook.onCodePoint(callback, 'a', false);

        Assert.assertEquals(1, callback.charCalls);
        Assert.assertEquals(Character.valueOf('a'), callback.lastChar);
        Assert.assertEquals(0, callback.keyCalls);
    }

    @Test
    public void codePointDoesNotTriggerWhenCtrlDown() {
        RecordingCallback callback = new RecordingCallback();
        SuggestionBarInputHook.onCodePoint(callback, 'b', true);

        Assert.assertEquals(0, callback.charCalls);
        Assert.assertNull(callback.lastChar);
    }

    @Test
    public void deleteAndEnterTriggerKeyCallback() {
        RecordingCallback callback = new RecordingCallback();

        SuggestionBarInputHook.onKeyDown(callback, KeyEvent.KEYCODE_DEL);
        Assert.assertEquals(1, callback.keyCalls);
        Assert.assertEquals(Boolean.TRUE, callback.lastDelete);
        Assert.assertEquals(Boolean.FALSE, callback.lastEnter);

        SuggestionBarInputHook.onKeyDown(callback, KeyEvent.KEYCODE_ENTER);
        Assert.assertEquals(2, callback.keyCalls);
        Assert.assertEquals(Boolean.FALSE, callback.lastDelete);
        Assert.assertEquals(Boolean.TRUE, callback.lastEnter);
    }
}
