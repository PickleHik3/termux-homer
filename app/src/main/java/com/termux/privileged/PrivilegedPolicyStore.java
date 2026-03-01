package com.termux.privileged;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Centralized storage for privileged backend and endpoint policy toggles.
 */
public final class PrivilegedPolicyStore {

    private static final String PREFS_NAME = "termux_privileged_policy";

    public static final String KEY_MASTER_ENABLED = "priv_master_enabled";
    public static final String KEY_PREFER_SHIZUKU = "priv_prefer_shizuku";
    public static final String KEY_ALLOW_SHELL_FALLBACK = "priv_allow_shell_fallback";

    public static final String KEY_ENDPOINT_REQUEST_PERMISSION = "priv_endpoint_request_permission";
    public static final String KEY_ENDPOINT_EXEC = "priv_endpoint_exec";
    public static final String KEY_ENDPOINT_BRIGHTNESS = "priv_endpoint_brightness";
    public static final String KEY_ENDPOINT_VOLUME = "priv_endpoint_volume";
    public static final String KEY_ENDPOINT_LOCK_SCREEN = "priv_endpoint_lock_screen";

    private PrivilegedPolicyStore() {
    }

    public enum Endpoint {
        REQUEST_PERMISSION,
        EXEC,
        BRIGHTNESS,
        VOLUME,
        LOCK_SCREEN
    }

    public static boolean isMasterEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_MASTER_ENABLED, true);
    }

    public static void setMasterEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply();
    }

    public static boolean isPreferShizuku(Context context) {
        return getPrefs(context).getBoolean(KEY_PREFER_SHIZUKU, true);
    }

    public static void setPreferShizuku(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_PREFER_SHIZUKU, enabled).apply();
    }

    public static boolean isShellFallbackEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ALLOW_SHELL_FALLBACK, true);
    }

    public static void setShellFallbackEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ALLOW_SHELL_FALLBACK, enabled).apply();
    }

    public static boolean isEndpointEnabled(Context context, @NonNull Endpoint endpoint) {
        SharedPreferences prefs = getPrefs(context);
        switch (endpoint) {
            case REQUEST_PERMISSION:
                return prefs.getBoolean(KEY_ENDPOINT_REQUEST_PERMISSION, true);
            case EXEC:
                return prefs.getBoolean(KEY_ENDPOINT_EXEC, true);
            case BRIGHTNESS:
                return prefs.getBoolean(KEY_ENDPOINT_BRIGHTNESS, true);
            case VOLUME:
                return prefs.getBoolean(KEY_ENDPOINT_VOLUME, true);
            case LOCK_SCREEN:
                return prefs.getBoolean(KEY_ENDPOINT_LOCK_SCREEN, true);
            default:
                return true;
        }
    }

    public static void setEndpointEnabled(Context context, @NonNull Endpoint endpoint, boolean enabled) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        switch (endpoint) {
            case REQUEST_PERMISSION:
                editor.putBoolean(KEY_ENDPOINT_REQUEST_PERMISSION, enabled);
                break;
            case EXEC:
                editor.putBoolean(KEY_ENDPOINT_EXEC, enabled);
                break;
            case BRIGHTNESS:
                editor.putBoolean(KEY_ENDPOINT_BRIGHTNESS, enabled);
                break;
            case VOLUME:
                editor.putBoolean(KEY_ENDPOINT_VOLUME, enabled);
                break;
            case LOCK_SCREEN:
                editor.putBoolean(KEY_ENDPOINT_LOCK_SCREEN, enabled);
                break;
        }
        editor.apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) {
            throw new IllegalStateException("Context is required for privileged policy access");
        }
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
