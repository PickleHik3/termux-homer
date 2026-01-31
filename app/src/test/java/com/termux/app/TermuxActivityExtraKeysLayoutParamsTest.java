package com.termux.app;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
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
}
