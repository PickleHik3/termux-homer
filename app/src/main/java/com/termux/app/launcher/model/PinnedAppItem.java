package com.termux.app.launcher.model;

import androidx.annotation.NonNull;

public final class PinnedAppItem implements PinnedItem {
    public final AppRef appRef;

    public PinnedAppItem(@NonNull AppRef appRef) {
        this.appRef = appRef;
    }

    @Override
    public int getType() {
        return TYPE_APP;
    }
}

