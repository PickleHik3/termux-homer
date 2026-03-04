package com.termux.app.launcher.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class PinnedFolderItem implements PinnedItem {
    public static final int MAX_GRID = 6;
    public static final int DEFAULT_ROWS = 3;
    public static final int DEFAULT_COLS = 3;

    public final String id;
    public String title;
    public int rows;
    public int cols;
    public boolean tintOverrideEnabled;
    public int tintColor;
    public final List<AppRef> apps;

    public PinnedFolderItem(@NonNull String id, @NonNull String title) {
        this.id = id;
        this.title = title;
        this.rows = DEFAULT_ROWS;
        this.cols = DEFAULT_COLS;
        this.tintOverrideEnabled = false;
        this.tintColor = 0xFF202020;
        this.apps = new ArrayList<>();
    }

    @Override
    public int getType() {
        return TYPE_FOLDER;
    }
}

