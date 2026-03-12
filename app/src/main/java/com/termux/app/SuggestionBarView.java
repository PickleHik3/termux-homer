package com.termux.app;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.LauncherApps;
import android.content.pm.ChangedPackages;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.DragEvent;
import android.util.AttributeSet;
import android.util.Log;
import android.view.VelocityTracker;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mmin18.widget.RealtimeBlurView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.termux.R;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherRankingEngine;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.ref.WeakReference;

public final class SuggestionBarView extends GridLayout {

    private static final String LOG_TAG = "SuggestionBarView";
    private static final int TEXT_COLOR = 0xFFC0B18B;
    private static final char[] AZ_ORDER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray();
    private static final int POPUP_MAX_WIDTH_DP = 320;
    private static final int POPUP_MIN_WIDTH_DP = 188;
    private static final int POPUP_MIN_WIDTH_TIGHT_DP = 132;
    private static final float POPUP_MAX_HEIGHT_FACTOR = 0.45f;
    private static final long APP_LAUNCH_TOUCH_DELAY_MS = 40L;
    private static final long PICKUP_DECISION_WINDOW_MS = 650L;
    private static final float PICKUP_X_AXIS_SLOP_FACTOR = 0.9f;
    private static final float PICKUP_Y_INTENT_SLOP_FACTOR = 1.8f;
    private static final float MENU_SELECTION_ARM_SLOP_FACTOR = 0.8f;

    private List<LauncherAppEntry> allApps = new ArrayList<>();
    private int applicationSequenceNumber = 0;

    private int maxButtonCount = 7;
    private float textSize = 12f;
    private boolean showIcons = true;
    private boolean bandW = false;
    private int searchTolerance = 70;
    private float iconScale = 1.0f;
    private int appBarOpacity = 80;
    private boolean blurEnabled = false;
    private int blurRadiusDp = 10;
    private int inheritedTintColor = 0xFF202020;
    private List<String> defaultButtonStrings = new ArrayList<>();
    private final Map<String, WeakReference<View>> launchTargetViews = new HashMap<>();
    private final Map<String, WeakReference<View>> launchTargetViewsByPackage = new HashMap<>();

    private LauncherAppDataProvider appDataProvider;
    private LauncherConfigRepository configRepository;
    private List<PinnedItem> pinnedItems = new ArrayList<>();
    private List<SuggestionBarButton> injectedSuggestionButtons;

    private PopupWindow folderPopupWindow;
    private PopupWindow appContextPopupWindow;
    private PopupWindow shortcutsPopupWindow;

    private String lastInput = "";
    private TerminalView lastTerminalView;

    private Character activeAzLetter;
    private int activeAzSelection = 0;
    private int activeAzPageIndex = 0;
    private List<LauncherAppEntry> activeAzCandidates = new ArrayList<>();
    private int pinnedPageIndex = 0;
    private int pinnedItemsPerPage = 1;
    private float swipeDownX = 0f;
    private float swipeDownY = 0f;
    private VelocityTracker swipeVelocityTracker;
    private boolean pageSwitchAnimating = false;
    private int folderDragHoverIndex = -1;
    @Nullable private LongPressPickupState activeLongPressPickupState;
    @Nullable private AppMenuContext activeAppMenuContext;
    @Nullable private List<ShortcutInfo> activeAppMenuShortcuts;
    private final List<MenuActionRow> appContextRows = new ArrayList<>();
    private final List<MenuActionRow> shortcutsRows = new ArrayList<>();
    @Nullable private MenuActionRow activeMenuHighlight;
    private int activeMenuTintBase = (0xFF202020 & 0x00FFFFFF);
    @Nullable private TextView shortcutsMainRowView;
    private final Runnable azResetRunnable = this::clearAzPreviewWithFade;

    public SuggestionBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMaxButtonCount(int maxButtonCount) {
        this.maxButtonCount = maxButtonCount;
    }

    public void setTextSize(float textSize) {
        this.textSize = textSize;
    }

    public void setShowIcons(boolean showIcons) {
        this.showIcons = showIcons;
    }

    public void setBandW(boolean bandW) {
        this.bandW = bandW;
    }

    public void setSearchTolerance(int searchTolerance) {
        this.searchTolerance = searchTolerance;
    }

    public void setIconScale(float iconScale) {
        this.iconScale = iconScale;
    }

    public void setAppBarOpacity(int appBarOpacity) {
        this.appBarOpacity = appBarOpacity;
    }

    public void setBlurConfig(boolean blurEnabled, int blurRadiusDp) {
        this.blurEnabled = blurEnabled;
        this.blurRadiusDp = Math.max(0, blurRadiusDp);
    }

    public void setInheritedTintColor(int inheritedTintColor) {
        this.inheritedTintColor = inheritedTintColor;
    }

    public void setConfigRepository(@Nullable LauncherConfigRepository configRepository) {
        this.configRepository = configRepository;
        if (this.configRepository != null) {
            this.pinnedItems = this.configRepository.loadPinnedItems();
        }
    }

    public void setAppDataProvider(@Nullable LauncherAppDataProvider appDataProvider) {
        this.appDataProvider = appDataProvider;
    }

    public void setDefaultButtons(List<String> defaultButtons) {
        if (defaultButtons == null) {
            this.defaultButtonStrings = new ArrayList<>();
        } else {
            this.defaultButtonStrings = new ArrayList<>(defaultButtons);
        }
    }

    public void clearAppCache() {
        allApps = new ArrayList<>();
        applicationSequenceNumber = 0;
        activeAzLetter = null;
        activeAzCandidates = new ArrayList<>();
        activeAzPageIndex = 0;
        injectedSuggestionButtons = null;
        launchTargetViews.clear();
        launchTargetViewsByPackage.clear();
        cancelAzResetTimeout();
        if (appDataProvider != null) {
            appDataProvider.invalidate();
        }
    }

    public void setSuggestionButtons(@Nullable List<? extends SuggestionBarButton> suggestionButtons) {
        if (suggestionButtons == null) {
            this.injectedSuggestionButtons = null;
        } else {
            this.injectedSuggestionButtons = new ArrayList<>(suggestionButtons);
        }
        this.allApps = injectedToEntries(this.injectedSuggestionButtons);
    }

    void reloadAllApps() {
        if (injectedSuggestionButtons != null) {
            allApps = injectedToEntries(injectedSuggestionButtons);
            return;
        }
        if (appDataProvider == null) {
            appDataProvider = new LauncherAppDataProvider(getContext());
        }
        allApps = appDataProvider.getAllApps();
        if (configRepository != null) {
            pinnedItems = configRepository.loadPinnedItems();
        }
    }

    public void previewAzLetter(char letter, int selectionIndex, boolean commit) {
        if (appDataProvider == null) {
            appDataProvider = new LauncherAppDataProvider(getContext());
        }
        char normalized = Character.toUpperCase(letter);
        if (activeAzLetter == null || activeAzLetter != normalized) {
            activeAzPageIndex = 0;
        }
        activeAzLetter = normalized;
        activeAzSelection = Math.max(0, selectionIndex);
        cancelAzResetTimeout();
        activeAzCandidates = appDataProvider.getAppsForLetter(activeAzLetter);
        if (activeAzCandidates.isEmpty()) {
            if (commit) {
                clearAzPreview();
            }
            return;
        }

        if (commit) {
            int pageOffset = activeAzPageIndex * Math.max(1, maxButtonCount);
            int index = pageOffset + Math.min(activeAzSelection, Math.max(0, maxButtonCount - 1));
            index = Math.min(index, activeAzCandidates.size() - 1);
            launchEntry(activeAzCandidates.get(index), lastTerminalView);
            clearAzPreview();
            return;
        }

        renderButtons(activeAzCandidates, true);
    }

    public void persistAzPreview(char letter, int selectionIndex) {
        previewAzLetter(letter, selectionIndex, false);
        scheduleAzResetTimeout();
    }

    @NonNull
    public Set<Character> getAvailableAzLetters() {
        if (allApps == null || allApps.isEmpty()) {
            reloadAllApps();
        }
        LinkedHashSet<Character> letters = new LinkedHashSet<>();
        if (allApps != null) {
            for (LauncherAppEntry app : allApps) {
                char letter = LauncherAppDataProvider.normalizeLetter(app.label == null ? "" : app.label);
                letters.add(letter);
            }
        }
        if (letters.isEmpty()) {
            letters.add('#');
        }
        return letters;
    }

    public void clearAzPreview() {
        cancelAzResetTimeout();
        activeAzLetter = null;
        activeAzSelection = 0;
        activeAzPageIndex = 0;
        activeAzCandidates = new ArrayList<>();
        reloadWithInput(lastInput, lastTerminalView);
    }

    public void clearAzPreviewWithFade() {
        if (activeAzLetter == null) return;
        cancelAzResetTimeout();
        animate()
            .alpha(0.35f)
            .setDuration(120)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setListenerSafe(null);
                    activeAzLetter = null;
                    activeAzSelection = 0;
                    activeAzPageIndex = 0;
                    activeAzCandidates = new ArrayList<>();
                    reloadWithInput(lastInput, lastTerminalView);
                    setAlpha(0.35f);
                    animate()
                        .alpha(1f)
                        .setDuration(160)
                        .setInterpolator(new DecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                setListenerSafe(null);
                                setAlpha(1f);
                            }
                        })
                        .start();
                }
            })
            .start();
    }

    public void onTerminalInteraction() {
        if (activeAzLetter != null) {
            clearAzPreviewWithFade();
        }
    }

    private void scheduleAzResetTimeout() {
        cancelAzResetTimeout();
        postDelayed(azResetRunnable, 5000);
    }

    private void cancelAzResetTimeout() {
        removeCallbacks(azResetRunnable);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event == null) return super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (swipeVelocityTracker != null) swipeVelocityTracker.recycle();
            swipeVelocityTracker = VelocityTracker.obtain();
            swipeVelocityTracker.addMovement(event);
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            swipeDownX = event.getX();
            swipeDownY = event.getY();
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (swipeVelocityTracker != null) swipeVelocityTracker.addMovement(event);
        } else if (action == MotionEvent.ACTION_UP) {
            if (swipeVelocityTracker != null) {
                swipeVelocityTracker.addMovement(event);
                swipeVelocityTracker.computeCurrentVelocity(1000);
            }
            float dx = event.getX() - swipeDownX;
            float dy = event.getY() - swipeDownY;
            float vx = swipeVelocityTracker == null ? 0f : swipeVelocityTracker.getXVelocity();
            float threshold = dp(28);
            boolean swipeQualified = Math.abs(dx) > threshold || Math.abs(vx) > 900f;
            if (swipeQualified && Math.abs(dx) > Math.abs(dy) * 1.2f &&
                TextUtils.isEmpty(lastInput.trim())) {
                int pageDelta = dx < 0 ? 1 : -1;
                if (activeAzLetter != null) {
                    int totalPages = getAzPagesCount();
                    if (totalPages > 1) {
                        int next = clamp(activeAzPageIndex + pageDelta, 0, totalPages - 1);
                        if (next != activeAzPageIndex) {
                            animateAzPageSwitch(pageDelta, Math.max(Math.abs(vx), Math.abs(dx) * 8f));
                            if (swipeVelocityTracker != null) {
                                swipeVelocityTracker.recycle();
                                swipeVelocityTracker = null;
                            }
                            return true;
                        }
                    }
                } else if (pinnedItemsPerPage > 0) {
                    int totalPages = getPinnedPagesCount();
                    if (totalPages > 1) {
                        int next = clamp(pinnedPageIndex + pageDelta, 0, totalPages - 1);
                        if (next != pinnedPageIndex) {
                            animatePageSwitch(pageDelta, Math.max(Math.abs(vx), Math.abs(dx) * 8f));
                            if (swipeVelocityTracker != null) {
                                swipeVelocityTracker.recycle();
                                swipeVelocityTracker = null;
                            }
                            return true;
                        }
                    }
                }
            }
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
            if (swipeVelocityTracker != null) {
                swipeVelocityTracker.recycle();
                swipeVelocityTracker = null;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
            if (swipeVelocityTracker != null) {
                swipeVelocityTracker.recycle();
                swipeVelocityTracker = null;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    void reloadWithInput(String input, final TerminalView terminalView) {
        if (allApps == null || allApps.isEmpty()) {
            reloadAllApps();
        }

        this.lastTerminalView = terminalView;
        this.lastInput = input == null ? "" : input;
        if (activeAzLetter != null && !this.lastInput.trim().isEmpty()) {
            activeAzLetter = null;
            activeAzSelection = 0;
            activeAzPageIndex = 0;
            activeAzCandidates = new ArrayList<>();
            cancelAzResetTimeout();
        }

        PackageManager packageManager = getContext().getPackageManager();
        ChangedPackages changedPackages = packageManager.getChangedPackages(applicationSequenceNumber);
        if (changedPackages != null) {
            applicationSequenceNumber = changedPackages.getSequenceNumber();
            reloadAllApps();
        }

        if (activeAzLetter != null) {
            List<LauncherAppEntry> candidates = appDataProvider.getAppsForLetter(activeAzLetter);
            renderButtons(candidates, true);
            return;
        }

        List<LauncherAppEntry> suggestionEntries;
        String trimmed = lastInput.trim();
        if (!trimmed.isEmpty()) {
            suggestionEntries = LauncherRankingEngine.filterAndRank(allApps, trimmed, searchTolerance);
        } else {
            suggestionEntries = buildPinnedOrDefaultSurface();
        }

        renderButtons(suggestionEntries, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    void reload() {
        reloadWithInput("", null);
    }

    private List<LauncherAppEntry> buildPinnedOrDefaultSurface() {
        if (configRepository != null) {
            if (pinnedItems == null || pinnedItems.isEmpty()) {
                pinnedItems = configRepository.loadPinnedItems();
            }
            if (pinnedItems != null && !pinnedItems.isEmpty()) {
                return entriesForPinnedItems(pinnedItems);
            }
        }
        return new ArrayList<>();
    }

    private void renderButtons(@NonNull List<LauncherAppEntry> entries, boolean azPreview) {
        removeAllViews();
        launchTargetViews.clear();
        launchTargetViewsByPackage.clear();
        int buttonCount = Math.max(1, maxButtonCount);
        int renderStartCol = 0;
        List<PinnedItem> pinnedForSlots = new ArrayList<>();
        int pinnedPageOffset = 0;
        int azTotalMatches = entries.size();

        if (azPreview) {
            int perPage = Math.max(1, maxButtonCount);
            int totalPages = getAzPagesCount();
            activeAzPageIndex = clamp(activeAzPageIndex, 0, Math.max(0, totalPages - 1));
            int offset = activeAzPageIndex * perPage;
            List<LauncherAppEntry> pageEntries = new ArrayList<>();
            for (int i = offset; i < entries.size() && pageEntries.size() < perPage; i++) {
                pageEntries.add(entries.get(i));
            }
            entries = pageEntries;
            buttonCount = perPage;
            pinnedItemsPerPage = 1;
            pinnedPageIndex = 0;
            if (azTotalMatches <= perPage && !entries.isEmpty() && activeAzLetter != null) {
                float anchor = computeAzAnchorPosition(activeAzLetter, perPage);
                int centeredStart = Math.round(anchor - ((entries.size() - 1) / 2f));
                renderStartCol = clamp(centeredStart, 0, Math.max(0, perPage - entries.size()));
            }
        }

        boolean pinnedSurface = !azPreview && TextUtils.isEmpty(lastInput.trim()) && pinnedItems != null && !pinnedItems.isEmpty();
        if (pinnedSurface) {
            pinnedItemsPerPage = computePinnedItemsPerPage();
            int totalPages = getPinnedPagesCount();
            pinnedPageIndex = clamp(pinnedPageIndex, 0, Math.max(0, totalPages - 1));
            pinnedPageOffset = pinnedPageIndex * pinnedItemsPerPage;
            for (int i = pinnedPageOffset; i < pinnedItems.size() && pinnedForSlots.size() < pinnedItemsPerPage; i++) {
                PinnedItem item = pinnedItems.get(i);
                if (item != null) pinnedForSlots.add(item);
            }
            buttonCount = Math.max(1, pinnedForSlots.size());
            entries = entriesForPinnedItems(pinnedForSlots);
        } else {
            pinnedItemsPerPage = 1;
            pinnedPageIndex = 0;
        }
        setColumnCount(buttonCount);

        boolean[] usedColumns = new boolean[Math.max(1, buttonCount)];
        for (int col = 0; col < entries.size() && col < buttonCount; col++) {
            LauncherAppEntry entry = entries.get(col);
            View view = createEntryButton(entry);
            int renderCol = azPreview ? (renderStartCol + col) : col;
            LayoutParams param = createSlotParams(renderCol);
            view.setLayoutParams(param);

            if (!azPreview && col < pinnedForSlots.size()) {
                final int pinnedIndex = pinnedPageOffset + col;
                final PinnedItem pinnedItem = pinnedForSlots.get(col);
                if (pinnedItem instanceof PinnedFolderItem) {
                    view = createFolderPreviewButton((PinnedFolderItem) pinnedItem);
                    view.setLayoutParams(param);
                    View.OnClickListener openFolder = v -> showFolderPopup((PinnedFolderItem) pinnedItem, v);
                    view.setOnClickListener(openFolder);
                    View pressTarget = resolvePrimaryPressTarget(view);
                    pressTarget.setOnClickListener(openFolder);
                    bindFolderContextLongPress(pressTarget, (PinnedFolderItem) pinnedItem, pinnedIndex, true);
                } else {
                    View pressTarget = resolvePrimaryPressTarget(view);
                    bindAppContextLongPress(pressTarget, entry, pinnedIndex, null, null, true);
                }
            } else {
                View pressTarget = resolvePrimaryPressTarget(view);
                bindAppContextLongPress(pressTarget, entry, -1, null, null, false);
            }

            addView(view);
            if (renderCol >= 0 && renderCol < usedColumns.length) {
                usedColumns[renderCol] = true;
            }
        }

        boolean showEmptyPinnedHint = !azPreview
            && TextUtils.isEmpty(lastInput.trim())
            && (pinnedItems == null || pinnedItems.isEmpty())
            && entries.isEmpty();

        if (showEmptyPinnedHint) {
            TextView hint = new TextView(getContext());
            hint.setText(R.string.termux_app_launcher_empty_pinned_hint);
            hint.setTextColor(resolvePinnedHintBaseColor());
            hint.setTextSize(11f);
            hint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            hint.setLetterSpacing(0.03f);
            hint.setGravity(Gravity.CENTER);
            hint.setSingleLine(true);
            hint.setPadding(dp(6), 0, dp(6), 0);
            hint.setAlpha(0.92f);
            GridLayout.LayoutParams hintParams = createSlotParams(0);
            hintParams.columnSpec = GridLayout.spec(0, Math.max(1, buttonCount), 1f);
            hintParams.width = 0;
            hint.setLayoutParams(hintParams);
            hint.setOnLongClickListener(v -> {
                showUnifiedPinEditor(0, null);
                return true;
            });
            applyPinnedHintShimmer(hint);
            addView(hint);
            for (int i = 1; i < buttonCount; i++) {
                ImageButton filler = new ImageButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
                filler.setVisibility(INVISIBLE);
                filler.setLayoutParams(createSlotParams(i));
                addView(filler);
            }
        } else {
            for (int i = 0; i < buttonCount; i++) {
                if (usedColumns[i]) continue;
                ImageButton filler = new ImageButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
                filler.setVisibility(VISIBLE);
                filler.setAlpha(0f);
                filler.setLayoutParams(createSlotParams(i));
                if (!azPreview) {
                    final int slotIndex = i;
                    filler.setOnLongClickListener(v -> {
                        showUnifiedPinEditor(slotIndex, null);
                        return true;
                    });
                }
                addView(filler);
            }
        }

        if (!azPreview) {
            final int slotIndex = pinnedItems == null ? 0 : pinnedItems.size();
            setOnLongClickListener(v -> {
                showUnifiedPinEditor(slotIndex, null);
                return true;
            });
            setOnDragListener(this::handlePinnedBarDragEvent);
        } else {
            setOnLongClickListener(null);
            setOnDragListener(null);
        }
    }

    private void applyPinnedHintShimmer(@NonNull TextView hintView) {
        final int baseColor = resolvePinnedHintBaseColor();
        final int shimmerColor = blendColors(baseColor, TEXT_COLOR, 0.24f);
        ValueAnimator shimmer = ValueAnimator.ofObject(new ArgbEvaluator(), baseColor, shimmerColor, baseColor);
        shimmer.setDuration(3200L);
        shimmer.setRepeatCount(ValueAnimator.INFINITE);
        shimmer.setRepeatMode(ValueAnimator.RESTART);
        shimmer.addUpdateListener(animation -> hintView.setTextColor((Integer) animation.getAnimatedValue()));
        hintView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (!shimmer.isStarted()) {
                    shimmer.start();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                shimmer.cancel();
            }
        });
        shimmer.start();
    }

    private int resolvePinnedHintBaseColor() {
        return blendColors(inheritedTintColor, 0xFFE1DED5, 0.58f);
    }

    private static int blendColors(int from, int to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        int fromA = (from >> 24) & 0xFF;
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toA = (to >> 24) & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;
        int outA = Math.round(fromA + ((toA - fromA) * clamped));
        int outR = Math.round(fromR + ((toR - fromR) * clamped));
        int outG = Math.round(fromG + ((toG - fromG) * clamped));
        int outB = Math.round(fromB + ((toB - fromB) * clamped));
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private View createEntryButton(@NonNull LauncherAppEntry entry) {
        if (entry.icon != null && showIcons) {
            FrameLayout shell = new FrameLayout(getContext());
            shell.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            ImageButton imageButton = new ImageButton(getContext());
            imageButton.setImageDrawable(entry.icon);
            int size = iconSizePx();
            imageButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            imageButton.setAdjustViewBounds(true);
            imageButton.setPadding(0, 0, 0, 0);
            imageButton.setBackgroundColor(0x00000000);
            imageButton.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            imageButton.setMinimumHeight(size);
            imageButton.setMinimumWidth(size);
            if (bandW) {
                float[] colorMatrix = {
                    0.33f, 0.33f, 0.33f, 0, 0,
                    0.33f, 0.33f, 0.33f, 0, 0,
                    0.33f, 0.33f, 0.33f, 0, 0,
                    0, 0, 0, 1, 0
                };
                ColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
                imageButton.setColorFilter(colorFilter);
            }
            imageButton.setOnClickListener(v -> launchEntryFromTouch(v, entry, lastTerminalView));
            imageButton.setContentDescription(entry.label);
            registerLaunchTarget(entry.appRef, imageButton);
            shell.addView(imageButton);
            return shell;
        }

        Button button = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        button.setTextSize(textSize);
        button.setText(entry.label);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(TEXT_COLOR);
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(v -> launchEntryFromTouch(v, entry, lastTerminalView));
        registerLaunchTarget(entry.appRef, button);
        return button;
    }

    private LayoutParams createSlotParams(int col) {
        LayoutParams param = new GridLayout.LayoutParams();
        param.width = 0;
        param.height = ViewGroup.LayoutParams.MATCH_PARENT;
        param.setMargins(0, 0, 0, 0);
        param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1f);
        param.rowSpec = GridLayout.spec(0, GridLayout.FILL, 1f);
        return param;
    }

    private void launchEntry(@NonNull LauncherAppEntry entry, @Nullable TerminalView terminalView) {
        launchEntry(entry, terminalView, null);
    }

    private void launchEntry(@NonNull LauncherAppEntry entry, @Nullable TerminalView terminalView, @Nullable View launchSourceView) {
        if (entry.appRef.packageName.startsWith("injected.test")) {
            return;
        }
        Context context = getContext();
        PackageManager packageManager = context.getPackageManager();
        String activityName = entry.appRef.activityName;
        if (!TextUtils.isEmpty(activityName) && activityName.startsWith(".")) {
            activityName = entry.appRef.packageName + activityName;
        }
        Intent pkgDefault = packageManager.getLaunchIntentForPackage(entry.appRef.packageName);

        Intent explicit = null;
        Intent explicitNoCategory = null;
        if (!TextUtils.isEmpty(activityName)) {
            explicit = new Intent(Intent.ACTION_MAIN);
            explicit.addCategory(Intent.CATEGORY_LAUNCHER);
            explicit.setComponent(new ComponentName(entry.appRef.packageName, activityName));

            explicitNoCategory = new Intent(Intent.ACTION_MAIN);
            explicitNoCategory.setComponent(new ComponentName(entry.appRef.packageName, activityName));
        }

        Intent resolveFallback = new Intent(Intent.ACTION_MAIN);
        resolveFallback.addCategory(Intent.CATEGORY_LAUNCHER);
        resolveFallback.setPackage(entry.appRef.packageName);
        ComponentName resolved = resolveFallback.resolveActivity(packageManager);
        if (resolved != null) {
            resolveFallback.setComponent(resolved);
        }

        LaunchAnimationContext launchAnimationContext = shouldUseTouchLaunchAnimation(launchSourceView)
            ? buildLaunchAnimationContext(launchSourceView)
            : null;

        boolean launched = false;
        if (tryStartMainActivity(context, pkgDefault != null ? pkgDefault.getComponent() : null, launchAnimationContext)) {
            launched = true;
        } else if (tryStartMainActivity(context, explicit != null ? explicit.getComponent() : null, launchAnimationContext)) {
            launched = true;
        } else if (tryStartMainActivity(context, resolved, launchAnimationContext)) {
            launched = true;
        } else if (tryStartActivity(context, pkgDefault, launchAnimationContext)) {
            launched = true;
        } else if (tryStartActivity(context, explicit, launchAnimationContext)) {
            launched = true;
        } else if (tryStartActivity(context, explicitNoCategory, launchAnimationContext)) {
            launched = true;
        } else if (resolved != null && tryStartActivity(context, resolveFallback, launchAnimationContext)) {
            launched = true;
        }
        if (!launched) {
            Intent packageMain = new Intent(Intent.ACTION_MAIN);
            packageMain.addCategory(Intent.CATEGORY_LAUNCHER);
            packageMain.setPackage(entry.appRef.packageName);
            List<android.content.pm.ResolveInfo> matches = packageManager.queryIntentActivities(packageMain, 0);
            for (android.content.pm.ResolveInfo match : matches) {
                if (match == null || match.activityInfo == null) continue;
                String pkg = match.activityInfo.packageName;
                String cls = match.activityInfo.name;
                if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(cls)) continue;
                Intent fallbackExplicit = new Intent(Intent.ACTION_MAIN);
                fallbackExplicit.addCategory(Intent.CATEGORY_LAUNCHER);
                fallbackExplicit.setComponent(new ComponentName(pkg, cls));
                if (tryStartMainActivity(context, fallbackExplicit.getComponent(), launchAnimationContext)
                    || tryStartActivity(context, fallbackExplicit, launchAnimationContext)) {
                    launched = true;
                    break;
                }
            }
        }

        if (!launched) {
            Log.w(LOG_TAG, "Failed to launch package " + entry.appRef.packageName
                + " activity=" + entry.appRef.activityName);
            return;
        }

        if (terminalView != null) {
            terminalView.clearInputLine();
        }
        dismissFolderPopup();
        dismissAppContextPopup();
        dismissShortcutsPopup();
    }

    private void launchEntryFromTouch(@NonNull View sourceView, @NonNull LauncherAppEntry entry, @Nullable TerminalView terminalView) {
        boolean touchAnimation = shouldUseTouchLaunchAnimation(sourceView);
        long launchDelay = 0L;
        if (touchAnimation) {
            applyLaunchBloom(sourceView);
            launchDelay = APP_LAUNCH_TOUCH_DELAY_MS;
        }
        sourceView.postDelayed(() -> launchEntry(entry, terminalView, touchAnimation ? sourceView : null), launchDelay);
    }

    private List<LauncherAppEntry> entriesForPinnedItems(@NonNull List<PinnedItem> source) {
        List<LauncherAppEntry> out = new ArrayList<>();
        for (PinnedItem item : source) {
            if (item instanceof PinnedAppItem) {
                LauncherAppEntry entry = resolveRef(((PinnedAppItem) item).appRef);
                if (entry != null) {
                    out.add(entry);
                }
            } else if (item instanceof PinnedFolderItem) {
                LauncherAppEntry synthetic = folderSyntheticEntry((PinnedFolderItem) item);
                out.add(synthetic);
            }
        }
        return out;
    }

    private LauncherAppEntry folderSyntheticEntry(@NonNull PinnedFolderItem folder) {
        Drawable icon = null;
        for (AppRef ref : folder.apps) {
            LauncherAppEntry entry = resolveRef(ref);
            if (entry != null && entry.icon != null) {
                icon = entry.icon;
                break;
            }
        }
        String title = TextUtils.isEmpty(folder.title) ? "Folder" : folder.title;
        return new LauncherAppEntry(new AppRef("folder", folder.id), title, icon);
    }

    @Nullable
    private LauncherAppEntry resolveRef(@NonNull AppRef ref) {
        if (appDataProvider == null) {
            appDataProvider = new LauncherAppDataProvider(getContext());
        }
        if (!TextUtils.isEmpty(ref.activityName)) {
            LauncherAppEntry exact = appDataProvider.findByRef(ref);
            if (exact != null) return exact;
        }
        ComponentName defaultComponent = null;
        Intent pkgDefault = getContext().getPackageManager().getLaunchIntentForPackage(ref.packageName);
        if (pkgDefault != null) {
            defaultComponent = pkgDefault.getComponent();
        }
        if (defaultComponent != null) {
            String defaultClassName = defaultComponent.getClassName();
            for (LauncherAppEntry entry : allApps) {
                if (entry.appRef.packageName.equals(ref.packageName)
                    && defaultClassName.equals(entry.appRef.activityName)) {
                    return entry;
                }
            }
        }
        for (LauncherAppEntry entry : allApps) {
            if (entry.appRef.packageName.equals(ref.packageName)) {
                return entry;
            }
        }
        return null;
    }

    private boolean tryStartMainActivity(@NonNull Context context, @Nullable ComponentName componentName, @Nullable LaunchAnimationContext animationContext) {
        if (componentName == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        try {
            LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) {
                return false;
            }
            launcherApps.startMainActivity(
                componentName,
                Process.myUserHandle(),
                animationContext != null ? animationContext.sourceBounds : null,
                animationContext != null ? animationContext.options : null
            );
            return true;
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "startMainActivity failed for " + componentName + ": " + throwable.getMessage());
            return false;
        }
    }

    private boolean tryStartActivity(@NonNull Context context, @Nullable Intent intent, @Nullable LaunchAnimationContext animationContext) {
        if (intent == null) return false;
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (animationContext != null && animationContext.sourceBounds != null) {
                intent.setSourceBounds(animationContext.sourceBounds);
            }
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (animationContext != null && animationContext.options != null) {
                    try {
                        activity.startActivity(intent, animationContext.options);
                        return true;
                    } catch (RuntimeException optionError) {
                        Log.d(LOG_TAG, "launch options fallback for " + intent + ": " + optionError.getMessage());
                    }
                }
                activity.startActivity(intent);
            } else {
                context.startActivity(intent);
            }
            return true;
        } catch (Exception e) {
            Log.d(LOG_TAG, "launch failed for intent " + intent + ": " + e.getMessage());
            return false;
        }
    }

    @Nullable
    private LaunchAnimationContext buildLaunchAnimationContext(@Nullable View sourceView) {
        if (sourceView == null || !(getContext() instanceof Activity)) {
            return null;
        }
        Rect sourceBounds = getSourceBoundsOnScreen(sourceView);
        if (sourceBounds == null) {
            return null;
        }
        int width = Math.max(1, sourceView.getWidth());
        int height = Math.max(1, sourceView.getHeight());
        ActivityOptions options;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            options = ActivityOptions.makeClipRevealAnimation(sourceView, 0, 0, width, height);
        } else {
            options = ActivityOptions.makeScaleUpAnimation(sourceView, 0, 0, width, height);
        }
        return new LaunchAnimationContext(sourceBounds, options.toBundle());
    }

    @Nullable
    public RectF getLaunchIconBounds(@NonNull ComponentName componentName) {
        WeakReference<View> ref = launchTargetViews.get(componentName.flattenToShortString());
        if (ref == null) {
            ref = launchTargetViews.get(componentName.flattenToString());
        }
        if (ref == null) {
            ref = launchTargetViewsByPackage.get(componentName.getPackageName());
        }
        View target = ref != null ? ref.get() : null;
        if (target == null || !target.isAttachedToWindow()) {
            target = findFirstAttachedLaunchTargetForPackage(componentName.getPackageName());
        }
        if (target == null || !target.isAttachedToWindow()) {
            return null;
        }
        int[] location = new int[2];
        target.getLocationOnScreen(location);
        return new RectF(
            location[0],
            location[1],
            location[0] + target.getWidth(),
            location[1] + target.getHeight()
        );
    }

    private void showUnifiedPinEditor(final int slotIndex, @Nullable final PinnedItem pinnedAtSlot) {
        if (configRepository == null) return;
        if (allApps == null || allApps.isEmpty()) reloadAllApps();

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 24, 32, 24);

        final List<LauncherAppEntry> source = new ArrayList<>(allApps);
        final List<PinOption> options = buildPinOptions(source, pinnedItems);
        final List<String> labels = new ArrayList<>();
        for (PinOption option : options) labels.add(option.label);

        final Set<String> selectedIds = new LinkedHashSet<>();
        final List<PinnedItem> orderedSelected = new ArrayList<>();
        for (PinnedItem item : pinnedItems) {
            String stable = stableIdForPinnedItem(item);
            if (stable == null || !selectedIds.add(stable)) continue;
            orderedSelected.add(clonePinnedItem(item));
        }

        TextView selectedTitle = new TextView(getContext());
        selectedTitle.setText("Pinned Apps");
        selectedTitle.setTextColor(TEXT_COLOR);
        selectedTitle.setPadding(0, 0, 0, 8);

        final ListView[] listViewHolder = new ListView[1];
        final OrderedPinnedAdapter[] orderedAdapterHolder = new OrderedPinnedAdapter[1];
        orderedAdapterHolder[0] = new OrderedPinnedAdapter(source, orderedSelected, position -> {
            if (position < 0 || position >= orderedSelected.size()) return;
            String stable = stableIdForPinnedItem(orderedSelected.get(position));
            orderedSelected.remove(position);
            if (stable != null) selectedIds.remove(stable);
            orderedAdapterHolder[0].notifyDataSetChanged();
            ListView lv = listViewHolder[0];
            if (lv != null) syncListChecks(lv, options, selectedIds);
        });
        final OrderedPinnedAdapter orderedAdapter = orderedAdapterHolder[0];
        RecyclerView orderedRecycler = new RecyclerView(getContext());
        orderedRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        orderedRecycler.setAdapter(orderedAdapter);
        orderedRecycler.setOverScrollMode(OVER_SCROLL_NEVER);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                if (from < 0 || to < 0 || from >= orderedSelected.size() || to >= orderedSelected.size()) {
                    return false;
                }
                Collections.swap(orderedSelected, from, to);
                orderedAdapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (pos < 0 || pos >= orderedSelected.size()) return;
                String stable = stableIdForPinnedItem(orderedSelected.get(pos));
                orderedSelected.remove(pos);
                if (stable != null) selectedIds.remove(stable);
                orderedAdapter.notifyItemRemoved(pos);
                ListView lv = listViewHolder[0];
                if (lv != null) syncListChecks(lv, options, selectedIds);
            }
        });
        touchHelper.attachToRecyclerView(orderedRecycler);

        TextView allAppsTitle = new TextView(getContext());
        allAppsTitle.setText("Apps");
        allAppsTitle.setTextColor(TEXT_COLOR);
        allAppsTitle.setPadding(0, 16, 0, 8);

        EditText searchInput = new EditText(getContext());
        searchInput.setHint("Search apps");
        searchInput.setSingleLine(true);

        ListView listView = new ListView(getContext());
        listViewHolder[0] = listView;
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        final List<PinOption> filteredOptions = new ArrayList<>(options);
        final List<String> filteredLabels = new ArrayList<>(labels);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_multiple_choice, filteredLabels);
        listView.setAdapter(adapter);
        listView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        syncListChecksFiltered(listView, filteredOptions, selectedIds);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = stringValue(s).trim().toLowerCase(Locale.ROOT);
                filteredOptions.clear();
                filteredLabels.clear();
                for (PinOption option : options) {
                    String haystack = (option.label == null ? "" : option.label).toLowerCase(Locale.ROOT);
                    if (query.isEmpty() || haystack.contains(query)) {
                        filteredOptions.add(option);
                        filteredLabels.add(option.label);
                    }
                }
                adapter.notifyDataSetChanged();
                syncListChecksFiltered(listView, filteredOptions, selectedIds);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filteredOptions.size()) return;
            PinOption option = filteredOptions.get(position);
            String stable = option.id;
            if (listView.isItemChecked(position)) {
                if (selectedIds.contains(stable)) return;
                selectedIds.add(stable);
                orderedSelected.add(clonePinnedItem(option.item));
                orderedAdapter.notifyDataSetChanged();
            } else {
                selectedIds.remove(stable);
                removePinnedByStableId(orderedSelected, stable);
                orderedAdapter.notifyDataSetChanged();
            }
            syncListChecksFiltered(listView, filteredOptions, selectedIds);
        });

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton folderAction = new ImageButton(getContext());
        folderAction.setImageResource(R.drawable.ic_create_new_folder_24);
        folderAction.setContentDescription("Create folder at this slot");
        styleIconButton(folderAction, dp(4));

        Button cancel = new Button(getContext());
        cancel.setText("Cancel");
        styleGhostButton(cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());

        Button save = new Button(getContext());
        save.setText("Save");
        styleGhostButton(save);

        final boolean[] folderMode = new boolean[] {false};
        final Runnable refreshFolderModeUi = () -> {
            save.setText(folderMode[0] ? "Create" : "Save");
            folderAction.setAlpha(folderMode[0] ? 1f : 0.6f);
        };
        folderAction.setOnClickListener(v -> {
            folderMode[0] = !folderMode[0];
            refreshFolderModeUi.run();
        });
        refreshFolderModeUi.run();

        save.setOnClickListener(v -> {
            if (folderMode[0]) {
                List<AppRef> folderApps = new ArrayList<>();
                for (PinnedItem item : orderedSelected) {
                    if (item instanceof PinnedAppItem) {
                        folderApps.add(((PinnedAppItem) item).appRef);
                    }
                }
                createOrReplaceFolderAtSlot(slotIndex, folderApps);
            } else {
                applyPinnedSelection(orderedSelected);
            }
            dialog.dismiss();
            reloadWithInput("", lastTerminalView);
        });

        LinearLayout.LayoutParams folderActionParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        folderActionParams.setMargins(0, 0, dp(12), 0);
        buttons.addView(folderAction, folderActionParams);
        buttons.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 0, 1f));
        buttons.addView(cancel);
        buttons.addView(save);

        orderedRecycler.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        root.addView(selectedTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(orderedRecycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
        root.addView(allAppsTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(searchInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));
        root.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.show();
    }

    private void showFolderContentsEditor(final int folderIndex, @NonNull final PinnedFolderItem folder) {
        if (allApps == null || allApps.isEmpty()) reloadAllApps();

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView title = new TextView(getContext());
        title.setText(TextUtils.isEmpty(folder.title) ? "Folder Apps" : folder.title);
        title.setTextColor(TEXT_COLOR);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(14f);

        final Set<String> selectedIds = new LinkedHashSet<>();
        for (AppRef appRef : folder.apps) {
            selectedIds.add(resolveForSelectionId(appRef));
        }

        final List<LauncherAppEntry> source = new ArrayList<>(allApps);
        Collections.sort(source, (a, b) -> {
            boolean aSelected = selectedIds.contains(a.appRef.stableId());
            boolean bSelected = selectedIds.contains(b.appRef.stableId());
            if (aSelected != bSelected) return aSelected ? -1 : 1;
            return String.CASE_INSENSITIVE_ORDER.compare(
                a.label == null ? "" : a.label,
                b.label == null ? "" : b.label
            );
        });
        final List<String> labels = new ArrayList<>();
        for (LauncherAppEntry app : source) labels.add(app.label);

        EditText searchInput = new EditText(getContext());
        searchInput.setHint("Search apps");
        searchInput.setSingleLine(true);

        final List<LauncherAppEntry> filteredApps = new ArrayList<>(source);
        final List<String> filteredLabels = new ArrayList<>(labels);

        ListView listView = new ListView(getContext());
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_multiple_choice, filteredLabels);
        listView.setAdapter(adapter);
        listView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        syncFolderChecks(listView, filteredApps, selectedIds);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = stringValue(s).trim().toLowerCase(Locale.ROOT);
                filteredApps.clear();
                filteredLabels.clear();
                for (LauncherAppEntry app : source) {
                    String label = app.label == null ? "" : app.label;
                    String packageName = app.appRef.packageName == null ? "" : app.appRef.packageName;
                    String haystack = (label + " " + packageName).toLowerCase(Locale.ROOT);
                    if (query.isEmpty() || haystack.contains(query)) {
                        filteredApps.add(app);
                        filteredLabels.add(label);
                    }
                }
                adapter.notifyDataSetChanged();
                syncFolderChecks(listView, filteredApps, selectedIds);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filteredApps.size()) return;
            String stable = filteredApps.get(position).appRef.stableId();
            if (listView.isItemChecked(position)) {
                selectedIds.add(stable);
            } else {
                selectedIds.remove(stable);
            }
        });

        LinearLayout topActions = new LinearLayout(getContext());
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        topActions.setGravity(Gravity.END);

        ImageButton delete = new ImageButton(getContext());
        delete.setImageResource(R.drawable.ic_delete_sweep_24);
        delete.setContentDescription("Delete folder");
        styleIconButton(delete, dp(4));
        delete.setOnClickListener(v -> {
            removePinnedAt(folderIndex);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        topActions.addView(delete, deleteParams);

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        Button cancel = new Button(getContext());
        cancel.setText("Cancel");
        styleGhostButton(cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());

        Button save = new Button(getContext());
        save.setText("Save");
        styleGhostButton(save);
        save.setOnClickListener(v -> {
            folder.apps.clear();
            for (LauncherAppEntry app : source) {
                if (selectedIds.contains(app.appRef.stableId())) {
                    folder.apps.add(resolveForSelectionRef(app.appRef));
                }
            }
            dialog.dismiss();
            persistPinsAndReload();
        });

        buttons.addView(cancel);
        buttons.addView(save);

        root.addView(topActions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(searchInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        root.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.show();
    }

    private void createOrReplaceFolderAtSlot(int slotIndex, @NonNull List<AppRef> selectedOrdered) {
        PinnedFolderItem folder = new PinnedFolderItem(UUID.randomUUID().toString(), "Folder");
        for (AppRef ref : selectedOrdered) {
            folder.apps.add(resolveForSelectionRef(ref));
        }
        if (slotIndex >= 0 && slotIndex < pinnedItems.size()) {
            pinnedItems.set(slotIndex, folder);
        } else {
            pinnedItems.add(folder);
        }
        persistPinsAndReload();
    }

    private View createFolderPreviewButton(@NonNull PinnedFolderItem folder) {
        FrameLayout root = new FrameLayout(getContext());
        root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout iconShell = new FrameLayout(getContext());
        int shellSize = iconSizePx();
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x26FFFFFF);
        bg.setStroke(1, 0x33FFFFFF);
        iconShell.setBackground(bg);
        iconShell.setLayoutParams(new LinearLayout.LayoutParams(shellSize, shellSize));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            iconShell.setClipToOutline(true);
        }
        iconShell.setPadding(0, 0, 0, 0);

        GridLayout miniGrid = new GridLayout(getContext());
        miniGrid.setColumnCount(2);
        miniGrid.setRowCount(2);
        miniGrid.setUseDefaultMargins(false);
        miniGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        int miniSize = Math.max(dp(9), Math.round(shellSize * 0.42f));
        int placed = 0;
        for (AppRef ref : folder.apps) {
            if (placed >= 4) break;
            LauncherAppEntry e = resolveRef(ref);
            if (e == null || e.icon == null) continue;
            ImageView mini = new ImageView(getContext());
            mini.setImageDrawable(e.icon);
            mini.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = miniSize;
            params.height = miniSize;
            params.setMargins(dp(1), dp(1), dp(1), dp(1));
            mini.setLayoutParams(params);
            miniGrid.addView(mini);
            placed++;
        }
        iconShell.addView(miniGrid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(iconShell, new FrameLayout.LayoutParams(shellSize, shellSize, Gravity.CENTER));
        return root;
    }

    private void applyPinnedSelection(@NonNull List<PinnedItem> selectedOrdered) {
        List<PinnedItem> rebuilt = new ArrayList<>();
        for (PinnedItem item : selectedOrdered) {
            if (item == null) continue;
            rebuilt.add(clonePinnedItem(item));
        }
        pinnedItems = rebuilt;
        configRepository.savePinnedItems(pinnedItems);
    }

    private void showPinnedAppOptions(int index, PinnedAppItem item) {
        String[] options = new String[] {
            "Replace app",
            "Unpin",
            "Move to folder",
            "Create folder"
        };

        new AlertDialog.Builder(getContext())
            .setTitle("Pinned app")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        showReplacePinnedApp(index);
                        break;
                    case 1:
                        removePinnedAt(index);
                        break;
                    case 2:
                        showMovePinnedAppToFolder(index, item);
                        break;
                    case 3:
                        showCreateFolderWithSeed(index, item);
                        break;
                    default:
                        break;
                }
            })
            .show();
    }

    private void showFolderItemOptions(int index, PinnedFolderItem folder) {
        String[] options = new String[] {
            "Open folder",
            "Edit folder apps",
            "Folder settings",
            "Unpin folder"
        };

        new AlertDialog.Builder(getContext())
            .setTitle(folder.title)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        showFolderPopup(folder, null);
                        break;
                    case 1:
                        showFolderAppEditor(folder);
                        break;
                    case 2:
                        showFolderSettings(folder);
                        break;
                    case 3:
                        removePinnedAt(index);
                        break;
                    default:
                        break;
                }
            })
            .show();
    }

    private void showReplacePinnedApp(int index) {
        if (allApps == null || allApps.isEmpty()) reloadAllApps();
        String[] labels = appLabels(allApps);
        new AlertDialog.Builder(getContext())
            .setTitle("Replace pinned app")
            .setItems(labels, (dialog, which) -> {
                AppRef ref = allApps.get(which).appRef;
                if (index >= 0 && index < pinnedItems.size()) {
                    pinnedItems.set(index, new PinnedAppItem(ref));
                }
                persistPinsAndReload();
            })
            .show();
    }

    private void showMovePinnedAppToFolder(int appIndex, PinnedAppItem item) {
        List<PinnedFolderItem> folders = allFolders();
        if (folders.isEmpty()) {
            showCreateFolderWithSeed(appIndex, item);
            return;
        }
        String[] names = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) names[i] = folders.get(i).title;

        new AlertDialog.Builder(getContext())
            .setTitle("Move to folder")
            .setItems(names, (dialog, which) -> {
                PinnedFolderItem folder = folders.get(which);
                folder.apps.add(resolveForSelectionRef(item.appRef));
                if (appIndex >= 0 && appIndex < pinnedItems.size()) {
                    pinnedItems.remove(appIndex);
                }
                persistPinsAndReload();
            })
            .show();
    }

    private void showCreateFolderWithSeed(int appIndex, PinnedAppItem item) {
        EditText titleInput = new EditText(getContext());
        titleInput.setHint("Folder name");
        new AlertDialog.Builder(getContext())
            .setTitle("Create folder")
            .setView(titleInput)
            .setPositiveButton("Create", (dialog, which) -> {
                String title = titleInput.getText() == null ? "Folder" : titleInput.getText().toString().trim();
                if (title.isEmpty()) title = "Folder";
                PinnedFolderItem folder = new PinnedFolderItem(UUID.randomUUID().toString(), title);
                folder.apps.add(resolveForSelectionRef(item.appRef));

                if (appIndex >= 0 && appIndex < pinnedItems.size()) {
                    pinnedItems.set(appIndex, folder);
                } else {
                    pinnedItems.add(folder);
                }
                persistPinsAndReload();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showFolderAppEditor(PinnedFolderItem folder) {
        if (allApps == null || allApps.isEmpty()) reloadAllApps();
        boolean[] checked = new boolean[allApps.size()];
        Set<String> existing = new HashSet<>();
        for (AppRef appRef : folder.apps) {
            existing.add(resolveForSelectionId(appRef));
        }
        for (int i = 0; i < allApps.size(); i++) {
            checked[i] = existing.contains(allApps.get(i).appRef.stableId());
        }

        String[] labels = appLabels(allApps);
        new AlertDialog.Builder(getContext())
            .setTitle("Edit folder apps")
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                checked[which] = isChecked;
            })
            .setPositiveButton("Save", (dialog, which) -> {
                folder.apps.clear();
                for (int i = 0; i < checked.length; i++) {
                    if (checked[i]) folder.apps.add(allApps.get(i).appRef);
                }
                persistPinsAndReload();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showFolderSettings(PinnedFolderItem folder) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable panel = new GradientDrawable();
        panel.setCornerRadius(dp(14));
        panel.setColor(0xEE1E1E1E);
        panel.setStroke(dp(1), 0x33FFFFFF);
        layout.setBackground(panel);

        TextView title = new TextView(getContext());
        title.setText("Folder settings");
        title.setTextColor(TEXT_COLOR);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(14f);
        title.setPadding(0, 0, 0, dp(8));

        EditText nameInput = new EditText(getContext());
        nameInput.setHint("Folder name");
        nameInput.setText(TextUtils.isEmpty(folder.title) ? "Folder" : folder.title);

        final int[] rowsValue = new int[] {clamp(folder.rows, 1, PinnedFolderItem.MAX_GRID)};
        final int[] colsValue = new int[] {clamp(folder.cols, 1, PinnedFolderItem.MAX_GRID)};
        LinearLayout rowsControl = buildStepperRow("Rows", rowsValue, 1, PinnedFolderItem.MAX_GRID);
        LinearLayout colsControl = buildStepperRow("Columns", colsValue, 1, PinnedFolderItem.MAX_GRID);

        EditText colorInput = new EditText(getContext());
        colorInput.setHint("Tint color");
        colorInput.setText(folder.tintOverrideEnabled ? String.format(Locale.US, "#%08X", folder.tintColor) : "");
        colorInput.setSingleLine(true);
        colorInput.setHint("#AARRGGBB or #RRGGBB");

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        Button cancel = new Button(getContext());
        cancel.setText("Cancel");
        styleGhostButton(cancel);

        Button save = new Button(getContext());
        save.setText("Save");
        styleGhostButton(save);

        buttons.addView(cancel);
        buttons.addView(save);

        layout.addView(title);
        layout.addView(nameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(rowsControl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(colsControl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(colorInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(getContext())
            .setView(layout)
            .create();

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String newName = stringValue(nameInput.getText()).trim();
            folder.title = newName.isEmpty() ? "Folder" : newName;
            folder.rows = rowsValue[0];
            folder.cols = colsValue[0];
            String color = stringValue(colorInput.getText()).trim();
            if (color.isEmpty()) {
                folder.tintOverrideEnabled = false;
            } else {
                Integer parsed = parseColor(color);
                if (parsed != null) {
                    folder.tintOverrideEnabled = true;
                    folder.tintColor = parsed;
                }
            }
            dialog.dismiss();
            persistPinsAndReload();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            );
        }
    }

    private void showFolderPopup(PinnedFolderItem folder, @Nullable View anchor) {
        dismissFolderPopup();
        dismissAppContextPopup();
        dismissShortcutsPopup();

        List<LauncherAppEntry> folderEntries = new ArrayList<>();
        for (AppRef ref : folder.apps) {
            LauncherAppEntry entry = resolveRef(ref);
            if (entry != null) folderEntries.add(entry);
        }
        if (folderEntries.isEmpty()) {
            return;
        }

        int rows = clamp(folder.rows, 1, PinnedFolderItem.MAX_GRID);
        int cols = clamp(folder.cols, 1, PinnedFolderItem.MAX_GRID);
        int cellCount = rows * cols;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int popupIconSize = computeFolderPopupIconSize(rows, cols, screenW, screenH);

        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(cols);
        grid.setRowCount(rows);
        grid.setPadding(dp(3), dp(3), dp(3), dp(3));
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        for (int i = 0; i < folderEntries.size() && i < cellCount; i++) {
            LauncherAppEntry entry = folderEntries.get(i);
            View btn = createPopupEntryButton(entry, popupIconSize, folder);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = popupIconSize;
            params.height = popupIconSize;
            params.columnSpec = GridLayout.spec(i % cols);
            params.rowSpec = GridLayout.spec(i / cols);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            btn.setLayoutParams(params);
            grid.addView(btn);
        }

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(3), dp(3), dp(3), dp(3));

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(2), dp(4), dp(4));

        TextView title = new TextView(getContext());
        title.setText(TextUtils.isEmpty(folder.title) ? "Folder" : folder.title);
        title.setTextColor(TEXT_COLOR);
        title.setTextSize(12f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        title.setClickable(true);
        title.setOnClickListener(v -> {
            dismissFolderPopup();
            showRenameFolderDialog(folder);
        });

        ImageButton gear = new ImageButton(getContext());
        gear.setImageResource(R.drawable.ic_settings);
        styleIconButton(gear, dp(3));
        int gearSize = dp(24);
        gear.setOnClickListener(v -> {
            dismissFolderPopup();
            int folderIndex = findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                showFolderContentsEditor(folderIndex, folder);
            }
        });

        header.addView(title);
        header.addView(gear, new LinearLayout.LayoutParams(gearSize, gearSize));
        shell.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int overlayBase = folder.tintOverrideEnabled ? (folder.tintColor & 0x00FFFFFF) : (inheritedTintColor & 0x00FFFFFF);
        folderPopupWindow = buildPopupWindow(shell, overlayBase, false, () -> {
            if (folderPopupWindow != null && !folderPopupWindow.isShowing()) {
                folderPopupWindow = null;
            }
        });
        showPopupAtAnchor(folderPopupWindow, anchor);
    }

    private void removePinnedAt(int index) {
        if (index >= 0 && index < pinnedItems.size()) {
            pinnedItems.remove(index);
            persistPinsAndReload();
        }
    }

    private void persistPinsAndReload() {
        dismissAppContextPopup();
        dismissShortcutsPopup();
        if (configRepository != null) {
            configRepository.savePinnedItems(pinnedItems);
            pinnedItems = configRepository.loadPinnedItems();
        }
        reloadWithInput("", lastTerminalView);
    }

    private void dismissFolderPopup() {
        if (folderPopupWindow != null) {
            final PopupWindow popup = folderPopupWindow;
            dismissPopupWindowAnimated(popup, () -> {
                if (folderPopupWindow == popup) folderPopupWindow = null;
            });
        }
    }

    private List<PinnedFolderItem> allFolders() {
        List<PinnedFolderItem> out = new ArrayList<>();
        for (PinnedItem item : pinnedItems) {
            if (item instanceof PinnedFolderItem) {
                out.add((PinnedFolderItem) item);
            }
        }
        return out;
    }

    private void showRenameFolderDialog(@NonNull PinnedFolderItem folder) {
        EditText titleInput = new EditText(getContext());
        titleInput.setHint("Folder name");
        titleInput.setText(TextUtils.isEmpty(folder.title) ? "Folder" : folder.title);
        new AlertDialog.Builder(getContext())
            .setTitle("Rename folder")
            .setView(titleInput)
            .setPositiveButton("Save", (dialog, which) -> {
                String updated = stringValue(titleInput.getText()).trim();
                folder.title = updated.isEmpty() ? "Folder" : updated;
                persistPinsAndReload();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @NonNull
    private static View resolvePrimaryPressTarget(@NonNull View view) {
        if (view instanceof FrameLayout) {
            FrameLayout frame = (FrameLayout) view;
            if (frame.getChildCount() > 0 && frame.getChildAt(0) != null) {
                return frame.getChildAt(0);
            }
        }
        return view;
    }

    private void bindAppContextLongPress(
        @NonNull View pressTarget,
        @NonNull LauncherAppEntry entry,
        int pinnedIndex,
        @Nullable PinnedFolderItem sourceFolder,
        @Nullable AppRef folderEntryRef,
        boolean allowDragPickup
    ) {
        bindContextLongPressGesture(pressTarget, pinnedIndex, allowDragPickup, () -> {
            dismissShortcutsPopup();
            showAppContextPopup(new AppMenuContext(entry, pressTarget, pinnedIndex, sourceFolder, folderEntryRef));
        });
    }

    private void bindFolderContextLongPress(
        @NonNull View pressTarget,
        @NonNull PinnedFolderItem folder,
        int pinnedIndex,
        boolean allowDragPickup
    ) {
        bindContextLongPressGesture(pressTarget, pinnedIndex, allowDragPickup, () -> {
            dismissFolderPopup();
            showFolderContextPopup(folder, pinnedIndex, pressTarget);
        });
    }

    private void bindContextLongPressGesture(
        @NonNull View pressTarget,
        int pinnedIndex,
        boolean allowDragPickup,
        @NonNull Runnable showContextPopup
    ) {
        pressTarget.setLongClickable(true);
        pressTarget.setOnLongClickListener(v -> {
            showContextPopup.run();
            LongPressPickupState state = activeLongPressPickupState;
            if (state == null || state.sourceView != pressTarget) {
                state = new LongPressPickupState(pressTarget, pinnedIndex, 0f, 0f);
                activeLongPressPickupState = state;
            }
            state.menuShown = true;
            state.menuShownAtMs = SystemClock.uptimeMillis();
            state.definitiveYMovement = false;
            state.selectionArmed = false;
            return true;
        });
        pressTarget.setOnTouchListener((v, event) -> {
            if (event == null) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                activeLongPressPickupState = new LongPressPickupState(
                    pressTarget,
                    pinnedIndex,
                    event.getRawX(),
                    event.getRawY()
                );
            } else if (action == MotionEvent.ACTION_MOVE) {
                LongPressPickupState state = activeLongPressPickupState;
                if (state != null && state.sourceView == pressTarget && state.menuShown && !state.dragStarted) {
                    float rawX = event.getRawX();
                    float rawY = event.getRawY();
                    float dx = rawX - state.downRawX;
                    float dy = rawY - state.downRawY;
                    float absDx = Math.abs(dx);
                    float absDy = Math.abs(dy);
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    float yIntentThreshold = slop * PICKUP_Y_INTENT_SLOP_FACTOR;
                    float xPickupThreshold = slop * PICKUP_X_AXIS_SLOP_FACTOR;
                    if (absDy >= yIntentThreshold) {
                        state.definitiveYMovement = true;
                    }
                    if (!state.selectionArmed && Math.max(absDx, absDy) >= (slop * MENU_SELECTION_ARM_SLOP_FACTOR)) {
                        state.selectionArmed = true;
                    }

                    boolean withinPickupWindow = (SystemClock.uptimeMillis() - state.menuShownAtMs) <= PICKUP_DECISION_WINDOW_MS;
                    boolean shouldStartPickup = allowDragPickup
                        && pinnedIndex >= 0
                        && withinPickupWindow
                        && !state.definitiveYMovement
                        && absDx >= xPickupThreshold;

                    if (shouldStartPickup) {
                        state.dragStarted = true;
                        clearMenuHighlight();
                        dismissAppContextPopup();
                        dismissFolderPopup();
                        startPinnedDrag(pressTarget, pinnedIndex);
                        activeLongPressPickupState = null;
                        return true;
                    }

                    boolean highlighted = updateMenuHighlightForRaw(rawX, rawY, true, state.selectionArmed);
                    if (highlighted) {
                        state.selectionArmed = true;
                    }
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                LongPressPickupState state = activeLongPressPickupState;
                if (state != null && state.sourceView == pressTarget) {
                    if (action == MotionEvent.ACTION_UP && state.menuShown && !state.dragStarted) {
                        if (state.selectionArmed) {
                            updateMenuHighlightForRaw(event.getRawX(), event.getRawY(), true, true);
                            executeHighlightedMenuActionOrKeepOpen();
                        }
                        activeLongPressPickupState = null;
                        return true;
                    }
                    if (action == MotionEvent.ACTION_CANCEL) {
                        clearMenuHighlight();
                    }
                    activeLongPressPickupState = null;
                }
            }
            return false;
        });
    }

    private void showAppContextPopup(@NonNull AppMenuContext context) {
        dismissAppContextPopup();
        List<ShortcutInfo> shortcuts = queryEntryShortcuts(context.entry);
        boolean hasShortcuts = !shortcuts.isEmpty();
        boolean folderSource = context.sourceFolder != null && context.folderEntryRef != null;
        int topPinnedIndex = context.pinnedIndex >= 0 ? context.pinnedIndex : findPinnedAppIndex(context.entry.appRef);
        boolean topPinned = topPinnedIndex >= 0;

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(3), dp(3), dp(3), dp(3));
        appContextRows.clear();
        shortcutsRows.clear();
        clearMenuHighlight();
        shortcutsMainRowView = null;
        activeAppMenuContext = context;
        activeAppMenuShortcuts = shortcuts;

        TextView header = new TextView(getContext());
        header.setText(context.entry.label);
        header.setTextColor(TEXT_COLOR);
        header.setTextSize(12f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(8), dp(4), dp(8), dp(6));
        shell.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int tintBase = context.sourceFolder != null && context.sourceFolder.tintOverrideEnabled
            ? (context.sourceFolder.tintColor & 0x00FFFFFF)
            : (inheritedTintColor & 0x00FFFFFF);
        activeMenuTintBase = tintBase;

        TextView uninstallRow = addPopupActionRow(shell, "Uninstall", tintBase, () -> {
            dismissAppContextPopup();
            requestUninstall(context.entry);
        });
        appContextRows.add(new MenuActionRow(uninstallRow, () -> {
            dismissAppContextPopup();
            requestUninstall(context.entry);
        }, false));

        TextView appInfoRow = addPopupActionRow(shell, "App info", tintBase, () -> {
            dismissAppContextPopup();
            openAppInfo(context.entry);
        });
        appContextRows.add(new MenuActionRow(appInfoRow, () -> {
            dismissAppContextPopup();
            openAppInfo(context.entry);
        }, false));

        if (folderSource) {
            TextView removeRow = addPopupActionRow(shell, "Remove from folder", tintBase, () -> {
                dismissAppContextPopup();
                removeFromContextSource(context);
            });
            appContextRows.add(new MenuActionRow(removeRow, () -> {
                dismissAppContextPopup();
                removeFromContextSource(context);
            }, false));
        } else if (topPinned) {
            final int targetPinnedIndex = topPinnedIndex;
            TextView unpinRow = addPopupActionRow(shell, "Unpin", tintBase, () -> {
                dismissAppContextPopup();
                removePinnedAt(targetPinnedIndex);
            });
            appContextRows.add(new MenuActionRow(unpinRow, () -> {
                dismissAppContextPopup();
                removePinnedAt(targetPinnedIndex);
            }, false));
        } else {
            TextView pinRow = addPopupActionRow(shell, "Pin", tintBase, () -> {
                dismissAppContextPopup();
                pinEntryToTopLevel(context.entry);
            });
            appContextRows.add(new MenuActionRow(pinRow, () -> {
                dismissAppContextPopup();
                pinEntryToTopLevel(context.entry);
            }, false));
        }

        if (hasShortcuts) {
            TextView shortcutsRow = addPopupActionRow(shell, "Shortcuts", tintBase, () -> {
                if (shortcutsPopupWindow != null && shortcutsPopupWindow.isShowing()) {
                    dismissShortcutsPopup();
                    clearMenuHighlight();
                } else {
                    showShortcutsPopup(context, shortcuts, shortcutsMainRowView);
                }
            });
            shortcutsMainRowView = shortcutsRow;
            appContextRows.add(new MenuActionRow(shortcutsRow, () -> {
                if (shortcutsPopupWindow == null || !shortcutsPopupWindow.isShowing()) {
                    showShortcutsPopup(context, shortcuts, shortcutsMainRowView);
                }
            }, true));
        }

        int rowWidth = normalizePopupRowWidths(appContextRows);
        constrainPopupHeaderWidth(header, rowWidth);

        appContextPopupWindow = buildPopupWindow(shell, tintBase, true, () -> {
            if (appContextPopupWindow != null && !appContextPopupWindow.isShowing()) {
                appContextPopupWindow = null;
            }
        });
        showPopupAtAnchor(appContextPopupWindow, context.anchor);
    }

    private void showFolderContextPopup(@NonNull PinnedFolderItem folder, int pinnedIndex, @NonNull View anchor) {
        dismissAppContextPopup();
        dismissFolderPopup();

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(3), dp(3), dp(3), dp(3));
        appContextRows.clear();
        shortcutsRows.clear();
        clearMenuHighlight();
        shortcutsMainRowView = null;
        activeAppMenuContext = null;
        activeAppMenuShortcuts = null;

        TextView header = new TextView(getContext());
        String title = TextUtils.isEmpty(folder.title) ? "Folder" : folder.title;
        header.setText(title);
        header.setTextColor(TEXT_COLOR);
        header.setTextSize(12f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(8), dp(4), dp(8), dp(6));
        shell.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int tintBase = folder.tintOverrideEnabled ? (folder.tintColor & 0x00FFFFFF) : (inheritedTintColor & 0x00FFFFFF);
        activeMenuTintBase = tintBase;

        TextView renameRow = addPopupActionRow(shell, "Rename", tintBase, () -> {
            dismissAppContextPopup();
            showRenameFolderDialog(folder);
        });
        appContextRows.add(new MenuActionRow(renameRow, () -> {
            dismissAppContextPopup();
            showRenameFolderDialog(folder);
        }, false));

        TextView chooseAppsRow = addPopupActionRow(shell, "Choose apps", tintBase, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                showFolderContentsEditor(folderIndex, folder);
            }
        });
        appContextRows.add(new MenuActionRow(chooseAppsRow, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                showFolderContentsEditor(folderIndex, folder);
            }
        }, false));

        TextView deleteRow = addPopupActionRow(shell, "Delete", tintBase, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                removePinnedAt(folderIndex);
            }
        });
        appContextRows.add(new MenuActionRow(deleteRow, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                removePinnedAt(folderIndex);
            }
        }, false));

        int rowWidth = normalizePopupRowWidths(appContextRows);
        constrainPopupHeaderWidth(header, rowWidth);

        appContextPopupWindow = buildPopupWindow(shell, tintBase, true, () -> {
            if (appContextPopupWindow != null && !appContextPopupWindow.isShowing()) {
                appContextPopupWindow = null;
            }
        });
        showPopupAtAnchor(appContextPopupWindow, anchor);
    }

    private void showShortcutsPopup(@NonNull AppMenuContext context, @NonNull List<ShortcutInfo> shortcuts, @Nullable View shortcutsRowAnchor) {
        dismissShortcutsPopup();
        if (shortcuts.isEmpty()) return;

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(3), dp(3), dp(3), dp(3));
        shortcutsRows.clear();

        for (ShortcutInfo info : shortcuts) {
            String label = info.getShortLabel() != null ? info.getShortLabel().toString() : info.getId();
            final TextView[] shortcutRowHolder = new TextView[1];
            TextView shortcutRow = addPopupActionRow(shell, label, activeMenuTintBase, () -> {
                launchShortcut(info, shortcutRowHolder[0]);
                dismissAppContextPopup();
            });
            shortcutRowHolder[0] = shortcutRow;
            shortcutsRows.add(new MenuActionRow(shortcutRow, () -> {
                launchShortcut(info, shortcutRowHolder[0]);
                dismissAppContextPopup();
            }, false));
        }
        normalizePopupRowWidths(shortcutsRows);

        int tintBase = context.sourceFolder != null && context.sourceFolder.tintOverrideEnabled
            ? (context.sourceFolder.tintColor & 0x00FFFFFF)
            : (inheritedTintColor & 0x00FFFFFF);
        shortcutsPopupWindow = buildPopupWindow(shell, tintBase, true, () -> {
            if (shortcutsPopupWindow != null && !shortcutsPopupWindow.isShowing()) {
                shortcutsPopupWindow = null;
            }
        });
        if (shortcutsRowAnchor != null && appContextPopupWindow != null && appContextPopupWindow.isShowing()) {
            showSidePopupAlignedToRow(shortcutsPopupWindow, shortcutsRowAnchor, appContextPopupWindow);
        } else {
            showPopupAtAnchor(shortcutsPopupWindow, context.anchor);
        }
    }

    @NonNull
    private List<ShortcutInfo> queryEntryShortcuts(@NonNull LauncherAppEntry entry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return new ArrayList<>();
        try {
            LauncherApps launcherApps = (LauncherApps) getContext().getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) return new ArrayList<>();
            LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
            query.setPackage(entry.appRef.packageName);
            if (!TextUtils.isEmpty(entry.appRef.activityName)) {
                String activityName = entry.appRef.activityName;
                if (activityName.startsWith(".")) {
                    activityName = entry.appRef.packageName + activityName;
                }
                query.setActivity(new ComponentName(entry.appRef.packageName, activityName));
            }
            int flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED;
            query.setQueryFlags(flags);
            UserHandle user = Process.myUserHandle();
            List<ShortcutInfo> shortcuts = launcherApps.getShortcuts(query, user);
            return shortcuts == null ? new ArrayList<>() : shortcuts;
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "shortcut query failed for " + entry.appRef.stableId() + ": " + throwable.getMessage());
            return new ArrayList<>();
        }
    }

    private void launchShortcut(@NonNull ShortcutInfo shortcutInfo) {
        launchShortcut(shortcutInfo, activeAppMenuContext != null ? activeAppMenuContext.anchor : null);
    }

    private void launchShortcut(@NonNull ShortcutInfo shortcutInfo, @Nullable View sourceView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;
        boolean touchAnimation = shouldUseTouchLaunchAnimation(sourceView);
        long launchDelay = 0L;
        if (touchAnimation && sourceView != null) {
            applyLaunchBloom(sourceView);
            launchDelay = APP_LAUNCH_TOUCH_DELAY_MS;
        }
        Runnable launcherRunnable = () -> doLaunchShortcut(shortcutInfo, touchAnimation ? sourceView : null);
        if (launchDelay > 0L && sourceView != null) {
            sourceView.postDelayed(launcherRunnable, launchDelay);
        } else {
            launcherRunnable.run();
        }
    }

    private void doLaunchShortcut(@NonNull ShortcutInfo shortcutInfo, @Nullable View sourceView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;
        try {
            LauncherApps launcherApps = (LauncherApps) getContext().getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) return;
            LaunchAnimationContext animationContext = shouldUseTouchLaunchAnimation(sourceView)
                ? buildLaunchAnimationContext(sourceView)
                : null;
            launcherApps.startShortcut(
                shortcutInfo.getPackage(),
                shortcutInfo.getId(),
                animationContext != null ? animationContext.sourceBounds : null,
                animationContext != null && animationContext.options != null
                    ? animationContext.options
                    : ActivityOptions.makeBasic().toBundle(),
                Process.myUserHandle()
            );
            dismissFolderPopup();
            if (lastTerminalView != null) {
                lastTerminalView.clearInputLine();
            }
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "shortcut launch failed for " + shortcutInfo.getId() + ": " + throwable.getMessage());
        }
    }

    private void openAppInfo(@NonNull LauncherAppEntry entry) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + entry.appRef.packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "app info open failed for " + entry.appRef.packageName + ": " + throwable.getMessage());
        }
    }

    private void requestUninstall(@NonNull LauncherAppEntry entry) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + entry.appRef.packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "uninstall intent failed for " + entry.appRef.packageName + ": " + throwable.getMessage());
        }
    }

    private void removeFromContextSource(@NonNull AppMenuContext context) {
        if (context.pinnedIndex >= 0) {
            removePinnedAt(context.pinnedIndex);
            return;
        }
        if (context.sourceFolder != null && context.folderEntryRef != null) {
            removeAppFromFolder(context.sourceFolder, context.folderEntryRef);
            persistPinsAndReload();
        }
    }

    private int findPinnedAppIndex(@NonNull AppRef ref) {
        AppRef resolved = resolveForSelectionRef(ref);
        String targetStable = resolved.stableId();
        for (int i = 0; i < pinnedItems.size(); i++) {
            PinnedItem item = pinnedItems.get(i);
            if (!(item instanceof PinnedAppItem)) continue;
            AppRef pinnedRef = resolveForSelectionRef(((PinnedAppItem) item).appRef);
            if (targetStable.equals(pinnedRef.stableId())) {
                return i;
            }
        }
        return -1;
    }

    private void pinEntryToTopLevel(@NonNull LauncherAppEntry entry) {
        if (findPinnedAppIndex(entry.appRef) >= 0) return;
        pinnedItems.add(new PinnedAppItem(resolveForSelectionRef(entry.appRef)));
        persistPinsAndReload();
    }

    private TextView addPopupActionRow(@NonNull LinearLayout shell, @NonNull String title, int tintBase, @NonNull Runnable action) {
        TextView actionRow = new TextView(getContext());
        actionRow.setText(title);
        actionRow.setTextColor(TEXT_COLOR);
        actionRow.setTextSize(12f);
        actionRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(8), dp(7), dp(8), dp(7));
        actionRow.setClickable(true);
        stylePopupRow(actionRow, false, tintBase);
        actionRow.setOnClickListener(v -> action.run());
        shell.addView(actionRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return actionRow;
    }

    private int normalizePopupRowWidths(@NonNull List<MenuActionRow> rows) {
        if (rows.isEmpty()) return 0;
        int maxWidth = 0;
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (MenuActionRow row : rows) {
            if (row.rowView == null) continue;
            row.rowView.measure(unspecified, unspecified);
            maxWidth = Math.max(maxWidth, row.rowView.getMeasuredWidth());
        }
        if (maxWidth <= 0) return 0;
        for (MenuActionRow row : rows) {
            if (row.rowView == null) continue;
            ViewGroup.LayoutParams params = row.rowView.getLayoutParams();
            if (params == null) {
                params = new LinearLayout.LayoutParams(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                params.width = maxWidth;
            }
            row.rowView.setLayoutParams(params);
        }
        return maxWidth;
    }

    private void constrainPopupHeaderWidth(@NonNull TextView header, int targetWidth) {
        if (targetWidth <= 0) return;
        header.setSingleLine(true);
        header.setEllipsize(TextUtils.TruncateAt.END);
        ViewGroup.LayoutParams params = header.getLayoutParams();
        if (params == null) {
            params = new LinearLayout.LayoutParams(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            params.width = targetWidth;
        }
        header.setLayoutParams(params);
    }

    private void stylePopupRow(@NonNull TextView row, boolean highlighted, int tintBase) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        if (highlighted) {
            int fill = blendColors((0x8C << 24) | (tintBase & 0x00FFFFFF), 0x66FFFFFF, 0.35f);
            bg.setColor(fill);
            bg.setStroke(dp(1), blendColors(0x99FFFFFF, (0xFF << 24) | (tintBase & 0x00FFFFFF), 0.5f));
            row.setTextColor(0xFFF0ECE0);
        } else {
            bg.setColor(0x00000000);
            bg.setStroke(0, 0x00000000);
            row.setTextColor(TEXT_COLOR);
        }
        row.setBackground(bg);
    }

    private void clearMenuHighlight() {
        if (activeMenuHighlight != null && activeMenuHighlight.rowView != null) {
            stylePopupRow(activeMenuHighlight.rowView, false, activeMenuTintBase);
        }
        activeMenuHighlight = null;
    }

    private void setMenuHighlight(@Nullable MenuActionRow target) {
        if (activeMenuHighlight == target) return;
        if (activeMenuHighlight != null && activeMenuHighlight.rowView != null) {
            stylePopupRow(activeMenuHighlight.rowView, false, activeMenuTintBase);
        }
        activeMenuHighlight = target;
        if (activeMenuHighlight != null && activeMenuHighlight.rowView != null) {
            stylePopupRow(activeMenuHighlight.rowView, true, activeMenuTintBase);
        }
    }

    private boolean updateMenuHighlightForRaw(float rawX, float rawY, boolean openShortcutsOnFocus, boolean allowProjectedOutside) {
        MenuActionRow target = resolveMenuRowAtRaw(rawX, rawY, openShortcutsOnFocus, allowProjectedOutside);
        boolean keepShortcutsVisible = isRowInList(target, shortcutsRows) || (target != null && target.opensShortcuts);
        if (!keepShortcutsVisible && shortcutsPopupWindow != null && shortcutsPopupWindow.isShowing()) {
            dismissShortcutsPopup();
        }
        setMenuHighlight(target);
        return target != null;
    }

    @Nullable
    private MenuActionRow resolveMenuRowAtRaw(float rawX, float rawY, boolean openShortcutsOnFocus, boolean allowProjectedOutside) {
        MenuActionRow strictShortcut = resolveStrictInsideRow(shortcutsRows, rawX, rawY);
        if (strictShortcut != null) {
            return strictShortcut;
        }
        MenuActionRow strictMain = resolveStrictInsideRow(appContextRows, rawX, rawY);
        if (strictMain != null) {
            maybeOpenShortcutsForFocusedRow(strictMain, openShortcutsOnFocus);
            return strictMain;
        }
        if (!allowProjectedOutside) {
            return null;
        }
        int lowestBottom = Math.max(lowestRowBottom(appContextRows), lowestRowBottom(shortcutsRows));
        if (lowestBottom > 0 && rawY > (lowestBottom + dp(12))) {
            return null;
        }

        boolean hasMain = !appContextRows.isEmpty() && appContextPopupWindow != null && appContextPopupWindow.isShowing();
        boolean hasShortcuts = !shortcutsRows.isEmpty() && shortcutsPopupWindow != null && shortcutsPopupWindow.isShowing();
        if (!hasMain && !hasShortcuts) {
            return null;
        }

        if (hasMain && hasShortcuts) {
            float mainDistance = popupDistance(appContextPopupWindow, rawX, rawY);
            float shortcutsDistance = popupDistance(shortcutsPopupWindow, rawX, rawY);
            if (shortcutsDistance < mainDistance) {
                return resolveNearestRowByY(shortcutsRows, rawY);
            }
            MenuActionRow row = resolveNearestRowByY(appContextRows, rawY);
            maybeOpenShortcutsForFocusedRow(row, openShortcutsOnFocus);
            return row;
        }
        if (hasShortcuts) {
            return resolveNearestRowByY(shortcutsRows, rawY);
        }
        MenuActionRow row = resolveNearestRowByY(appContextRows, rawY);
        maybeOpenShortcutsForFocusedRow(row, openShortcutsOnFocus);
        return row;
    }

    private static boolean isRowInList(@Nullable MenuActionRow row, @NonNull List<MenuActionRow> rows) {
        if (row == null) return false;
        for (MenuActionRow candidate : rows) {
            if (candidate == row) return true;
        }
        return false;
    }

    private void maybeOpenShortcutsForFocusedRow(@Nullable MenuActionRow row, boolean openShortcutsOnFocus) {
        if (row == null || !openShortcutsOnFocus || !row.opensShortcuts || shortcutsPopupWindow != null) {
            return;
        }
        if (activeAppMenuContext != null && activeAppMenuShortcuts != null && !activeAppMenuShortcuts.isEmpty()) {
            showShortcutsPopup(activeAppMenuContext, activeAppMenuShortcuts, shortcutsMainRowView);
        }
    }

    @Nullable
    private MenuActionRow resolveStrictInsideRow(@NonNull List<MenuActionRow> rows, float rawX, float rawY) {
        for (MenuActionRow row : rows) {
            if (row.rowView != null && isRawInsideView(row.rowView, rawX, rawY)) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private static MenuActionRow resolveNearestRowByY(@NonNull List<MenuActionRow> rows, float rawY) {
        MenuActionRow nearest = null;
        float bestDistance = Float.MAX_VALUE;
        Rect rowBounds = new Rect();
        for (MenuActionRow row : rows) {
            if (!getViewScreenRect(row.rowView, rowBounds)) continue;
            if (rawY >= rowBounds.top && rawY <= rowBounds.bottom) {
                return row;
            }
            float distance = rawY < rowBounds.top ? (rowBounds.top - rawY) : (rawY - rowBounds.bottom);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = row;
            }
        }
        return nearest;
    }

    private static int lowestRowBottom(@NonNull List<MenuActionRow> rows) {
        int bottom = -1;
        Rect rowBounds = new Rect();
        for (MenuActionRow row : rows) {
            if (!getViewScreenRect(row.rowView, rowBounds)) continue;
            if (rowBounds.bottom > bottom) bottom = rowBounds.bottom;
        }
        return bottom;
    }

    private float popupDistance(@Nullable PopupWindow popupWindow, float rawX, float rawY) {
        if (popupWindow == null || !popupWindow.isShowing()) {
            return Float.MAX_VALUE;
        }
        View content = popupWindow.getContentView();
        Rect bounds = new Rect();
        if (!getViewScreenRect(content, bounds)) {
            return Float.MAX_VALUE;
        }
        float dx = 0f;
        if (rawX < bounds.left) {
            dx = bounds.left - rawX;
        } else if (rawX > bounds.right) {
            dx = rawX - bounds.right;
        }
        float dy = 0f;
        if (rawY < bounds.top) {
            dy = bounds.top - rawY;
        } else if (rawY > bounds.bottom) {
            dy = rawY - bounds.bottom;
        }
        return (dx * dx) + (dy * dy);
    }

    private static boolean isRawInsideView(@Nullable View view, float rawX, float rawY) {
        Rect bounds = new Rect();
        if (!getViewScreenRect(view, bounds)) {
            return false;
        }
        return rawX >= bounds.left && rawX <= bounds.right && rawY >= bounds.top && rawY <= bounds.bottom;
    }

    private static boolean getViewScreenRect(@Nullable View view, @NonNull Rect outRect) {
        if (view == null || outRect == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        outRect.set(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
        return true;
    }

    private void executeHighlightedMenuActionOrKeepOpen() {
        if (activeMenuHighlight != null && activeMenuHighlight.action != null) {
            activeMenuHighlight.action.run();
        }
    }

    @NonNull
    private PopupWindow buildPopupWindow(@NonNull View content, int tintBase, boolean tightWrap, @Nullable Runnable onDismiss) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int maxWidth = popupMaxWidth(screenW);
        int minWidth = popupMinWidth(screenW, tightWrap);
        int maxHeight = popupMaxHeight(screenH);

        content.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        );
        int desiredWidth = clamp(content.getMeasuredWidth(), minWidth, maxWidth);
        int desiredHeight = Math.max(dp(36), Math.min(content.getMeasuredHeight(), maxHeight));

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        scrollView.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout popupRoot = new FrameLayout(getContext());
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(dp(12));
        int alpha = clamp(appBarOpacity, 0, 100);
        int overlayColor = (((int) (255f * (alpha / 100f))) << 24) | (tintBase & 0x00FFFFFF);
        panelBg.setColor(overlayColor);
        popupRoot.setBackground(panelBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupRoot.setClipToOutline(true);
        }
        if (blurEnabled && blurRadiusDp > 0) {
            RealtimeBlurView blurView = new RealtimeBlurView(getContext());
            blurView.setBlurRadius(Math.max(0f, (float) dp(blurRadiusDp)));
            blurView.setOverlayColor(overlayColor);
            popupRoot.addView(blurView, new FrameLayout.LayoutParams(desiredWidth, desiredHeight));
        }
        popupRoot.addView(scrollView, new FrameLayout.LayoutParams(desiredWidth, desiredHeight));

        PopupWindow popup = new PopupWindow(popupRoot, desiredWidth, desiredHeight, false);
        popup.setFocusable(false);
        popup.setTouchable(true);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setElevation(8f);
        popup.setOnDismissListener(() -> {
            if (onDismiss != null) onDismiss.run();
        });
        return popup;
    }

    private void showPopupAtAnchor(@NonNull PopupWindow popupWindow, @Nullable View anchor) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int popupWidth = popupWindow.getWidth();
        int popupHeight = popupWindow.getHeight();
        int gap = dp(4);
        if (anchor != null) {
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            int x = location[0] + (anchor.getWidth() / 2) - (popupWidth / 2);
            int y = location[1] - popupHeight - gap;
            x = clamp(x, 0, Math.max(0, screenW - popupWidth));
            y = clamp(y, 0, Math.max(0, screenH - popupHeight));
            popupWindow.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
        } else {
            popupWindow.showAtLocation(this, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, getHeight() + gap);
        }
        View root = popupWindow.getContentView();
        if (root != null) {
            root.setAlpha(0f);
            root.setTranslationY(dp(8));
            root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(150)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

    private int popupMaxWidth(int screenW) {
        return Math.min(screenW - dp(24), Math.min(dp(POPUP_MAX_WIDTH_DP), (int) (screenW * 0.9f)));
    }

    private int popupMinWidth(int screenW, boolean tightWrap) {
        int target = tightWrap ? POPUP_MIN_WIDTH_TIGHT_DP : POPUP_MIN_WIDTH_DP;
        return Math.min(popupMaxWidth(screenW), dp(target));
    }

    private int popupMaxHeight(int screenH) {
        return Math.min(screenH - dp(80), (int) (screenH * POPUP_MAX_HEIGHT_FACTOR));
    }

    private void showSidePopupAlignedToRow(@NonNull PopupWindow sidePopup, @NonNull View rowAnchor, @NonNull PopupWindow mainPopup) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int[] mainLoc = new int[2];
        int[] rowLoc = new int[2];
        View mainRoot = mainPopup.getContentView();
        if (mainRoot == null) {
            showPopupAtAnchor(sidePopup, rowAnchor);
            return;
        }
        mainRoot.getLocationOnScreen(mainLoc);
        rowAnchor.getLocationOnScreen(rowLoc);
        int gap = dp(4);
        int popupWidth = sidePopup.getWidth();
        int popupHeight = sidePopup.getHeight();
        int preferredRightX = mainLoc[0] + mainPopup.getWidth() + gap;
        int preferredLeftX = mainLoc[0] - popupWidth - gap;
        int x = preferredRightX;
        if (preferredRightX + popupWidth > screenW && preferredLeftX >= 0) {
            x = preferredLeftX;
        }
        x = clamp(x, 0, Math.max(0, screenW - popupWidth));
        int rowCenterY = rowLoc[1] + (rowAnchor.getHeight() / 2);
        int y = clamp(rowCenterY - (popupHeight / 2), 0, Math.max(0, screenH - popupHeight));
        sidePopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
        View root = sidePopup.getContentView();
        if (root != null) {
            root.setAlpha(0f);
            root.setTranslationX(x >= mainLoc[0] ? dp(6) : -dp(6));
            root.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(140L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

    private void dismissAppContextPopup() {
        dismissShortcutsPopup();
        clearMenuHighlight();
        appContextRows.clear();
        activeAppMenuContext = null;
        activeAppMenuShortcuts = null;
        shortcutsMainRowView = null;
        if (appContextPopupWindow != null) {
            final PopupWindow popup = appContextPopupWindow;
            dismissPopupWindowAnimated(popup, () -> {
                if (appContextPopupWindow == popup) {
                    appContextPopupWindow = null;
                }
            });
        }
    }

    private void dismissShortcutsPopup() {
        clearMenuHighlight();
        shortcutsRows.clear();
        if (shortcutsPopupWindow != null) {
            final PopupWindow popup = shortcutsPopupWindow;
            dismissPopupWindowAnimated(popup, () -> {
                if (shortcutsPopupWindow == popup) {
                    shortcutsPopupWindow = null;
                }
            });
        }
    }

    private void dismissPopupWindowAnimated(@NonNull PopupWindow popup, @Nullable Runnable onDone) {
        View content = popup.getContentView();
        if (content != null && popup.isShowing()) {
            content.animate()
                .alpha(0f)
                .translationY(dp(6))
                .setDuration(110)
                .withEndAction(() -> {
                    try {
                        popup.dismiss();
                    } catch (Exception ignored) {
                    }
                    if (onDone != null) onDone.run();
                })
                .start();
        } else {
            try {
                popup.dismiss();
            } catch (Exception ignored) {
            }
            if (onDone != null) onDone.run();
        }
    }

    private int findPinnedFolderIndex(@NonNull PinnedFolderItem folder) {
        for (int i = 0; i < pinnedItems.size(); i++) {
            PinnedItem item = pinnedItems.get(i);
            if (!(item instanceof PinnedFolderItem)) continue;
            if (((PinnedFolderItem) item).id.equals(folder.id)) return i;
        }
        return -1;
    }

    private void updateFolderDragInsertionPreview(int targetIndex) {
        if (targetIndex < 0) {
            clearFolderDragInsertionPreview();
            return;
        }
        int maxSlots = Math.max(1, maxButtonCount);
        int clamped = clamp(targetIndex, 0, maxSlots - 1);
        if (folderDragHoverIndex == clamped) return;
        folderDragHoverIndex = clamped;
        applyBarDragTransforms();
    }

    private void clearFolderDragInsertionPreview() {
        if (folderDragHoverIndex < 0) return;
        folderDragHoverIndex = -1;
        applyBarDragTransforms();
    }

    private void applyBarDragTransforms() {
        int maxSlots = Math.max(1, maxButtonCount);
        float insertShift = (folderDragHoverIndex >= 0) ? Math.max(dp(10), getWidth() / (float) Math.max(4, maxSlots) * 0.35f) : 0f;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null) continue;
            float tx = 0f;
            if (folderDragHoverIndex >= 0 && i >= folderDragHoverIndex) {
                tx += insertShift;
            }
            child.animate().translationX(tx).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private String[] appLabels(List<LauncherAppEntry> entries) {
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            labels[i] = entries.get(i).label;
        }
        return labels;
    }

    private static final class OrderedPinnedAdapter extends RecyclerView.Adapter<OrderedPinnedAdapter.RowHolder> {
        interface DeleteCallback {
            void onDelete(int position);
        }

        private final List<LauncherAppEntry> source;
        private final List<PinnedItem> ordered;
        private final DeleteCallback deleteCallback;

        OrderedPinnedAdapter(List<LauncherAppEntry> source, List<PinnedItem> ordered, DeleteCallback deleteCallback) {
            this.source = source;
            this.ordered = ordered;
            this.deleteCallback = deleteCallback;
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpStatic(parent, 6), dpStatic(parent, 8), dpStatic(parent, 4), dpStatic(parent, 8));

            ImageView drag = new ImageView(parent.getContext());
            drag.setImageResource(R.drawable.ic_drag_indicator_24);
            drag.setColorFilter(TEXT_COLOR);
            LinearLayout.LayoutParams dragParams = new LinearLayout.LayoutParams(dpStatic(parent, 20), dpStatic(parent, 20));
            dragParams.setMargins(0, 0, dpStatic(parent, 8), 0);
            row.addView(drag, dragParams);

            ImageView folder = new ImageView(parent.getContext());
            folder.setImageResource(R.drawable.ic_folder_24);
            folder.setColorFilter(TEXT_COLOR);
            LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(dpStatic(parent, 18), dpStatic(parent, 18));
            folderParams.setMargins(0, 0, dpStatic(parent, 6), 0);
            row.addView(folder, folderParams);

            TextView label = new TextView(parent.getContext());
            label.setTextColor(TEXT_COLOR);
            label.setSingleLine(true);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageButton delete = new ImageButton(parent.getContext());
            delete.setImageResource(R.drawable.ic_delete_sweep_24);
            delete.setColorFilter(TEXT_COLOR);
            delete.setBackgroundColor(0x00000000);
            delete.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            delete.setPadding(dpStatic(parent, 2), dpStatic(parent, 2), dpStatic(parent, 2), dpStatic(parent, 2));
            delete.setLayoutParams(new LinearLayout.LayoutParams(dpStatic(parent, 24), dpStatic(parent, 24)));

            row.addView(label);
            row.addView(delete);
            return new RowHolder(row, label, folder, delete);
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            PinnedItem item = ordered.get(position);
            holder.label.setText(resolveLabel(source, item));
            holder.folder.setVisibility(item instanceof PinnedFolderItem ? View.VISIBLE : View.GONE);
            holder.delete.setOnClickListener(v -> {
                if (deleteCallback != null) {
                    deleteCallback.onDelete(holder.getAdapterPosition());
                }
            });
        }

        @Override
        public int getItemCount() {
            return ordered.size();
        }

        private static String resolveLabel(List<LauncherAppEntry> source, PinnedItem item) {
            if (item instanceof PinnedFolderItem) {
                String title = ((PinnedFolderItem) item).title;
                return TextUtils.isEmpty(title) ? "Folder" : title;
            }
            if (item instanceof PinnedAppItem) {
                AppRef ref = ((PinnedAppItem) item).appRef;
                String stable = ref.stableId();
                for (LauncherAppEntry entry : source) {
                    if (stable.equals(entry.appRef.stableId()) || entry.appRef.packageName.equals(ref.packageName)) {
                        return entry.label;
                    }
                }
                return ref.packageName;
            }
            return "Pinned item";
        }

        static final class RowHolder extends RecyclerView.ViewHolder {
            final TextView label;
            final ImageView folder;
            final ImageButton delete;

            RowHolder(@NonNull View itemView, TextView label, ImageView folder, ImageButton delete) {
                super(itemView);
                this.label = label;
                this.folder = folder;
                this.delete = delete;
            }
        }

        private static int dpStatic(@NonNull ViewGroup parent, int value) {
            return Math.round(value * parent.getResources().getDisplayMetrics().density);
        }
    }

    private static final class PinOption {
        final String id;
        final String label;
        final PinnedItem item;

        PinOption(String id, String label, PinnedItem item) {
            this.id = id;
            this.label = label;
            this.item = item;
        }
    }

    private static final class MenuActionRow {
        @NonNull final TextView rowView;
        @NonNull final Runnable action;
        final boolean opensShortcuts;

        MenuActionRow(@NonNull TextView rowView, @NonNull Runnable action, boolean opensShortcuts) {
            this.rowView = rowView;
            this.action = action;
            this.opensShortcuts = opensShortcuts;
        }
    }

    private static final class AppMenuContext {
        final LauncherAppEntry entry;
        final View anchor;
        final int pinnedIndex;
        @Nullable final PinnedFolderItem sourceFolder;
        @Nullable final AppRef folderEntryRef;

        AppMenuContext(
            @NonNull LauncherAppEntry entry,
            @NonNull View anchor,
            int pinnedIndex,
            @Nullable PinnedFolderItem sourceFolder,
            @Nullable AppRef folderEntryRef
        ) {
            this.entry = entry;
            this.anchor = anchor;
            this.pinnedIndex = pinnedIndex;
            this.sourceFolder = sourceFolder;
            this.folderEntryRef = folderEntryRef;
        }
    }

    private static final class LongPressPickupState {
        final View sourceView;
        final int pinnedIndex;
        final float downRawX;
        final float downRawY;
        boolean menuShown = false;
        boolean dragStarted = false;
        long menuShownAtMs = 0L;
        boolean definitiveYMovement = false;
        boolean selectionArmed = false;

        LongPressPickupState(@NonNull View sourceView, int pinnedIndex, float downRawX, float downRawY) {
            this.sourceView = sourceView;
            this.pinnedIndex = pinnedIndex;
            this.downRawX = downRawX;
            this.downRawY = downRawY;
        }
    }

    private List<PinOption> buildPinOptions(@NonNull List<LauncherAppEntry> apps, @NonNull List<PinnedItem> currentPinned) {
        List<PinOption> out = new ArrayList<>();
        for (LauncherAppEntry app : apps) {
            AppRef ref = resolveForSelectionRef(app.appRef);
            out.add(new PinOption(ref.stableId(), app.label, new PinnedAppItem(ref)));
        }
        return out;
    }

    private static void syncListChecks(@NonNull ListView listView, @NonNull List<PinOption> options, @NonNull Set<String> selectedIds) {
        for (int i = 0; i < options.size(); i++) {
            listView.setItemChecked(i, selectedIds.contains(options.get(i).id));
        }
    }

    private static void syncListChecksFiltered(@NonNull ListView listView, @NonNull List<PinOption> options, @NonNull Set<String> selectedIds) {
        for (int i = 0; i < options.size(); i++) {
            listView.setItemChecked(i, selectedIds.contains(options.get(i).id));
        }
    }

    private static void syncFolderChecks(@NonNull ListView listView, @NonNull List<LauncherAppEntry> apps, @NonNull Set<String> selectedIds) {
        for (int i = 0; i < apps.size(); i++) {
            listView.setItemChecked(i, selectedIds.contains(apps.get(i).appRef.stableId()));
        }
    }

    private boolean startPinnedDrag(@NonNull View view, int sourceIndex) {
        ClipData clip = ClipData.newPlainText("pinned-item", Integer.toString(sourceIndex));
        PinnedDragState dragState = new PinnedDragState(sourceIndex);
        View.DragShadowBuilder shadow = createRaisedDragShadow(view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clip, shadow, dragState, 0);
        } else {
            view.startDrag(clip, shadow, dragState, 0);
        }
        return true;
    }

    private boolean startFolderPopupDrag(@NonNull View view, @NonNull LauncherAppEntry entry, @NonNull PinnedFolderItem folder) {
        ClipData clip = ClipData.newPlainText("folder-app", entry.appRef.stableId());
        FolderAppDragState dragState = new FolderAppDragState(resolveForSelectionRef(entry.appRef), folder);
        View.DragShadowBuilder shadow = createRaisedDragShadow(view);
        boolean started;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            started = view.startDragAndDrop(clip, shadow, dragState, 0);
        } else {
            started = view.startDrag(clip, shadow, dragState, 0);
        }
        if (started) {
            dismissFolderPopup();
        }
        return started;
    }

    @NonNull
    private View.DragShadowBuilder createRaisedDragShadow(@NonNull View view) {
        return new View.DragShadowBuilder(view) {
            @Override
            public void onProvideShadowMetrics(@NonNull Point outShadowSize, @NonNull Point outShadowTouchPoint) {
                super.onProvideShadowMetrics(outShadowSize, outShadowTouchPoint);
                if (outShadowSize.y > 1) {
                    outShadowTouchPoint.y = Math.min(outShadowSize.y - 1, Math.round(outShadowSize.y * 0.86f));
                }
            }
        };
    }

    private boolean handlePinnedBarDragEvent(@NonNull View targetView, @NonNull DragEvent event) {
        Object localState = event.getLocalState();
        boolean pinnedDrag = localState instanceof PinnedDragState;
        boolean folderDrag = localState instanceof FolderAppDragState;
        if (!pinnedDrag && !folderDrag) return false;

        int slotCount = Math.max(1, maxButtonCount);
        float width = Math.max(1f, targetView.getWidth());
        float x = Math.max(0f, Math.min(width, event.getX()));
        float slotWidth = width / slotCount;
        float contentX = x;
        int hoveredSlot = clamp((int) (contentX / Math.max(1f, slotWidth)), 0, slotCount - 1);
        float slotStartX = hoveredSlot * slotWidth;
        float dropXRatio = slotWidth <= 0f ? 0.5f : Math.max(0f, Math.min(1f, (contentX - slotStartX) / slotWidth));

        int pageOffset = Math.max(0, pinnedPageIndex) * Math.max(1, pinnedItemsPerPage);
        int targetIndex = clamp(pageOffset + hoveredSlot, 0, pinnedItems == null ? 0 : pinnedItems.size());
        PinnedItem targetItem = null;
        if (pinnedItems != null && targetIndex >= 0 && targetIndex < pinnedItems.size()) {
            targetItem = pinnedItems.get(targetIndex);
        }

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                updateFolderDragInsertionPreview(hoveredSlot);
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                updateFolderDragInsertionPreview(hoveredSlot);
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                targetView.setAlpha(0.92f);
                updateFolderDragInsertionPreview(hoveredSlot);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                targetView.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP:
                targetView.setAlpha(1f);
                clearFolderDragInsertionPreview();
                if (pinnedDrag) {
                    return applyPinnedDrop((PinnedDragState) localState, targetIndex, targetItem, dropXRatio);
                } else {
                    return applyFolderDropToBar((FolderAppDragState) localState, targetIndex, targetItem, dropXRatio);
                }
            case DragEvent.ACTION_DRAG_ENDED:
                targetView.setAlpha(1f);
                clearFolderDragInsertionPreview();
                return true;
            default:
                return false;
        }
    }

    private boolean applyPinnedDrop(@NonNull PinnedDragState dragState, int targetIndex, @Nullable PinnedItem targetItem, float dropXRatio) {
        if (dragState.sourceIndex < 0 || dragState.sourceIndex >= pinnedItems.size()) return false;
        if (targetIndex < 0 || targetIndex > pinnedItems.size()) return false;

        PinnedItem sourceItem = pinnedItems.get(dragState.sourceIndex);
        boolean sourceIsApp = sourceItem instanceof PinnedAppItem;
        AppRef sourceRef = sourceIsApp ? resolveForSelectionRef(((PinnedAppItem) sourceItem).appRef) : null;

        if (sourceIsApp && targetItem instanceof PinnedFolderItem) {
            PinnedFolderItem folder = (PinnedFolderItem) targetItem;
            boolean alreadyInFolder = false;
            for (AppRef ref : folder.apps) {
                if (sourceRef != null && ref.stableId().equals(sourceRef.stableId())) {
                    alreadyInFolder = true;
                    break;
                }
            }
            if (!alreadyInFolder && sourceRef != null) folder.apps.add(sourceRef);
            pinnedItems.remove(dragState.sourceIndex);
            persistPinsAndReload();
            return true;
        }

        if (sourceIsApp && targetItem instanceof PinnedAppItem && shouldCreateFolderOnDrop(dropXRatio)) {
            AppRef targetRef = resolveForSelectionRef(((PinnedAppItem) targetItem).appRef);
            int source = dragState.sourceIndex;
            int target = targetIndex;
            if (source < target) {
                pinnedItems.remove(source);
                target = target - 1;
            } else {
                pinnedItems.remove(source);
            }
            target = clamp(target, 0, Math.max(0, pinnedItems.size() - 1));
            PinnedFolderItem folder = new PinnedFolderItem(UUID.randomUUID().toString(), "Folder");
            folder.apps.add(targetRef);
            if (!targetRef.stableId().equals(sourceRef.stableId())) {
                folder.apps.add(sourceRef);
            }
            pinnedItems.set(target, folder);
            persistPinsAndReload();
            return true;
        }

        int insertionIndex = computeInsertionIndex(targetIndex, targetItem, dropXRatio);
        return movePinnedItem(dragState.sourceIndex, insertionIndex);
    }

    private boolean applyFolderDropToBar(@NonNull FolderAppDragState dragState, int targetIndex, @Nullable PinnedItem targetItem, float dropXRatio) {
        if (targetIndex < 0) return false;
        AppRef dragged = dragState.appRef;
        if (dragged == null) return false;

        int existingIndex = -1;
        for (int i = 0; i < pinnedItems.size(); i++) {
            PinnedItem item = pinnedItems.get(i);
            if (item instanceof PinnedAppItem) {
                if (((PinnedAppItem) item).appRef.stableId().equals(dragged.stableId())) {
                    existingIndex = i;
                    break;
                }
            }
        }
        if (existingIndex >= 0) {
            pinnedItems.remove(existingIndex);
            if (existingIndex < targetIndex) targetIndex--;
        }

        targetIndex = computeInsertionIndex(targetIndex, targetItem, dropXRatio);
        targetIndex = clamp(targetIndex, 0, pinnedItems.size());
        pinnedItems.add(targetIndex, new PinnedAppItem(dragged));

        if (dragState.sourceFolder != null) {
            removeAppFromFolder(dragState.sourceFolder, dragged);
        }
        persistPinsAndReload();
        return true;
    }

    private int computeInsertionIndex(int targetIndex, @Nullable PinnedItem targetItem, float dropXRatio) {
        int insertionIndex = targetIndex;
        if (targetItem != null && dropXRatio >= 0.5f) {
            insertionIndex = targetIndex + 1;
        }
        return clamp(insertionIndex, 0, Math.max(0, pinnedItems.size()));
    }

    private boolean shouldCreateFolderOnDrop(float dropXRatio) {
        return dropXRatio >= 0.28f && dropXRatio <= 0.72f;
    }

    private boolean movePinnedItem(int fromIndex, int insertionIndex) {
        if (fromIndex < 0 || fromIndex >= pinnedItems.size()) return false;
        int boundedInsertion = clamp(insertionIndex, 0, pinnedItems.size());
        PinnedItem moved = pinnedItems.remove(fromIndex);
        if (fromIndex < boundedInsertion) boundedInsertion--;
        boundedInsertion = clamp(boundedInsertion, 0, pinnedItems.size());
        pinnedItems.add(boundedInsertion, moved);
        if (fromIndex == boundedInsertion) {
            return false;
        }
        persistPinsAndReload();
        return true;
    }

    private void removeAppFromFolder(@NonNull PinnedFolderItem folder, @NonNull AppRef appRef) {
        for (int i = folder.apps.size() - 1; i >= 0; i--) {
            if (folder.apps.get(i).stableId().equals(appRef.stableId())) {
                folder.apps.remove(i);
            }
        }
        if (folder.apps.isEmpty()) {
            for (int i = 0; i < pinnedItems.size(); i++) {
                PinnedItem item = pinnedItems.get(i);
                if (item instanceof PinnedFolderItem) {
                    if (((PinnedFolderItem) item).id.equals(folder.id)) {
                        pinnedItems.remove(i);
                        break;
                    }
                }
            }
        }
    }

    @Nullable
    private static String stableIdForPinnedItem(@Nullable PinnedItem item) {
        if (item instanceof PinnedAppItem) {
            return ((PinnedAppItem) item).appRef.stableId();
        }
        if (item instanceof PinnedFolderItem) {
            return "folder:" + ((PinnedFolderItem) item).id;
        }
        return null;
    }

    private static void removePinnedByStableId(@NonNull List<PinnedItem> items, @NonNull String stableId) {
        for (int i = items.size() - 1; i >= 0; i--) {
            String itemStableId = stableIdForPinnedItem(items.get(i));
            if (stableId.equals(itemStableId)) {
                items.remove(i);
            }
        }
    }

    @NonNull
    private static PinnedItem clonePinnedItem(@NonNull PinnedItem item) {
        if (item instanceof PinnedAppItem) {
            AppRef ref = ((PinnedAppItem) item).appRef;
            return new PinnedAppItem(new AppRef(ref.packageName, ref.activityName));
        }
        if (item instanceof PinnedFolderItem) {
            PinnedFolderItem folder = (PinnedFolderItem) item;
            PinnedFolderItem copy = new PinnedFolderItem(folder.id, folder.title);
            copy.rows = folder.rows;
            copy.cols = folder.cols;
            copy.tintOverrideEnabled = folder.tintOverrideEnabled;
            copy.tintColor = folder.tintColor;
            for (AppRef ref : folder.apps) {
                copy.apps.add(new AppRef(ref.packageName, ref.activityName));
            }
            return copy;
        }
        return item;
    }

    @Nullable
    private String resolveForSelectionId(@NonNull AppRef ref) {
        AppRef resolved = resolveForSelectionRef(ref);
        return resolved == null ? null : resolved.stableId();
    }

    @NonNull
    private AppRef resolveForSelectionRef(@NonNull AppRef ref) {
        if (!TextUtils.isEmpty(ref.activityName)) return ref;
        LauncherAppEntry resolved = resolveRef(ref);
        return resolved != null ? resolved.appRef : ref;
    }

    private static final class PinnedDragState {
        final int sourceIndex;

        PinnedDragState(int sourceIndex) {
            this.sourceIndex = sourceIndex;
        }
    }

    private static final class FolderAppDragState {
        final AppRef appRef;
        final PinnedFolderItem sourceFolder;

        FolderAppDragState(@NonNull AppRef appRef, @NonNull PinnedFolderItem sourceFolder) {
            this.appRef = appRef;
            this.sourceFolder = sourceFolder;
        }
    }

    private LinearLayout buildStepperRow(@NonNull String label, @NonNull int[] valueRef, int min, int max) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView title = new TextView(getContext());
        title.setText(label);
        title.setTextColor(TEXT_COLOR);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton minus = new ImageButton(getContext());
        minus.setImageResource(android.R.drawable.ic_media_previous);
        styleIconButton(minus, dp(2));
        row.addView(minus, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView valueText = new TextView(getContext());
        valueText.setTextColor(TEXT_COLOR);
        valueText.setTypeface(Typeface.DEFAULT_BOLD);
        valueText.setText(Integer.toString(valueRef[0]));
        valueText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.setMargins(dp(6), 0, dp(6), 0);
        row.addView(valueText, valueParams);

        ImageButton plus = new ImageButton(getContext());
        plus.setImageResource(android.R.drawable.ic_input_add);
        styleIconButton(plus, dp(2));
        row.addView(plus, new LinearLayout.LayoutParams(dp(24), dp(24)));

        minus.setOnClickListener(v -> {
            valueRef[0] = clamp(valueRef[0] - 1, min, max);
            valueText.setText(Integer.toString(valueRef[0]));
        });
        plus.setOnClickListener(v -> {
            valueRef[0] = clamp(valueRef[0] + 1, min, max);
            valueText.setText(Integer.toString(valueRef[0]));
        });
        return row;
    }

    private static int parseInt(CharSequence value, int fallback) {
        try {
            return Integer.parseInt(stringValue(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String stringValue(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    @Nullable
    private static Integer parseColor(String value) {
        try {
            String clean = value.startsWith("#") ? value.substring(1) : value;
            if (clean.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(clean, 16));
            }
            if (clean.length() == 8) {
                return (int) Long.parseLong(clean, 16);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float computeAzAnchorPosition(char letter, int slots) {
        if (slots <= 1) return 0f;
        Set<Character> available = getAvailableAzLetters();
        List<Character> ordered = new ArrayList<>();
        for (char c : AZ_ORDER) {
            if (available.contains(c)) {
                ordered.add(c);
            }
        }
        if (ordered.isEmpty()) return (slots - 1) / 2f;
        char target = Character.toUpperCase(letter);
        int index = ordered.indexOf(target);
        if (index < 0) index = 0;
        if (ordered.size() == 1) return (slots - 1) / 2f;
        float normalized = (float) index / (float) (ordered.size() - 1);
        return Math.max(0f, Math.min(slots - 1, normalized * (slots - 1)));
    }

    private void animatePageSwitch(int pageDelta, float velocityPxPerSec) {
        if (pageSwitchAnimating) return;
        int totalPages = getPinnedPagesCount();
        if (totalPages <= 1) return;
        int targetPage = clamp(pinnedPageIndex + pageDelta, 0, totalPages - 1);
        if (targetPage == pinnedPageIndex) return;

        pageSwitchAnimating = true;
        final int direction = pageDelta > 0 ? 1 : -1;
        final float travel = Math.max(dp(24), getWidth() * 0.28f);
        final long duration = computePageAnimDuration(velocityPxPerSec);

        animate()
            .translationX(-direction * travel)
            .alpha(0.42f)
            .setDuration(duration)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setListenerSafe(null);
                pinnedPageIndex = targetPage;
                reloadWithInput("", lastTerminalView);
                setTranslationX(direction * travel * 0.48f);
                setAlpha(0.52f);
                animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(Math.max(110, duration - 20))
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            setListenerSafe(null);
                            pageSwitchAnimating = false;
                        }
                    })
                    .start();
            }
            })
            .start();
    }

    private void animateAzPageSwitch(int pageDelta, float velocityPxPerSec) {
        if (pageSwitchAnimating) return;
        int totalPages = getAzPagesCount();
        if (totalPages <= 1) return;
        int targetPage = clamp(activeAzPageIndex + pageDelta, 0, totalPages - 1);
        if (targetPage == activeAzPageIndex) return;

        pageSwitchAnimating = true;
        final int direction = pageDelta > 0 ? 1 : -1;
        final float travel = Math.max(dp(24), getWidth() * 0.28f);
        final long duration = computePageAnimDuration(velocityPxPerSec);

        animate()
            .translationX(-direction * travel)
            .alpha(0.42f)
            .setDuration(duration)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setListenerSafe(null);
                    activeAzPageIndex = targetPage;
                    renderButtons(activeAzCandidates, true);
                    setTranslationX(direction * travel * 0.48f);
                    setAlpha(0.52f);
                    animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(Math.max(110, duration - 20))
                        .setInterpolator(new DecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                setListenerSafe(null);
                                pageSwitchAnimating = false;
                            }
                        })
                        .start();
                }
            })
            .start();
    }

    private long computePageAnimDuration(float velocityPxPerSec) {
        float v = Math.max(200f, Math.min(6000f, Math.abs(velocityPxPerSec)));
        long ms = (long) (280f - ((v - 200f) / (6000f - 200f)) * 140f);
        return clamp((int) ms, 120, 280);
    }

    private void setListenerSafe(@Nullable AnimatorListenerAdapter adapter) {
        animate().setListener(adapter);
    }

    private View createPopupEntryButton(@NonNull LauncherAppEntry entry, int sizePx, @NonNull PinnedFolderItem sourceFolder) {
        if (entry.icon == null || !showIcons) {
            View fallback = createEntryButton(entry);
            View pressTarget = resolvePrimaryPressTarget(fallback);
            bindAppContextLongPress(pressTarget, entry, -1, sourceFolder, resolveForSelectionRef(entry.appRef), false);
            return fallback;
        }
        ImageButton button = new ImageButton(getContext());
        button.setImageDrawable(entry.icon);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setAdjustViewBounds(true);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(0x00000000);
        button.setMinimumWidth(sizePx);
        button.setMinimumHeight(sizePx);
        button.setLayoutParams(new ViewGroup.LayoutParams(sizePx, sizePx));
        if (bandW) {
            float[] colorMatrix = {
                0.33f, 0.33f, 0.33f, 0, 0,
                0.33f, 0.33f, 0.33f, 0, 0,
                0.33f, 0.33f, 0.33f, 0, 0,
                0, 0, 0, 1, 0
            };
            button.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        }
        button.setOnClickListener(v -> launchEntryFromTouch(v, entry, lastTerminalView));
        bindAppContextLongPress(button, entry, -1, sourceFolder, resolveForSelectionRef(entry.appRef), false);
        button.setContentDescription(entry.label);
        registerLaunchTarget(entry.appRef, button);
        return button;
    }

    private void registerLaunchTarget(@NonNull AppRef appRef, @NonNull View view) {
        String key = componentKeyFromRef(appRef);
        if (key == null) return;
        launchTargetViews.put(key, new WeakReference<>(view));
        launchTargetViewsByPackage.put(appRef.packageName, new WeakReference<>(view));
        String fullKey = componentFullKeyFromRef(appRef);
        if (fullKey != null) {
            launchTargetViews.put(fullKey, new WeakReference<>(view));
        }
    }

    @Nullable
    private Rect getSourceBoundsOnScreen(@NonNull View sourceView) {
        if (!sourceView.isAttachedToWindow()) {
            return null;
        }
        int[] location = new int[2];
        sourceView.getLocationOnScreen(location);
        int width = Math.max(1, sourceView.getWidth());
        int height = Math.max(1, sourceView.getHeight());
        return new Rect(location[0], location[1], location[0] + width, location[1] + height);
    }

    @Nullable
    private View findFirstAttachedLaunchTargetForPackage(@NonNull String packageName) {
        for (Map.Entry<String, WeakReference<View>> entry : launchTargetViews.entrySet()) {
            if (!entry.getKey().startsWith(packageName + "/")) {
                continue;
            }
            View candidate = entry.getValue().get();
            if (candidate != null && candidate.isAttachedToWindow()) {
                return candidate;
            }
        }
        return null;
    }

    private static final class LaunchAnimationContext {
        @Nullable final Rect sourceBounds;
        @Nullable final Bundle options;

        LaunchAnimationContext(@Nullable Rect sourceBounds, @Nullable Bundle options) {
            this.sourceBounds = sourceBounds;
            this.options = options;
        }
    }

    @Nullable
    private String componentKeyFromRef(@Nullable AppRef appRef) {
        if (appRef == null || TextUtils.isEmpty(appRef.packageName) || TextUtils.isEmpty(appRef.activityName)) {
            return null;
        }
        String activity = appRef.activityName;
        if (activity.startsWith(".")) {
            activity = appRef.packageName + activity;
        }
        ComponentName componentName = new ComponentName(appRef.packageName, activity);
        return componentName.flattenToShortString();
    }

    @Nullable
    private String componentFullKeyFromRef(@Nullable AppRef appRef) {
        if (appRef == null || TextUtils.isEmpty(appRef.packageName) || TextUtils.isEmpty(appRef.activityName)) {
            return null;
        }
        String activity = appRef.activityName;
        if (activity.startsWith(".")) {
            activity = appRef.packageName + activity;
        }
        return new ComponentName(appRef.packageName, activity).flattenToString();
    }

    private boolean shouldUseTouchLaunchAnimation(@Nullable View sourceView) {
        return sourceView != null;
    }

    private void applyLaunchBloom(@NonNull View sourceView) {
        ViewGroup container = findBloomHost(sourceView);
        if (container == null) {
            return;
        }
        ensureUnclipped(container);
        View glowFill = new View(getContext());
        View ring = new View(getContext());
        int base = inheritedTintColor & 0x00FFFFFF;
        GradientDrawable fill = new GradientDrawable();
        fill.setShape(GradientDrawable.OVAL);
        fill.setColor((0x55 << 24) | base);
        glowFill.setBackground(fill);
        GradientDrawable stroke = new GradientDrawable();
        stroke.setShape(GradientDrawable.OVAL);
        stroke.setColor(0x00000000);
        stroke.setStroke(dp(2), (0xDD << 24) | base);
        ring.setBackground(stroke);
        int size = Math.max(sourceView.getWidth(), sourceView.getHeight());
        if (size <= 0) {
            size = iconSizePx();
        }
        int glowSize = Math.round(size * 1.16f);
        int ringSize = Math.round(size * 0.96f);
        glowFill.setLayoutParams(new ViewGroup.LayoutParams(glowSize, glowSize));
        ring.setLayoutParams(new ViewGroup.LayoutParams(ringSize, ringSize));
        glowFill.setAlpha(0f);
        ring.setAlpha(0f);
        int[] srcLoc = new int[2];
        int[] rootLoc = new int[2];
        sourceView.getLocationOnScreen(srcLoc);
        container.getLocationOnScreen(rootLoc);
        float cx = (srcLoc[0] - rootLoc[0]) + sourceView.getWidth() / 2f;
        float cy = (srcLoc[1] - rootLoc[1]) + sourceView.getHeight() / 2f;
        glowFill.setX(cx - glowSize / 2f);
        glowFill.setY(cy - glowSize / 2f);
        ring.setX(cx - ringSize / 2f);
        ring.setY(cy - ringSize / 2f);
        glowFill.setScaleX(0.84f);
        glowFill.setScaleY(0.84f);
        ring.setScaleX(0.84f);
        ring.setScaleY(0.84f);
        container.addView(glowFill);
        container.addView(ring);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            glowFill.setElevation(dp(22));
            glowFill.setTranslationZ(dp(22));
            ring.setElevation(dp(24));
            ring.setTranslationZ(dp(24));
        }
        sourceView.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(56L)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> sourceView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140L)
                .setInterpolator(new OvershootInterpolator(1.28f))
                .start())
            .start();
        glowFill.animate()
            .alpha(0.36f)
            .scaleX(1.18f)
            .scaleY(1.18f)
            .setDuration(104L)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> glowFill.animate()
                .alpha(0f)
                .scaleX(1.38f)
                .scaleY(1.38f)
                .setDuration(124L)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> container.removeView(glowFill))
                .start())
            .start();
        ring.animate()
            .alpha(0.9f)
            .scaleX(1.24f)
            .scaleY(1.24f)
            .setDuration(118L)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> ring.animate()
                .alpha(0f)
                .scaleX(1.48f)
                .scaleY(1.48f)
                .setDuration(132L)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> container.removeView(ring))
                .start())
            .start();
    }

    @Nullable
    private ViewGroup findBloomHost(@NonNull View sourceView) {
        ViewGroup fallback = (sourceView.getParent() instanceof ViewGroup) ? (ViewGroup) sourceView.getParent() : null;
        ViewParent parent = sourceView.getParent();
        while (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            if (group.getWidth() > sourceView.getWidth() * 3 && group.getHeight() > sourceView.getHeight() * 3) {
                fallback = group;
            }
            parent = group.getParent();
        }
        return fallback;
    }

    private void ensureUnclipped(@NonNull ViewGroup container) {
        ViewParent p = container;
        int hops = 0;
        while (p instanceof ViewGroup && hops < 4) {
            ViewGroup g = (ViewGroup) p;
            g.setClipChildren(false);
            g.setClipToPadding(false);
            p = g.getParent();
            hops++;
        }
    }

    private int computeFolderPopupIconSize(int rows, int cols, int screenW, int screenH) {
        int maxPopupWidth = Math.min(screenW - dp(24), (int) (screenW * 0.9f));
        int maxPopupHeight = Math.min(screenH - dp(80), (int) (screenH * 0.45f));
        int headerHeight = dp(30);
        int horizontalPadding = dp(20);
        int verticalPadding = dp(20) + headerHeight;
        int cellMargin = dp(4);
        int byWidth = (maxPopupWidth - horizontalPadding - (cellMargin * cols * 2)) / Math.max(cols, 1);
        int byHeight = (maxPopupHeight - verticalPadding - (cellMargin * rows * 2)) / Math.max(rows, 1);
        int candidate = Math.min(iconSizePx(), Math.min(byWidth, byHeight));
        return clamp(candidate, dp(16), iconSizePx());
    }

    private void styleGhostButton(@NonNull Button button) {
        button.setBackgroundColor(0x00000000);
        button.setTextColor(TEXT_COLOR);
        button.setAllCaps(false);
    }

    private void styleIconButton(@NonNull ImageButton button, int paddingPx) {
        button.setBackgroundColor(0x00000000);
        button.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setColorFilter(TEXT_COLOR);
    }

    private int computePinnedItemsPerPage() {
        return Math.max(1, maxButtonCount);
    }

    private int getPinnedPagesCount() {
        int totalPinned = pinnedItems == null ? 0 : pinnedItems.size();
        int perPage = Math.max(1, pinnedItemsPerPage);
        if (totalPinned <= 0) return 1;
        return (totalPinned + perPage - 1) / perPage;
    }

    private int getAzPagesCount() {
        int total = activeAzCandidates == null ? 0 : activeAzCandidates.size();
        int perPage = Math.max(1, maxButtonCount);
        if (total <= 0) return 1;
        return (total + perPage - 1) / perPage;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int iconSizePx() {
        return Math.max(dp(20), Math.round(24f * iconScale * getResources().getDisplayMetrics().density));
    }

    @NonNull
    private static List<LauncherAppEntry> injectedToEntries(@Nullable List<? extends SuggestionBarButton> buttons) {
        List<LauncherAppEntry> out = new ArrayList<>();
        if (buttons == null) return out;
        for (int i = 0; i < buttons.size(); i++) {
            SuggestionBarButton button = buttons.get(i);
            if (button == null) continue;
            String label = button.getText() == null ? "" : button.getText();
            AppRef ref = new AppRef("injected.test", "entry" + i);
            out.add(new LauncherAppEntry(ref, label, button.getIcon()));
        }
        return out;
    }
}
