package com.termux.view;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalViewCurrentInputTest {

    @Test
    public void extractCurrentInput_returnsNullWhenSplitCharMissing() {
        assertNull(TerminalView.extractCurrentInputFromLine("command", 0, ':', null));
    }

    @Test
    public void extractCurrentInput_sanitizesInput() {
        String result = TerminalView.extractCurrentInputFromLine("shell: git status!", 0, ':', null);
        assertEquals("git status", result);
    }

    @Test
    public void extractCurrentInput_collapsesSpaces() {
        String result = TerminalView.extractCurrentInputFromLine("prompt:   foo   bar", 0, ':', null);
        assertEquals("foo bar", result);
    }

    @Test
    public void extractCurrentInput_insertsCharAtEnd() {
        String result = TerminalView.extractCurrentInputFromLine("cmd: foo", 8, ':', 'x');
        assertEquals("foox", result);
    }

    @Test
    public void extractCurrentInput_insertsCharInMiddle() {
        String result = TerminalView.extractCurrentInputFromLine("cmd: foo", 7, ':', 'x');
        assertEquals("foxo", result);
    }

    @Test
    public void extractCurrentInput_insertsCharAtStart() {
        String result = TerminalView.extractCurrentInputFromLine("cmd: foo", 0, ':', 'x');
        assertEquals("foox", result);
    }
}
