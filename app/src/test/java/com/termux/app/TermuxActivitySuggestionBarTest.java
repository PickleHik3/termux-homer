package com.termux.app;

import android.os.Build;
import android.util.DisplayMetrics;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class TermuxActivitySuggestionBarTest {

    @Test
    public void setSuggestionBarView_appliesHostMaxButtons() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        ReflectionHelpers.callInstanceMethod(activity, "setSuggestionBarView");

        assertNotNull(activity.mSuggestionBarView);
        int appliedMax = ReflectionHelpers.getField(activity.mSuggestionBarView, "maxButtonCount");
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int expectedMax;
        if (activity.getPreferences() == null) {
            expectedMax = 0;
        } else {
            int configuredMax = activity.getPreferences().getAppLauncherButtonCount();
            expectedMax = configuredMax > 0 ? configuredMax : TermuxActivity.calculateSuggestionBarMaxButtons(metrics);
        }

        assertEquals(expectedMax, appliedMax);
    }
}
