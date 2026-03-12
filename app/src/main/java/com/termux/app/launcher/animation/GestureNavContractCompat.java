/*
 * Copyright (C) 2020 The Android Open Source Project
 * Copyright (C) 2024 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.termux.app.launcher.animation;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.content.Intent.EXTRA_COMPONENT_NAME;
import static android.content.Intent.EXTRA_USER;

/**
 * Compatibility implementation for launcher gesture-nav contract handoff used by Launcher3 based launchers.
 */
public final class GestureNavContractCompat {

    private static final String LOG_TAG = "GestureNavContract";

    public static final String EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1";
    public static final String EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position";
    public static final String EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control";
    public static final String EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK";

    public final ComponentName componentName;
    public final UserHandle user;
    private final Message callback;

    public GestureNavContractCompat(@NonNull ComponentName componentName, @NonNull UserHandle user, @NonNull Message callback) {
        this.componentName = componentName;
        this.user = user;
        this.callback = callback;
    }

    @Nullable
    public static GestureNavContractCompat fromIntent(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        Bundle extras = intent.getBundleExtra(EXTRA_GESTURE_CONTRACT);
        if (extras == null) {
            return null;
        }
        intent.removeExtra(EXTRA_GESTURE_CONTRACT);

        ComponentName componentName = getParcelable(extras, EXTRA_COMPONENT_NAME, ComponentName.class);
        UserHandle userHandle = getParcelable(extras, EXTRA_USER, UserHandle.class);
        Message callback = getParcelable(extras, EXTRA_REMOTE_CALLBACK, Message.class);
        if (componentName == null || userHandle == null || callback == null || callback.replyTo == null) {
            return null;
        }
        return new GestureNavContractCompat(componentName, userHandle, callback);
    }

    public boolean sendEndPosition(@NonNull RectF position) {
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_ICON_POSITION, position);
        result.putParcelable(EXTRA_ICON_SURFACE, null);

        Message outgoing = Message.obtain();
        outgoing.copyFrom(callback);
        outgoing.setData(result);
        try {
            outgoing.replyTo.send(outgoing);
            return true;
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Failed to send launcher gesture-nav icon position callback: " + e.getMessage());
            return false;
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static <T> T getParcelable(@NonNull Bundle bundle, @NonNull String key, @NonNull Class<T> type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return bundle.getParcelable(key, type);
        }
        Object value = bundle.getParcelable(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }
}
