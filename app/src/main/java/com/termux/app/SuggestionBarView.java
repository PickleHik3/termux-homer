package com.termux.app;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ChangedPackages;
import android.content.pm.PackageManager;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.DragEvent;
import android.util.AttributeSet;
import android.view.VelocityTracker;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SuggestionBarView extends GridLayout {

    private static final int TEXT_COLOR = 0xFFC0B18B;
    private static final char[] AZ_ORDER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray();

    private List<LauncherAppEntry> allApps = new ArrayList<>();
    private int applicationSequenceNumber = 0;

    private int maxButtonCount = 5;
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

    private LauncherAppDataProvider appDataProvider;
    private LauncherConfigRepository configRepository;
    private List<PinnedItem> pinnedItems = new ArrayList<>();
    private List<SuggestionBarButton> injectedSuggestionButtons;

    private PopupWindow folderPopupWindow;

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
                int anchor = computeAzAnchorSlot(activeAzLetter, perPage);
                int centeredStart = anchor - (entries.size() / 2);
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

        int addedCount = 0;
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
                view.setOnDragListener((target, event) -> handlePinnedDrop(target, event, pinnedIndex, pinnedItem));
                if (pinnedItem instanceof PinnedFolderItem) {
                    view = createFolderPreviewButton((PinnedFolderItem) pinnedItem);
                    view.setLayoutParams(param);
                    view.setOnDragListener((target, event) -> handlePinnedDrop(target, event, pinnedIndex, pinnedItem));
                    view.setOnClickListener(v -> showFolderPopup((PinnedFolderItem) pinnedItem, v));
                    view.setOnLongClickListener(v -> {
                        showFolderContentsEditor(pinnedIndex, (PinnedFolderItem) pinnedItem);
                        return true;
                    });
                } else {
                    final PinnedAppItem appItem = (PinnedAppItem) pinnedItem;
                    view.setOnLongClickListener(v -> startPinnedDrag(v, pinnedIndex, appItem));
                }
            } else if (!azPreview) {
                final int slotIndex = col;
                view.setOnLongClickListener(v -> {
                    showUnifiedPinEditor(slotIndex, null);
                    return true;
                });
            }

            addView(view);
            addedCount++;
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
            hint.setText("Long-press here to pin your favorite apps");
            hint.setTextColor(0x99B8B8B8);
            hint.setTextSize(11f);
            hint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
            hint.setGravity(Gravity.CENTER);
            hint.setSingleLine(true);
            hint.setPadding(dp(6), 0, dp(6), 0);
            GridLayout.LayoutParams hintParams = createSlotParams(0);
            hintParams.columnSpec = GridLayout.spec(0, Math.max(1, buttonCount), 1f);
            hintParams.width = 0;
            hint.setLayoutParams(hintParams);
            hint.setOnLongClickListener(v -> {
                showUnifiedPinEditor(0, null);
                return true;
            });
            applyPinnedHintAnimation(hint);
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
                filler.setVisibility(INVISIBLE);
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
        } else {
            setOnLongClickListener(null);
        }
    }

    private void applyPinnedHintAnimation(@NonNull TextView hintView) {
        ObjectAnimator shimmerX = ObjectAnimator.ofFloat(hintView, View.TRANSLATION_X, -dp(10), dp(10));
        shimmerX.setDuration(1700L);
        shimmerX.setRepeatCount(ObjectAnimator.INFINITE);
        shimmerX.setRepeatMode(ObjectAnimator.REVERSE);
        shimmerX.setInterpolator(new LinearInterpolator());

        ObjectAnimator shimmerAlpha = ObjectAnimator.ofFloat(hintView, View.ALPHA, 0.45f, 0.8f, 0.45f);
        shimmerAlpha.setDuration(1700L);
        shimmerAlpha.setRepeatCount(ObjectAnimator.INFINITE);
        shimmerAlpha.setRepeatMode(ObjectAnimator.RESTART);
        shimmerAlpha.setInterpolator(new LinearInterpolator());

        shimmerX.start();
        shimmerAlpha.start();
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
            imageButton.setOnClickListener(v -> launchEntry(entry, lastTerminalView));
            imageButton.setContentDescription(entry.label);
            shell.addView(imageButton);
            return shell;
        }

        Button button = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        button.setTextSize(textSize);
        button.setText(entry.label);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(TEXT_COLOR);
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(v -> launchEntry(entry, lastTerminalView));
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
        if (entry.appRef.packageName.startsWith("injected.test")) {
            return;
        }
        Context context = getContext();
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.setClassName(entry.appRef.packageName, entry.appRef.activityName);
        try {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity);
                    activity.startActivity(launchIntent, options.toBundle());
                } else {
                    activity.startActivity(launchIntent);
                }
            } else {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            }
        } catch (Exception ignored) {
            Intent fallback = context.getPackageManager().getLaunchIntentForPackage(entry.appRef.packageName);
            if (fallback != null) {
                if (context instanceof Activity) {
                    Activity activity = (Activity) context;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity);
                        activity.startActivity(fallback, options.toBundle());
                    } else {
                        activity.startActivity(fallback);
                    }
                } else {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(fallback);
                }
            }
        }

        if (terminalView != null) {
            terminalView.clearInputLine();
        }
        dismissFolderPopup();
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
        for (LauncherAppEntry entry : allApps) {
            if (entry.appRef.packageName.equals(ref.packageName)) {
                return entry;
            }
        }
        return null;
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

        ImageButton settings = new ImageButton(getContext());
        settings.setImageResource(R.drawable.ic_settings);
        settings.setContentDescription("Folder settings");
        styleIconButton(settings, dp(4));
        settings.setOnClickListener(v -> {
            dialog.dismiss();
            showFolderSettings(folder);
        });
        topActions.addView(settings, new LinearLayout.LayoutParams(dp(28), dp(28)));

        ImageButton delete = new ImageButton(getContext());
        delete.setImageResource(R.drawable.ic_delete_sweep_24);
        delete.setContentDescription("Delete folder");
        styleIconButton(delete, dp(4));
        delete.setOnClickListener(v -> {
            removePinnedAt(folderIndex);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        deleteParams.setMargins(dp(10), 0, 0, 0);
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
            View btn = createPopupEntryButton(entry, popupIconSize);
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

        ImageButton gear = new ImageButton(getContext());
        gear.setImageResource(R.drawable.ic_settings);
        styleIconButton(gear, dp(3));
        int gearSize = dp(24);
        gear.setOnClickListener(v -> {
            dismissFolderPopup();
            showFolderSettings(folder);
        });

        header.addView(title);
        header.addView(gear, new LinearLayout.LayoutParams(gearSize, gearSize));
        shell.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        shell.measure(View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST));
        int contentW = shell.getMeasuredWidth();
        int contentH = shell.getMeasuredHeight();

        FrameLayout popupRoot = new FrameLayout(getContext());
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(dp(12));
        popupRoot.setBackground(panelBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupRoot.setClipToOutline(true);
        }
        int alpha = clamp(appBarOpacity, 0, 100);
        int overlayBase = folder.tintOverrideEnabled ? (folder.tintColor & 0x00FFFFFF) : (inheritedTintColor & 0x00FFFFFF);
        int overlayColor = (((int) (255f * (alpha / 100f))) << 24) | overlayBase;
        panelBg.setColor(overlayColor);
        popupRoot.addView(shell, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popupRoot.measure(View.MeasureSpec.makeMeasureSpec(contentW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(contentH, View.MeasureSpec.EXACTLY));
        int popupWidth = popupRoot.getMeasuredWidth();
        int popupHeight = popupRoot.getMeasuredHeight();

        folderPopupWindow = new PopupWindow(popupRoot, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        folderPopupWindow.setFocusable(false);
        folderPopupWindow.setTouchable(true);
        folderPopupWindow.setOutsideTouchable(true);
        folderPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        folderPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        folderPopupWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
        folderPopupWindow.setElevation(8f);
        folderPopupWindow.setOnDismissListener(() -> {
            if (folderPopupWindow != null && !folderPopupWindow.isShowing()) {
                folderPopupWindow = null;
            }
        });
        int gap = dp(4);
        if (anchor != null) {
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            int x = location[0] + (anchor.getWidth() / 2) - (popupWidth / 2);
            int y = location[1] - popupHeight - gap;
            x = clamp(x, 0, Math.max(0, screenW - popupWidth));
            y = Math.max(0, y);
            folderPopupWindow.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
        } else {
            folderPopupWindow.showAtLocation(this, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, getHeight() + gap);
        }
        popupRoot.setAlpha(0f);
        popupRoot.setTranslationY(dp(8));
        popupRoot.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(150)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void removePinnedAt(int index) {
        if (index >= 0 && index < pinnedItems.size()) {
            pinnedItems.remove(index);
            persistPinsAndReload();
        }
    }

    private void persistPinsAndReload() {
        if (configRepository != null) {
            configRepository.savePinnedItems(pinnedItems);
            pinnedItems = configRepository.loadPinnedItems();
        }
        reloadWithInput("", lastTerminalView);
    }

    private void dismissFolderPopup() {
        if (folderPopupWindow != null) {
            final PopupWindow popup = folderPopupWindow;
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
                        if (folderPopupWindow == popup) folderPopupWindow = null;
                    })
                    .start();
            } else {
                popup.dismiss();
                if (folderPopupWindow == popup) folderPopupWindow = null;
            }
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

    private boolean startPinnedDrag(@NonNull View view, int sourceIndex, @NonNull PinnedAppItem item) {
        ClipData clip = ClipData.newPlainText("pinned-app", item.appRef.stableId());
        PinnedDragState dragState = new PinnedDragState(sourceIndex, resolveForSelectionRef(item.appRef));
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clip, shadow, dragState, 0);
        } else {
            view.startDrag(clip, shadow, dragState, 0);
        }
        return true;
    }

    private boolean handlePinnedDrop(@NonNull View targetView, @NonNull DragEvent event, int targetIndex, @Nullable PinnedItem targetItem) {
        Object localState = event.getLocalState();
        if (!(localState instanceof PinnedDragState)) return false;
        PinnedDragState dragState = (PinnedDragState) localState;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                targetView.setAlpha(0.72f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                targetView.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP:
                targetView.setAlpha(1f);
                return applyPinnedDrop(dragState, targetIndex, targetItem);
            case DragEvent.ACTION_DRAG_ENDED:
                targetView.setAlpha(1f);
                return true;
            default:
                return false;
        }
    }

    private boolean applyPinnedDrop(@NonNull PinnedDragState dragState, int targetIndex, @Nullable PinnedItem targetItem) {
        if (dragState.sourceIndex < 0 || dragState.sourceIndex >= pinnedItems.size()) return false;
        if (targetIndex < 0 || targetIndex >= pinnedItems.size()) return false;
        if (dragState.sourceIndex == targetIndex) return false;

        PinnedItem sourceItem = pinnedItems.get(dragState.sourceIndex);
        if (!(sourceItem instanceof PinnedAppItem)) return false;
        AppRef sourceRef = resolveForSelectionRef(((PinnedAppItem) sourceItem).appRef);

        if (targetItem instanceof PinnedFolderItem) {
            PinnedFolderItem folder = (PinnedFolderItem) targetItem;
            boolean alreadyInFolder = false;
            for (AppRef ref : folder.apps) {
                if (ref.stableId().equals(sourceRef.stableId())) {
                    alreadyInFolder = true;
                    break;
                }
            }
            if (!alreadyInFolder) folder.apps.add(sourceRef);
            pinnedItems.remove(dragState.sourceIndex);
            persistPinsAndReload();
            return true;
        }

        if (targetItem instanceof PinnedAppItem) {
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

        return false;
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
        final AppRef appRef;

        PinnedDragState(int sourceIndex, @NonNull AppRef appRef) {
            this.sourceIndex = sourceIndex;
            this.appRef = appRef;
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

    private int computeAzAnchorSlot(char letter, int slots) {
        if (slots <= 1) return 0;
        Set<Character> available = getAvailableAzLetters();
        List<Character> ordered = new ArrayList<>();
        for (char c : AZ_ORDER) {
            if (available.contains(c)) {
                ordered.add(c);
            }
        }
        if (ordered.isEmpty()) return slots / 2;
        char target = Character.toUpperCase(letter);
        int index = ordered.indexOf(target);
        if (index < 0) index = 0;
        if (ordered.size() == 1) return slots / 2;
        float normalized = (float) index / (float) (ordered.size() - 1);
        return clamp(Math.round(normalized * (slots - 1)), 0, slots - 1);
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

    private View createPopupEntryButton(@NonNull LauncherAppEntry entry, int sizePx) {
        if (entry.icon == null || !showIcons) {
            return createEntryButton(entry);
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
        button.setOnClickListener(v -> launchEntry(entry, lastTerminalView));
        button.setContentDescription(entry.label);
        return button;
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
