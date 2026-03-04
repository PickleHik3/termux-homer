package com.termux.app.launcher.model;

import androidx.annotation.NonNull;

public final class AppRef {
    public final String packageName;
    public final String activityName;

    public AppRef(@NonNull String packageName, @NonNull String activityName) {
        this.packageName = packageName;
        this.activityName = activityName;
    }

    @NonNull
    public String stableId() {
        return packageName + "/" + activityName;
    }
}

