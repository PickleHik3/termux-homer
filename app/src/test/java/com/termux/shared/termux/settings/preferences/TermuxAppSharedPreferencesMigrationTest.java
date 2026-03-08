package com.termux.shared.termux.settings.preferences;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TermuxAppSharedPreferencesMigrationTest {

    @Test
    public void migrationEnablesWhenNotDoneAndNotStored() {
        assertTrue(TermuxAppSharedPreferences.shouldEnableTerminalMarginAdjustmentOnMigration(false, false, false));
    }

    @Test
    public void migrationEnablesWhenNotDoneAndExplicitlyDisabled() {
        assertTrue(TermuxAppSharedPreferences.shouldEnableTerminalMarginAdjustmentOnMigration(false, true, false));
    }

    @Test
    public void migrationDoesNotEnableWhenAlreadyDone() {
        assertFalse(TermuxAppSharedPreferences.shouldEnableTerminalMarginAdjustmentOnMigration(true, true, false));
    }

    @Test
    public void migrationDoesNotEnableWhenNotDoneButAlreadyEnabledAndStored() {
        assertFalse(TermuxAppSharedPreferences.shouldEnableTerminalMarginAdjustmentOnMigration(false, true, true));
    }
}
