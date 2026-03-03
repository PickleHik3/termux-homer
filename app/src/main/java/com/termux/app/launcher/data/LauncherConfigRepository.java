package com.termux.app.launcher.data;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LauncherConfigRepository {
    public static final int SCHEMA_VERSION = 1;

    private final TermuxAppSharedPreferences preferences;

    public LauncherConfigRepository(@NonNull TermuxAppSharedPreferences preferences) {
        this.preferences = preferences;
    }

    public List<PinnedItem> loadPinnedItems() {
        String raw = preferences.getAppLauncherPinnedItemsV2();
        if (raw == null || raw.trim().isEmpty()) {
            return migrateFromLegacyIfNeeded();
        }

        try {
            JSONObject root = new JSONObject(raw);
            JSONArray items = root.optJSONArray("items");
            if (items == null) return migrateFromLegacyIfNeeded();
            List<PinnedItem> out = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String type = item.optString("type", "");
                if ("app".equals(type)) {
                    AppRef ref = new AppRef(item.optString("packageName", ""), item.optString("activityName", ""));
                    if (!ref.packageName.isEmpty()) {
                        out.add(new PinnedAppItem(ref));
                    }
                } else if ("folder".equals(type)) {
                    String id = item.optString("id", UUID.randomUUID().toString());
                    String title = item.optString("title", "Folder");
                    PinnedFolderItem folder = new PinnedFolderItem(id, title);
                    folder.rows = clamp(item.optInt("rows", PinnedFolderItem.DEFAULT_ROWS), 1, PinnedFolderItem.MAX_GRID);
                    folder.cols = clamp(item.optInt("cols", PinnedFolderItem.DEFAULT_COLS), 1, PinnedFolderItem.MAX_GRID);
                    folder.tintOverrideEnabled = item.optBoolean("tintOverrideEnabled", false);
                    folder.tintColor = item.optInt("tintColor", 0xFF202020);
                    JSONArray apps = item.optJSONArray("apps");
                    if (apps != null) {
                        for (int j = 0; j < apps.length(); j++) {
                            JSONObject app = apps.optJSONObject(j);
                            if (app == null) continue;
                            String packageName = app.optString("packageName", "");
                            String activityName = app.optString("activityName", "");
                            if (!packageName.isEmpty() && !activityName.isEmpty()) {
                                folder.apps.add(new AppRef(packageName, activityName));
                            }
                        }
                    }
                    out.add(folder);
                }
            }
            if (out.isEmpty()) {
                return migrateFromLegacyIfNeeded();
            }
            return out;
        } catch (JSONException ignored) {
            return migrateFromLegacyIfNeeded();
        }
    }

    public void savePinnedItems(@NonNull List<PinnedItem> pinnedItems) {
        JSONObject root = new JSONObject();
        JSONArray items = new JSONArray();
        for (PinnedItem pinnedItem : pinnedItems) {
            if (pinnedItem instanceof PinnedAppItem) {
                PinnedAppItem appItem = (PinnedAppItem) pinnedItem;
                JSONObject item = new JSONObject();
                try {
                    item.put("type", "app");
                    item.put("packageName", appItem.appRef.packageName);
                    item.put("activityName", appItem.appRef.activityName);
                    items.put(item);
                } catch (JSONException ignored) {
                }
            } else if (pinnedItem instanceof PinnedFolderItem) {
                PinnedFolderItem folderItem = (PinnedFolderItem) pinnedItem;
                JSONObject item = new JSONObject();
                JSONArray apps = new JSONArray();
                for (AppRef ref : folderItem.apps) {
                    JSONObject app = new JSONObject();
                    try {
                        app.put("packageName", ref.packageName);
                        app.put("activityName", ref.activityName);
                        apps.put(app);
                    } catch (JSONException ignored) {
                    }
                }
                try {
                    item.put("type", "folder");
                    item.put("id", folderItem.id);
                    item.put("title", folderItem.title);
                    item.put("rows", clamp(folderItem.rows, 1, PinnedFolderItem.MAX_GRID));
                    item.put("cols", clamp(folderItem.cols, 1, PinnedFolderItem.MAX_GRID));
                    item.put("tintOverrideEnabled", folderItem.tintOverrideEnabled);
                    item.put("tintColor", folderItem.tintColor);
                    item.put("apps", apps);
                    items.put(item);
                } catch (JSONException ignored) {
                }
            }
        }

        try {
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("items", items);
            preferences.setAppLauncherPinnedItemsV2(root.toString());
            preferences.setAppLauncherPinnedItemsSchemaVersion(SCHEMA_VERSION);
        } catch (JSONException ignored) {
        }
    }

    public List<PinnedItem> migrateFromLegacyIfNeeded() {
        List<PinnedItem> out = new ArrayList<>();
        String legacy = preferences.getAppLauncherDefaultButtons();
        if (legacy != null && !legacy.trim().isEmpty()) {
            String[] parts = legacy.split(",");
            for (String part : parts) {
                String value = part.trim();
                if (value.isEmpty()) continue;
                // ActivityName is unknown in legacy mode, use package only and resolve at runtime.
                out.add(new PinnedAppItem(new AppRef(value, "")));
            }
        }
        savePinnedItems(out);
        return out;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
