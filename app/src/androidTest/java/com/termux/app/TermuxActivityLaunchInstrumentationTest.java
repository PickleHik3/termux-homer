package com.termux.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class TermuxActivityLaunchInstrumentationTest {

    private static final int TEST_WIDTH = 1080;
    private static final int TEST_HEIGHT = 1920;

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    public void termuxActivityStartsAndRecreates() {
        try (ActivityScenario<TermuxActivity> scenario = ActivityScenario.launch(TermuxActivity.class)) {
            scenario.recreate();
            scenario.onActivity(activity -> assertTrue("TermuxActivity should reach RESUMED",
                    activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)));
        }
    }

    @Test
    public void relativeLayoutMeasureEnforcesRelativeLayoutParams() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RelativeLayout parent = new RelativeLayout(context);
        View child = new View(context);
        RelativeLayout.LayoutParams correctParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        parent.addView(child, correctParams);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(TEST_WIDTH, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(TEST_HEIGHT, View.MeasureSpec.EXACTLY);
        child.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        assertThrows(ClassCastException.class, () -> parent.measure(widthSpec, heightSpec));
        child.setLayoutParams(correctParams);
        parent.measure(widthSpec, heightSpec);
        parent.layout(0, 0, parent.getMeasuredWidth(), parent.getMeasuredHeight());
    }
}
