package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.github.mmin18.widget.RealtimeBlurView;
import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityExtraKeysLayoutParamsTest {

    @Test
    public void updateExtraKeysBackgroundHeight_keepsRelativeLayoutParams() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        View extraKeysBackground = activity.findViewById(R.id.extrakeys_background);
        View extraKeysBackgroundBlur = activity.findViewById(R.id.extrakeys_backgroundblur);
        RelativeLayout rootRelativeLayout = activity.findViewById(R.id.activity_termux_root_relative_layout);

        assertNotNull(extraKeysBackground);
        assertNotNull(extraKeysBackgroundBlur);
        assertNotNull(rootRelativeLayout);

        ViewGroup.LayoutParams backgroundLpBefore = extraKeysBackground.getLayoutParams();
        ViewGroup.LayoutParams backgroundBlurLpBefore = extraKeysBackgroundBlur.getLayoutParams();

        assertTrue(backgroundLpBefore instanceof RelativeLayout.LayoutParams);
        assertTrue(backgroundBlurLpBefore instanceof RelativeLayout.LayoutParams);

        int expectedHeight = 123;

        ReflectionHelpers.callInstanceMethod(activity, "updateExtraKeysBackgroundHeight",
                ReflectionHelpers.ClassParameter.from(View.class, extraKeysBackground),
                ReflectionHelpers.ClassParameter.from(int.class, expectedHeight));
        ReflectionHelpers.callInstanceMethod(activity, "updateExtraKeysBackgroundHeight",
                ReflectionHelpers.ClassParameter.from(View.class, extraKeysBackgroundBlur),
                ReflectionHelpers.ClassParameter.from(int.class, expectedHeight));

        ViewGroup.LayoutParams backgroundLpAfter = extraKeysBackground.getLayoutParams();
        ViewGroup.LayoutParams backgroundBlurLpAfter = extraKeysBackgroundBlur.getLayoutParams();

        assertEquals(RelativeLayout.LayoutParams.class, backgroundLpAfter.getClass());
        assertEquals(RelativeLayout.LayoutParams.class, backgroundBlurLpAfter.getClass());
        assertEquals(expectedHeight, backgroundLpAfter.height);
        assertEquals(expectedHeight, backgroundBlurLpAfter.height);
        assertSame(backgroundLpBefore, backgroundLpAfter);
        assertSame(backgroundBlurLpBefore, backgroundBlurLpAfter);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
        rootRelativeLayout.measure(widthSpec, heightSpec);
        rootRelativeLayout.layout(0, 0, 1080, 1920);
    }

    @Test
    public void realtimeBlurViews_areInflatedAndConfigurable() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        View sessionsBlur = activity.findViewById(R.id.sessions_backgroundblur);
        View extraKeysBlur = activity.findViewById(R.id.extrakeys_backgroundblur);

        assertTrue(sessionsBlur instanceof RealtimeBlurView);
        assertTrue(extraKeysBlur instanceof RealtimeBlurView);

        ((RealtimeBlurView) sessionsBlur).setBlurRadius(12f);
        ((RealtimeBlurView) extraKeysBlur).setBlurRadius(16f);
    }

    @Test
    public void applyTerminalStatusBarInset_offsetsOnlyBackgroundLayers() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        View terminalMonetBackground = activity.findViewById(R.id.terminal_monetbackground);
        View terminalBlurBackground = activity.findViewById(R.id.terminal_backgroundblur);
        View terminalGrainOverlay = activity.findViewById(R.id.terminal_grain_overlay);
        RelativeLayout terminalContentRoot = activity.findViewById(R.id.activity_termux_root_relative_layout);

        assertNotNull(terminalMonetBackground);
        assertNotNull(terminalBlurBackground);
        assertNotNull(terminalGrainOverlay);
        assertNotNull(terminalContentRoot);

        ReflectionHelpers.callInstanceMethod(activity, "applyTerminalStatusBarInset",
            ReflectionHelpers.ClassParameter.from(int.class, 32));

        assertEquals(-32, ((FrameLayout.LayoutParams) terminalMonetBackground.getLayoutParams()).topMargin);
        assertEquals(-32, ((FrameLayout.LayoutParams) terminalBlurBackground.getLayoutParams()).topMargin);
        assertEquals(-32, ((FrameLayout.LayoutParams) terminalGrainOverlay.getLayoutParams()).topMargin);
        assertEquals(32, terminalContentRoot.getPaddingTop());

        ReflectionHelpers.callInstanceMethod(activity, "applyTerminalStatusBarInset",
            ReflectionHelpers.ClassParameter.from(int.class, 0));

        assertEquals(0, ((FrameLayout.LayoutParams) terminalMonetBackground.getLayoutParams()).topMargin);
        assertEquals(0, ((FrameLayout.LayoutParams) terminalBlurBackground.getLayoutParams()).topMargin);
        assertEquals(0, ((FrameLayout.LayoutParams) terminalGrainOverlay.getLayoutParams()).topMargin);
        assertEquals(0, terminalContentRoot.getPaddingTop());
    }
}
