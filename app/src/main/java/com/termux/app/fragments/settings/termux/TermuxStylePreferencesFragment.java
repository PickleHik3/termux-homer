package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import com.termux.R;
import com.termux.app.style.TermuxBackgroundManager;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

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
                mPreferences.setMonetBackgroundEnabled(value);
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
}
