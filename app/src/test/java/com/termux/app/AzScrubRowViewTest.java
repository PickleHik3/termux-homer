package com.termux.app;

import android.os.Build;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@LooperMode(LooperMode.Mode.LEGACY)
public class AzScrubRowViewTest {

    @Test
    public void scrubMapping_isDeterministic() {
        AzScrubRowView view = new AzScrubRowView(RuntimeEnvironment.application);
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(540, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(48, android.view.View.MeasureSpec.EXACTLY)
        );
        view.layout(0, 0, 540, 48);

        final char[] lastLetter = {'?'};
        final int[] lastSelection = {-1};
        final boolean[] committed = {false};

        view.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char letter, int selectionIndex, boolean commit) {
                lastLetter[0] = letter;
                lastSelection[0] = selectionIndex;
                committed[0] = commit;
            }

            @Override
            public void onCancel() {}
        });

        view.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 0f, 24f, 0));
        assertEquals('A', lastLetter[0]);
        assertEquals(0, lastSelection[0]);
        assertFalse(committed[0]);

        view.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 539f, 24f, 0));
        assertEquals('#', lastLetter[0]);

        view.onTouchEvent(MotionEvent.obtain(0, 30, MotionEvent.ACTION_MOVE, 200f, -40f, 0));
        assertTrue(lastSelection[0] >= 1);
        assertFalse(committed[0]);

        view.onTouchEvent(MotionEvent.obtain(0, 40, MotionEvent.ACTION_UP, 200f, -40f, 0));
        assertFalse(committed[0]);
    }
}
