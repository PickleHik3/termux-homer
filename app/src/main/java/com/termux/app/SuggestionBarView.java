package com.termux.app;

import android.annotation.SuppressLint;
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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
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
        List<LauncherAppEntry> ordered = new ArrayList<>(allApps);
        if (ordered.isEmpty()) return ordered;

        if (configRepository != null) {
            if (pinnedItems == null || pinnedItems.isEmpty()) {
                pinnedItems = configRepository.loadPinnedItems();
            }
            List<LauncherAppEntry> pinnedEntries = entriesForPinnedItems(pinnedItems);
            if (!pinnedEntries.isEmpty()) {
                moveEntriesToFront(ordered, pinnedEntries);
                return ordered;
            }
        }

        // legacy fallback behavior
        List<LauncherAppEntry> defaults = new ArrayList<>();
        List<String> names = new ArrayList<>(defaultButtonStrings);
        Collections.reverse(names);
        for (String item : names) {
            LauncherAppEntry match = findStartsWith(ordered, item);
            if (match != null) defaults.add(match);
        }
        moveEntriesToFront(ordered, defaults);
        return ordered;
    }

    private void renderButtons(@NonNull List<LauncherAppEntry> entries, boolean azPreview) {
        removeAllViews();
        int buttonCount = Math.max(1, maxButtonCount);

        List<PinnedItem> pinnedForSlots = trimmedPinnedItems();

        int addedCount = 0;
        for (int col = 0; col < entries.size() && col < buttonCount; col++) {
            LauncherAppEntry entry = entries.get(col);
            View view = createEntryButton(entry);
            LayoutParams param = createSlotParams(col);
            view.setLayoutParams(param);

            if (!azPreview && col < pinnedForSlots.size()) {
                final int pinnedIndex = col;
                final PinnedItem pinnedItem = pinnedForSlots.get(col);
                if (pinnedItem instanceof PinnedFolderItem) {
                    view.setOnClickListener(v -> showFolderPopup((PinnedFolderItem) pinnedItem, v));
                }
                view.setOnLongClickListener(v -> {
                    onPinnedItemLongPress(pinnedIndex, pinnedItem);
                    return true;
                });
            } else if (!azPreview) {
                view.setOnLongClickListener(v -> {
                    showPinnedAppsEditor();
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
                filler.setOnLongClickListener(v -> {
                    showPinnedAppsEditor();
                    return true;
                });
            }
            addView(filler);
        }
    }

    private View createEntryButton(@NonNull LauncherAppEntry entry) {
        if (entry.icon != null && showIcons) {
            ImageButton imageButton = new ImageButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
            imageButton.setImageDrawable(entry.icon);
            int size = Math.max(20, Math.round(24f * iconScale * getResources().getDisplayMetrics().density));
            imageButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            imageButton.setAdjustViewBounds(true);
            imageButton.setPadding(0, 0, 0, 0);
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
            return imageButton;
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

    private List<PinnedItem> trimmedPinnedItems() {
        List<PinnedItem> out = new ArrayList<>();
        if (pinnedItems == null) return out;
        for (PinnedItem item : pinnedItems) {
            if (item == null) continue;
            out.add(item);
            if (out.size() >= maxButtonCount) break;
        }
        return out;
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

    private static void moveEntriesToFront(List<LauncherAppEntry> base, List<LauncherAppEntry> prioritized) {
        for (int i = prioritized.size() - 1; i >= 0; i--) {
            LauncherAppEntry item = prioritized.get(i);
            int index = indexOfEntry(base, item);
            if (index >= 0) {
                base.remove(index);
            }
            base.add(0, item);
        }
    }

    private static int indexOfEntry(List<LauncherAppEntry> list, LauncherAppEntry target) {
        for (int i = 0; i < list.size(); i++) {
            LauncherAppEntry e = list.get(i);
            if (e.appRef.stableId().equals(target.appRef.stableId())) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static LauncherAppEntry findStartsWith(List<LauncherAppEntry> entries, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (q.isEmpty()) return null;
        for (LauncherAppEntry entry : entries) {
            if (entry.label != null && entry.label.toLowerCase(Locale.US).startsWith(q)) {
                return entry;
            }
        }
        return null;
    }

    private void onPinnedItemLongPress(int pinnedIndex, PinnedItem pinnedItem) {
        if (pinnedItem instanceof PinnedFolderItem) {
            showFolderItemOptions(pinnedIndex, (PinnedFolderItem) pinnedItem);
            return;
        }
        if (pinnedItem instanceof PinnedAppItem) {
            showPinnedAppOptions(pinnedIndex, (PinnedAppItem) pinnedItem);
        }
    }

    private void showPinnedAppsEditor() {
        if (configRepository == null) return;
        if (allApps == null || allApps.isEmpty()) reloadAllApps();

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 24, 32, 24);

        final List<LauncherAppEntry> source = new ArrayList<>(allApps);
        final List<String> labels = new ArrayList<>();
        for (LauncherAppEntry app : source) labels.add(app.label);

        Set<String> selectedIds = new LinkedHashSet<>();
        List<AppRef> orderedSelected = new ArrayList<>();

        for (PinnedItem item : pinnedItems) {
            if (item instanceof PinnedAppItem) {
                AppRef ref = ((PinnedAppItem) item).appRef;
                String resolved = resolveForSelectionId(ref);
                if (resolved != null && selectedIds.add(resolved)) {
                    orderedSelected.add(resolveForSelectionRef(ref));
                }
            }
        }

        TextView selectedTitle = new TextView(getContext());
        selectedTitle.setText("Pinned Order (drag to reorder)");
        selectedTitle.setTextColor(TEXT_COLOR);
        selectedTitle.setPadding(0, 0, 0, 8);

        final OrderedPinnedAdapter orderedAdapter = new OrderedPinnedAdapter(source, orderedSelected);
        RecyclerView orderedRecycler = new RecyclerView(getContext());
        orderedRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        orderedRecycler.setAdapter(orderedAdapter);
        orderedRecycler.setOverScrollMode(OVER_SCROLL_NEVER);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
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
        allAppsTitle.setText("All Launchable Apps");
        allAppsTitle.setTextColor(TEXT_COLOR);
        allAppsTitle.setPadding(0, 16, 0, 8);

        ListView listView = new ListView(getContext());
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_multiple_choice, labels);
        listView.setAdapter(adapter);

        for (int i = 0; i < source.size(); i++) {
            if (selectedIds.contains(source.get(i).appRef.stableId())) {
                listView.setItemChecked(i, true);
            }
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            LauncherAppEntry app = source.get(position);
            String stable = app.appRef.stableId();
            if (listView.isItemChecked(position)) {
                if (selectedIds.add(stable)) {
                    orderedSelected.add(app.appRef);
                    orderedAdapter.notifyDataSetChanged();
                }
            } else {
                selectedIds.remove(stable);
                removeRef(orderedSelected, stable);
                orderedAdapter.notifyDataSetChanged();
            }
        });

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        Button cancel = new Button(getContext());
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> dialog.dismiss());

        Button save = new Button(getContext());
        save.setText("Save Pins");
        save.setOnClickListener(v -> {
            applyPinnedSelection(orderedSelected);
            dialog.dismiss();
            reloadWithInput("", lastTerminalView);
        });

        buttons.addView(cancel);
        buttons.addView(save);

        root.addView(selectedTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(orderedRecycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 320));
        root.addView(allAppsTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 700));
        root.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.show();
    }

    private void applyPinnedSelection(@NonNull List<AppRef> selectedOrdered) {
        List<PinnedItem> current = new ArrayList<>(pinnedItems);
        Set<String> selectedIds = new HashSet<>();
        for (AppRef ref : selectedOrdered) selectedIds.add(resolveForSelectionId(ref));

        List<PinnedItem> rebuilt = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();

        for (PinnedItem item : current) {
            if (item instanceof PinnedFolderItem) {
                rebuilt.add(item);
                continue;
            }
            if (item instanceof PinnedAppItem) {
                String id = resolveForSelectionId(((PinnedAppItem) item).appRef);
                if (id != null && selectedIds.contains(id)) {
                    AppRef replacement = findRefById(selectedOrdered, id);
                    if (replacement != null) {
                        rebuilt.add(new PinnedAppItem(replacement));
                        usedIds.add(id);
                    }
                }
            }
        }

        for (AppRef ref : selectedOrdered) {
            String id = resolveForSelectionId(ref);
            if (id != null && !usedIds.contains(id)) {
                rebuilt.add(new PinnedAppItem(ref));
                usedIds.add(id);
            }
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
        layout.setPadding(32, 8, 32, 8);

        EditText rowsInput = new EditText(getContext());
        rowsInput.setHint("Rows (1-6)");
        rowsInput.setText(Integer.toString(folder.rows));

        EditText colsInput = new EditText(getContext());
        colsInput.setHint("Columns (1-6)");
        colsInput.setText(Integer.toString(folder.cols));

        EditText colorInput = new EditText(getContext());
        colorInput.setHint("Tint override (hex, optional)");
        colorInput.setText(folder.tintOverrideEnabled ? String.format(Locale.US, "#%08X", folder.tintColor) : "");

        layout.addView(rowsInput);
        layout.addView(colsInput);
        layout.addView(colorInput);

        new AlertDialog.Builder(getContext())
            .setTitle("Folder settings")
            .setView(layout)
            .setPositiveButton("Save", (dialog, which) -> {
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
                persistPinsAndReload();
            })
            .setNegativeButton("Cancel", null)
            .show();
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

        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(clamp(folder.cols, 1, PinnedFolderItem.MAX_GRID));
        grid.setRowCount(clamp(folder.rows, 1, PinnedFolderItem.MAX_GRID));
        grid.setPadding(16, 16, 16, 16);

        int cellCount = clamp(folder.rows, 1, PinnedFolderItem.MAX_GRID) * clamp(folder.cols, 1, PinnedFolderItem.MAX_GRID);
        for (int i = 0; i < folderEntries.size() && i < cellCount; i++) {
            LauncherAppEntry entry = folderEntries.get(i);
            View btn = createEntryButton(entry);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(i % folder.cols, 1f);
            params.rowSpec = GridLayout.spec(i / folder.cols, 1f);
            btn.setLayoutParams(params);
            grid.addView(btn);
        }

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(8, 8, 8, 8);

        Button gear = new Button(getContext());
        gear.setText("⚙");
        gear.setOnClickListener(v -> {
            dismissFolderPopup();
            showFolderSettings(folder);
        });

        shell.addView(gear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(shell);
        FrameLayout popupRoot = new FrameLayout(getContext());

        if (blurEnabled) {
            RealtimeBlurView blurView = new RealtimeBlurView(getContext());
            blurView.setBlurRadius(blurRadiusDp * getResources().getDisplayMetrics().density);
            blurView.setOverlayColor(0x00000000);
            popupRoot.addView(blurView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        int alpha = clamp(appBarOpacity, 0, 100);
        int overlayBase = folder.tintOverrideEnabled ? (folder.tintColor & 0x00FFFFFF) : (inheritedTintColor & 0x00FFFFFF);
        int overlayColor = (((int) (255f * (alpha / 100f))) << 24) | overlayBase;
        View overlay = new View(getContext());
        overlay.setBackgroundColor(overlayColor);
        popupRoot.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        popupRoot.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        folderPopupWindow = new PopupWindow(popupRoot, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        folderPopupWindow.setOutsideTouchable(true);
        folderPopupWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
        folderPopupWindow.setElevation(8f);

        View parent = this;
        if (anchor != null) {
            folderPopupWindow.showAsDropDown(anchor, 0, -anchor.getHeight() * 2);
        } else {
            folderPopupWindow.showAtLocation(parent, Gravity.BOTTOM, 0, getHeight() + 24);
        }
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
            folderPopupWindow.dismiss();
            folderPopupWindow = null;
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
        private final List<LauncherAppEntry> source;
        private final List<AppRef> ordered;

        OrderedPinnedAdapter(List<LauncherAppEntry> source, List<AppRef> ordered) {
            this.source = source;
            this.ordered = ordered;
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
            handle.setText("\u2261");
            handle.setTextColor(TEXT_COLOR);
            handle.setTextSize(18f);
            handle.setPadding(12, 0, 12, 0);

            row.addView(label);
            row.addView(handle);
            return new RowHolder(row, label);
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            AppRef ref = ordered.get(position);
            holder.label.setText(resolveLabel(source, ref));
        }

        @Override
        public int getItemCount() {
            return ordered.size();
        }

        private static String resolveLabel(List<LauncherAppEntry> source, AppRef ref) {
            String stable = ref.stableId();
            for (LauncherAppEntry entry : source) {
                if (stable.equals(entry.appRef.stableId()) || entry.appRef.packageName.equals(ref.packageName)) {
                    return entry.label;
                }
            }
            return ref.packageName;
        }

        static final class RowHolder extends RecyclerView.ViewHolder {
            final TextView label;

            RowHolder(@NonNull View itemView, TextView label) {
                super(itemView);
                this.label = label;
            }
        }
    }

    private static void removeRef(List<AppRef> refs, String stableId) {
        for (int i = refs.size() - 1; i >= 0; i--) {
            if (stableId.equals(refs.get(i).stableId())) {
                refs.remove(i);
            }
        }
    }

    @Nullable
    private static AppRef findRefById(List<AppRef> refs, String stableId) {
        for (AppRef ref : refs) {
            if (stableId.equals(ref.stableId())) return ref;
        }
        return null;
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
