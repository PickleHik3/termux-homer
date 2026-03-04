package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.PrivilegedPolicyStore;
import com.termux.privileged.ShizukuBackend;

@Keep
public class PrivilegedAccessPreferencesFragment extends PreferenceFragmentCompat {

    private static final String KEY_STATUS = "privileged_backend_status";
    private static final String KEY_REQUEST_PERMISSION = "priv_request_shizuku_permission";
    private static final String KEY_MASTER = PrivilegedPolicyStore.KEY_MASTER_ENABLED;
    private static final String KEY_PREFER_SHIZUKU = PrivilegedPolicyStore.KEY_PREFER_SHIZUKU;
    private static final String KEY_ALLOW_SHELL = PrivilegedPolicyStore.KEY_ALLOW_SHELL_FALLBACK;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(PrivilegedAccessPreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_privileged_access_preferences, rootKey);

        configurePermissionRequestAction(context);
        configureBackendToggleRefresh(context, KEY_MASTER);
        configureBackendToggleRefresh(context, KEY_PREFER_SHIZUKU);
        configureBackendToggleRefresh(context, KEY_ALLOW_SHELL);
        refreshStatusSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatusSummary();
    }

    private void configurePermissionRequestAction(@NonNull Context context) {
        Preference preference = findPreference(KEY_REQUEST_PERMISSION);
        if (preference == null)
            return;

        preference.setOnPreferenceClickListener(clicked -> {
            PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
            boolean requested = manager.requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);
            String message = requested ?
                getString(R.string.termux_privileged_request_permission_requested) :
                getString(R.string.termux_privileged_request_permission_unavailable);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            refreshStatusSummary();
            return true;
        });
    }

    private void configureBackendToggleRefresh(@NonNull Context context, @NonNull String key) {
        Preference preference = findPreference(key);
        if (preference == null)
            return;

        preference.setOnPreferenceChangeListener((changed, newValue) -> {
            PrivilegedBackendManager.getInstance().reselectBackend().thenAccept(success -> {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(this::refreshStatusSummary);
                }
            });
            return true;
        });
    }

    private void refreshStatusSummary() {
        Preference statusPreference = findPreference(KEY_STATUS);
        if (statusPreference == null)
            return;

        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        statusPreference.setSummary(manager.getStatusDescription());
    }
}

class PrivilegedAccessPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;
    private static PrivilegedAccessPreferencesDataStore mInstance;

    private PrivilegedAccessPreferencesDataStore(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized PrivilegedAccessPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new PrivilegedAccessPreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (key == null)
            return;

        switch (key) {
            case PrivilegedPolicyStore.KEY_MASTER_ENABLED:
                PrivilegedPolicyStore.setMasterEnabled(mContext, value);
                break;
            case PrivilegedPolicyStore.KEY_PREFER_SHIZUKU:
                PrivilegedPolicyStore.setPreferShizuku(mContext, value);
                break;
            case PrivilegedPolicyStore.KEY_ALLOW_SHELL_FALLBACK:
                PrivilegedPolicyStore.setShellFallbackEnabled(mContext, value);
                break;
            case PrivilegedPolicyStore.KEY_ENDPOINT_REQUEST_PERMISSION:
                PrivilegedPolicyStore.setEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.REQUEST_PERMISSION, value);
                break;
            case PrivilegedPolicyStore.KEY_ENDPOINT_EXEC:
                PrivilegedPolicyStore.setEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.EXEC, value);
                break;
            case PrivilegedPolicyStore.KEY_ENDPOINT_BRIGHTNESS:
                PrivilegedPolicyStore.setEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.BRIGHTNESS, value);
                break;
            case PrivilegedPolicyStore.KEY_ENDPOINT_VOLUME:
                PrivilegedPolicyStore.setEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.VOLUME, value);
                break;
            case PrivilegedPolicyStore.KEY_ENDPOINT_LOCK_SCREEN:
                PrivilegedPolicyStore.setEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.LOCK_SCREEN, value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (key == null)
            return defValue;

        switch (key) {
            case PrivilegedPolicyStore.KEY_MASTER_ENABLED:
                return PrivilegedPolicyStore.isMasterEnabled(mContext);
            case PrivilegedPolicyStore.KEY_PREFER_SHIZUKU:
                return PrivilegedPolicyStore.isPreferShizuku(mContext);
            case PrivilegedPolicyStore.KEY_ALLOW_SHELL_FALLBACK:
                return PrivilegedPolicyStore.isShellFallbackEnabled(mContext);
            case PrivilegedPolicyStore.KEY_ENDPOINT_REQUEST_PERMISSION:
                return PrivilegedPolicyStore.isEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.REQUEST_PERMISSION);
            case PrivilegedPolicyStore.KEY_ENDPOINT_EXEC:
                return PrivilegedPolicyStore.isEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.EXEC);
            case PrivilegedPolicyStore.KEY_ENDPOINT_BRIGHTNESS:
                return PrivilegedPolicyStore.isEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.BRIGHTNESS);
            case PrivilegedPolicyStore.KEY_ENDPOINT_VOLUME:
                return PrivilegedPolicyStore.isEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.VOLUME);
            case PrivilegedPolicyStore.KEY_ENDPOINT_LOCK_SCREEN:
                return PrivilegedPolicyStore.isEndpointEnabled(mContext, PrivilegedPolicyStore.Endpoint.LOCK_SCREEN);
            default:
                return defValue;
        }
    }

    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        return defValue;
    }
}
