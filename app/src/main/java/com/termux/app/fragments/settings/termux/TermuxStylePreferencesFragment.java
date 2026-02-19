package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.ColorInt;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.style.TermuxBackgroundManager;
import com.termux.shared.data.DataUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Keep
public class TermuxStylePreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_style_preferences, rootKey);
        configureBackgroundPreferences(context);
    }

    /**
     * Configure background preferences and make appropriate changes in the state of components.
     *
     * @param context The context for operations.
     */
    private void configureBackgroundPreferences(@NonNull Context context) {
        SwitchPreferenceCompat backgroundImagePreference = findPreference("background_image_enabled");
        if (backgroundImagePreference != null) {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
            if (preferences == null)
                return;
            // If background image preference is disabled and background images are
            // missing, then don't allow user to enable it from setting.
            if (!preferences.isBackgroundImageEnabled() && !TermuxBackgroundManager.isImageFilesExist(context, false)) {
                backgroundImagePreference.setEnabled(false);
            }
        }
    }
}

class TermuxStylePreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;

    private final TermuxAppSharedPreferences mPreferences;

    private static TermuxStylePreferencesDataStore mInstance;
    private static final String LOG_TAG = "TermuxStylePreferences";

    private TermuxStylePreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TermuxStylePreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxStylePreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch(key) {
            case "background_image_enabled":
                mPreferences.setBackgroundImageEnabled(value);
                break;
            case "use_system_wallpaper":
                mPreferences.setUseSystemWallpaperEnabled(value);
                break;
            case "extrakeys_blur_enabled":
                mPreferences.setExtraKeysBlurEnabled(value);
                break;
            case "sessions_blur_enabled":
                mPreferences.setSessionsBlurEnabled(value);
                break;
            case "monet_background_enabled":
                setMonetBackgroundAndOverlayEnabled(value);
                break;
            case "monet_overlay_enabled":
                // Legacy compatibility: keep both toggles in sync even if old UI writes this key.
                setMonetBackgroundAndOverlayEnabled(value);
                break;
            case "app_launcher_show_icons":
                mPreferences.setAppLauncherShowIconsEnabled(value);
                break;
            case "app_launcher_bw_icons":
                mPreferences.setAppLauncherBwIconsEnabled(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null)
            return false;
        switch(key) {
            case "background_image_enabled":
                return mPreferences.isBackgroundImageEnabled();
            case "use_system_wallpaper":
                return mPreferences.isUseSystemWallpaperEnabled();
            case "extrakeys_blur_enabled":
                return mPreferences.isExtraKeysBlurEnabled();
            case "sessions_blur_enabled":
                return mPreferences.isSessionsBlurEnabled();
            case "monet_background_enabled":
                return mPreferences.isMonetBackgroundEnabled();
            case "monet_overlay_enabled":
                return mPreferences.isMonetBackgroundEnabled();
            case "app_launcher_show_icons":
                return mPreferences.isAppLauncherShowIconsEnabled();
            case "app_launcher_bw_icons":
                return mPreferences.isAppLauncherBwIconsEnabled();
            default:
                return false;
        }
    }

    @Override
    public void putInt(String key, int value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch (key) {
            case "terminal_background_opacity":
                mPreferences.setTerminalBackgroundOpacity(value);
                syncBackgroundOverlayColor(value, null);
                TermuxActivity.updateTermuxActivityStyling(mContext, true);
                break;
            case "terminal_blur_radius":
                mPreferences.setTerminalBlurRadius(value);
                break;
            case "sessions_blur_radius":
                mPreferences.setSessionsBlurRadius(value);
                break;
            case "extrakeys_blur_radius":
                mPreferences.setExtraKeysBlurRadius(value);
                break;
            case "app_bar_opacity":
                mPreferences.setAppBarOpacity(value);
                break;
            default:
                break;
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch (key) {
            case "terminal_background_opacity":
                return mPreferences.getTerminalBackgroundOpacity();
            case "terminal_blur_radius":
                return mPreferences.getTerminalBlurRadius();
            case "sessions_blur_radius":
                return mPreferences.getSessionsBlurRadius();
            case "extrakeys_blur_radius":
                return mPreferences.getExtraKeysBlurRadius();
            case "app_bar_opacity":
                return mPreferences.getAppBarOpacity();
            default:
                return defValue;
        }
    }

    @Override
    public void putString(String key, String value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch (key) {
            case "app_launcher_button_count":
                mPreferences.setAppLauncherButtonCount(DataUtils.getIntFromString(value, mPreferences.getAppLauncherButtonCount()));
                break;
            case "app_launcher_search_mode":
                mPreferences.setAppLauncherSearchMode(value);
                break;
            case "app_launcher_input_char":
                mPreferences.setAppLauncherInputChar(value);
                break;
            case "app_launcher_default_buttons":
                mPreferences.setAppLauncherDefaultButtons(value);
                break;
            case "app_launcher_bar_height":
                mPreferences.setAppLauncherBarHeightScale(DataUtils.getFloatFromString(value, mPreferences.getAppLauncherBarHeightScale()));
                break;
            case "app_launcher_icon_scale":
                mPreferences.setAppLauncherIconScale(DataUtils.getFloatFromString(value, mPreferences.getAppLauncherIconScale()));
                break;
            default:
                break;
        }
    }

    @Override
    public String getString(String key, String defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch (key) {
            case "app_launcher_button_count":
                return Integer.toString(mPreferences.getAppLauncherButtonCount());
            case "app_launcher_search_mode":
                return mPreferences.getAppLauncherSearchMode();
            case "app_launcher_input_char":
                return mPreferences.getAppLauncherInputChar();
            case "app_launcher_default_buttons":
                return mPreferences.getAppLauncherDefaultButtons();
            case "app_launcher_bar_height":
                return Float.toString(mPreferences.getAppLauncherBarHeightScale());
            case "app_launcher_icon_scale":
                return Float.toString(mPreferences.getAppLauncherIconScale());
            default:
                return defValue;
        }
    }

    private void syncBackgroundOverlayColor(int opacityPercent, Integer baseColorOverride) {
        int alpha = (int) ((DataUtils.clamp(opacityPercent, 0, 100) / 100f) * 255);
        Properties properties = loadTermuxProperties();
        String currentValue = properties.getProperty(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR);
        int baseColor = baseColorOverride != null ? baseColorOverride : TermuxSharedProperties.getBackgroundOverlayInternalPropertyValueFromValue(currentValue);
        if (mPreferences.isMonetOverlayEnabled()) {
            baseColor = getMonetSurfaceColor(baseColor);
        }
        int newColor = (baseColor & 0x00FFFFFF) | (alpha << 24);
        writeOverlayColorToProperties(String.format("#%08X", newColor));
    }

    private void setMonetBackgroundAndOverlayEnabled(boolean enabled) {
        if (enabled) {
            String current = getCurrentOverlayColorString();
            if (current != null && !current.isEmpty()) {
                mPreferences.setManualOverlayColor(current);
            }
        }
        mPreferences.setMonetBackgroundEnabled(enabled);
        mPreferences.setMonetOverlayEnabled(enabled);
        Integer manualOverride = null;
        if (!enabled) {
            String manualColor = mPreferences.getManualOverlayColor();
            if (manualColor != null && !manualColor.isEmpty()) {
                manualOverride = TermuxSharedProperties.getBackgroundOverlayInternalPropertyValueFromValue(manualColor);
            }
        }
        syncBackgroundOverlayColor(mPreferences.getTerminalBackgroundOpacity(), manualOverride);
        TermuxActivity.updateTermuxActivityStyling(mContext, true);
    }

    private String getCurrentOverlayColorString() {
        Properties properties = loadTermuxProperties();
        return properties.getProperty(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR);
    }

    private Properties loadTermuxProperties() {
        File propertiesFile = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        if (propertiesFile == null) {
            propertiesFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        }
        Properties properties = SharedProperties.getPropertiesFromFile(mContext, propertiesFile, null);
        return properties == null ? new Properties() : properties;
    }

    private void writeOverlayColorToProperties(@NonNull String overlayColor) {
        File propertiesFile = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        if (propertiesFile == null) {
            propertiesFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        }
        File parentDir = propertiesFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentDir.mkdirs();
        }
        List<String> lines = new ArrayList<>();
        boolean updated = false;
        if (propertiesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(propertiesFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("#") && trimmed.matches("^\\s*" + TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR + "\\s*=.*$")) {
                        lines.add(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR + "=" + overlayColor);
                        updated = true;
                    } else {
                        lines.add(line);
                    }
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read termux.properties", e);
            }
        }
        if (!updated) {
            lines.add(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR + "=" + overlayColor);
        }
        StringBuilder output = new StringBuilder();
        for (String line : lines) {
            output.append(line).append('\n');
        }
        FileUtils.writeTextToFile("termux.properties", propertiesFile.getAbsolutePath(), StandardCharsets.UTF_8, output.toString(), false);
    }

    @ColorInt
    private int getMonetSurfaceColor(@ColorInt int fallbackColor) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Use a mid-tone dynamic accent directly from Android Monet palette.
                return ContextCompat.getColor(mContext, android.R.color.system_accent1_500);
            }
            return ContextCompat.getColor(mContext, R.color.main_accent);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to resolve Monet surface color", e);
            return fallbackColor;
        }
    }
}
