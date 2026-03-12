package com.termux.app.launcher.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * No-root floating launch animation inspired by Launcher3/Lawnchair icon expansion.
 */
public final class FloatingLaunchAnimator {

    public static final long RECOMMENDED_LAUNCH_DELAY_MS = 64L;
    private static final long MORPH_DURATION_MS = 220L;
    private static final long SCRIM_DURATION_MS = 110L;
    private static final long SOURCE_PRESS_DURATION_MS = 54L;
    private static final long CLEANUP_FADE_MS = 90L;

    private FloatingLaunchAnimator() {}

    public static boolean play(@NonNull View sourceView, int baseColor) {
        if (!sourceView.isAttachedToWindow()) {
            return false;
        }
        Activity activity = findActivity(sourceView.getContext());
        if (activity == null) {
            return false;
        }
        View decorView = activity.getWindow().getDecorView();
        if (!(decorView instanceof ViewGroup)) {
            return false;
        }
        ViewGroup root = (ViewGroup) decorView;
        if (root.getWidth() <= 0 || root.getHeight() <= 0) {
            return false;
        }
        Rect from = viewBoundsOnScreen(sourceView);
        Rect rootBounds = viewBoundsOnScreen(root);
        if (from == null || rootBounds == null) {
            return false;
        }
        from.offset(-rootBounds.left, -rootBounds.top);
        if (from.width() <= 0 || from.height() <= 0) {
            return false;
        }
        Rect to = new Rect(0, 0, root.getWidth(), root.getHeight());
        int color = 0xFF000000 | (baseColor & 0x00FFFFFF);
        int scrimColor = 0x12000000 | (baseColor & 0x00FFFFFF);

        FrameLayout overlay = new FrameLayout(sourceView.getContext());
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);
        FrameLayout.LayoutParams scrimLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );

        View scrim = new View(sourceView.getContext());
        scrim.setBackgroundColor(scrimColor);
        scrim.setAlpha(0f);
        overlay.addView(scrim, scrimLp);

        View card = new View(sourceView.getContext());
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(0x00000000);
        float startRadius = Math.max(12f, Math.min(from.width(), from.height()) * 0.26f);
        cardBackground.setStroke(dp(sourceView.getContext(), 2f), (0xCC << 24) | (color & 0x00FFFFFF));
        cardBackground.setCornerRadius(startRadius);
        card.setBackground(cardBackground);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(from.width(), from.height());
        cardLp.leftMargin = from.left;
        cardLp.topMargin = from.top;
        overlay.addView(card, cardLp);

        root.addView(overlay, overlayLp);

        ValueAnimator morph = ValueAnimator.ofFloat(0f, 1f);
        morph.setDuration(MORPH_DURATION_MS);
        morph.setInterpolator(LauncherAnimationInterpolators.TOUCH_RESPONSE);
        morph.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            cardLp.leftMargin = lerp(from.left, to.left, t);
            cardLp.topMargin = lerp(from.top, to.top, t);
            cardLp.width = Math.max(1, lerp(from.width(), to.width(), t));
            cardLp.height = Math.max(1, lerp(from.height(), to.height(), t));
            card.setLayoutParams(cardLp);
            float radiusProgress = LauncherAnimationInterpolators.EMPHASIZED_DECELERATE.getInterpolation(t);
            cardBackground.setCornerRadius(lerp(startRadius, 0f, radiusProgress));
            card.setAlpha(lerp(0.75f, 0.08f, t));
        });

        ObjectAnimator scrimIn = ObjectAnimator.ofFloat(scrim, View.ALPHA, 0f, 0.08f);
        scrimIn.setDuration(SCRIM_DURATION_MS);
        scrimIn.setInterpolator(LauncherAnimationInterpolators.LINEAR_OUT_SLOW_IN);

        ObjectAnimator pressX = ObjectAnimator.ofFloat(sourceView, View.SCALE_X, 1f, 0.94f);
        ObjectAnimator pressY = ObjectAnimator.ofFloat(sourceView, View.SCALE_Y, 1f, 0.94f);
        pressX.setDuration(SOURCE_PRESS_DURATION_MS);
        pressY.setDuration(SOURCE_PRESS_DURATION_MS);
        pressX.setInterpolator(LauncherAnimationInterpolators.FAST_OUT_SLOW_IN);
        pressY.setInterpolator(LauncherAnimationInterpolators.FAST_OUT_SLOW_IN);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(morph, scrimIn, pressX, pressY);
        set.addListener(new AnimatorListenerAdapter() {
            private boolean removed;

            @Override
            public void onAnimationEnd(Animator animation) {
                sourceView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160L)
                    .setInterpolator(LauncherAnimationInterpolators.EMPHASIZED_DECELERATE)
                    .start();
                cleanup();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                sourceView.setScaleX(1f);
                sourceView.setScaleY(1f);
                cleanup();
            }

            private void cleanup() {
                if (removed) {
                    return;
                }
                removed = true;
                overlay.animate()
                    .alpha(0f)
                    .setDuration(CLEANUP_FADE_MS)
                    .setInterpolator(LauncherAnimationInterpolators.LINEAR)
                    .withEndAction(() -> root.removeView(overlay))
                    .start();
            }
        });
        set.start();
        return true;
    }

    private static int lerp(int start, int end, float t) {
        return Math.round(start + (end - start) * t);
    }

    private static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private static int dp(@NonNull Context context, float value) {
        return Math.max(1, Math.round(value * context.getResources().getDisplayMetrics().density));
    }

    @Nullable
    private static Rect viewBoundsOnScreen(@Nullable View view) {
        if (view == null || !view.isAttachedToWindow()) {
            return null;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
    }

    @Nullable
    private static Activity findActivity(@Nullable Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }
}
