package com.termux.app.launcher.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LauncherAppDataProvider {
    private final Context context;
    private final List<LauncherAppEntry> cachedApps = new ArrayList<>();
    private final Map<String, LauncherAppEntry> cachedById = new LinkedHashMap<>();
    private final Map<Character, List<LauncherAppEntry>> letterBuckets = new HashMap<>();

    public LauncherAppDataProvider(@NonNull Context context) {
        this.context = context;
    }

    public synchronized void invalidate() {
        cachedApps.clear();
        cachedById.clear();
        letterBuckets.clear();
    }

    public synchronized List<LauncherAppEntry> getAllApps() {
        if (cachedApps.isEmpty()) {
            loadAppsLocked();
        }
        return new ArrayList<>(cachedApps);
    }

    public synchronized LauncherAppEntry findByRef(@NonNull AppRef ref) {
        if (cachedApps.isEmpty()) {
            loadAppsLocked();
        }
        return cachedById.get(ref.stableId());
    }

    public synchronized List<LauncherAppEntry> getAppsForLetter(char letter) {
        if (cachedApps.isEmpty()) {
            loadAppsLocked();
        }
        char normalized = normalizeLetter(letter);
        List<LauncherAppEntry> bucket = letterBuckets.get(normalized);
        return bucket == null ? new ArrayList<>() : new ArrayList<>(bucket);
    }

    private void loadAppsLocked() {
        PackageManager packageManager = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables = packageManager.queryIntentActivities(main, 0);
        Collections.sort(launchables, new ResolveInfo.DisplayNameComparator(packageManager));

        for (ResolveInfo resolveInfo : launchables) {
            ActivityInfo info = resolveInfo.activityInfo;
            if (info == null || info.packageName == null || info.name == null) continue;
            String label = info.loadLabel(packageManager) != null ? info.loadLabel(packageManager).toString() : info.packageName;
            AppRef ref = new AppRef(info.packageName, info.name);
            LauncherAppEntry entry = new LauncherAppEntry(ref, label, info.loadIcon(packageManager));
            cachedApps.add(entry);
            cachedById.put(ref.stableId(), entry);
            char key = normalizeLetter(label.isEmpty() ? '#' : label.charAt(0));
            List<LauncherAppEntry> bucket = letterBuckets.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                letterBuckets.put(key, bucket);
            }
            bucket.add(entry);
        }
    }

    private static char normalizeLetter(char c) {
        char upper = Character.toUpperCase(c);
        if (upper >= 'A' && upper <= 'Z') {
            return upper;
        }
        return '#';
    }

    public static char normalizeLetter(@NonNull String label) {
        if (label.isEmpty()) return '#';
        return normalizeLetter(label.toUpperCase(Locale.US).charAt(0));
    }
}

