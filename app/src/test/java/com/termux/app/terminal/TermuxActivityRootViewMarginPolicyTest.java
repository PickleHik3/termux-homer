package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TermuxActivityRootViewMarginPolicyTest {

    @Test
    public void imeHidden_forcesZeroMargin() {
        int margin = TermuxActivityRootView.computeImeDrivenBottomMarginPx(
            false,
            620,
            120,
            180,
            2000,
            1200,
            16,
            false
        );

        assertEquals(0, margin);
    }

    @Test
    public void imeVisible_prefersMeasuredOverlapWhenAvailable() {
        int margin = TermuxActivityRootView.computeImeDrivenBottomMarginPx(
            true,
            520,
            120,
            460,
            2000,
            1200,
            16,
            false
        );

        assertEquals(460, margin);
    }

    @Test
    public void imeVisible_smallMarginsAreIgnored() {
        int margin = TermuxActivityRootView.computeImeDrivenBottomMarginPx(
            true,
            126,
            120,
            10,
            2000,
            1200,
            16,
            false
        );

        assertEquals(0, margin);
    }

    @Test
    public void marginsAreClampedByRootHeightCap() {
        int margin = TermuxActivityRootView.computeImeDrivenBottomMarginPx(
            true,
            920,
            120,
            0,
            800,
            1200,
            16,
            true
        );

        // 70% of root height is 560; this cap should apply before the hard cap.
        assertEquals(560, margin);
    }

    @Test
    public void legacyOverlapMarginUsesSameThresholdsAndCaps() {
        int margin = TermuxActivityRootView.computeLegacyOverlapMarginPx(
            900,
            1000,
            1200,
            16
        );

        assertEquals(700, margin);
    }

    @Test
    public void imeFallbackDisabled_ignoresTransientImeInsetSpike() {
        int margin = TermuxActivityRootView.computeImeDrivenBottomMarginPx(
            true,
            520,
            120,
            0,
            2000,
            1200,
            16,
            false
        );

        assertEquals(0, margin);
    }

    @Test
    public void imeFallbackEnabled_usesImeInsetWhenOverlapUnavailable() {
        int margin = TermuxActivityRootView.computeImeDrivenBottomMarginPx(
            true,
            520,
            120,
            0,
            2000,
            1200,
            16,
            true
        );

        assertEquals(400, margin);
    }
}
