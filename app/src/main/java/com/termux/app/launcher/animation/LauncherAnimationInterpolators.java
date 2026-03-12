package com.termux.app.launcher.animation;

import android.graphics.Path;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Interpolators aligned with Launcher3/Lawnchair motion profiles.
 */
public final class LauncherAnimationInterpolators {

    public static final Interpolator LINEAR = new LinearInterpolator();
    public static final Interpolator FAST_OUT_SLOW_IN = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    public static final Interpolator LINEAR_OUT_SLOW_IN = new PathInterpolator(0f, 0f, 0.2f, 1f);
    public static final Interpolator TOUCH_RESPONSE = new PathInterpolator(0.3f, 0f, 0.1f, 1f);
    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    public static final Interpolator EMPHASIZED = createEmphasizedInterpolator();

    private LauncherAnimationInterpolators() {}

    private static PathInterpolator createEmphasizedInterpolator() {
        Path path = new Path();
        path.moveTo(0f, 0f);
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return new PathInterpolator(path);
    }
}

