package com.termux.app.launcher.model;

public interface PinnedItem {
    int TYPE_APP = 0;
    int TYPE_FOLDER = 1;

    int getType();
}

