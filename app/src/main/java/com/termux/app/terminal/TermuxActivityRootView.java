package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.view.ViewUtils;

/**
 * The {@link TermuxActivity} relies on {@link android.view.WindowManager.LayoutParams#SOFT_INPUT_ADJUST_RESIZE)}
 * set by {@link TermuxTerminalViewClient#setSoftKeyboardState(boolean, boolean)} to automatically
 * resize the view and push the terminal up when soft keyboard is opened. However, this does not
 * always work properly. When `enforce-char-based-input=true` is set in `termux.properties`
 * and {@link com.termux.view.TerminalView#onCreateInputConnection(EditorInfo)} sets the inputType
 * to `InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS`
 * instead of the default `InputType.TYPE_NULL` for termux, some keyboards may still show suggestions.
 * Gboard does too, but only when text is copied and clipboard suggestions **and** number keys row
 * toggles are enabled in its settings. When number keys row toggle is not enabled, Gboard will still
 * show the row but will switch it with suggestions if needed. If its enabled, then number keys row
 * is always shown and suggestions are shown in an additional row on top of it. This additional row is likely
 * part of the candidates view returned by the keyboard app in {@link InputMethodService#onCreateCandidatesView()}.
 *
 * With the above configuration, the additional clipboard suggestions row partially covers the
 * extra keys/terminal. Reopening the keyboard/activity does not fix the issue. This is either a bug
 * in the Android OS where it does not consider the candidate's view height in its calculation to push
 * up the view or because Gboard does not include the candidate's view height in the height reported
 * to android that should be used, hence causing an overlap.
 *
 * Gboard logs the following entry to `logcat` when its opened with or without the suggestions bar showing:
 * I/KeyboardViewUtil: KeyboardViewUtil.calculateMaxKeyboardBodyHeight():62 leave 500 height for app when screen height:2392, header height:176 and isFullscreenMode:false, so the max keyboard body height is:1716
 * where `keyboard_height = screen_height - height_for_app - header_height` (62 is a hardcoded value in Gboard source code and may be a version number)
 * So this may in fact be due to Gboard but https://stackoverflow.com/questions/57567272 suggests
 * otherwise. Another similar report https://stackoverflow.com/questions/66761661.
 * Also check https://github.com/termux/termux-app/issues/1539.
 *
 * This overlap may happen even without `enforce-char-based-input=true` for keyboards with extended layouts
 * like number row, etc.
 *
 * To fix these issues, `activity_termux.xml` has the constant 1sp transparent
 * `activity_termux_bottom_space_view` View at the bottom. This will appear as a line matching the
 * activity theme. When {@link TermuxActivity} {@link ViewTreeObserver.OnGlobalLayoutListener} is
 * called when any of the sub view layouts change,  like keyboard opening/closing keyboard,
 * extra keys/input view switched, etc, we check if the bottom space view is visible or not.
 * If its not, then we add a margin to the bottom of the root view, so that the keyboard does not
 * overlap the extra keys/terminal, since the margin will push up the view. By default the margin
 * added is equal to the height of the hidden part of extra keys/terminal. For Gboard's case, the
 * hidden part equals the `header_height`. The updates to margins may cause a jitter in some cases
 * when the view is redrawn if the margin is incorrect, but logic has been implemented to avoid that.
 */
public class TermuxActivityRootView extends LinearLayout implements ViewTreeObserver.OnGlobalLayoutListener {

    public TermuxActivity mActivity;

    public Integer marginBottom;

    public Integer lastMarginBottom;

    public long lastMarginBottomTime;

    public long lastMarginBottomExtraTime;
    private long lastMarginApplyTime;
    private boolean mInsetsInitialized;
    private boolean mLastImeVisible;
    private int mLastImeBottomInset;
    private int mLastSystemBarsBottomInset;

    /**
     * Log root view events.
     */
    private boolean ROOT_VIEW_LOGGING_ENABLED = false;

    private static final String LOG_TAG = "TermuxActivityRootView";

    private static int mStatusBarHeight;
    private static final int SMALL_MARGIN_THRESHOLD_DP = 16;
    private static final int MAX_MARGIN_ADJUSTMENT_DP = 420;
    private static final int JITTER_DELTA_THRESHOLD_DP = 28;
    private static final long MARGIN_APPLY_DEBOUNCE_MS = 140L;

    public TermuxActivityRootView(Context context) {
        super(context);
    }

    public TermuxActivityRootView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TermuxActivityRootView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setActivity(TermuxActivity activity) {
        mActivity = activity;
    }

    /**
     * Re-apply the last known bottom margin before first post-resume layout when IME is already visible.
     * This reduces one-frame "full terminal then snap" flicker when returning from other apps.
     */
    public void preApplyLastKnownImeMarginIfVisible() {
        if (mActivity == null || lastMarginBottom == null || lastMarginBottom <= 0) {
            return;
        }
        InsetsSnapshot insetsSnapshot = resolveInsetsSnapshot();
        if (insetsSnapshot == null || !insetsSnapshot.imeVisible) {
            return;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) getLayoutParams();
        if (params != null && params.bottomMargin != lastMarginBottom) {
            params.setMargins(0, 0, 0, lastMarginBottom);
            setLayoutParams(params);
        }
    }

    /**
     * Sets whether root view logging is enabled or not.
     *
     * @param value The boolean value that defines the state.
     */
    public void setIsRootViewLoggingEnabled(boolean value) {
        ROOT_VIEW_LOGGING_ENABLED = value;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (marginBottom != null) {
            if (ROOT_VIEW_LOGGING_ENABLED)
                Logger.logVerbose(LOG_TAG, "onMeasure: Setting bottom margin to " + marginBottom);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) getLayoutParams();
            params.setMargins(0, 0, 0, marginBottom);
            setLayoutParams(params);
            marginBottom = null;
            requestLayout();
        }
    }

    @Override
    public void onGlobalLayout() {
        if (mActivity == null || !mActivity.isVisible())
            return;
        View bottomSpaceView = mActivity.getTermuxActivityBottomSpaceView();
        if (bottomSpaceView == null)
            return;
        boolean root_view_logging_enabled = ROOT_VIEW_LOGGING_ENABLED;
        if (root_view_logging_enabled)
            Logger.logVerbose(LOG_TAG, ":\nonGlobalLayout:");
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params == null)
            return;
        // Get the position Rects of the bottom space view and the main window holding it
        Rect[] windowAndViewRects = ViewUtils.getWindowAndViewRects(bottomSpaceView, mStatusBarHeight);
        if (windowAndViewRects == null)
            return;
        Rect windowAvailableRect = windowAndViewRects[0];
        Rect bottomSpaceViewRect = windowAndViewRects[1];

        int pxHidden = bottomSpaceViewRect.bottom - windowAvailableRect.bottom;
        int smallMarginThresholdPx = (int) ViewUtils.dpToPx(getContext(), SMALL_MARGIN_THRESHOLD_DP);
        int maxMarginPx = (int) ViewUtils.dpToPx(getContext(), MAX_MARGIN_ADJUSTMENT_DP);
        InsetsSnapshot insetsSnapshot = resolveInsetsSnapshot();
        int targetMarginPx;
        boolean shouldFallbackToLegacyOverlap = insetsSnapshot == null ||
            (!insetsSnapshot.imeVisible && pxHidden > smallMarginThresholdPx);
        if (!shouldFallbackToLegacyOverlap) {
            targetMarginPx = computeImeDrivenBottomMarginPx(
                insetsSnapshot.imeVisible,
                insetsSnapshot.imeBottomInsetPx,
                insetsSnapshot.systemBarsBottomInsetPx,
                pxHidden,
                getHeight(),
                maxMarginPx,
                smallMarginThresholdPx
            );
        } else {
            targetMarginPx = computeLegacyOverlapMarginPx(
                pxHidden,
                getHeight(),
                maxMarginPx,
                smallMarginThresholdPx
            );
        }

        long now = System.currentTimeMillis();
        int jitterDeltaPx = (int) ViewUtils.dpToPx(getContext(), JITTER_DELTA_THRESHOLD_DP);
        if (params.bottomMargin > 0 && targetMarginPx > 0 &&
            Math.abs(targetMarginPx - params.bottomMargin) <= jitterDeltaPx &&
            (now - lastMarginApplyTime) < MARGIN_APPLY_DEBOUNCE_MS) {
            targetMarginPx = params.bottomMargin;
        }

        // Keep the last stable visible position while IME dismiss animation/insets are settling.
        if (insetsSnapshot != null && !insetsSnapshot.imeVisible && pxHidden > 0 &&
            params.bottomMargin > 0 && (now - lastMarginApplyTime) < MARGIN_APPLY_DEBOUNCE_MS) {
            targetMarginPx = params.bottomMargin;
        }

        if (root_view_logging_enabled) {
            Logger.logVerbose(LOG_TAG, "windowAvailableRect " + ViewUtils.toRectString(windowAvailableRect) + ", bottomSpaceViewRect " + ViewUtils.toRectString(bottomSpaceViewRect));
            Logger.logVerbose(LOG_TAG,
                "imeVisible=" + (insetsSnapshot != null && insetsSnapshot.imeVisible) +
                ", imeBottomInset=" + (insetsSnapshot != null ? insetsSnapshot.imeBottomInsetPx : -1) +
                ", systemBottomInset=" + (insetsSnapshot != null ? insetsSnapshot.systemBarsBottomInsetPx : -1) +
                ", overlap=" + pxHidden + ", currentBottom=" + params.bottomMargin + ", targetBottom=" + targetMarginPx);
        }

        if (params.bottomMargin != targetMarginPx) {
            if (root_view_logging_enabled)
                Logger.logVerbose(LOG_TAG, "Setting bottom margin to " + targetMarginPx);
            params.setMargins(0, 0, 0, targetMarginPx);
            setLayoutParams(params);
            lastMarginBottom = targetMarginPx > 0 ? targetMarginPx : null;
            lastMarginApplyTime = now;
        } else if (root_view_logging_enabled) {
            Logger.logVerbose(LOG_TAG, "Bottom margin already equals " + targetMarginPx);
        }
    }

    void updateInsetsCache(WindowInsetsCompat compat) {
        if (compat == null)
            return;
        mLastImeVisible = compat.isVisible(WindowInsetsCompat.Type.ime());
        mLastImeBottomInset = compat.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        mLastSystemBarsBottomInset = compat.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        mInsetsInitialized = true;
    }

    @Nullable
    private InsetsSnapshot resolveInsetsSnapshot() {
        WindowInsets insets = getRootWindowInsets();
        if (insets != null) {
            WindowInsetsCompat compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this);
            updateInsetsCache(compat);
        }
        if (!mInsetsInitialized) {
            return null;
        }
        return new InsetsSnapshot(mLastImeVisible, mLastImeBottomInset, mLastSystemBarsBottomInset);
    }

    static int computeImeDrivenBottomMarginPx(boolean imeVisible, int imeBottomInsetPx, int systemBarsBottomInsetPx,
                                              int overlapPx, int rootHeightPx, int maxMarginPx,
                                              int smallMarginThresholdPx) {
        if (!imeVisible) {
            return 0;
        }
        int imeMarginPx = Math.max(0, imeBottomInsetPx - Math.max(0, systemBarsBottomInsetPx));
        int overlapMarginPx = Math.max(0, overlapPx);
        int targetMarginPx = Math.max(imeMarginPx, overlapMarginPx);
        if (targetMarginPx > 0 && targetMarginPx <= smallMarginThresholdPx) {
            targetMarginPx = 0;
        }
        return clampMarginPx(targetMarginPx, rootHeightPx, maxMarginPx);
    }

    static int computeLegacyOverlapMarginPx(int overlapPx, int rootHeightPx, int maxMarginPx,
                                            int smallMarginThresholdPx) {
        int targetMarginPx = Math.max(0, overlapPx);
        if (targetMarginPx > 0 && targetMarginPx <= smallMarginThresholdPx) {
            targetMarginPx = 0;
        }
        return clampMarginPx(targetMarginPx, rootHeightPx, maxMarginPx);
    }

    private static int clampMarginPx(int targetMarginPx, int rootHeightPx, int maxMarginPx) {
        int clamped = Math.max(0, targetMarginPx);
        int rootBasedCapPx = rootHeightPx > 0 ? Math.round(rootHeightPx * 0.70f) : Integer.MAX_VALUE;
        int hardCapPx = maxMarginPx > 0 ? maxMarginPx : Integer.MAX_VALUE;
        int effectiveCapPx = Math.min(rootBasedCapPx, hardCapPx);
        if (clamped > effectiveCapPx) {
            clamped = effectiveCapPx;
        }
        return clamped;
    }

    private static final class InsetsSnapshot {
        final boolean imeVisible;
        final int imeBottomInsetPx;
        final int systemBarsBottomInsetPx;

        InsetsSnapshot(boolean imeVisible, int imeBottomInsetPx, int systemBarsBottomInsetPx) {
            this.imeVisible = imeVisible;
            this.imeBottomInsetPx = imeBottomInsetPx;
            this.systemBarsBottomInsetPx = systemBarsBottomInsetPx;
        }
    }

    public static class WindowInsetsListener implements View.OnApplyWindowInsetsListener {

        @Override
        public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
            WindowInsetsCompat compat = WindowInsetsCompat.toWindowInsetsCompat(insets, v);
            mStatusBarHeight = compat.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            if (v instanceof TermuxActivityRootView) {
                ((TermuxActivityRootView) v).updateInsetsCache(compat);
            }
            // Let view window handle insets however it wants
            return v.onApplyWindowInsets(insets);
        }
    }
}
