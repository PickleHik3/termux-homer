package com.termux.app.launcher.model;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class LauncherAppEntry {
    public final AppRef appRef;
    public final String label;
    @Nullable public final Drawable icon;

    public LauncherAppEntry(@NonNull AppRef appRef, @NonNull String label, @Nullable Drawable icon) {
        this.appRef = appRef;
        this.label = label;
        this.icon = icon;
    }
}

