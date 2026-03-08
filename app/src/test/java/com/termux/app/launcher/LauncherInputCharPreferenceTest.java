package com.termux.app.launcher;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LauncherInputCharPreferenceTest {

    @Test
    public void blankInputCharFallsBackToSlash() {
        assertEquals("/", TermuxAppSharedPreferences.normalizeAppLauncherInputChar(""));
    }

    @Test
    public void nullInputCharFallsBackToSlash() {
        assertEquals("/", TermuxAppSharedPreferences.normalizeAppLauncherInputChar(null));
    }

    @Test
    public void nonBlankInputCharIsPreserved() {
        assertEquals(";", TermuxAppSharedPreferences.normalizeAppLauncherInputChar(";"));
    }
}
