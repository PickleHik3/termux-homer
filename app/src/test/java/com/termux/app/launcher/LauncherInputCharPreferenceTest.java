package com.termux.app.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@LooperMode(LooperMode.Mode.LEGACY)
public class LauncherInputCharPreferenceTest {

    @Test
    public void blankInputCharFallsBackToSlash() {
        Context context = RuntimeEnvironment.application;
        TermuxAppSharedPreferences preferences = ReflectionHelpers.callConstructor(
            TermuxAppSharedPreferences.class,
            ReflectionHelpers.ClassParameter.from(Context.class, context)
        );

        SharedPreferences rawPreferences = preferences.getSharedPreferences();
        rawPreferences.edit()
            .putString(TERMUX_APP.KEY_APP_LAUNCHER_INPUT_CHAR, "")
            .commit();

        assertEquals("/", preferences.getAppLauncherInputChar());
    }
}
