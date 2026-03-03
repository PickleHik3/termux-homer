package com.termux.app;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.VelocityTracker;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SuggestionBarView extends GridLayout {

    private static final int TEXT_COLOR = 0xFFC0B18B;

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
    private int pinnedPageIndex = 0;
    private int pinnedItemsPerPage = 1;
    private float swipeDownX = 0f;
    private float swipeDownY = 0f;
    private VelocityTracker swipeVelocityTracker;
    private boolean pageSwitchAnimating = false;

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
        injectedSuggestionButtons = null;
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
        activeAzLetter = Character.toUpperCase(letter);
        activeAzSelection = Math.max(0, selectionIndex);
        List<LauncherAppEntry> candidates = appDataProvider.getAppsForLetter(activeAzLetter);
        if (candidates.isEmpty()) {
            if (commit) {
                activeAzLetter = null;
                reloadWithInput(lastInput, lastTerminalView);
            }
            return;
        }

        if (commit) {
            int index = Math.min(activeAzSelection, candidates.size() - 1);
            launchEntry(candidates.get(index), lastTerminalView);
            activeAzLetter = null;
            reloadWithInput("", lastTerminalView);
            return;
        }

        renderButtons(candidates, true);
    }

    public void clearAzPreview() {
        activeAzLetter = null;
        activeAzSelection = 0;
        reloadWithInput(lastInput, lastTerminalView);
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
                activeAzLetter == null && TextUtils.isEmpty(lastInput.trim()) && pinnedItemsPerPage > 0) {
                int pageDelta = dx < 0 ? 1 : -1;
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
        List<PinnedItem> pinnedForSlots = new ArrayList<>();
        int pinnedPageOffset = 0;

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
        for (int col = 0; col < entries.size() && col < buttonCount; col++) {
            LauncherAppEntry entry = entries.get(col);
            View view = createEntryButton(entry);
            LayoutParams param = createSlotParams(col);
            view.setLayoutParams(param);

            if (!azPreview && col < pinnedForSlots.size()) {
                final int pinnedIndex = pinnedPageOffset + col;
                final PinnedItem pinnedItem = pinnedForSlots.get(col);
                if (pinnedItem instanceof PinnedFolderItem) {
                    view = createFolderPreviewButton((PinnedFolderItem) pinnedItem);
                    view.setLayoutParams(param);
                    view.setOnClickListener(v -> showFolderPopup((PinnedFolderItem) pinnedItem, v));
                    view.setOnLongClickListener(v -> {
                        showFolderContentsEditor(pinnedIndex, (PinnedFolderItem) pinnedItem);
                        return true;
                    });
                } else {
                    view.setOnLongClickListener(v -> {
                        showUnifiedPinEditor(pinnedIndex, pinnedItem);
                        return true;
                    });
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
        }

        int missing = buttonCount - addedCount;
        for (int i = 0; i < missing; i++) {
            ImageButton filler = new ImageButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
            filler.setVisibility(INVISIBLE);
            filler.setLayoutParams(createSlotParams(addedCount + i));
            if (!azPreview) {
                final int slotIndex = addedCount + i;
                filler.setOnLongClickListener(v -> {
                    showUnifiedPinEditor(slotIndex, null);
                    return true;
                });
            }
            addView(filler);
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
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.setClassName(entry.appRef.packageName, entry.appRef.activityName);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(launchIntent);
        } catch (Exception ignored) {
            Intent fallback = getContext().getPackageManager().getLaunchIntentForPackage(entry.appRef.packageName);
            if (fallback != null) getContext().startActivity(fallback);
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
        selectedTitle.setText("Pinned Order (drag to reorder)");
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
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
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
            }
        });
        touchHelper.attachToRecyclerView(orderedRecycler);

        TextView allAppsTitle = new TextView(getContext());
        allAppsTitle.setText("Apps and Folders");
        allAppsTitle.setTextColor(TEXT_COLOR);
        allAppsTitle.setPadding(0, 16, 0, 8);

        ListView listView = new ListView(getContext());
        listViewHolder[0] = listView;
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_multiple_choice, labels);
        listView.setAdapter(adapter);
        listView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        for (int i = 0; i < options.size(); i++) {
            listView.setItemChecked(i, selectedIds.contains(options.get(i).id));
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            PinOption option = options.get(position);
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
            syncListChecks(listView, options, selectedIds);
        });

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        Button cancel = new Button(getContext());
        cancel.setText("Cancel");
        styleGhostButton(cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());

        Button save = new Button(getContext());
        save.setText("Save Pins");
        styleGhostButton(save);

        ImageButton folderAction = new ImageButton(getContext());
        folderAction.setImageResource(R.drawable.ic_create_new_folder_24);
        folderAction.setContentDescription("Create folder at this slot");
        styleIconButton(folderAction, dp(4));
        LinearLayout.LayoutParams folderActionParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        folderActionParams.setMargins(dp(8), 0, 0, 0);

        final boolean[] folderMode = new boolean[] {false};
        final Runnable refreshFolderModeUi = () -> {
            save.setText(folderMode[0] ? "Create Folder" : "Save Pins");
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

        buttons.addView(cancel);
        buttons.addView(save);
        buttons.addView(folderAction, folderActionParams);

        orderedRecycler.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        root.addView(selectedTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(orderedRecycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
        root.addView(allAppsTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        final List<String> labels = new ArrayList<>();
        for (LauncherAppEntry app : source) labels.add(app.label);

        ListView listView = new ListView(getContext());
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_multiple_choice, labels));
        listView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        for (int i = 0; i < source.size(); i++) {
            listView.setItemChecked(i, selectedIds.contains(source.get(i).appRef.stableId()));
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String stable = source.get(position).appRef.stableId();
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
        settings.setImageResource(android.R.drawable.ic_menu_manage);
        settings.setContentDescription("Folder settings");
        styleIconButton(settings, dp(4));
        settings.setOnClickListener(v -> {
            dialog.dismiss();
            showFolderSettings(folder);
        });
        topActions.addView(settings, new LinearLayout.LayoutParams(dp(28), dp(28)));

        ImageButton delete = new ImageButton(getContext());
        delete.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        delete.setContentDescription("Delete folder");
        styleIconButton(delete, dp(4));
        delete.setOnClickListener(v -> {
            removePinnedAt(folderIndex);
            dialog.dismiss();
        });
        topActions.addView(delete, new LinearLayout.LayoutParams(dp(28), dp(28)));

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
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        root.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.show();
    }

    private void createOrReplaceFolderAtSlot(int slotIndex, @NonNull List<AppRef> selectedOrdered) {
        PinnedFolderItem folder = new PinnedFolderItem(UUID.randomUUID().toString(), "Folder");
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
        int shellSize = iconSizePx() + dp(2);
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

        int miniSize = Math.max(dp(10), Math.round(shellSize * 0.48f));
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
            params.setMargins(dp(-1), dp(-1), dp(-1), dp(-1));
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
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(12));

        EditText rowsInput = new EditText(getContext());
        rowsInput.setHint("Rows (1-6)");
        rowsInput.setText(Integer.toString(folder.rows));

        EditText colsInput = new EditText(getContext());
        colsInput.setHint("Columns (1-6)");
        colsInput.setText(Integer.toString(folder.cols));

        EditText colorInput = new EditText(getContext());
        colorInput.setHint("Tint override (hex, optional)");
        colorInput.setText(folder.tintOverrideEnabled ? String.format(Locale.US, "#%08X", folder.tintColor) : "");

        TextView title = new TextView(getContext());
        title.setText("Folder settings");
        title.setTextColor(TEXT_COLOR);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(14f);
        title.setPadding(0, 0, 0, dp(8));

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
            folder.rows = clamp(parseInt(rowsInput.getText(), folder.rows), 1, PinnedFolderItem.MAX_GRID);
            folder.cols = clamp(parseInt(colsInput.getText(), folder.cols), 1, PinnedFolderItem.MAX_GRID);
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

        buttons.addView(cancel);
        buttons.addView(save);

        layout.addView(title);
        layout.addView(rowsInput);
        layout.addView(colsInput);
        layout.addView(colorInput);
        layout.addView(buttons);

        dialog.setContentView(layout);
        dialog.show();
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
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
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
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            btn.setLayoutParams(params);
            grid.addView(btn);
        }

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(8), dp(8), dp(8), dp(8));

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

        TextView gear = new TextView(getContext());
        gear.setText("\u2699");
        gear.setTextColor(TEXT_COLOR);
        gear.setTextSize(16f);
        gear.setGravity(Gravity.CENTER);
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
        FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(contentW, contentH);

        if (blurEnabled) {
            RealtimeBlurView blurView = new RealtimeBlurView(getContext(), null);
            blurView.setBlurRadius(blurRadiusDp * getResources().getDisplayMetrics().density);
            blurView.setOverlayColor(0x00000000);
            popupRoot.addView(blurView, fillParams);
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
            row.setPadding(12, 12, 12, 12);

            TextView label = new TextView(parent.getContext());
            label.setTextColor(TEXT_COLOR);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView handle = new TextView(parent.getContext());
            handle.setText("\u2630");
            handle.setTextColor(TEXT_COLOR);
            handle.setTextSize(16f);
            handle.setGravity(Gravity.CENTER);
            handle.setLayoutParams(new LinearLayout.LayoutParams(dpStatic(parent, 28), dpStatic(parent, 28)));

            TextView delete = new TextView(parent.getContext());
            delete.setText("\u2715");
            delete.setTextColor(TEXT_COLOR);
            delete.setTextSize(16f);
            delete.setGravity(Gravity.CENTER);
            delete.setLayoutParams(new LinearLayout.LayoutParams(dpStatic(parent, 28), dpStatic(parent, 28)));

            row.addView(label);
            row.addView(handle);
            row.addView(delete);
            return new RowHolder(row, label, delete);
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            holder.label.setText(resolveLabel(source, ordered.get(position)));
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
                return "[Folder] " + (TextUtils.isEmpty(title) ? "Folder" : title);
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
            final TextView delete;

            RowHolder(@NonNull View itemView, TextView label, TextView delete) {
                super(itemView);
                this.label = label;
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
        for (PinnedItem item : currentPinned) {
            if (!(item instanceof PinnedFolderItem)) continue;
            PinnedFolderItem folder = (PinnedFolderItem) item;
            String id = "folder:" + folder.id;
            String label = "[Folder] " + (TextUtils.isEmpty(folder.title) ? "Folder" : folder.title);
            out.add(new PinOption(id, label, clonePinnedItem(folder)));
        }
        return out;
    }

    private static void syncListChecks(@NonNull ListView listView, @NonNull List<PinOption> options, @NonNull Set<String> selectedIds) {
        for (int i = 0; i < options.size(); i++) {
            listView.setItemChecked(i, selectedIds.contains(options.get(i).id));
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
