package com.termux.shared.termux.settings.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class TermuxAppSharedPreferencesMigrationTest {

    @Test
    public void migrationEnablesMarginAdjustmentWhenPreviouslyDisabled() {
        Context context = RuntimeEnvironment.application;
        TermuxAppSharedPreferences preferences = ReflectionHelpers.callConstructor(
            TermuxAppSharedPreferences.class,
            ReflectionHelpers.ClassParameter.from(Context.class, context)
        );

        preferences.setTerminalMarginAdjustment(false);
        preferences.getSharedPreferences()
            .edit()
            .putBoolean(TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT_DEFAULT_MIGRATION_DONE, false)
            .apply();

        preferences.migrateTerminalMarginAdjustmentDefaultIfNeeded();

        assertTrue(preferences.isTerminalMarginAdjustmentEnabled());
        assertTrue(SharedPreferenceUtils.getBoolean(
            preferences.getSharedPreferences(),
            TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT_DEFAULT_MIGRATION_DONE,
            false
        ));
    }

    @Test
    public void migrationDoesNotOverrideWhenAlreadyMarkedDone() {
        Context context = RuntimeEnvironment.application;
        TermuxAppSharedPreferences preferences = ReflectionHelpers.callConstructor(
            TermuxAppSharedPreferences.class,
            ReflectionHelpers.ClassParameter.from(Context.class, context)
        );

        SharedPreferences rawPreferences = preferences.getSharedPreferences();
        preferences.setTerminalMarginAdjustment(false);
        rawPreferences.edit()
            .putBoolean(TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT_DEFAULT_MIGRATION_DONE, true)
            .apply();

        preferences.migrateTerminalMarginAdjustmentDefaultIfNeeded();

        assertFalse(preferences.isTerminalMarginAdjustmentEnabled());
    }
}
